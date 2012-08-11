package i3.download;

/**
 *
 * @author microbiologia
 */
public class DownloadException extends Exception {

    public DownloadException(Exception e) {
        super(e);
    }

    public DownloadException(String message, Throwable cause) {
        super(message, cause);
    }

    public DownloadException(String message) {
        super(message);
    }


}
