package i3.decompress;

import i3.io.IoUtils;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A selection fluent interface, methods which return 'Selector' (except the
 * factory) always return 'this'.
 *
 * clients can use it like this:
 *
 * <b>selector.selectBySuffix(".rtf", false).select(Content.CRC32,
 * Inequalities.equal(crc));</b>
 *
 * This has a implied or, meaning both all rtf files and files with the crc are
 * selected.
 *
 * <b>selector.selectBySuffix(".rtf", false).limit().select(Content.CRC32,
 * Inequalities.equal(crc));</b>
 *
 * This is requires that the files to be extracted be a rtf, and have the crc.
 *
 * <b>selector.selectBySuffix(".rtf", false).invert().select(Content.CRC32,
 * Inequalities.equal(crc));</b>
 *
 * This requires that the files to be extracted are not a rtf and have the crc.
 *
 * The selectXXX() methods will not add results if you try to use them on a file
 * where XXX is not supported. A warning will be logged in these cases.
 *
 * To order the results use the orderBy(Ascending|Descending)(Comparator c).
 *
 * To get the FileViews call the iterator methods or the
 * getSelected(Min|Max)(Comparator c).
 *
 * @author i30817
 */
@SuppressWarnings(value = "unchecked")
public final class Selector implements Closeable, Iterable<FileView> {

    private static ServiceLoader<ExtractorProvider> loader = ServiceLoader.load(ExtractorProvider.class);

    /**
     * Get a selection for a local file.
     *
     * @param fileToExtract
     * @return a valid view over the file or null if no provider found
     * @throws ExtractionException if file is not valid (not found, corrupt
     * etc).
     */
    public static Selector from(Path localFile) throws ExtractionException {
        try {
            for (ExtractorProvider c : loader) {
                if (c.acceptFile(localFile.getFileName().toString())) {
                    return new Selector(c.create(localFile));
                }
            }
        } catch (ExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExtractionException(e);
        }
        return null;
    }

    /**
     * Get a selection for a url. If the url does not point to a local file, a
     * copy is downloaded to the tmp directory.
     *
     * @param fileToExtract
     * @return a valid view over the file or null if no provider found.
     * @throws ExtractionException if file is not valid (not found, corrupt
     * etc).
     */
    public static Selector from(URL urlToExtract) throws ExtractionException {
        try {
            for (ExtractorProvider c : loader) {
                if (c.acceptFile(IoUtils.getName(urlToExtract))) {
                    Path f = IoUtils.downloadToLocalFile(urlToExtract);
                    return new Selector(c.create(f));
                }
            }
        } catch (Exception e) {
            throw new ExtractionException(e);
        }
        return null;
    }

    /**
     * One of the extractors accepts this kind of file
     */
    public static boolean acceptsFile(String filename) {
        for (ExtractorProvider e : loader) {
            if (e.acceptFile(filename)) {
                return true;
            }
        }
        return false;
    }
    private List headers;
    private Extractor extractor;
    private Set workSet;

    public Selector(Extractor extractor) {
        this(extractor, extractor.getFileHeaders());
    }

    private Selector(Extractor extractor, List headers) {
        this.headers = headers;
        this.extractor = extractor;
        this.workSet = new LinkedHashSet(headers.size() * 2);
    }

    /**
     * Return if selector is empty (max number of selectable files == 0)
     *
     * @return is empty
     */
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    /**
     * Return the number of selected files
     *
     * @return number of selected files
     */
    public int selectedSize() {
        return workSet.size();
    }

    /**
     * Return the number of selectable files
     *
     * @return number of files in the archive
     */
    public int size() {
        return headers.size();
    }

    /**
     * Clears the selected files
     */
    public void clear() {
        workSet.clear();
    }

    /**
     * You can only get InputStreams from the returned contents before close()
     * is called.
     *
     * @throws java.io.IOException
     */
    @Override
    public void close() throws IOException {
        if (extractor == null) {
            return;
        }
        extractor.close();
        headers = null;
        workSet = null;
        extractor = null;
    }

    /**
     * The selector selected files will be the only files selectable in the
     * future and resets the selection
     *
     * It's currently not possible to reset discarded files by this method in
     * the same selector
     *
     * @return this
     */
    public Selector limit() {
        if (workSet.isEmpty()) {
            headers = Collections.EMPTY_LIST;
        } else if (workSet.size() != headers.size()) {
            headers.retainAll(workSet);
        }
        workSet.clear();
        return this;
    }

    /**
     * The selector not selected files will be the only files selectable in the
     * future and resets the selection
     *
     * It's currently not possible to reset discarded files by this method in
     * the same selector
     *
     * @return this
     */
    public Selector invert() {
        if (workSet.size() == headers.size()) {
            headers = Collections.EMPTY_LIST;
        } else if (!workSet.isEmpty()) {
            headers.removeAll(workSet);
        }
        workSet.clear();
        return this;
    }

    /**
     * Tries to reserve for extraction all selectable files
     *
     * @return this
     */
    public Selector selectAll() {
        workSet.addAll(headers);
        return this;
    }

    /**
     * Tries to reserve for extraction all the files where ineqValue Inequality
     * FileExaminer.examine(current) == true See the Contents enum for a lot of
     * arguments for this.
     *
     * @param ineq inequality given
     * @param examiner the date
     * @return this
     */
    public <T extends Comparable<T>> Selector select(FileExaminer<T> examiner, Inequality<T> ineq) {
        if (ineq == null || examiner == null) {
            return this;
        }

        for (Object h : headers) {
            T property = examiner.examine(extractor, h);
            if (property == null) {
                continue;
            }
            if (ineq.check(property)) {
                workSet.add(h);
            }
        }
        return this;
    }

    /**
     * Tries to reserve for extraction all the files with a filename that match
     * the given regex.
     *
     * @param regex
     * @return this
     * @throws PatternSyntaxException if the regex is invalid
     */
    public Selector selectByRegex(String regex) {
        if (regex == null) {
            return this;
            //name never null
        }
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher("");
        for (Object h : headers) {
            m.reset(extractor.getFileName(h));
            if (m.matches()) {
                workSet.add(h);
            }
        }
        return this;
    }

    /**
     * Tries to reserve for extraction all the files with a filename that match
     * the given regex.
     *
     * @param regex
     * @param int java regex flags (from java.​util.​regex.​Pattern)
     * @return this
     * @throws PatternSyntaxException if the regex is invalid
     */
    public Selector selectByRegex(String regex, int flags) {
        if (regex == null) {
            return this;
            //name never null
        }
        Pattern p = Pattern.compile(regex, flags);
        Matcher m = p.matcher("");
        for (Object h : headers) {
            m.reset(extractor.getFileName(h));
            if (m.matches()) {
                workSet.add(h);
            }
        }
        return this;
    }

    /**
     * Tries to reserve for extraction all the files with a filepath that match
     * the given regex.
     *
     * @param regex
     * @param int java regex flags (from java.​util.​regex.​Pattern)
     * @return this
     * @throws PatternSyntaxException if the regex is invalid
     */
    public Selector selectByRegexPath(String regex, int flags) {
        if (regex == null) {
            return this;
            //name never null
        }
        Pattern p = Pattern.compile(regex, flags);
        Matcher m = p.matcher("");
        for (Object h : headers) {
            m.reset(extractor.getFilePath(h));
            if (m.matches()) {
                workSet.add(h);
            }
        }
        return this;
    }

    /**
     * Tries to reserve for extraction all the files with the given suffix.
     *
     * @param suffix
     * @param caseSensitive
     * @return this
     */
    public Selector selectBySuffix(String suffix, boolean caseSensitive) {
        if (suffix == null) {
            return this;
        }
        //Archived name is never null...
        if (caseSensitive) {
            for (Object h : headers) {
                if (extractor.getFilePath(h).endsWith(suffix)) {
                    workSet.add(h);
                }
            }
        } else {
            for (Object h : headers) {
                if (extractor.getFilePath(h).toLowerCase(Locale.ENGLISH).endsWith(suffix.toLowerCase(Locale.ENGLISH))) {
                    workSet.add(h);
                }
            }
        }
        return this;
    }

    /**
     * Order by ascending content.
     *
     * @param c content comparator
     * @return this
     */
    public Selector orderByAscending(FileComparator c) {
        Object[] a = workSet.toArray();
        Arrays.sort(a, c.getComparator(extractor));
        workSet = new LinkedHashSet(a.length * 2);
        for (int i = 0; i < a.length; i++) {
            workSet.add(a[i]);
        }
        return this;
    }

    /**
     * Gets the first selected or null
     *
     * @return the first selected or null if there is nothing selected (because
     * nothing was selected or the archive has no matching files)
     */
    public FileView getSelected() {
        Iterator it = workSet.iterator();
        if (it.hasNext()) {
            return new FileView(it.next(), extractor);
        } else {
            return null;
        }
    }

    /**
     * Get the largest content selected.
     *
     * @param c content comparator
     * @return the contents, null if there are no selected contents
     */
    public FileView getSelectedMax(FileComparator c) {
        try {
            Object header = Collections.max(workSet, c.getComparator(extractor));
            return new FileView(header, extractor);
        } catch (NoSuchElementException ex) {
            return null;
        }
    }

    /**
     * Get the smallest content selected.
     *
     * @param c content comparator
     * @return the contents, null if there are no selected contents
     */
    public FileView getSelectedMin(FileComparator c) {
        try {
            Object header = Collections.min(workSet, c.getComparator(extractor));
            return new FileView(header, extractor);
        } catch (NoSuchElementException ex) {
            return null;
        }
    }

    /**
     * Order by descending content.
     *
     * @param c content comparator
     * @return this
     */
    public Selector orderByDescending(FileComparator c) {
        Object[] a = workSet.toArray();
        Arrays.sort(a, Collections.reverseOrder(c.getComparator(extractor)));
        workSet = new LinkedHashSet(a.length * 2);
        for (int i = 0; i < a.length; i++) {
            workSet.add(a[i]);
        }
        return this;
    }

    /**
     * Extracts all the files specified. WARNING: if FileView.getInputStream()
     * was called call close() on it input stream given before trying to use the
     * next inputStream.
     *
     * @return A Iterator with the selected files.
     * @throws ConcurrentModificationException if Selector is modified while
     * iterating over the result.
     */
    @Override
    public Iterator<FileView> iterator() {
        return new ContentsIterator(workSet.iterator(), extractor);
    }

    @Override
    public String toString() {
        ArrayList notSelected = new ArrayList(headers.size());
        int maxName = 0, maxPath = 0;
        for (Object fileheader : headers) {
            if (!workSet.contains(fileheader)) {
                notSelected.add(fileheader);
            }
            FileView v = new FileView(fileheader, extractor);
            String name = v.getFileName();
            int size = name.length();
            maxName = maxName < size ? size : maxName;
            size = v.getFilePath().length();
            maxPath = maxPath < size ? size : maxPath;
        }
        String body = "| Y |  %c  | %-" + maxName + "s | %-" + maxPath + "s |%n";
        //extra is the fixed chars from start to %n
        int seperatorSize = maxName + maxPath + 17;
        String body2 = "| N |  %c  | %-" + maxName + "s | %-" + maxPath + "s |%n";

        ByteArrayOutputStream out;
        try (PrintStream p = new PrintStream(out = new ByteArrayOutputStream(1024), false, "UTF-8")) {
            String filename = extractor.getArchive().toString();
            printfTableSeperator(p, filename.length() + 4);
            p.printf("| %s |%n", filename);
            printfTableSeperator(p, filename.length() + 4);
            p.printf("| ∈ | F/D | %-" + maxName + "s | %-" + maxPath + "s |%n", "Name", "Path");
            printfTableSeperator(p, seperatorSize);
            for (Object fileheader : workSet) {
                FileView v = new FileView(fileheader, extractor);
                p.printf(body, v.isDirectory() ? 'D' : 'F', v.getFileName(), v.getFilePath());

            }
            for (Object fileheader : notSelected) {
                FileView v = new FileView(fileheader, extractor);
                p.printf(body2, v.isDirectory() ? 'D' : 'F', v.getFileName(), v.getFilePath());
            }
            printfTableSeperator(p, seperatorSize);
            p.flush();
            return out.toString("UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new AssertionError(ex);
        }
    }

    private void printfTableSeperator(final PrintStream p, int lineSize) {
        lineSize -= 2; //for the '+'
        p.printf("+");
        for (int i = 0; i < lineSize; i++) {
            p.print("-");
        }
        p.printf("+%n");
    }

    private static final class ContentsIterator implements Iterator<FileView> {

        private Iterator it;
        private Extractor extractor;

        private ContentsIterator(Iterator it, Extractor extractor) {
            this.it = it;
            this.extractor = extractor;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public FileView next() {
            return new FileView(it.next(), extractor);
        }

        @Override
        public void remove() {
            it.remove();
        }
    }
}
