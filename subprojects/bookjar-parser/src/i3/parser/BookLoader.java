package i3.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javax.swing.text.StyledDocument;

/**
 * A BookLoader service provider interface. In this class, extension refers to
 * the file extension without the dot. The enum map is a argument of some
 * properties used by some implementations. The possible arguments for now are
 * in the enum PROPERTIES.
 *
 * @author microbiologia
 */
public abstract class BookLoader {

    private static ServiceLoader<BookLoader> loader = ServiceLoader.load(BookLoader.class);
    /**
     * Standardizes the parsed document
     */
    protected Reparser reParser = new Reparser();

    /**
     * Gets a book loader that can parse the given name (optional) + extension
     *
     * @param filename
     * @return
     */
    public static BookLoader forFileName(final String fileName) {
        if (fileName == null) {
            return null;
        }

        for (BookLoader cp : loader) {
            if (cp.accepts(fileName)) {
                return cp;
            }
        }
        //heroics...
        return new TxtLoader();
    }

    /**
     * Gets a book loader that can parse the given mimetype
     *
     * @param filename
     * @return
     */
    public static BookLoader forMimeType(final String mimeType) {
        if (mimeType == null) {
            return null;
        }

        for (BookLoader cp : loader) {
            if (cp.acceptsMimeType(mimeType)) {
                return cp;
            }
        }
        //heroics...
        return new TxtLoader();
    }

    /**
     * Lowercase set of the suported extensions (without the dot) Not all files
     * accepted by acceptFiles have a extension here for instance, compressed
     * files don't contribute to this, but nevertheless accept "rar" files
     *
     * @return a set of all suppported extensions of the plugins.
     */
    public static Set<String> allSupportedExtensions() {
        Set<String> list = new HashSet<String>();
        for (BookLoader cp : loader) {
            list.addAll(cp.supportedExtensions());
        }

        return list;
    }

    /**
     * One of the parsers accepts this kind of file
     */
    public static boolean acceptsFiles(String filename) {
        for (BookLoader e : loader) {
            if (e.accepts(filename)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The supported lowercase extensions for this loader (without the dot), if
     * any exists otherwise a empty set
     *
     * @return
     */
    public abstract Set<String> supportedExtensions();

    /**
     * If this BookLoader accepts reading the given filename
     *
     * @param filename
     * @return
     */
    protected abstract boolean accepts(final String fileName);

    /**
     * If this BookLoader accepts reading the given mimetype
     *
     * @param filename
     * @return
     */
    protected abstract boolean acceptsMimeType(final String mimeType);

    /**
     * This parses the text in the given string.
     */
    public StyledDocument create(final String string, Map<Property, Object> properties) {
        try {
            return create(new ByteArrayInputStream(string.getBytes("UTF-8")), properties);
        } catch (IOException ex) {
            throw new AssertionError("UTF-8 should always be supported", ex);
        }
    }

    /**
     * This parses the text in the given inputStream. Implementations should
     * assume UTF-8 except in the cases where you can probe for the charset.
     */
    public abstract StyledDocument create(InputStream reader, Map<Property, Object> properties) throws IOException;

    /**
     * This parses the text in the given file.
     *
     * @param streamOrigin the origin of the input
     * @param properties properties used by some loaders
     * @throws IOException
     */
    public abstract StyledDocument create(URL origin, Map<Property, Object> properties) throws IOException;
}
