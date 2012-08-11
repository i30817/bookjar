package i3.download;

/**
 *
 * @author i30817
 */
class RunningState extends State {

    private final long bytesRead;
    private final long timeRemaining;

    public RunningState(long bytesRead, long timeRemaining, long expectedSizeInBytes, String mimeType) {
        super(expectedSizeInBytes, mimeType, null);
        this.bytesRead = bytesRead;
        this.timeRemaining = timeRemaining;
    }

    public long getBytesRead() {
        return bytesRead;
    }

    public boolean isCancelled() {
        return false;
    }

    public long getTimeRemaining() {
        return timeRemaining;
    }

    public boolean isDone() {
        return false;
    }

    public boolean isStartState() {
        return false;
    }

    State cancel(Exception error) {
        return new CancelledState(getBytesRead(), getExpectedSize(), getMimeType(), error);
    }

    State next(long bytesRead, long timeRemaining, long expectedSizeInBytes) {
        if (bytesRead == expectedSizeInBytes) {
            return new DoneState(expectedSizeInBytes, getMimeType());
        }
        return new RunningState(bytesRead, timeRemaining, expectedSizeInBytes, getMimeType());
    }
}
