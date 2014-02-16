package i3.ui;

import ca.odell.glazedlists.FilterList;
import ca.odell.glazedlists.ListSelection;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.TextFilterator;
import ca.odell.glazedlists.TransformedList;
import ca.odell.glazedlists.matchers.MatcherEditor;
import ca.odell.glazedlists.matchers.MatcherEditor.Event;
import ca.odell.glazedlists.matchers.MatcherEditor.Listener;
import ca.odell.glazedlists.swing.DefaultEventListModel;
import ca.odell.glazedlists.swing.DefaultEventSelectionModel;
import ca.odell.glazedlists.swing.GlazedListsSwing;
import ca.odell.glazedlists.swing.TextComponentMatcherEditor;
import i3.main.Bookjar;
import i3.main.Library;
import i3.main.LocalBook;
import i3.swing.component.ComposedJPopupMenu;
import i3.swing.component.FlowPanelBuilder;
import i3.swing.component.ImageList;
import i3.swing.component.ListPopupActionWrapper;
import static i3.thread.Threads.*;
import i3.ui.data.CoverSearchOrRandom;
import i3.ui.data.RandomImage;
import i3.ui.data.ReadImageFromArchive;
import i3.ui.data.ReadImageFromCache;
import i3.ui.data.ScaleImage;
import i3.ui.data.WriteImageToCache;
import i3.util.Strings;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FontMetrics;
import java.awt.SystemColor;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.jdesktop.swingx.prompt.PromptSupport;

public class LibraryView implements Serializable {

    private static final long serialVersionUID = 3226271353624776442L;
    public static final int CELL_WIDTH = 300;
    public static final int CELL_HEIGHT = 300;
    private transient JComboBox<String> sortBox;
    private transient JComponent view;
    private transient ImageList<LocalBook> imageList;
    private transient JTextField search;
    //sort stuff
    private static final String[] strings = {"Modified", "Last Read"};
    private static final int UNSORTED = 1;
    private static final int MODIFIED = 0;
    private transient SortedList<LocalBook> sorted;
    //saved state on the class
    private final Library books = new Library();
    private int sortTypeIndex;

    public JComponent getView() {
        if (view == null) {
            FlowPanelBuilder builder = new FlowPanelBuilder();
            builder.addEscapeAction(Key.Toggle_library.getAction());
            JButton close = new JButton(Key.Close_library.getAction());
            search = new JTextField();
            final String normalPrompt = "Search added books (use dir:\"name\" to search inside a directory)";
            PromptSupport.setForeground(SystemColor.textInactiveText, search);
            PromptSupport.setPrompt(normalPrompt, search);
            PromptSupport.setFocusBehavior(PromptSupport.FocusBehavior.SHOW_PROMPT, search);

            builder.add(close, FlowPanelBuilder.SizeConfig.FillSize);
            builder.add(search, FlowPanelBuilder.SizeConfig.FillSize);
            MatcherEditor<LocalBook> matcher = new TextComponentMatcherEditor<>(search.getDocument(), new BookMarksFilter());
            matcher.addMatcherEditorListener(new ScrollToSelection());

            DefaultEventSelectionModel<LocalBook> selModel;
            ListModel<LocalBook> magic;
            books.eventList.getReadWriteLock().writeLock().lock();
            try {
                FilterList<LocalBook> filterList = new FilterList<>(books.eventList, matcher);
                sorted = new SortedList<>(filterList, null);
                //both the models below use a thread proxy since they are going to be called from swing
                TransformedList<LocalBook, LocalBook> l = GlazedListsSwing.swingThreadProxyList(sorted);
                selModel = new DefaultEventSelectionModel<>(l);
                selModel.setSelectionMode(ListSelection.MULTIPLE_INTERVAL_SELECTION_DEFENSIVE);
                magic = new DefaultEventListModel<>(l);
            } finally {
                books.eventList.getReadWriteLock().writeLock().unlock();
            }
            imageList = new ImageList<>(CELL_WIDTH, CELL_HEIGHT, new RenderBook(), magic, selModel);
            sortBox = new JComboBox<>(strings);
            sortBox.setAction(Key.Sort_library.getAction());
            //sent before
            if (Library.libraryNotExists()) {
                sortBox.setSelectedIndex(sortTypeIndex);
            }
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

    void sortLibrary() {
        sortTypeIndex = sortBox.getSelectedIndex();
        //sort outside the edt
        new SwingWorker() {

            @Override
            protected Object doInBackground() throws Exception {
                sorted.getReadWriteLock().writeLock().lock();
                try {
                    if (sortTypeIndex == UNSORTED) {
                        sorted.setComparator(null);
                    } else if (sortTypeIndex == MODIFIED) {
                        sorted.setComparator(new LastModifiedComparator());
                    }
                } finally {
                    sorted.getReadWriteLock().writeLock().unlock();
                }
                return null;
            }
        }.execute();
    }

    /**
     * {@link  i3.swing.component.ImageList#setAction(javax.swing.Action)}
     */
    public void setAction(Action selectAction) {
        imageList.setAction(selectAction);
    }

    /**
     * Use in edt.
     *
     * @return selected books if any
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
     * {@link i3.main.Library#withLock(java.lang.Runnable) }
     */
    public void withLock(Runnable composition) {
        books.withLock(composition);
    }

    /**
     * {@link i3.main.Library#get(java.nio.file.Path) }
     */
    public LocalBook get(Path key) {
        return books.get(key);
    }

    /**
     * {@link i3.main.Library#getFirst()}
     */
    public LocalBook getFirst() {
        return books.getFirst();
    }

    /**
     * {@link i3.main.Library#removeBooks(java.util.Collection) }
     */
    public void removeBooks(Collection<LocalBook> key) {
        books.removeBooks(key);
    }

    /**
     * {@link i3.main.Library#createIfAbsent(java.nio.file.Path, java.lang.String, boolean)
     * }
     */
    public LocalBook createIfAbsent(final Path key, String language, boolean gutenberg) {
        return books.createIfAbsent(key, language, gutenberg);
    }

    /**
     * {@link i3.main.Library#replace(i3.main.LocalBook) }
     */
    public void replace(final LocalBook value) {
        books.replace(value);
    }

    /**
     * {@link i3.main.Library#validateLibrary() }
     */
    public void validateLibrary() {
        books.validateLibrary();
    }

    /**
     * {@link i3.main.Library#updateLibrary(java.nio.file.Path) }
     */
    public Callable<Boolean> updateLibrary(final Path library) throws IOException {
        return books.updateLibrary(library);
    }

    private static class RenderBook implements ImageList.RenderValues<LocalBook> {

        //operation extremely memory hungry. OOME danger. So only one thread at a time.
        private final transient ExecutorService extractPool = newLIFOScalingExecutor("ExtractImageFromFile", 1, 15L, true);
        //Offloat to a webservice (thank you open library). Go wild.
        private final transient ExecutorService networkPool = newLIFOScalingExecutor("OpenLibrary", 6, 5L, true);

        @Override
        public void requestCellImage(final ImageList<LocalBook> list, final LocalBook entry, final int imageWidth, final int imageHeight) {
            final String name = entry.getFileName();

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
            if (Library.libraryNotExists()) {
                networkPool.execute(new CoverSearchOrRandom<>(name, entry, list, imageWidth, imageHeight));
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
                        BufferedImage img = new ReadImageFromArchive(entry.getAbsoluteFile()).read();
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
            return entry.getFileName();
        }

        @Override
        public String getTooltipText(ImageList<LocalBook> list, FontMetrics tooltipMetrics, LocalBook entry) {
            float percent = entry.getReadPercentage();
            StringBuilder buffer = new StringBuilder("<html><body>");
            if (percent == 1f) {
                buffer.append("<p><font color=#2e8b57>fully read</color></p>");
            } else if (percent > 0.2f && percent < 1f) {
                buffer.append("<p><font color=#cd853f>");
                buffer.append(String.format("%,.3f%% read", percent * 100));
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

            final String name = entry.getFileName();
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
            list.add(book.getFileName());
            Path file = book.getRelativeFile();
            while (true) {
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

        @Override
        public int compare(LocalBook mark1, LocalBook mark2) {
            try {
                Path file1 = mark1.getAbsoluteFile(),
                        file2 = mark2.getAbsoluteFile();
                Long lm1 = lastModifiedTime.get(file1), lm2 = lastModifiedTime.get(file2);
                if (lm1 == null) {
                    lm1 = Files.getLastModifiedTime(file1).toMillis();
                    lastModifiedTime.put(file1, lm1);
                }
                if (lm2 == null) {
                    lm2 = Files.getLastModifiedTime(file2).toMillis();
                    lastModifiedTime.put(file2, lm2);
                }
                //reverse  (most recent to less)
                return -lm1.compareTo(lm2);
            } catch (IOException | NullPointerException ex) {
                //one of the files doesn't actually exists, but perserve the metadata for possible repair
                return 0;
            }
        }

    }

}
