package i3.decompress;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

/**
 * Implementations of this class should have no constructors. To use them,
 * register the class as a provider of this interface in a meta-inf file in the
 * classpath (like all other service providers) and use the Extractor.from(URL)
 * method.
 *
 * This is to be implemented to extract each kind of compressed archive. It
 * provides no facilities for compressing and/or adding into a compressed
 * archive. If the format doesn't support one of these methods it should log the
 * call.
 *
 * @author i30817
 */
public abstract class Extractor implements Closeable {

    /**
     * Return a collection of the header type that this extractor type uses.
     *
     * @return Collection
     */
    public abstract List getFileHeaders();

    /**
     * Return a InputStream on a implementation.
     *
     * @param headerObject (subclass header, as Object)
     * @return InputStream, never null.
     * @throws IOException if couldn't create inputstream
     */
    public abstract InputStream getInputStream(Object headerObject) throws IOException;

    /**
     * Writes a compressed archive file into a OutputStream.
     *
     * @param headerObject (subclass header, as Object)
     * @param OutputStream
     * @throws IOException if couldn't writeInto to outputstream
     */
    public abstract void writeInto(Object headerObject, OutputStream out) throws IOException;

    /**
     * Return the file path (archive directory + File.pathSeperator + FileName)
     * on a implementation of a kind of header
     *
     * @param headerObject (subclass header, as Object)
     * @return file path, never null.
     */
    public abstract String getFilePath(Object headerObject);

    /**
     * Return the name on a implementation of a kind of header
     *
     * @param headerObject (subclass header, as Object)
     * @return file path, never null.
     */
    public abstract String getFileName(Object headerObject);

    /**
     * Return the file modification date on a implementation of a kind of header
     *
     * @param headerObject (subclass header, as Object)
     * @return file creation date, null if unknown.
     */
    public abstract Date getModificationDate(Object headerObject);

    /**
     * Return the file size on a implementation of a kind of header
     *
     * @param headerObject (subclass header, as Object)
     * @return file size, null if unknown.
     */
    public abstract Long getFileSize(Object headerObject);

    /**
     * Return the compressed file size on a implementation of a kind of header
     *
     * @param headerObject (subclass header, as Object)
     * @return compressed file size, null if unknown.
     */
    public abstract Long getCompressedFileSize(Object headerObject);

    /**
     * Return the file CRC32 on a implementation of a kind of header
     *
     * @param headerObject (subclass header, as Object)
     * @return file CRC32, null if unknown.
     */
    public abstract Long getCRC32(Object headerObject);

    /**
     * Return if the file is a directory
     *
     * @param headerObject (subclass header, as Object)
     * @return file is directory
     */
    public abstract boolean isDirectory(Object headerObject);
}
