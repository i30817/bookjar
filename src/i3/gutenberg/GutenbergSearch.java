package i3.gutenberg;

import i3.io.IoUtils;
import i3.io.ProgressMonitorStream;
import static i3.thread.Threads.*;
import java.awt.Component;
import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipInputStream;
import javax.swing.JPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * This class is not thread safe. Multiple concurrent instances of it should not
 * be created.
 *
 * @author i30817
 */
public final class GutenbergSearch implements Closeable {

    public static final String MULTIPLE_DATA_SEPARATOR = "\n";

    static {
        System.setProperty("entityExpansionLimit", "1000000");
    }
    private final ExecutorService discardingSingleThreadIndexer = newFixedDiscardingExecutor("Indexer", 1, true);
    private final Path dir;
    private final Directory cacheDir;
    private volatile SearcherManager manager;
    private final EnglishAnalyzer englishAnalyzer = new EnglishAnalyzer(Version.LUCENE_41);

    @Override
    public void close() throws IOException {
        if (manager != null) {
            manager.close();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    /**
     * The file given must exist and be writable and readable...
     *
     * @param databaseDir
     */
    public GutenbergSearch(Path databaseDir) {
        Directory tmpDir = null;
        dir = databaseDir.resolve("gutenberg");
        try {
            tmpDir = FSDirectory.open(dir.toFile());
        } catch (IOException ex) {
            //if this happens the object is inconsistent and all other public
            //methods will fail with IOException, except isReady(). Good enough for me.
            LogManager.getLogger().error("lucene index error, other methods will fail", ex);
        }
        cacheDir = tmpDir;
    }

    /**
     * Is the search database ready to search?
     *
     * @return if the database is ready
     */
    public boolean isReady() {
        if (manager == null) {
            try {
                manager = new SearcherManager(cacheDir, null);
            } catch (IOException ex) {
                return false;
            }
        }
        return cacheDir != null && DirectoryReader.indexExists(cacheDir);
    }

    /**
     * Starts indexing the Gutenberg web site with JPanel view as a parent of
     * the progress panel on another thread. If called while it is already
     * indexing the call is ignored.
     *
     * @param view
     */
    public void startThreadedIndex(final JPanel view) {
        if (cacheDir == null) {
            LogManager.getLogger().error("could not index lucene database due to previous error");
            return;
        }

        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        prepare(view);
                    } catch (CorruptIndexException ex) {
                        LogManager.getLogger().warn("existing index format was corrupt, deleting index directory before retrying", ex);
                        //delete the corrupt index here because we just estabilished there are no readers - they'd throw too.
                        //(this is needed since IndexWriter throws IndexFormatTooOldException even if opened only CREATE mode)
                        IoUtils.deleteFileOrDir(dir);
                        //retry
                        prepare(view);
                    }
                    if (manager == null) {
                        //don't expect a exception here since the index was writen above
                        manager = new SearcherManager(cacheDir, null);
                    }
                    IndexSearcher s = manager.acquire();
                    try {
                        //change the reader if the index changed
                        manager.maybeRefreshBlocking();
                    } finally {
                        manager.release(s);
                    }
                } catch (IOException e) {
                    LogManager.getLogger().warn("indexing interrupted", e);
                }
            }
        };
        discardingSingleThreadIndexer.execute(r);
    }

    private void prepare(final Component parent) throws IOException {
        ProgressMonitorStream stream = null;
        ZipInputStream zp = null;
        IndexWriter indexWriter = new IndexWriter(cacheDir,
                new IndexWriterConfig(Version.LUCENE_41, englishAnalyzer).
                setOpenMode(IndexWriterConfig.OpenMode.CREATE));
        try {
            URL u = new URL("http://www.gutenberg.org/feeds/catalog.rdf.zip");
//            u = IoUtils.toURL(new File("/home/paulo/Downloads/catalog.rdf.zip"));
            stream = ProgressMonitorStream.create(u, "Indexing Project Gutenberg", parent);
            zp = new ZipInputStream(stream);
            zp.getNextEntry();
            GutenbergRDFParser parser = new GutenbergRDFParser(indexWriter);
            parser.parse(zp);
            indexWriter.close();
        } catch (IOException ex) {
            //see IndexWriter.close() javadoc, the second close is needed
            try {
                indexWriter.close();
            } finally {
                if (IndexWriter.isLocked(cacheDir)) {
                    IndexWriter.unlock(cacheDir);
                }
            }
            throw ex;
        } finally {
            IoUtils.close(stream, zp);
        }
    }

    /**
     * Query the existing index. The property keys used are "author & author2
     * ... & authorN", "title", "language", and "metadata" that has the
     * mimetype, extent and url separated by spaces.
     *
     * @param bookQuery The terms to search books for, if null or empty nop.
     * @param subjects the subjects to search If not null and not empty it will
     * filter the results of bookQuery except if bookQuery is nop, where it will
     * search subjects instead. If null or empty, nop (any subject).
     * @param languageLocale the language of the books to search If null or
     * Locale.ROOT, nop (any language).
     * @param maxHits maximum number of returned hits
     * @param callback a callback to use the returned documents
     * @throws a exception if a error occurred connecting to the database or
     * querying it.
     */
    public void query(String bookQuery, String subjects, Locale languageLocale, int maxHits, SearchCallback callback) throws IOException, ParseException {
        if (!isReady()) {
            LogManager.getLogger().error("could not query lucene database due to previous error");
            return;
        }
        //preprocessing
        boolean shouldSearchSubjects = subjects != null && !subjects.isEmpty();
        boolean shouldSearchBooks = bookQuery != null && !bookQuery.isEmpty();
        boolean shouldSearchLanguage = languageLocale != Locale.ROOT && languageLocale != null;
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
            return;
        }

        QueryParser parser = null;
        Query query = null;
        if (shouldSearchBooks) {
            parser = new MultiFieldQueryParser(Version.LUCENE_41, new String[]{"title", "creator"}, englishAnalyzer);
            parser.setAutoGeneratePhraseQueries(true);
            query = parser.parse(bookQuery);
//            System.out.println(query.toString()+" | "+bookQuery);
        }
        if (shouldSearchSubjects) {
            if (parser == null) {
                parser = new QueryParser(Version.LUCENE_41, "subject", englishAnalyzer);
                parser.setAutoGeneratePhraseQueries(true);
                query = parser.parse(subjects);
            } else {
                Filter f = new QueryWrapperFilter(parser.parse("subject:" + subjects));
                query = new FilteredQuery(query, f);
            }
        }

        if (shouldSearchLanguage) {
            String filterLanguage = languageLocale.getLanguage();
            Filter f = new QueryWrapperFilter(parser.parse("language:" + filterLanguage));
            query = new FilteredQuery(query, f);
        }

        IndexSearcher s = manager.acquire();
        try {
            TopDocs docs = s.search(query, null, maxHits, Sort.INDEXORDER, false, false);
            for (ScoreDoc d : docs.scoreDocs) {
                if (!callback.shouldContinue(s.doc(d.doc))) {
                    break;
                }
            }
        } finally {
            manager.release(s);
        }
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

    public static abstract class SearchCallback {

        public abstract boolean shouldContinue(Document d);
    }
}
