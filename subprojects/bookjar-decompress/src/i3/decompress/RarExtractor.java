package i3.decompress;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import i3.io.IoUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.apache.logging.log4j.LogManager;

@SuppressWarnings(value = "unchecked")
public final class RarExtractor extends Extractor {

    private static final NullOut nullOut = new NullOut();
    //next index that is ready to extract (for solid archives)
    private AtomicInteger nextIndex;
    //linearizes extractions
    private ExecutorService threadPool;
    private Archive rarArchive;
    private List<FileHeader> headers;

    static {
        //com.github.junrar.Archive prints and ignores some exceptions
        //(including a nullpointer without filename),
        //catch them so we can report to the user with the real filename/do something else
        java.util.logging.Logger.getLogger(com.github.junrar.Archive.class.getName())
                .addHandler(new Handler() {

                    @Override
                    public void publish(LogRecord record) {
                        Throwable t = record.getThrown();
                        if (t != null) {
                            throw new IllegalStateException("possibly corrupt or encrypted rar file", t);
                        }
                    }

                    @Override
                    public void flush() {
                    }

                    @Override
                    public void close() throws SecurityException {
                    }
                });
    }

    public RarExtractor() {
        //serviceloader constructor
    }

    public RarExtractor(Path fileToExtract) throws RarException, IOException {

        File f = fileToExtract.toFile();
        if (f == null) {
            throw new NullPointerException();
        }

        this.rarArchive = new Archive(fileToExtract.toFile());
        this.nextIndex = new AtomicInteger();
        this.threadPool = Executors.newSingleThreadExecutor(IoUtils.createThreadFactory(true, "UnrarExtractAux"));
        this.headers = rarArchive.getFileHeaders();
    }

    public List getFileHeaders() {
        return new ArrayList(headers);
    }

    public InputStream getInputStream(final Object headerObject) throws IOException {
        final InputStreamPipe in = new InputStreamPipe();
        final PipedOutputStream out = new PipedOutputStream(in);
        /**
         * For this to advance the the client needs to call close() on any
         * previous returned InputStream.
         */
        threadPool.execute(new ExtractToInputStreamTask(
                new WeakReference<>(rarArchive),
                headers, nextIndex, (FileHeader) headerObject,
                out, in));
        return in;
    }

    public void writeInto(Object headerObject, OutputStream out) throws IOException {
        try {
            extract(rarArchive, (FileHeader) headerObject, headers, nextIndex, out);
        } catch (RarException ex) {
            throw new IOException(ex);
        }
    }

    public String getFilePath(Object headerObject) {
        FileHeader rarHeader = (FileHeader) headerObject;
        if (rarHeader.isUnicode()) {
            return rarHeader.getFileNameW();
        } else {
            return rarHeader.getFileNameString();
        }
    }

    public String getFileName(Object headerObject) {
        FileHeader rarHeader = (FileHeader) headerObject;
        String name;
        if (rarHeader.isUnicode()) {
            name = rarHeader.getFileNameW();
        } else {
            name = rarHeader.getFileNameString();
        }
        //in rar the inner separator is always '\'
        return name.substring(name.lastIndexOf('\\') + 1);
    }

    public Date getModificationDate(Object headerObject) {
        FileHeader rarHeader = (FileHeader) headerObject;
        return rarHeader.getMTime();
    }

    public Long getFileSize(Object headerObject) {
        FileHeader rarHeader = (FileHeader) headerObject;
        return rarHeader.getFullUnpackSize();
    }

    public Long getCompressedFileSize(Object headerObject) {
        FileHeader rarHeader = (FileHeader) headerObject;
        return rarHeader.getFullPackSize();
    }

    public Long getCRC32(Object headerObject) {
        FileHeader rarHeader = (FileHeader) headerObject;
        //bug in the library
        return rarHeader.getFileCRC() & 0X00000000ffffffffL;
    }

    public boolean isDirectory(Object headerObject) {
        FileHeader rarHeader = (FileHeader) headerObject;
        return rarHeader.isDirectory();
    }

    public void close() throws IOException {

        if (rarArchive == null) {
            return;
        }
        try {
            rarArchive.close();
        } finally {
            //interrupt writer if possible
            threadPool.shutdownNow();
            headers = null;
            rarArchive = null;
            threadPool = null;
        }
    }

    private static void extract(Archive rarArchive, FileHeader headerObject, List<FileHeader> headers, AtomicInteger nextIndex, OutputStream out) throws RarException {
        if (!rarArchive.getMainHeader().isSolid()) {
            rarArchive.extractFile((FileHeader) headerObject, out);
            return;
        }
        int desiredIndex = headers.indexOf(headerObject);
        try {
            extractToNull(rarArchive, headers, nextIndex.get(), desiredIndex);
            rarArchive.extractFile((FileHeader) headerObject, out);
            nextIndex.set(desiredIndex + 1);
        } catch (Throwable e) {
            //reset solid extraction (to allow to try again).
            nextIndex.set(0);
            throw e;
        }
    }

    private static void extractToNull(Archive rarArchive, List<FileHeader> headers, int nextIndex, int desiredIndex) throws RarException {
        //Over pretended file
        if (nextIndex > desiredIndex) {
            nextIndex = 0;
        }

        for (; nextIndex < desiredIndex; nextIndex++) {
            rarArchive.extractFile(headers.get(nextIndex), nullOut);
        }
    }

    private static final class NullOut extends OutputStream {

        private void throwError() throws RuntimeException {
            throw new RuntimeException("Interrupted");
        }

        @Override
        public void write(int b) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                throwError();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                throwError();
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                throwError();
            }
        }
    }

    private static final class InputStreamPipe extends PipedInputStream {

        private volatile Throwable error;

        public Throwable getError() {
            return error;
        }

        public void setError(Throwable error) {
            this.error = error;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (error != null || Thread.currentThread().isInterrupted()) {
                throwError();
            }

            try {
                return super.read(b, off, len);
            } catch (InterruptedIOException e) {
                throwError();
            }
            return -1;
        }

        @Override
        public int read(byte[] b) throws IOException {
            if (error != null || Thread.currentThread().isInterrupted()) {
                throwError();
            }

            try {
                return super.read(b);
            } catch (InterruptedIOException e) {
                throwError();
            }
            return -1;
        }

        @Override
        public int read() throws IOException {
            if (error != null || Thread.currentThread().isInterrupted()) {
                throwError();
            }

            try {
                return super.read();
            } catch (InterruptedIOException e) {
                throwError();
            }
            return -1;
        }

        private void throwError() throws RuntimeException {
            throw new RuntimeException(error);
        }
    }

    private static final class ExtractToInputStreamTask implements Runnable {

        FileHeader headerObject;
        OutputStream out;
        InputStreamPipe in;
        WeakReference<Archive> weakArchive;
        AtomicInteger nextIndex;
        List<FileHeader> headers;

        public ExtractToInputStreamTask(WeakReference<Archive> weakArchive, List<FileHeader> headers, AtomicInteger nextIndex, FileHeader headerObject, OutputStream out, InputStreamPipe in) {
            this.headerObject = headerObject;
            this.out = out;
            this.in = in;
            this.nextIndex = nextIndex;
            this.weakArchive = weakArchive;
            this.headers = headers;
        }

        public void run() {
            //close the stream otherwise the reader waits forever
            try (OutputStream out = this.out) {
                Archive rar = weakArchive.get();
                if (rar != null) {
                    extract(rar, headerObject, headers, nextIndex, out);
                }
            } catch (Throwable ex) {
                //HACK: Its expected for the reader to close the stream... maybe before reading
                if ("Pipe closed".equals(ex.getMessage()) || (ex.getCause() != null && "Pipe closed".equals(ex.getCause().getMessage()))) {
                    LogManager.getLogger().info("rar client thread closed pipe");
                } else {
                    //Need to propagate to reader. See InputStreamPipe
                    in.setError(ex);
                }
            }
        }
    }
}
