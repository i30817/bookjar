package i3.decompress;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import i3.io.IoUtils;

/**
 * A selection fluent interface.
 * clients will use it like this:
 *
 * selector.selectBySuffix(".rtf", false).selectByModificationDate(LARGER_THAN, someDate);
 * This is an implied or, meaning both kind of archives are to be extraced.
 *
 * On the other hand you can use it like this:
 * selector.selectBySuffix(".rtf", false).subSelector().selectByModificationDate(LARGER_THAN, someDate);
 * Where the result of the first is filtered.
 *
 * The selectByXXX() methods will not add results if you try to use
 * them on a file where XXX is not supported. A warning will be raised in these cases.
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

    public static final Logger log = Logger.getAnonymousLogger();
    private static ServiceLoader<ExtractorProvider> loader = ServiceLoader.load(ExtractorProvider.class);

    /**
     * Get a selection for a local file.
     * @param fileToExtract
     * @return a valid view over the file or null if no provider found
     * @throws ExtractionException if file is not valid (not found, corrupt etc).
     */
    public static Selector from(Path localFile) throws ExtractionException {
        try {
            for (ExtractorProvider c : loader) {
                if (c.acceptFile(localFile.getFileName().toString())) {
                    return new Selector(c.create(localFile));
                }
            }
        } catch (Exception e) {
            throw new ExtractionException(e);
        }
        return null;
    }

    /**
     * Get a selection for a url. If the url does not
     * point to a local file, a copy is downloaded
     * to the tmp directory.
     * @param fileToExtract
     * @return a valid view over the file or null if no
     * provider found.
     * @throws ExtractionException if file is not valid (not found, corrupt etc).
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
     * Return if archive is empty
     * @return is empty
     */
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    /**
     * Return the number of selected files
     * @return number of selected files
     */
    public int selectedSize() {
        return workSet.size();
    }

    /**
     * Return the number of files in the archive
     * @return number of files in the archive
     */
    public int size() {
        return headers.size();
    }

    /**
     * Clears the selected archives
     */
    public void clear() {
        workSet.clear();
    }

    /**
     * You can only get InputStreams from the returned contents before
     * close() is called.
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
     * Creates a selector where the selected archives of the current Selector
     * are the only archives (to be) selectable by the returned Selector
     */
    public Selector subSelector() {
        if (workSet.size() == headers.size()) {
            workSet.clear();
            return this;
        } else if (workSet.isEmpty()) {
            return new Selector(extractor, Collections.EMPTY_LIST);
        }
        return new Selector(extractor, new ArrayList(workSet));
    }

    /**
     * Creates a selector where the not selected archives of the current Selector
     * are the only archives (to be) selectable by the returned Selector
     */
    public Selector inverseSelector() {
        if (workSet.isEmpty()) {
            return this;
        } else if (workSet.size() == headers.size()) {
            return new Selector(extractor, Collections.EMPTY_LIST);
        }
        //removing in arraylists is painfull
        LinkedList arr = new LinkedList(headers);
        arr.removeAll(workSet);
        return new Selector(extractor, arr);
    }

    /**
     * Tries to reserve for extraction all files
     * @return this
     */
    public Selector selectAll() {
        for (Object h : headers) {
            workSet.add(h);
        }
        return this;
    }

    /**
     * Tries to reserve for extraction all the files
     * where  ineqValue Inequality FileExaminer.examine(current) == true
     * See the Contents enum for a lot of arguments for this.
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
     * Tries to reserve for extraction all the files
     * with a filename that match the  given regex.
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
     * Tries to reserve for extraction all the files
     * with a filename that match the  given regex.
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
     * Tries to reserve for extraction all the files
     * with a filepath that match the  given regex.
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
     * @return the first selected or null if
     * there is nothing selected (because nothing was selected
     * or the archive has no matching files)
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
     * @param c content comparator
     * @return the contents, null if there
     * are no selected contents
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
     * @param c content comparator
     * @return the contents, null if there
     * are no selected contents
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
     * Extracts all the files specified.
     * WARNING: if FileView.getInputStream() was called
     * call close() on it input stream given before trying to use
     * the next inputStream.
     * @return A Iterator/Iterable with the contents of the selected files.
     * @throws ConcurrentModificationException if Selector is
     * modified while iterating over the result.
     */
    @Override
    public ContentsIterator iterator() {
        return new ContentsIterator(workSet.iterator(), workSet.size(), extractor);
    }

    /**
     * As iterator() only the first numberToExtract files specified.
     * WARNING: if FileView.getInputStream() was called
     * call close() on it input stream given before trying to use
     * the next inputStream.
     * @return A Iterator/Iterable with the contents of the selected files.
     * @throws ConcurrentModificationException if Selector is
     * modified while iterating over the result.
     */
    public ContentsIterator iterator(int numberToExtract) {
        int toExtract = Math.max(0, Math.min(numberToExtract, workSet.size()));
        return new ContentsIterator(workSet.iterator(), toExtract, extractor);
    }

    public static final class ContentsIterator implements Iterator<FileView>, Iterable<FileView> {

        private Iterator headerCopy;
        private Extractor extractor;
        private int size, index;

        private ContentsIterator(Iterator headerCopy, int size, Extractor extractor) {
            this.headerCopy = headerCopy;
            this.size = size;
            this.extractor = extractor;
        }

        @Override
        public Iterator<FileView> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return index < size;
        }

        @Override
        public FileView next() {
            if (hasNext()) {
                index++;
                return new FileView(headerCopy.next(), extractor);
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            headerCopy.remove();
        }
    }
}