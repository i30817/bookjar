package i3.download;

/**
 *
 * @author i30817
 */
class CancelledState extends  State {

    private final long bytesRead;

    public CancelledState(long bytesRead, long expectedSizeInBytes, String mimeType, Exception error) {
        super(expectedSizeInBytes, mimeType, error);
        this.bytesRead = bytesRead;
    }

    public long getBytesRead() {
        return bytesRead;
    }

    public long getTimeRemaining(){
        return -1L;
    }

    public boolean isCancelled(){
        return true;
    }

    public boolean isDone() {
        return false;
    }

    State cancel(Exception error){
        return this;
    }

    State next(long bytesRead, long timeRemaining, long expectedSizeInBytes){
        return this;
    }

    public boolean isStartState(){
        return false;
    }


}
