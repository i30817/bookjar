package i3.download;

/**
 *
 * @author i30817
 */
class DoneState extends State {

    public DoneState(long finalSizeInBytes, String mimeType) {
        super(finalSizeInBytes, mimeType, null);
    }

    public long getBytesRead() {
        return getExpectedSize();
    }

    public int getProgress() {
        return 100;
    }

    public boolean isCancelled() {
        return false;
    }

    public long getTimeRemaining() {
        return 0;
    }

    public boolean isDone() {
        return true;
    }

    public boolean isStartState() {
        return false;
    }

    State cancel(Exception e) {
        return this;
    }

    State next(long bytesRead, long timeRemaining, long expectedSizeInBytes) {
        return this;
    }
}
