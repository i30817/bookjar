/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package i3.decompress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

/**
 * A containter of the compressed archive contents
 * @author i30817
 */
public final class FileView {

    private Object header;
    private Extractor delegator;

    FileView(Object header, Extractor delegator) {
        this.header = header;
        this.delegator = delegator;
    }

    /**
     * Return a InputStream of a File.
     * @return InputStream
     * @throws IOException if couldn't create the InputStream
     */
    public InputStream getInputStream() throws IOException {
        return delegator.getInputStream(header);
    }

    /**
     * Writes compressed archive into a OutputStream.
     * @param OutputStream
     * @throws IOException if couldn't writeInto to outputstream
     */
    public void writeInto(OutputStream out) throws IOException {
        delegator.writeInto(header, out);
    }

    /**
     * Return the file path of a File.
     * @return file name, never null.
     */
    public String getFilePath() {
        return delegator.getFilePath(header);
    }

    /**
     * Return the file name of a File.
     * @return file name, never null.
     */
    public String getFileName() {
        return delegator.getFileName(header);
    }

    /**
     * Return the file modification date on a implementation of a kind of header
     * @param headerObject (subclass header, as Object)
     * @return file modification date, null if unknown.
     */
    public Date getModificationDate() {
        return delegator.getModificationDate(header);
    }

    /**
     * Return the file size on a implementation of a kind of header
     * @param headerObject (subclass header, as Object)
     * @return file size, null if unknown.
     */
    public Long getFileSize() {
        return delegator.getFileSize(header);
    }

    /**
     * Return the compressed file size on a implementation of a kind of header
     * @param headerObject (subclass header, as Object)
     * @return compressed file size, null if unknown.
     */
    public Long getCompressedFileSize() {
        return delegator.getCompressedFileSize(header);
    }

    /**
     * Return the file CRC32 on a implementation of a kind of header
     * @param headerObject (subclass header, as Object)
     * @return file CRC32, null if unknown.
     */
    public Long getCRC32() {
        return delegator.getCRC32(header) & 0X00000000ffffffffL;
    }

    /**
     * Return if the file is a directory
     * @return file is directory
     */
    public boolean isDirectory() {
        return delegator.isDirectory(header);
    }
}
