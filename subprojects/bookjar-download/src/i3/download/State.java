package i3.download;

/**
 * Immutable abstract class to hold the state
 * of a download at some point in time.
 * @author i30817
 */
public abstract class State {

    private Exception error;
    private long expectedSize;
    private final String mimeType;

    /**
     * Create a DownloadState
     * @param downloadURL
     * @param localFile
     * @param expectedSizeInBytes
     * @param mimeType
     * @throws DownloadException
     */
    public State(long expectedSizeInBytes, String mimeType, Exception error) {
        super();
        this.expectedSize = expectedSizeInBytes;
//        this.localFile = localFile;
//        this.url = downloadURL;
        this.mimeType = mimeType;
        this.error = error;
    }

    /**
     * A percentage of the download progress from 0 to 100,
     * -1 if progress is not known.
     * @return return -1 if not known, 0 to 100 if known.
     */
    public int getProgress() {
        if (!isRemoteSizeKnown()) {
            return -1;
        }
        return (int) (getBytesRead() / (double)getExpectedSize() * 100);
    }

    /**
     * @return gets a error that ocurred in the download
     * or null if none found.
     */
    public Exception getError() {
        return error;
    }

    /**
     * @return the mimeType, null if unknown
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * @return is the remote file size known
     */
    public boolean isRemoteSizeKnown() {
        return getExpectedSize() >= 0L;
    }

    /**
     * @return the expected size of the download, -1
     * if unknown
     */
    public long getExpectedSize() {
        return expectedSize;
    }

    /**
     * @return bytes read at the time this snapshot was create
     * getBytesRead() >= 0 && getBytesRead() <= getExpectedSize()
     */
    public abstract long getBytesRead();

    /**
     * @return expected time remaining for the download,
     * -1 if not known
     */
    public abstract long getTimeRemaining();

    /**
     * @return download is cancelled
     */
    public abstract boolean isCancelled();

    /**
     * @return the download is done.
     */
    public abstract boolean isDone();

    /**
     * Cancel the download
     * @return the new download state (doesn't
     * actually cancel, just creates the state
     * transition)
     */
    abstract State cancel(Exception error);

    /**
     * Next state, if possible
     * @return the new download state (doesn't
     * actually begin, just creates the state
     * transition)
     */
    abstract State next(long bytesRead, long timeRemaining, long expectedSizeInBytes);

    /**
     * @return download state is a start
     * state.
     */
    public abstract boolean isStartState();
}
