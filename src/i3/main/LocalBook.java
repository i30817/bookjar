package i3.main;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import i3.parser.ParserListener;
import i3.parser.Property;
import i3.util.Strings;
import i3.util.Tuples;
import i3.swing.SearchIterator;

/**
 * Data store for book information, stored in
 * BookMarks
 * @author fc30817
 */
public final class LocalBook implements Book, Serializable {

    private final int index;
    private final float percentageRead;
    private final boolean gutenbergFile;
    private final String language;
    private transient Path file;
    private transient volatile Map<Property, Object> metadata;

    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        file = Paths.get((String) input.readObject(), (String) input.readObject(), input.readUTF());
    }

    private void writeObject(ObjectOutputStream output) throws IOException {
        output.defaultWriteObject();
        //there is a bug caused by a FileChooser File subclass serialization
        //after the shutdownhooks are run. Shouldn't be a problem now since using Path

        //assuming that books are normally placed in this tree:
        // branch
        //  -Author Name
        //      -Series
        //       -Book
        //a way to serialize the least is to try to get 3 dirs above the book,
        //so the 'most' common substring is shared. Then write out 2 dirs above and
        //finally the file.
        int max = Math.max(file.getNameCount() - 3, 0);
        Path upperBranch = file.getRoot().resolve(file.subpath(0, max));
        int max2 = Math.max(file.getNameCount() - 1, 0);
        Path middleBranch = file.subpath(max, max2);

        output.writeObject(upperBranch.toString().intern());
        output.writeObject(middleBranch.toString().intern());
        output.writeUTF(file.getFileName().toString());
    }

    public LocalBook(Path file, String language, int index, float percentageRead, boolean gutenbergFile) {
        this.language = language;
        this.file = file;
        this.index = index;
        this.percentageRead = percentageRead;
        this.gutenbergFile = gutenbergFile;
    }

    @Override
    public Tuples.T2<String[], String> authorsAndTitle() {
        return sanatizeFilename(file.getFileName().toString());
    }

    @Override
    public String toString() {
        return file + " read:" + percentageRead + "% isGutenberg: " + gutenbergFile;
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

    public LocalBook setLanguage(String language) {
        return new LocalBook(file, language, index, percentageRead, gutenbergFile);
    }

    public Path getFile() {
        return file;
    }

    public Integer getReadIndex() {
        return index;
    }

    public Float getReadPercentage() {
        return percentageRead;
    }

    public LocalBook setReadIndex(int readIndex) {
        return new LocalBook(file, getLanguage(), readIndex, percentageRead, gutenbergFile);
    }

    public LocalBook setReadPercentage(float readPercentage) {
        return new LocalBook(file, getLanguage(), index, readPercentage, gutenbergFile);
    }

    public boolean isGutenbergFile() {
        return gutenbergFile;
    }

    public Map<Property, Object> getMetadata() {
        if (metadata == null) {
            metadata = new EnumMap<>(Property.class);
            metadata.put(Property.REFORMAT, true);
            if (gutenbergFile) {
                metadata.put(Property.PARSER_LISTENER, new GutenbergTransformer(metadata));
            }
            //other properties put elsewhere
        }
        return metadata;
    }

    public URL getURL() {
        try {
            return file.toUri().toURL();
        } catch (MalformedURLException ex) {
            throw new AssertionError("Impossible!", ex);
        }
    }

    private static String authorPreprocessing(String authors) {
        //remove author name abreviations
        authors = authors.replaceAll("(?:\\s|^)\\p{Lu}(?:\\s|$)", " ");
        //other extranous things - removing digits
        //doesn't cause false positives for name.
        return authors.replaceAll("\\p{Digit}", "");
    }

    /**
     * encodes a way to parse the authors and title from a string.
     * this can handle author names separated by '&' or ' and ', and
     * can handle authors/title separators like '-'
     * so if you control these use them.
     */
    private static Tuples.T2<String[], String> sanatizeFilename(String bookName) {
        //strip extension
        bookName = Strings.subStringBeforeLast(bookName, '.');
        //strip groupings (used for series name, filetype, scanner)
        bookName = bookName.replaceAll("(?:\\[.*?\\])|(?:\\{.*?\\})|(?:\\(.*?\\))", "");
        //strip leading 0s
        bookName = bookName.replaceAll("(?:\\s|^)0+([^0])", " $1");
        //remove space equivalents before splitting
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

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LocalBook other = (LocalBook) obj;
        if (!Objects.equals(this.file, other.file)) {
            return false;
        }
        return true;
    }

    public int hashCode() {
        int hash = 5;
        hash = 67 * hash + Objects.hashCode(this.file);
        return hash;
    }
}
