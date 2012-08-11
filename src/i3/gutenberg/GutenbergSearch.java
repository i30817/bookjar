package i3.gutenberg;

import i3.main.Bookjar;
import java.awt.Component;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipInputStream;
import javax.swing.JPanel;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import i3.io.IoUtils;
import i3.io.ProgressMonitorStream;
import static i3.thread.Threads.*;

/**
 * This class is not thread safe. Multiple concurrent
 * instances of it should not be created.
 * @author microbiologia
 */
public final class GutenbergSearch implements Closeable {

    public static final String MULTIPLE_DATA_SEPARATOR = "\n";

    static {
        System.setProperty("entityExpansionLimit", "1000000");
    }
    private final Path cacheDir;
    private final Path lockFile;
    private final ExecutorService discardingSingleThreadIndexer = newFixedDiscardingExecutor("Indexer", 1, true);
    private volatile IndexWriter indexWriter;
    private volatile IndexSearcher indexReader;

    /**
     * The file given must exist and be writable and readable...
     * @param databaseDir
     */
    public GutenbergSearch(Path databaseDir) {
        cacheDir = databaseDir.resolve("gutenberg");
        lockFile = databaseDir.resolve("lock");
        boolean isIndexed = isIndexed();
        if (!isIndexed) {
            IoUtils.deleteFileOrDir(cacheDir);
            IoUtils.deleteFileOrDir(lockFile);
        }
    }

    /**
     * Is the search database ready to search?
     * @return f the database is ready
     */
    public boolean isIndexed() {
        return Files.exists(cacheDir) && !Files.exists(lockFile);
    }

    /**
     * Starts indexing the gutenberg website with JPanel view
     * as a parent of the progress panel.
     * If called while it is already indexing the call is ignored.
     * @param view
     */
    public void startIndexing(final JPanel view) {
        Runnable r = new Runnable() {

            @Override
            public void run() {
                try {
                    prepare(view);
                } catch (IOException e) {
                    Bookjar.log.log(Level.WARNING, "Indexing stopped", e);
                }
            }
        };
        discardingSingleThreadIndexer.execute(r);
    }

    private void prepare(final Component parent) throws IOException {
        boolean failed = false;
        ProgressMonitorStream stream = null;
        ZipInputStream zp = null;
        //init the lock
        Files.createFile(lockFile);
        try {
            URL u = new URL("http://www.gutenberg.org/feeds/catalog.rdf.zip");
//            u = IoUtils.toURL(new File("/home/paulo/Downloads/catalog.rdf.zip"));
            stream = ProgressMonitorStream.create(u, "Indexing Project Gutenberg", parent);
            IoUtils.deleteFileOrDir(cacheDir);
            zp = new ZipInputStream(stream);
            zp.getNextEntry();
            writeIndex(zp);
        } catch (IOException e) {
            failed = true;
            throw e;
        } finally {
            IoUtils.close(stream, zp);
            if (failed) {
                IoUtils.deleteFileOrDir(cacheDir);
            }
            //release the lock
            IoUtils.deleteFileOrDir(lockFile);
        }
    }

    private void writeIndex(InputStream xml) throws IOException {
        try {
            indexWriter = new IndexWriter(cacheDir.toString(), new StandardAnalyzer(), true);
            GutenbergRDFParser parser = new GutenbergRDFParser(indexWriter);
            parser.parse(xml);
            indexWriter.optimize();
        } finally {
            if (indexWriter != null) {
                indexWriter.close();
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (indexReader != null) {
            indexReader.close();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    /**
     * Query the existing index. The property keys used
     * are "author & author2 ... & authorN", "title", "language",
     * and "metadata" that has the mimetype,
     * extent and url separated by spaces.
     * @param bookQuery The terms to search books for, can not be null or empty if the subjects are too.
     * @param subjects the subjects to search in, if null or empty, any subjects permitted.
     * @param languageLocale the language of the books to search, if null,
     * any language permitted.
     * @throws a exception if a error occurred connecting to the database or querying it.
     * @return the Result.
     */
    public Hits query(String bookQuery, String subjects, Locale languageLocale) throws IOException, ParseException {
        boolean indexed = isIndexed();
        //first time
        if (indexed && indexReader == null) {
            indexReader = new IndexSearcher(cacheDir.toString());
        }
        if (!indexed) {
            return null;
        }

        //preprocessing
        boolean shouldSearchSubjects = subjects != null && !subjects.isEmpty();
        boolean shouldSearchBooks = bookQuery != null && !bookQuery.isEmpty();
        boolean shouldSearchLanguage = languageLocale != null;
        if (shouldSearchLanguage) {
            if (shouldSearchBooks) {
                bookQuery = bookQuery.toLowerCase(languageLocale);
                bookQuery = lowercaseUserQueryToLuceneQuery(bookQuery);
            }
            if (shouldSearchSubjects) {
                subjects = subjects.toLowerCase(languageLocale);
                subjects = lowercaseUserQueryToLuceneQuery(subjects);
            }
        } else {
            if (shouldSearchBooks) {
                bookQuery = bookQuery.toLowerCase();
                bookQuery = lowercaseUserQueryToLuceneQuery(bookQuery);
            }
            if (shouldSearchSubjects) {
                subjects = subjects.toLowerCase();
                subjects = lowercaseUserTopicToLuceneTopic(subjects);
            }
        }

        if (!shouldSearchSubjects && !shouldSearchBooks) {
            return null;
        }

        Analyzer analyzer = new StandardAnalyzer();
        QueryParser parser = null;
        Query query = null;
        if (shouldSearchBooks) {
            parser = new MultiFieldQueryParser(new String[]{"title", "creator"}, analyzer);
            query = parser.parse(bookQuery);
        }
        if (shouldSearchSubjects) {
            if (parser == null) {
                parser = new QueryParser("subject", analyzer);
                query = parser.parse(subjects);
            } else {
                Filter f = new QueryWrapperFilter(parser.parse("subject:" + subjects));
                query = new FilteredQuery(query, f);
            }
        }

        if (languageLocale != null) {
            String filterLanguage = languageLocale.getLanguage();
            Filter f = new QueryWrapperFilter(parser.parse("language:" + filterLanguage));
            query = new FilteredQuery(query, f);
        }

        return indexReader.search(query);
    }

    private String lowercaseUserTopicToLuceneTopic(String lowerCaseString) {
        lowerCaseString = QueryParser.escape(lowerCaseString);
        String[] words = lowerCaseString.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String word : words) {
            if (b.length() != 0) {
                b.append(" OR ");
            }
            b.append(word);
        }
        return b.toString().trim();
    }

    private String lowercaseUserQueryToLuceneQuery(String lowerCaseString) {
        lowerCaseString = QueryParser.escape(lowerCaseString);
        String[] words = lowerCaseString.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String word : words) {
            if (b.length() != 0) {
                b.append(" AND ");
            }
            b.append(word);
        }
        //this is kinda slow.
//        int len = b.length();
//        if (len != 0 && b.charAt(len - 1) != ' ') {
//            //match incomplete words
//            b.append("*");
//        }
        return b.toString().trim();
    }
}
