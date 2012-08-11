package i3.swing;

import java.awt.Component;
import java.awt.HeadlessException;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.AbstractAction;
import javax.swing.GroupLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public final class ProgressMonitor {

    private volatile long total = -1, current = 0;
    private volatile String status;
    private volatile boolean closed;
    private ProgressDialog view = null;
    private final UpdateView updateView = new UpdateView();

    /**
     * If total is < 0 then indeterminate == true
     * @param total
     * @param message to display
     */
    public ProgressMonitor(long total, String message) {
        setTotal(total);
        status = message;
    }

    /**
     * Starts the view on the edt eventually
     * and continues. This method should be
     * used outside the edt ONLY, since
     * the setVisible method of swing is blocking.
     * @param owner
     */
    public void startViewInEDT(final Component owner) {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Called from the edt!");
        }
        SwingUtilities.invokeLater(new CreateView(owner, total));
    }

    /**
     * Set a new total and changes the progressbar to determinate,
     * if >= 0 (-1 stays indeterminate)
     * @return
     */
    public void setTotal(long total) {
        if (total >= 0) {
            this.total = total;
            fireChangeEvent();
            SwingUtilities.invokeLater(new SetStreamSize());
        }
    }

    /**
     * Close and dispose the view if visible.
     */
    public void close() {
        if (!closed) {
            closed = true;
            fireChangeEvent();
            SwingUtilities.invokeLater(new DisposeView());
        }
    }

    /**
     * @return was closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Update the progress monitor and fire listeners
     * if the values changed.
     * @param status changed if != null
     * @param current numeric value for the
     * JProgressBar if total size is known
     */
    public void setCurrent(final String status, final long current) {
        if (current < 0) {
            throw new IllegalStateException("can't set a current value < 0");
        }
        boolean valueNotEquals = current != this.current;
        if (valueNotEquals) {
            this.current = current;
        }

        boolean statusNotEquals = status != null && !status.equals(this.status);
        if (statusNotEquals) {
            this.status = status;
        }

        if (valueNotEquals || statusNotEquals) {
            fireChangeEvent();
            SwingUtilities.invokeLater(updateView);
        }
    }

    /*--------------------------------[ ListenerSupport ]--------------------------------*/
    private List<ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private ChangeEvent ce = new ChangeEvent(this);

    public void addChangeListener(ChangeListener listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listeners.remove(listener);
    }

    private void fireChangeEvent() {
        for (ChangeListener listener : listeners) {
            listener.stateChanged(ce);
        }
    }

    /*-----View and View updated classes-----*/
    private class ProgressDialog extends JDialog {

        public final int MAXIMUM = 999999;
        JLabel statusLabel;
        JProgressBar progressBar;

        public ProgressDialog(Window owner, long size) throws HeadlessException {
            super(owner, DEFAULT_MODALITY_TYPE);
            init(size);
        }

        private void init(long size) {

            progressBar = new JProgressBar();
            progressBar.setMinimum(0);
            progressBar.setValue(0);
            progressBar.setIndeterminate(size < 0 ? true : false);
            progressBar.setMaximum(MAXIMUM);

            statusLabel = new JLabel();
            statusLabel.setText(status);
            statusLabel.setHorizontalTextPosition(SwingConstants.CENTER);
            Icon i = (Icon) UIManager.get("OptionPane.informationIcon");
            JLabel iconLabel = new JLabel(i);
            String cancelText = (String) UIManager.get("OptionPane.cancelButtonText");
            JButton cancelButton = new JButton(new AbstractAction(cancelText) {

                public void actionPerformed(ActionEvent e) {
                    close();
                }
            });

            JPanel panel = new JPanel();
            GroupLayout l = new GroupLayout(panel);
            panel.setLayout(l);
            l.setAutoCreateContainerGaps(true);
            l.setAutoCreateGaps(true);
            JPanel cancelPanel = new JPanel();
            cancelPanel.add(cancelButton);

            GroupLayout.ParallelGroup horizontal = l.createParallelGroup().addGroup(
                    l.createSequentialGroup().addComponent(iconLabel).addGroup(
                    l.createParallelGroup().addComponent(statusLabel).addComponent(progressBar))).addComponent(cancelPanel);
            GroupLayout.SequentialGroup vertical = l.createSequentialGroup().addGroup(
                    l.createParallelGroup().addComponent(iconLabel).addGroup(
                    l.createSequentialGroup().addComponent(statusLabel).addComponent(progressBar))).addComponent(cancelPanel);

            l.setHorizontalGroup(horizontal);
            l.setVerticalGroup(vertical);

            addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosed(WindowEvent e) {
                    close();
                }
            });
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            add(panel);
        }
    }

    private class UpdateView implements Runnable {

        @Override
        public void run() {
            if (view != null) {
                double max = total;
                if (max >= 0) {
                    int value = (int) ((current / max) * view.MAXIMUM);
                    view.progressBar.setValue(value);
                }
                view.statusLabel.setText(status);
            }
        }
    }

    private class SetStreamSize implements Runnable {

        public void run() {
            if (view != null) {
                view.progressBar.setIndeterminate(false);
                view.repaint();
            }
        }
    }

    private class CreateView implements Runnable {

        private final Component owner;
        private final long size;

        private CreateView(Component owner, long total) {
            this.owner = owner;
            this.size = total;
        }

        @Override
        public void run() {
            if (view == null) {
                Window window = SwingUtilities.getWindowAncestor(owner);
                view = new ProgressDialog(window, size);
                view.setLocationRelativeTo(window);
                view.pack();
                view.setVisible(true);
            }
        }
    }

    private class DisposeView implements Runnable {

        @Override
        public void run() {
            if (view != null) {
                view.dispose();
                view = null;
            }
        }
    }
}
