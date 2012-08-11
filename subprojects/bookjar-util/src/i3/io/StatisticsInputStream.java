package i3.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * A input stream that calculates some statistics
 * as it reads
 * @author fc30817
 */
public class StatisticsInputStream extends InputStream {

    private volatile long totalBytesRead;
    private volatile long timeRemaing = -1;
    private long startTimeMillis;
    private final InputStream delegate;
    private final long expectedSize;


    /**
     *
     * @param delegate (to wrap)
     * @param expectedSize (expected file size, -1 is unknown)
     */
    public StatisticsInputStream(InputStream delegate, long expectedSize) {
        super();
        this.expectedSize = expectedSize;
        this.delegate = delegate;
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate.skip(n);
    }

    @Override
    public void reset() throws IOException {
        delegate.reset();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (startTimeMillis == 0) {
            startTimeMillis = System.currentTimeMillis();
        }
        int read = delegate.read(b, off, len);
        if (read != -1) {
            updateMeasure(read);
        }
        return read;
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (startTimeMillis == 0) {
            startTimeMillis = System.currentTimeMillis();
        }
        int read = delegate.read(b);
        if (read != -1) {
            updateMeasure(read);
        }
        return read;
    }

    public int read() throws IOException {
        if (startTimeMillis == 0) {
            startTimeMillis = System.currentTimeMillis();
        }
        int byteRead = delegate.read();
        if (byteRead != -1) {
            updateMeasure(1);
        }
        return byteRead;
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

    @Override
    public void mark(int readlimit) {
        delegate.mark(readlimit);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    /**
     * Called with the number of bytes read in the current
     * read (not the total).
     * @param bytesConsumed
     */
    protected void updateMeasure(int bytesConsumed) {
        totalBytesRead += bytesConsumed;
        long now = System.currentTimeMillis();
        long delta_T = now - startTimeMillis;
        //You'd think negative intervals couldn't happen. You'd be wrong.
        if (delta_T <= 0) {
            return;
        }
        //(time elapsed/dl'ed size)*size left
        if (expectedSize != -1L) {
            long remainingBytes = expectedSize - totalBytesRead;
            timeRemaing = (delta_T /  Math.round((double)totalBytesRead) ) * remainingBytes;
        }
    }

    public final long getBytesRead() {
        return totalBytesRead;
    }

    public final long getTimeRemaining() {
        return timeRemaing;
    }
}
