package i3.decompress;

import java.io.IOException;

/**
 * This is the boundary exception for reading compressed
 * files.
 * @author microbiologia
 */
public final class ExtractionException extends IOException {

    public ExtractionException(Throwable cause) {
        super(cause);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExtractionException(String message) {
        super(message);
    }
}
