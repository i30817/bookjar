package i3.main;

import i3.parser.ParserListener;
import i3.parser.Property;
import i3.swing.SearchIterator;
import i3.util.Strings;
import i3.util.Tuples;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/**
 * Almost-Immutable Data store for book information - a book is considered equal
 * to another if they have the same file name. Not the path, the filename.
 *
 * These pojos mutable part comes from the library dir, which is a static
 * property which can change and is accessed for real locations. Therefore
 * 'getURL' and 'getAbsoluteFile' should not be accessed while the book
 * 'notExists' at the peril of exceptions (it could return a 'imaginary' path,
 * but fail fast is better).
 *
 * @author fc30817
 */
public final class LocalBook implements Book, Serializable {

    private static final long serialVersionUID = -7345464131163339842L;
    private final int index;
    private final float percentageRead;
    private final boolean gutenbergFile;
    private final String language;
    //should be final but it's assigned by readObj (for writeObj performance)
    private transient Path file;
    private transient Map<Property, Object> metadata;//lazy init, not thread-safe
    //reconstructed  on library startup, or when trying to read detects a exception
    private final transient boolean broken;

    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        file = Paths.get((String) input.readObject(), (String) input.readUTF());
    }

    private void writeObject(ObjectOutputStream output) throws IOException {
        output.defaultWriteObject();
        //there is a bug caused by a FileChooser File subclass serialization
        //after the shutdownhooks are run. Shouldn't be a problem now since using Path

        //assuming that books are normally placed in these trees:
        // Library            or        Library
        //  -Author Name                 -Author Name
        //      -Series                   -Book
        //       -Book
        //
        //and that the local books are relative to Library (invariant), then we can do this
        Path parent = file.getParent();
        if (parent == null) {
            output.writeObject("");//ignored for Paths.get()
        } else {
            output.writeObject(parent.toString().intern());
        }
        output.writeUTF(file.getFileName().toString());
    }

    LocalBook(Path file, String language, int index, float percentageRead, boolean gutenbergFile, boolean broken) {
        this.language = language;
        this.file = file;
        this.index = index;
        this.percentageRead = percentageRead;
        this.gutenbergFile = gutenbergFile;
        this.broken = broken;
    }

    private LocalBook(Path file, String language, int index, float percentageRead, boolean gutenbergFile, boolean broken, Map<Property, Object> metadata) {
        this.language = language;
        this.file = file;
        this.index = index;
        this.percentageRead = percentageRead;
        this.gutenbergFile = gutenbergFile;
        this.broken = broken;
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return file.getFileName().toString();
    }

    public String getDisplayLanguage() {
        if (language == null) {
            return null;
        }
        return new Locale(language).getDisplayLanguage();
    }

    public String getLanguage() {
        return language;
    }

    public Path getRelativeFile() {
        //local books are relative to the library dir to allow dir mobility and dir watching
        return file;
    }

    public String getFileName() {
        return file.getFileName().toString();
    }

    public Integer getBookmark() {
        return index;
    }

    public Float getReadPercentage() {
        return percentageRead;
    }

    public LocalBook setRelativeFile(Path f) {
        return new LocalBook(f, language, index, percentageRead, gutenbergFile, broken, metadata);
    }

    public LocalBook setLanguage(String language) {
        return new LocalBook(file, language, index, percentageRead, gutenbergFile, broken, metadata);
    }

    public LocalBook setBookmark(int readIndex) {
        return new LocalBook(file, language, readIndex, percentageRead, gutenbergFile, broken, metadata);
    }

    public LocalBook setReadPercentage(float readPercentage) {
        assert !Float.isNaN(readPercentage) && !Float.isInfinite(readPercentage) && readPercentage >= 0D;
        return new LocalBook(file, language, index, readPercentage, gutenbergFile, broken, metadata);
    }

    public LocalBook setBroken(boolean b) {
        return new LocalBook(file, language, index, percentageRead, gutenbergFile, b, metadata);
    }

    /**
     * IsBroken is a less costly way to check for broken files than
     * book.notExists(). This is assured by a startup library task that checks
     * for broken files and a directory watcher that sets this too,
     *
     * @return if this was set to broken during program execution. Does not
     * check for if the library or the file exists.
     */
    public boolean isBroken() {
        return broken;
    }

    public boolean isGutenbergFile() {
        return gutenbergFile;
    }

    public synchronized Map<Property, Object> getMetadata() {
        if (metadata == null) {
            metadata = new EnumMap<>(Property.class);
            metadata.put(Property.REFORMAT, true);
            if (gutenbergFile) {
                metadata.put(Property.PARSER_LISTENER, new GutenbergTransformer(metadata));
            }
            //other properties put elsewhere
        }
        return Collections.synchronizedMap(metadata);
    }

    @Override
    public int hashCode() {//only check filename on purpose
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.file.getFileName());
        return hash;
    }

    @Override
    public boolean equals(Object obj) {//only check filename on purpose
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LocalBook other = (LocalBook) obj;
        if (!Objects.equals(this.file.getFileName(), other.file.getFileName())) {
            return false;
        }
        return true;
    }

    boolean haveEqualParents(LocalBook canonical) {
        Path parent = getRelativeFile().getParent();
        Path otherParent = canonical.getRelativeFile().getParent();
        return parent == otherParent || (parent != null && parent.equals(otherParent));
    }

    /**
     * ***** Non immutable parts start here ********
     */
    /**
     * A localfile doesn't exist, if the library doesn't exist; or the file does
     * not exist as a absolute file
     *
     * @return exists in the current library
     */
    public boolean notExists() {
        return !Library.rootAvailable || Files.notExists(Library.libraryRoot.resolve(file));
    }

    /**
     * @return absolute local URL, may not exist!
     */
    public URL getURL() {
        try {
            //local books are relative to the library dir to allow dir mobility and dir watching
            return getAbsoluteFile().toUri().toURL();
        } catch (MalformedURLException ex) {
            throw new AssertionError("Impossible!", ex);
        }
    }

    /**
     * @return absolute file path, may not exist!
     */
    public Path getAbsoluteFile() {
        //local books are relative to the library dir to allow dir mobility and dir watching
        return Library.libraryRoot.resolve(file);
    }

    /**
     * ****** Mutables end here ********
     */
    /**
     *
     * @return tuple with the best effort authors and title. The format parsed
     * is:
     *
     * AuthorName '&|and' 2ndAuthorName '-' Title.extension
     */
    @Override
    public Tuples.T2<String[], String> authorsAndTitle() {
        return sanatizeFilename(file.getFileName().toString());
    }

    private static String authorPreprocessing(String authors) {
        //remove author name abreviations
        authors = authors.replaceAll("(?:\\s|^)\\p{Lu}(?:\\s|$)", " ");
        //other extranous things - removing digits
        //doesn't cause false positives for name.
        return authors.replaceAll("\\p{Digit}", "");
    }

    /**
     * encodes a way to parse the authors and title from a string. this can
     * handle author names separated by '&' or ' and ', and can handle
     * authors/title separators like '-' so if you control these use them.
     */
    private static Tuples.T2<String[], String> sanatizeFilename(String bookName) {
        //strip extension
        bookName = Strings.subStringBeforeLast(bookName, '.');
        //strip groupings (used for series name, filetype, scanner)
        bookName = bookName.replaceAll("(?:\\[.*?\\])|(?:\\{.*?\\})|(?:\\(.*?\\))", "");
        //strip leading 0s
        bookName = bookName.replaceAll("(?:\\s|^)0+([^0])", " $1");
        //remove space/abrev. equivalents before splitting
        bookName = bookName.replaceAll("(?:_|\\.|,)", " ");
        //split
        String authors = "";
        int authorLastIndex = bookName.indexOf("- ");
        if (authorLastIndex != -1) {
            authors = authorPreprocessing(bookName.substring(0, authorLastIndex));
            bookName = bookName.substring(authorLastIndex + 2);
        }
        bookName = bookName.replaceAll("-", " ");
        //escape lucene special chars (don't use query escaper - it doesn't work)
        String escapeChars = "[\\|\\&\"\\\\+\\-\\!\\(\\)\\:\\^\\]\\{\\}\\~\\*\\?]";
        bookName = bookName.replaceAll(escapeChars, "");
        //split authors in "&", " and "
        String[] authorsArr = authors.split("&|(?: [Aa][Nn][Dd] )");
        //same thing for authors names after split
        for (int i = 0; i < authorsArr.length; i++) {
            authorsArr[i] = authorsArr[i].replaceAll(escapeChars, "").trim();
        }
        //        System.out.println(Arrays.toString(authorsArr)+" "+bookFileName+" "+Thread.currentThread().getName());
        return Tuples.createPair(authorsArr, bookName);
    }

    private static final class GutenbergTransformer implements ParserListener {

        private static final String START_TAG = "START OF THIS PROJECT GUTENBERG";
        private static final String END_TAG = "END OF THIS PROJECT GUTENBERG";
        private final MutableAttributeSet italicAttr = new SimpleAttributeSet();
        private final Map properties;

        public GutenbergTransformer(Map properties) {
            this.properties = properties;
            italicAttr.addAttribute(StyleConstants.Italic, true);
        }

        @Override
        public void startDocument(DefaultStyledDocument doc) {
        }

        @Override
        public void endDocument(DefaultStyledDocument doc) {

            SearchIterator s = new SearchIterator(START_TAG, doc, 0);
            if (!s.hasNext()) {
                assert false : "gutenberg file without license";
            }
            s.next();
            s.setSearchText("***");
            if (!s.hasNext()) {
                assert false : "malformed gutenberg license";
            }
            int textStartIndex = s.next() + 4; //plus \n
            s = new SearchIterator(END_TAG, doc, doc.getLength());
            if (!s.hasPrevious()) {
                assert false : "malformed gutenberg license";
            }
            s.previous();
            s.setSearchText("***");
            if (!s.hasPrevious()) {
                assert false : "malformed gutenberg license";
            }
            int endTagIndex = s.previous();

            try {
                //remove the gutenberg license from the model
                doc.remove(endTagIndex, doc.getLength() - endTagIndex);
                doc.remove(0, textStartIndex);

                //italicise gutenberg txt files
                String name = (String) properties.get(Property.EXTRACTED_FILENAME);
                if (!name.endsWith(".txt")) {
                    return;
                }

                s = new SearchIterator("_", doc, 0);
                List<Integer> antiClobber = new LinkedList<>();
                while (s.hasNext()) {
                    int start = s.next();
                    if (!s.hasNext()) {
                        assert false : "unclosed gutenberg italic _ at index " + start;
                    }
                    antiClobber.add(0, s.next());
                    antiClobber.add(0, start);
                }

                for (Iterator<Integer> i = antiClobber.iterator(); i.hasNext();) {
                    int start = i.next(), end = i.next();
                    String text = doc.getText(start + 1, end - start - 1);
                    doc.replace(start, end - start + 1, text, italicAttr);
                }
            } catch (BadLocationException ex) {
                ex.printStackTrace();
            }
        }
    }

}
