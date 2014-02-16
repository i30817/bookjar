package i3.swing.component;

import i3.download.Download;
import i3.download.DownloadException;
import i3.download.DownloadState;
import i3.download.DownloadView;
import java.awt.Desktop;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.ServiceLoader;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;

/**
 *
 * @author microbiologia
 */
public class DownloadsList<E> implements Observer {

    private final JScrollPane view;
    private final JList<ModelObject<E>> list;
    private final DefaultListModel<ModelObject<E>> model;
    private DownloadView<E> downloadView;

    /**
     * Creates a DownloadList.
     *
     * Uses the downloads.DownloadViev specified by the Meta-inf.
     */
    @SuppressWarnings({"unchecked", "cast"})//service loader doesn't typecheck
    public DownloadsList() {
        for (DownloadView<E> o : ServiceLoader.load(DownloadView.class)) {
            this.downloadView = o;
            break;
        }
        if (downloadView == null) {
            throw new IllegalStateException("Missing view implementation, check that a META-INF.services directory with a ServiceLoader glue file named i3.download.DownloadFile with a implementation location line exists in the classpath");
        }

        list = new WrappedList();
        list.setCellRenderer(this.downloadView);
        model = new DefaultListModel<>();
        list.setModel(model);
        list.addMouseListener(new ContextMenuMouseListener());
        view = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        list.setFixedCellWidth(this.downloadView.getFixedCellWidth() + view.getVerticalScrollBar().getPreferredSize().width);
        list.setFixedCellHeight(this.downloadView.getFixedCellHeight());
    }

    public JScrollPane getView() {
        return view;
    }

    /**
     * Get the download object corresponding to the url
     *
     * @param downloading
     * @return Download if any in the list with the same url
     */
    public DownloadState get(URL downloading) {
        for (int i = 0; i < model.getSize(); i++) {
            Download download = model.get(i).download;
            URL url = download.getURL();
            if (url.getPath().equals(downloading.getPath())) {
                return download.getCurrentDownloadState();
            }
        }
        return null;
    }

    private int getIndex(URL downloading) {
        for (int i = 0; i < model.getSize(); i++) {
            Download download = model.get(i).download;
            URL url = download.getURL();
            if (url.getPath().equals(downloading.getPath())) {
                return i;
            }
        }
        return -1;
    }

    public void add(E listValue, URL toDownload, Path localFile) throws IOException {
        add(listValue, toDownload, localFile, -1, null);
    }

    public void add(E listValue, URL toDownload, Path localFile, long expectedSize) throws IOException {
        add(listValue, toDownload, localFile, expectedSize, null);
    }

    public void add(E listValue, URL toDownload, Path localFile, long expectedSize, String mimeType) throws IOException {
        try {
            int current = getIndex(toDownload);
            if (current != -1) {
                list.ensureIndexIsVisible(current);
                list.setSelectedIndex(current);
                Download download = model.get(current).download;
                DownloadState state = download.getCurrentDownloadState();
                if (state.isCancelled()) {
                    download.retry();
                }
                return;
            }

            Download newDownload = new Download(toDownload, localFile, expectedSize, mimeType);
            newDownload.addObserver(this);
            model.add(0, new ModelObject<>(listValue, newDownload));
            list.ensureIndexIsVisible(0);
            list.setSelectedIndex(0);
            newDownload.start();
        } catch (DownloadException ex) {
            throw new IOException(ex);
        }
    }

    public void update(Observable o, Object arg) {
        Download d = (Download) o;
        DownloadState s = d.getCurrentDownloadState();
        if (s.isDone()) {
            int index = getIndex(s.getURL());
            ModelObject<E> m = model.get(index);
            //only should be called once when finished
            downloadView.finished(m.value, s);
        }
        view.repaint();
    }

    /**
     * Delegates to the chosen download view
     *
     * @param addedValue
     * @param state (needs to be done, assert if not and running in debug mode)
     */
    public void open(E addedValue, DownloadState state) {
        assert state.isDone();
        downloadView.open(addedValue, state);
    }

    private class ContextMenuMouseListener extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            int index = list.locationToIndex(e.getPoint());

            if (index != -1 && !list.isSelectedIndex(index)) {
                list.setSelectedIndex(index);
            }

            ModelObject<E> selected = list.getSelectedValue();
            if (selected == null) {
                return;
            }
            DownloadState state = selected.download.getCurrentDownloadState();
            if (e.getClickCount() > 1 && e.getButton() == MouseEvent.BUTTON1 && state.isDone()) {
                downloadView.open(selected.value, state);
                return;
            }

            if ((e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != MouseEvent.BUTTON3_DOWN_MASK) {
                return;
            }

            List<ModelObject<E>> downloads = list.getSelectedValuesList();

            Action cancel = new Cancel("Cancel", downloads);
            Action retry = new Retry("Retry", downloads);
            Action copyLink = new CopyLink("Copy download link", downloads);
            Action selectAll = new SelectAll("Select All");
            Action removeFromList = new Remove("Remove from list", downloads);
            Action openDirectoryAction = new OpenDirectory("Open folder", downloads);
            Action open = new Open("Open", selected);

            Action first;
            if (state.isDone()) {
                first = open;
            } else if (state.isCancelled()) {
                first = retry;
            } else {
                first = cancel;
            }

            JPopupMenu contextMenu = new JPopupMenu();
            contextMenu.add(new JMenuItem(first));
            contextMenu.add(new JMenuItem(openDirectoryAction));
            contextMenu.add(new JMenuItem(copyLink));
            contextMenu.add(new JMenuItem(selectAll));
            contextMenu.add(new JMenuItem(removeFromList));
            contextMenu.show(list, e.getX(), e.getY());
        }
    }

    private class Open extends AbstractAction {

        private final ModelObject<E> selected;

        public Open(String name, ModelObject<E> selected) {
            super(name);
            this.selected = selected;
        }

        public void actionPerformed(ActionEvent e) {
            assert selected.download.getCurrentDownloadState().isDone();
            downloadView.open(selected.value, selected.download.getCurrentDownloadState());
        }
    }

    private class OpenDirectory extends AbstractAction {

        private final List<ModelObject<E>> downloads;

        public OpenDirectory(String name, List<ModelObject<E>> downloads) {
            super(name);
            this.downloads = downloads;
        }

        public void actionPerformed(ActionEvent e) {
            if (!Desktop.isDesktopSupported()) {
                Download.log.info("Desktop unsupported, can't open the downloaded file");
                return;
            }
            final Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) {
                Download.log.info("Desktop open action unsupported, can't open the downloaded file");
                return;
            }

            for (ModelObject<E> d : downloads) {
                try {
                    Desktop.getDesktop().open(d.download.getDownloadedFile().getParent().toFile());
                } catch (IOException | NullPointerException ex) {
                    Download.log.info("Couldn't open parent folder of file " + d.download.getDownloadedFile());
                }
            }
        }
    }

    private class Remove extends AbstractAction {

        private final List<ModelObject<E>> downloads;

        public Remove(String name, List<ModelObject<E>> downloads) {
            super(name);
            this.downloads = downloads;
        }

        public void actionPerformed(ActionEvent e) {
            for (ModelObject<E> d : downloads) {
                DownloadState current = d.download.getCurrentDownloadState();
                if (current.isCancelled() || current.isDone()) {
                    DefaultListModel m = (DefaultListModel) list.getModel();
                    m.removeElement(d);
                    list.repaint();
                }
            }
        }
    }

    private class SelectAll extends AbstractAction {

        public SelectAll(String name) {
            super(name);
        }

        public void actionPerformed(ActionEvent e) {
            int end = list.getModel().getSize() - 1;
            if (end >= 0) {
                list.setSelectionInterval(0, end);
            }
        }
    }

    private class Cancel extends AbstractAction {

        private final List<ModelObject<E>> downloads;

        public Cancel(String name, List<ModelObject<E>> downloads) {
            super(name);
            this.downloads = downloads;
        }

        public void actionPerformed(ActionEvent e) {
            for (ModelObject<E> d : downloads) {
                d.download.cancel();
            }
        }
    }

    private class CopyLink extends AbstractAction {

        private final List<ModelObject<E>> downloads;

        public CopyLink(String name, List<ModelObject<E>> downloads) {
            super(name);
            this.downloads = downloads;
        }

        public void actionPerformed(ActionEvent e) {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                try {
                    sm.checkSystemClipboardAccess();
                } catch (Exception ex) {
                    return;
                }
            }
            StringBuilder builder = new StringBuilder();
            for (ModelObject<E> d : downloads) {
                builder.append(d.download.getURL().toExternalForm());
                builder.append('\n');
            }
            Toolkit tk = Toolkit.getDefaultToolkit();
            StringSelection st = new StringSelection(builder.toString());
            Clipboard cp = tk.getSystemClipboard();
            cp.setContents(st, st);
        }
    }

    private class Retry extends AbstractAction {

        private final List<ModelObject<E>> downloads;

        public Retry(String name, List<ModelObject<E>> downloads) {
            super(name);
            this.downloads = downloads;
        }

        public void actionPerformed(ActionEvent e) {
            for (ModelObject<E> d : downloads) {
                DownloadState current = d.download.getCurrentDownloadState();
                if (current.isCancelled()) {
                    try {
                        d.download.retry();
                    } catch (IOException ex) {
                        Download.log.log(Level.SEVERE, "Couldn't start download", ex);
                    }
                }
            }
        }
    }

    private class WrappedList extends JList<ModelObject<E>> {

        @Override
        public String getToolTipText(MouseEvent event) {
            Point point = event.getPoint();
            int index = this.locationToIndex(point);
            //Get the value of the item in the list
            if (index < 0) {
                return null;
            }
            ModelObject<E> m = model.getElementAt(index);
            return downloadView.getTooltipText(m.value, m.download.getCurrentDownloadState());
        }
    }
}
