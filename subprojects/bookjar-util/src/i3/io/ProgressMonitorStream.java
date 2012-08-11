package i3.io;

import java.awt.Component;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import javax.swing.SwingUtilities;
import i3.net.AuthentificationProxySelector;
import i3.swing.ProgressMonitor;

/**
 * The normal progressmonitorstream initializes its progressmonitor
 * lazily, (no other configuration). That means it initializes it outside
 * the edt. The create method is thread unsafe.
 *
 * close() closes the view dialog also.
 */
public final class ProgressMonitorStream extends StatisticsInputStream {

    public static final int READ_TIMEOUT = 5000;
    private final ProgressMonitor pm;

    private ProgressMonitorStream(InputStream delegate, long expectedSize, ProgressMonitor progress) {
        super(delegate, expectedSize);
        this.pm = progress;
    }

    /**
     * WARNING - this method throws an exception if started from the EDT.
     * This is simply because using this stream in the EDT would block the
     * popup window that it creates.
     *
     * Popups a progress monitor dialog. This popup starts as indeterminate
     * and then, if the size becomes known, it is set to the appropriate
     * value.
     * @param url
     * @param message
     * @param parent
     * @throws IOException if there was a problem connecting to the resource.
     * @throws RuntimeException if the method is called from the EDT (don't,
     * it would take far too long to return even if it worked.
     */
    public static ProgressMonitorStream create(URL url, String message, Component parent) throws IOException {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("This method should not be called from the EDT");
        }

        ProgressMonitor p = new ProgressMonitor(-1, message);
        try {
            //only bother to show dialog if there is a network interface up
            if (!AuthentificationProxySelector.nonLocalNetworkUp()) {
                throw new IOException("No network");
            }
            final URLConnection conn = url.openConnection();
            conn.setReadTimeout(READ_TIMEOUT);
            p.startViewInEDT(parent);
            long max = conn.getContentLengthLong();
            p.setTotal(max);
            InputStream s = new BufferedInputStream(conn.getInputStream());
            return new ProgressMonitorStream(s, max, p);
        } catch (IOException ex) {
            p.close();
            throw ex;
        }
    }

    @Override
    protected void updateMeasure(int bytesConsumed) {
        super.updateMeasure(bytesConsumed);
        updateProgress(getBytesRead());
    }

    @Override
    public void close() throws IOException {
        super.close();
        pm.close();
    }

    private void updateProgress(long bytesRead) {
        if (pm.isClosed()) {
            IoUtils.close(this);
        } else {
            pm.setCurrent(null, bytesRead);
        }
    }

}
