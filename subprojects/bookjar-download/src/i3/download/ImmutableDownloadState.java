package i3.download;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author fc30817
 */
final class ImmutableDownloadState implements DownloadState {

    private final String name;
    private final URL downloadURL;
    private final Path downloadedFile;
    private final State immutableState;

    public ImmutableDownloadState(String name, URL downloadURL, Path downloadedFile, State state) {
        this.name = name;
        this.downloadURL = downloadURL;
        this.downloadedFile = downloadedFile;
        this.immutableState = state;
    }

    public boolean isDone() {
        return immutableState.isDone() && Files.exists(downloadedFile);
    }

    public boolean isCancelled() {
        return immutableState.isCancelled();
    }

    public Path getDownloadedFile() {
        return downloadedFile;
    }

    public String getName() {
        return name;
    }

    public URL getURL() {
        return downloadURL;
    }

    public boolean isRemoteSizeKnown() {
        return immutableState.isRemoteSizeKnown();
    }

    public int getProgress() {
        return immutableState.getProgress();
    }

    public long getTimeRemaining() {
        return immutableState.getTimeRemaining();
    }

    public long getExpectedSize() {
        return immutableState.getExpectedSize();
    }

    public Exception getError() {
        return immutableState.getError();
    }

    public long getBytesRead() {
        return immutableState.getBytesRead();
    }

    public String getMimeType() {
        return immutableState.getMimeType();
    }


}
