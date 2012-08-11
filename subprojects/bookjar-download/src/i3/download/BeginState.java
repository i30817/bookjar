package i3.download;

/**
 *
 * @author i30817
 */
class BeginState extends State {

    /**
     * Create a BeginState
     * @param downloadURL
     * @param localFile
     * @param expectedSizeInBytes
     * @param mimeType
     * @param error can be null
     * @throws DownloadException
     */
    public BeginState(long expectedSizeInBytes, String mimeType) {
        super(expectedSizeInBytes, mimeType, null);
    }

    public long getBytesRead() {
        return 0L;
    }

    public long getTimeRemaining() {
        return -1L;
    }

    public boolean isCancelled() {
        return false;
    }

    public boolean isDone() {
        return false;
    }

    State cancel(Exception error) {
        return new CancelledState(getBytesRead(), getExpectedSize(), getMimeType(), error);
    }

    @Override
    State next(long bytesRead, long timeRemaining, long expectedSizeInBytes) {
        return new RunningState(bytesRead, timeRemaining, expectedSizeInBytes, getMimeType());
    }

    public boolean isStartState() {
        return true;
    }
}
