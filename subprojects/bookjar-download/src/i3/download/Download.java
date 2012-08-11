package i3.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Observable;
import java.util.logging.Logger;
import i3.io.StatisticsInputStream;

public final class Download extends Observable {

    private volatile StatisticsInputStream stream;
    private volatile State currentState;
    private Path localFile;
    private final URL url;
    public static final Logger log = Logger.getAnonymousLogger();

    /**
     * Create a download
     * @param downloadURL
     * @param localFile
     * @param expectedSizeInBytes
     * @param mimeType
     * @throws DownloadException
     */
    public Download(URL downloadURL, Path localFile, long expectedSizeInBytes, String mimeType) throws DownloadException {
        super();
        this.localFile = localFile;
        this.url = downloadURL;
        currentState = new BeginState(expectedSizeInBytes, mimeType);
    }

    /**
     * Create a download with a unknown mimeType
     * @param downloadURL
     * @param localFile
     * @param expectedSizeInBytes
     * @throws DownloadException
     */
    public Download(URL downloadURL, Path localFile, long expectedSizeInBytes) throws DownloadException {
        this(downloadURL, localFile, expectedSizeInBytes, null);
    }

    /**
     * Create a download with a unknown size and a unknown mimeType
     * @param downloadURL
     * @param localFile
     * @throws DownloadException
     */
    public Download(URL downloadURL, Path localFile) throws DownloadException {
        this(downloadURL, localFile, -1, null);
    }

    /**
     * The local file where the download is being put
     * @return
     */
    public Path getDownloadedFile() {
        return localFile;
    }

    /**
     * The origin of the download
     * @return
     */
    public URL getURL() {
        return url;
    }

    /**
     * Name of the downloaded file
     * @return
     */
    public String getName() {
        return getDownloadedFile().getFileName().toString();
    }

    /**
     * Cancel the download, only cancels if it is running
     * (not if done and not if not yet started)
     */
    public void cancel() {
        currentState = currentState.cancel(null);
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ioe) {
            }
        }
        setChanged();
        notifyObservers();
    }

    private void cancel(Exception error) {
        currentState = currentState.cancel(error);
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ioe) {
            }
        }
    }

    /**
     * This view of the current download state
     * is immutable, but only should be called
     * once per set of changes since its instance
     * can and will change between calls.
     * @return
     */
    public DownloadState getCurrentDownloadState() {
        return new ImmutableDownloadState(getName(), getURL(), getDownloadedFile(), currentState);
    }

    public void retry() throws IOException {
        if (!currentState.isCancelled()) {
            throw new IllegalStateException("To retry the download must be cancelled");
        }
        State st = currentState;
        currentState = new BeginState(st.getExpectedSize(), st.getMimeType());
        start();
    }

    /**
     * Start the download
     * @throws IOException
     */
    public void start() throws IOException {
        final State st = currentState;
        if (!st.isStartState()) {
            throw new IllegalStateException("Can't start a download twice, try cancel first");
        }
        final Path downloadLocalFile = getDownloadedFile();
        final URL remoteURL = getURL();
        final URLConnection conn = remoteURL.openConnection();

        final Runnable continuation = new Runnable() {

            @Override
            public void run() {
                State copy = currentState;
                if (copy.isCancelled()) {
                    throw new IllegalStateException("Closed stream");
                }

                if (!copy.isDone()) {
                    setChanged();
                    notifyObservers();
                    //only replace after notifying otherwise it could
                    //set the state to finished, obviating the if
                    currentState = copy.next(stream.getBytesRead(),
                            stream.getTimeRemaining(),
                            copy.getExpectedSize());
                }
            }
        };


        Runnable task = new Runnable() {

            public void run() {
                long size = st.getExpectedSize();
                //conn.setConnectTimeout(1500);
                if (size <= -1L) {
                    size = conn.getContentLengthLong();
                }

                try {
                    stream = new StatisticsInputStream(conn.getInputStream(), size);
                    Files.createDirectories(localFile.getParent());
                    //next after start
                    currentState = currentState.next(0L, 0L, size);
                    writeInto(stream, true, Files.newOutputStream(downloadLocalFile), true, 1024, continuation);
                } catch (IOException ex) {
                    cancel(ex);
                    return;
                } finally {
                    //if a exception is not thrown this is the end state
                    setChanged();
                    notifyObservers();
                }
            }
        };
        Thread t = new Thread(task, "Download Thread");
        t.setDaemon(true);
        t.start();
    }

    public static void writeInto(final InputStream input, boolean closeInput, final OutputStream output, boolean closeOutput, final int bufferSize, final Runnable continuation) throws IOException {
        final byte[] buffer = new byte[bufferSize];
        int n = 0;
        try {
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
                continuation.run();
            }
        } finally {
            if (closeInput && input != null) {
                input.close();
            }
            if (closeOutput && output != null) {
                output.close();
            }
        }
    }
}
