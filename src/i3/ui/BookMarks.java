package i3.ui;

import ca.odell.glazedlists.FilterList;
import ca.odell.glazedlists.ListSelection;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.TextFilterator;
import ca.odell.glazedlists.TransformedList;
import ca.odell.glazedlists.matchers.MatcherEditor;
import ca.odell.glazedlists.matchers.MatcherEditor.Event;
import ca.odell.glazedlists.matchers.MatcherEditor.Listener;
import ca.odell.glazedlists.swing.EventListModel;
import ca.odell.glazedlists.swing.EventSelectionModel;
import ca.odell.glazedlists.swing.GlazedListsSwing;
import ca.odell.glazedlists.swing.TextComponentMatcherEditor;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import i3.main.Bookjar;
import i3.main.LocalBook;
import org.jdesktop.swingx.prompt.PromptSupport;
import i3.ui.data.CoverSearchOrRandom;
import i3.ui.data.RandomImage;
import i3.ui.data.ReadImageFromArchive;
import i3.ui.data.ReadImageFromCache;
import i3.ui.data.ScaleImage;
import i3.ui.data.WriteImageToCache;
import i3.util.Strings;
import i3.io.IoUtils;
import i3.swing.component.ComposedJPopupMenu;
import i3.swing.component.FlowPanelBuilder;
import i3.swing.component.ImageList;
import i3.swing.component.ListPopupActionWrapper;
import static i3.thread.Threads.*;

/**
 * A dictionary that saves locations, indexes, percentage read
 */
public class BookMarks implements Serializable {

    public static final int CELL_WIDTH = 300;
    public static final int CELL_HEIGHT = 300;
    private static final long serialVersionUID = 3226271353622776442L;
    private transient JComboBox<String> sortBox;
    private transient JComponent view;
    private transient ImageList<LocalBook> imageList;
    private transient JTextField search;
    //saved state on the class
    private Books books = new Books();
    private int sortTypeIndex;

    public JComponent getView() {

        if (view == null) {
            FlowPanelBuilder builder = new FlowPanelBuilder();
            builder.addEscapeAction(Key.Toggle_library.getAction());
            JButton close = new JButton(Key.Close_library.getAction());
            search = new JTextField();
            builder.add(close, FlowPanelBuilder.SizeConfig.FillSize);
            PromptSupport.setForeground(SystemColor.textInactiveText, search);
            PromptSupport.setPrompt("Search added books (use dir:\"name\" to search inside a directory)", search);
            PromptSupport.setFocusBehavior(PromptSupport.FocusBehavior.SHOW_PROMPT, search);
            //bug in tooltips hack inside the Imagelist forces me to this (swing reuses the tooltip).
            search.setToolTipText(null);
            builder.add(search, FlowPanelBuilder.SizeConfig.FillSize);
            MatcherEditor<LocalBook> matcher = new TextComponentMatcherEditor<>(search.getDocument(), new BookMarksFilter());
            matcher.addMatcherEditorListener(new ScrollToSelection());

            ChooseSortAction chooseSortAction;
            EventSelectionModel<LocalBook> selModel;
            ListModel<LocalBook> magic;
            books.eventList.getReadWriteLock().writeLock().lock();
            try {
                FilterList<LocalBook> filterList = new FilterList<>(books.eventList, matcher);
                SortedList<LocalBook> sorted = new SortedList<>(filterList, null);
                //both the models below use a thread proxy since they are going to be calle from swing
                TransformedList<LocalBook, LocalBook> l = GlazedListsSwing.swingThreadProxyList(sorted);
                selModel = new EventSelectionModel<>(l);
                selModel.setSelectionMode(ListSelection.MULTIPLE_INTERVAL_SELECTION_DEFENSIVE);
                magic = new EventListModel<>(l);
                chooseSortAction = new ChooseSortAction(sorted);
            } finally {
                books.eventList.getReadWriteLock().writeLock().unlock();
            }
            imageList = new ImageList<>(CELL_WIDTH, CELL_HEIGHT, new RenderBook(), magic, selModel);
            sortBox = new JComboBox<>(strings);
            sortBox.setAction(chooseSortAction);
            //start sorted if needed.
            sortBox.setSelectedIndex(sortTypeIndex);
            builder.add(sortBox, FlowPanelBuilder.SizeConfig.PreferredSize);
            JComponent listView = imageList.getView();
            //remove borders (null won't work for all laf)
            listView.setBorder(BorderFactory.createEmptyBorder());
            view = new JPanel();
            view.setLayout(new BorderLayout());
            view.add(builder.getView(), BorderLayout.NORTH);
            view.add(listView, BorderLayout.CENTER);
            view.setInheritsPopupMenu(true);
            JPopupMenu listMenu = new ComposedJPopupMenu(view);
            if (Desktop.isDesktopSupported()) {
                if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Action browse = new ListPopupActionWrapper(Key.Browse_libraryThing.getAction(), listMenu);
                    listMenu.add(browse);
                }
                if (Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Action open = new ListPopupActionWrapper(Key.Open_folders.getAction(), listMenu);
                    listMenu.add(open);
                }
            }
            Action remove = new ListPopupActionWrapper(Key.Remove_books.getAction(), listMenu);
            listMenu.add(remove);
            listMenu.addSeparator();
            listView.setComponentPopupMenu(listMenu);
        }
        return view;
    }

    public InputMap getInputMap() {
        return imageList.getInputMap();
    }

    public ActionMap getActionMap() {
        return imageList.getActionMap();
    }

    /**
     * Adds a mouse listener for the bookmark
     */
    public void addMouseListener(MouseListener mouseListener) {
        imageList.addMouseListener(mouseListener);
    }

    /**
     * Return the URI ATM on the given point, if any
     * @return URI or null
     */
    public URL locationToURL(Point point) {
        LocalBook obj = (LocalBook) imageList.locationToObject(point);
        if (obj != null) {
            return obj.getURL();
        }
        return null;
    }

    /**
     * Use in edt.
     * @return selected urls if any
     */
    public List<LocalBook> getSelected() {
        return imageList.getSelectedObjects();
    }

    public boolean isViewVisible() {
        return view != null && view.isShowing();
    }

    public void requestSearchFocusInWindow() {
        search.selectAll();
        search.requestFocusInWindow();
    }

    /**
     * @see ui.Books#withLock
     */
    public void withLock(Runnable composition) {
        books.withLock(composition);
    }

    /**
     * @see ui.Books#get
     */
    public LocalBook get(URL key) {
        return books.get(key);
    }

    /**
     * @see ui.Books#getFirst
     */
    public LocalBook getFirst() {
        return books.getFirst();
    }

    /**
     * @see ui.Books#remove
     */
    public void remove(URL key) {
        books.remove(key);
    }

    /**
     * @see ui.Books#createIfAbsent
     */
    public LocalBook createIfAbsent(final URL key) {
        return books.createIfAbsent(key);
    }

    /**
     * @see ui.Books#put
     */
    public void put(final URL key, final LocalBook value) {
        books.put(key, value);
    }

    /**
     * @see ui.Books#putAll
     */
    public void putAll(final Collection<Path> keys) {
        books.putAll(keys);
    }

    private static class RenderBook implements ImageList.RenderValues<LocalBook> {
        //operation extremely memory hungry. OOME danger. So only one thread at a time.

        private final transient ExecutorService extractPool = newLIFOScalingExecutor("ExtractImageFromFile", 1, 15L, true);
        //Offloat to a webservice (thank you open library). Go wild.
        private final transient ExecutorService networkPool = newLIFOScalingExecutor("OpenLibrary", 6, 5L, true);

        @Override
        public void requestCellImage(final ImageList<LocalBook> list, final LocalBook entry, final int imageWidth, final int imageHeight) {
            final Path file = entry.getFile();
            final String name = file.getFileName().toString();

            BufferedImage img = RandomImage.getValue(entry);
            if (img != null) {
                list.putImage(entry, img);
                return;
            }
            img = new ReadImageFromCache(name).read();
            if (img != null) {
                list.putImage(entry, img);
                return;
            }
            extractPool.execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        if (list.isObjectInvisible(entry)) {
                            list.deferImage(entry);
                            return;
                        }
                        BufferedImage img = new ReadImageFromArchive(file).read();
                        if (img != null) {
                            img = new ScaleImage(imageWidth, imageHeight).scale(img);
                            list.putImage(entry, img);
                            new WriteImageToCache(name).write(img);
                            return;
                        }
                    } catch (Exception e) {
                        Bookjar.log.log(Level.SEVERE, "Could not get cover image", e);
                    }
                    networkPool.execute(new CoverSearchOrRandom<>(name, entry, list, imageWidth, imageHeight));
                    Thread.yield();
                }
            });
        }

        @Override
        public String getCellText(ImageList<LocalBook> list, LocalBook entry) {
            return entry.getFile().getFileName().toString();
        }

        @Override
        public String getTooltipText(ImageList<LocalBook> list, FontMetrics tooltipMetrics, LocalBook entry) {
            float percent = entry.getReadPercentage();
            StringBuilder buffer = new StringBuilder("<html><body>");
            if (percent == 1f) {
                buffer.append("<p><font color=#2e8b57>fully read</color></p>");
            } else if (percent > 0.2f && percent < 1f) {
                buffer.append("<p><font color=#cd853f>");
                buffer.append(String.format("%.3g%% read", percent * 100));
                buffer.append("</color></p>");
            } else {
                buffer.append("<p><font color=#ff4500>");
                if (percent == 0f) {
                    buffer.append("unread");
                } else {
                    buffer.append(String.format("%.3g%% read", percent * 100));
                }
                buffer.append("</color></p>");
            }

            final String name = entry.getFile().getFileName().toString();
            int requiredWidth = list.getView().getWidth();

            Strings.linebreakString(buffer, name, "<br>", requiredWidth, tooltipMetrics);
            final String language = entry.getDisplayLanguage() == null ? "" : entry.getDisplayLanguage();
            buffer.append("<p>");
            buffer.append(language);
            buffer.append("</p></html></body>");
            return buffer.toString();
        }
    }

    private static class BookMarksFilter implements TextFilterator<LocalBook> {

        @Override
        public void getFilterStrings(List<String> list, LocalBook book) {
            Path file = book.getFile();
            list.add(file.getFileName().toString());

            //allow search for dir names up to 3 dirs above
            for (int i = 0; i < 3; i++) {
                file = file.getParent();
                if (file == null) {
                    return;
                }

                String name = file.getFileName().toString();
                list.add("dir:\"" + name + "\"");
                String[] tokenizedName = name.split("\\s+");
                for (String subname : tokenizedName) {
                    list.add("dir:" + subname);
                }
            }
        }
    }
    private static final String[] strings = {"Modified", "Last Read"};

    private class ChooseSortAction extends AbstractAction {

        private final static int UNSORTED = 1;
        private final static int MODIFIED = 0;
        private final SortedList<LocalBook> sorted;

        public ChooseSortAction(SortedList<LocalBook> sorted) {
            super();
            this.sorted = sorted;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JComboBox cb = (JComboBox) e.getSource();
            sortTypeIndex = cb.getSelectedIndex();
            //sort outside the edt
            new SwingWorker() {

                @Override
                protected Object doInBackground() throws Exception {
                    LastModifiedComparator comp = new LastModifiedComparator(BookMarks.this);
                    sorted.getReadWriteLock().writeLock().lock();
                    try {
                        if (sortTypeIndex == UNSORTED) {
                            sorted.setComparator(null);
                        } else if (sortTypeIndex == MODIFIED) {
                            sorted.setComparator(comp);
                        }
                    } finally {
                        sorted.getReadWriteLock().writeLock().unlock();
                    }
                    comp.removeInnacessibleFiles();
                    return null;
                }
            }.execute();
        }
    }

    private class ScrollToSelection implements Listener<LocalBook>, Runnable {

        @Override
        public void changedMatcher(Event<LocalBook> matcherEvent) {
            SwingUtilities.invokeLater(this);
        }

        @Override
        public void run() {
            imageList.ensureSelectedIsVisible();
        }
    }

    private static class LastModifiedComparator implements Comparator<LocalBook> {

        private final Map<Path, Long> lastModifiedTime = new HashMap<>();
        private final HashSet<Path> innacessibleFiles = new HashSet<>();
        private final BookMarks bm;

        public LastModifiedComparator(BookMarks bm) {
            this.bm = bm;
        }

        @Override
        public int compare(LocalBook mark1, LocalBook mark2) {
            final Path file1 = mark1.getFile();
            final Path file2 = mark2.getFile();
            Long lm1 = lastModifiedTime.get(file1), lm2 = lastModifiedTime.get(file2);
            try {
                if (lm1 == null) {
                    lm1 = Files.getLastModifiedTime(file1, LinkOption.NOFOLLOW_LINKS).toMillis();
                    lastModifiedTime.put(file1, lm1);
                }
                if (lm2 == null) {
                    lm2 = Files.getLastModifiedTime(file2, LinkOption.NOFOLLOW_LINKS).toMillis();
                    lastModifiedTime.put(file2, lm2);
                }
                //reverse (most recent to less)
                return -lm1.compareTo(lm2);
            } catch (IOException ex) {
                if (Files.notExists(file1) || !Files.isReadable(file1)) {
                    innacessibleFiles.add(file1);
                }
                if (Files.notExists(file2) || !Files.isReadable(file2)) {
                    innacessibleFiles.add(file2);
                }
            }
            return Integer.MIN_VALUE;
        }

        private void removeInnacessibleFiles() {
            for (Path p : innacessibleFiles) {
                bm.remove(IoUtils.toURL(p));
                Bookjar.log.log(Level.WARNING, "Removed inacessible file {0}", p);
            }
            innacessibleFiles.clear();
        }
    }
}