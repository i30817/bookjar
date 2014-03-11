package i3.decompress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.apache.logging.log4j.LogManager;

public final class ZipExtractor extends Extractor {

    private ZipFile zipArchive;

    public ZipExtractor() {
        //serviceloader constructor
    }

    public ZipExtractor(Path fileToExtract) throws ZipException, IOException {
        zipArchive = new ZipFile(fileToExtract.toFile());
    }

    public void close() throws IOException {
        zipArchive.close();
    }

    public List getFileHeaders() {
        return Collections.list(zipArchive.entries());
    }

    public InputStream getInputStream(Object headerObject) throws IOException {
        ZipEntry header = (ZipEntry) headerObject;
        return zipArchive.getInputStream(header);
    }

    public void writeInto(Object headerObject, OutputStream out) throws IOException {
        try (InputStream in = getInputStream(headerObject)) {
            byte[] bts = new byte[1000];
            int i = 0;
            do {
                out.write(bts, 0, i);
                i = in.read(bts);
            } while (i != -1);
        }
    }

    public String getFilePath(Object headerObject) {
        ZipEntry header = (ZipEntry) headerObject;
        return header.getName();
    }

    public String getFileName(Object headerObject) {
        ZipEntry header = (ZipEntry) headerObject;
        String name = header.getName();
        //in zip the inner separator is always '\'
        return name.substring(name.lastIndexOf('\\') + 1);
    }

    public Date getModificationDate(Object headerObject) {
        ZipEntry header = (ZipEntry) headerObject;
        long time = header.getTime();
        if (time == -1) {
            LogManager.getLogger().error(
                    "the zip file has no modifcation date and you tried to get it or "
                    + "use it for selection - the result is no files added in OR predicates "
                    + "or all files removed in AND predicates!");
            return null;
        }

        return new Date(time);
    }

    public Long getFileSize(Object headerObject) {
        ZipEntry header = (ZipEntry) headerObject;
        long size = header.getSize();
        if (size == -1) {
            LogManager.getLogger().error(
                    "the zip file has no uncompressed file size  and you tried to get it or "
                    + "use it for selection - the result is no files added in OR predicates "
                    + "or all files removed in AND predicates!");
            return null;
        }

        return Long.valueOf(size);
    }

    public Long getCompressedFileSize(Object headerObject) {
        ZipEntry header = (ZipEntry) headerObject;
        long size = header.getCompressedSize();
        if (size == -1) {
            LogManager.getLogger().error(
                    "the zip file has no compressed file size  and you tried to get it or "
                    + "use it for selection - the result is no files added in OR predicates "
                    + "or all files removed in AND predicates!");
            return null;
        }

        return Long.valueOf(size);
    }

    public Long getCRC32(Object headerObject) {
        ZipEntry header = (ZipEntry) headerObject;
        long crc32 = header.getCrc();

        if (crc32 == -1) {
            LogManager.getLogger().error(
                    "the zip file has no file CRC32  and you tried to get it or "
                    + "use it for selection - the result is no files added in OR predicates "
                    + "or all files removed in AND predicates!");
            return null;
        }
        return Long.valueOf(crc32);
    }

    public boolean isDirectory(Object headerObject) {
        ZipEntry header = (ZipEntry) headerObject;
        return header.isDirectory();
    }
}
