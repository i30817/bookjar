package i3.download;

import java.net.URL;
import java.nio.file.Path;

/**
 * Interface to the (immutable copy of)
 * the mutable state
 * of the download at some time.
 * Every function here will return the same
 * value as long as you don't call the
 * method that produces this objects more
 * than once.
 * @author fc30817
 */
public interface DownloadState {

    /**
     * The local file where the download is being put
     * @return
     */
    Path getDownloadedFile();

    /**
     * Name of the downloaded file
     * @return
     */
    String getName();

    /**
     * The origin of the download
     * @return
     */
    URL getURL();

    /**
     * if download is cancelled
     * @return
     */
    boolean isCancelled();

    /**
     * Returns if download is complete
     * @return
     */
    boolean isDone();

    /**
     * A percentage of the download progress from 0 to 100, -1 if progress is not known.
     * @return -1 if not known, 0 to 100 if known.
     */
    int getProgress();

    boolean isRemoteSizeKnown();

    long getBytesRead();

    Exception getError();

    long getExpectedSize();

    long getTimeRemaining();

    String getMimeType();
}
