package i3.ui;

import java.awt.image.BufferedImage;
import i3.download.DownloadState;
import i3.gutenberg.GutenbergSearch;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FontMetrics;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import i3.main.Bookjar;
import i3.main.GutenbergBook;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.Hit;
import org.apache.lucene.search.Hits;
import org.jdesktop.swingx.prompt.PromptSupport;
import i3.ui.data.CoverSearchOrRandom;
import i3.ui.data.RandomImage;
import i3.ui.data.ReadImageFromCache;
import i3.util.Factory;
import i3.util.Strings;
import i3.io.IoUtils;
import i3.swing.component.ComposedJPopupMenu;
import i3.swing.component.DownloadsList;
import i3.swing.component.FlowPanelBuilder;
import i3.swing.component.ImageList;
import i3.swing.component.ListPopupActionWrapper;
import static i3.thread.Threads.*;

/**
 *
 * @author microbiologia
 */
public class GutenbergPanel {

    public static final int CELL_HEIGHT = 300;
    public static final int CELL_WIDTH = 300;
    private final Pattern multipleTopic = Pattern.compile("t:([^\\s]*)\\s?");
    private final Pattern singleTopic = Pattern.compile("t:\"([^\"]*)\"?");
    private JPanel view;
    private final JTextField searchText;
    private final DefaultListModel<GutenbergBook> list = new DefaultListModel<>();
    private ImageList<GutenbergBook> imageList;
    private DownloadsList<GutenbergBook> downloads;
    private final Action reindex, updateList;
    private final Path libraryDir;
    private ComboBoxModel<LocaleWrapper> cbModel;
    private String oldSearch = "";
    private Locale oldLanguage;
    private final GutenbergSearch search;
    private SwingWorker lastTask;

    public GutenbergPanel() {
        Path appDir = Bookjar.programLocation;
        libraryDir = appDir.resolve("library");
        search = new GutenbergSearch(appDir);
        reindex = new ReindexAction("Update");
        updateList = new UpdateList();
        searchText = new JTextField();
    }

    public void requestSearchFocusInWindow() {
        searchText.selectAll();
        searchText.requestFocusInWindow();
    }

    public List<GutenbergBook> getSelected() {
        return imageList.getSelectedObjects();
    }

    public boolean isViewVisible() {
        return view != null && view.isShowing();
    }

    private void startIndexing() {
        search.startIndexing(view);
    }

    public JComponent getView() {
        if (view == null) {
            downloads = new DownloadsList<>();
            FlowPanelBuilder f = new FlowPanelBuilder();
            f.addEscapeAction(Key.Toggle_gutenberg.getAction());
            JButton close = new JButton(Key.Close_gutenberg.getAction());
            f.add(close, FlowPanelBuilder.SizeConfig.FillSize);
            PromptSupport.setPrompt("Search and download ebooks in Project Gutenberg (use t:word or t:\"words\" to search topics)", searchText);
            PromptSupport.setForeground(SystemColor.textInactiveText, searchText);
            PromptSupport.setFocusBehavior(PromptSupport.FocusBehavior.SHOW_PROMPT, searchText);
            //bug in tooltips hack inside the Imagelist forces me to this.
            searchText.setToolTipText(null);
            f.add(searchText, FlowPanelBuilder.SizeConfig.FillSize);
            Set<LocaleWrapper> set = new TreeSet<>();
            for (Locale l : Locale.getAvailableLocales()) {
                set.add(new LocaleWrapper(l));
            }
            LocaleWrapper any = new LocaleWrapper();
            set.add(any);
            cbModel = new DefaultComboBoxModel<>(set.toArray(new LocaleWrapper[set.size()]));
            JComboBox<LocaleWrapper> box = new JComboBox<>(cbModel);
            box.setAction(updateList);
            f.add(box, FlowPanelBuilder.SizeConfig.PreferredSize);
            f.add(new JButton(reindex), FlowPanelBuilder.SizeConfig.FillSize);
            searchText.setAction(updateList);
            imageList = new ImageList<>(CELL_WIDTH, CELL_HEIGHT, new RenderGutenberg(), list);
            //remove borders (null won't work for all laf)
            imageList.getView().setBorder(BorderFactory.createEmptyBorder());
            downloads.getView().setBorder(BorderFactory.createEmptyBorder());
            f.getView().setBorder(BorderFactory.createEmptyBorder());
            imageList.addMouseListener(new ListenToClickOnGutenbergList(imageList));

            JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, downloads.getView(), imageList.getView());
            pane.setDividerLocation(downloads.getView().getPreferredSize().width);
            pane.setOneTouchExpandable(true);
            pane.setInheritsPopupMenu(true);

            //timer for list updates
            final Timer t = new Timer(1500, updateList);
            t.setCoalesce(true);
            view = new JPanel() {
                @Override
                public void addNotify() {
                    super.addNotify();
                    if (!search.isIndexed()) {
                        startIndexing();
                    }
                    t.start();
                }

                @Override
                public void removeNotify() {
                    super.removeNotify();
                    t.stop();
                }
            };
            view.setLayout(new BorderLayout());
            view.add(f.getView(), BorderLayout.NORTH);
            view.add(pane, BorderLayout.CENTER);
            view.setInheritsPopupMenu(true);
            if (Desktop.isDesktopSupported()) {
                if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    JPopupMenu listMenu = new ComposedJPopupMenu(view);
                    Action browse = new ListPopupActionWrapper(Key.Browse_libraryThing.getAction(), listMenu);
                    listMenu.add(browse);
                    listMenu.addSeparator();
                    imageList.getView().setComponentPopupMenu(listMenu);
                }
            }
        }
        return view;
    }

    private static class LocaleWrapper implements Comparable<LocaleWrapper> {

        Locale l;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof LocaleWrapper)) {
                return false;
            }
            LocaleWrapper w = (LocaleWrapper) obj;
            Locale locale = w.getLocale();
            Locale current = getLocale();
            if (locale == null || current == null) {
                return locale == current;
            }

            return current.getDisplayLanguage().equals(locale.getDisplayLanguage());
        }

        @Override
        public int hashCode() {
            return l.getDisplayLanguage().hashCode();
        }

        public LocaleWrapper(Locale l) {
            this.l = l;
        }

        public LocaleWrapper() {
            this.l = Locale.ROOT;
        }

        public Locale getLocale() {
            return l == Locale.ROOT ? null : l;
        }

        @Override
        public String toString() {
            return l == Locale.ROOT ? "any language" : l.getDisplayLanguage();
        }

        @Override
        public int compareTo(LocaleWrapper o) {
            Locale other = o.getLocale();
            if (other == null) {
                return -1;
            }
            String thisLanguage = l.getDisplayLanguage();
            String otherLanguage = other.getDisplayLanguage();

            return Strings.compareNatural(thisLanguage, otherLanguage);
        }
    }

    private static class RenderGutenberg implements ImageList.RenderValues<GutenbergBook> {

        //Offloat to a webservice (thank you open library). Go wild.
        private final ExecutorService networkPool = newLIFOScalingExecutor("DownloadGutenbergImageFromOpenLibrary", 6, 5L, true);

        @Override
        public void requestCellImage(final ImageList<GutenbergBook> list, final GutenbergBook entry, final int imageWidth, final int imageHeight) {

            BufferedImage img = RandomImage.getValue(entry);
            if (img != null) {
                list.putImage(entry, img);
                return;
            }

            final String title = entry.getPartialName(" & ", " - ");
            img = new ReadImageFromCache(title).read();
            if (img != null) {
                list.putImage(entry, img);
                return;
            }

            networkPool.execute(new CoverSearchOrRandom<>(title, entry, list, imageWidth, imageHeight));
        }

        @Override
        public String getCellText(ImageList<GutenbergBook> list, GutenbergBook obj) {
            GutenbergBook entry = (GutenbergBook) obj;
            String authors = entry.getAuthors(" & ");
            if (!authors.isEmpty()) {
                authors = authors + " - ";
            }
            return authors + entry.getFirstTitle();
        }

        @Override
        public String getTooltipText(ImageList<GutenbergBook> list, FontMetrics tooltipMetrics, GutenbergBook obj) {
            GutenbergBook entry = (GutenbergBook) obj;
            StringBuilder tooltipText = new StringBuilder("<html><body>");
            //titles can be very, very long...
            int desiredWidth = list.getView().getWidth();
            //Some space will be wasted because of <b>
            String tmp = "<b>Title:</b> " + entry.getTitle();
            Strings.linebreakString(tooltipText, tmp, "<br>", desiredWidth, tooltipMetrics);
            String authors = entry.getAuthors("<br>and ");
            addPrefixIfNotEmpty(tooltipText, "<b>Creator:</b> ", authors);
            String contributors = entry.getContributors("<br>and ");
            addPrefixIfNotEmpty(tooltipText, "<br><b>Contributor:</b> ", contributors);
            String subjects = entry.getSubjects("<br>");
            addPrefixIfNotEmpty(tooltipText, "<br><b>Subjects:</b> ", subjects);
            String languages = entry.getDisplayLanguages("<br>Other language: ");
            addPrefixIfNotEmpty(tooltipText, "<br><b>Language:</b> ", languages);
            tooltipText.append("</body></html>");

            return tooltipText.toString();
        }

        private void addPrefixIfNotEmpty(StringBuilder b, String prefix, String toAdd) {
            if (!toAdd.isEmpty()) {
                b.append(prefix).append(toAdd);
            }
        }
    }

    private void updateList() {
        assert (SwingUtilities.isEventDispatchThread()) : "Why are you not calling this from the EDT!!!";
        String currentSearch = searchText.getText();
        LocaleWrapper lw = (LocaleWrapper) cbModel.getSelectedItem();
        final Locale currentLang = lw.getLocale();

        //only do it if something changed since the last time this was invoked...
        if (currentSearch.equals(oldSearch) && currentLang == oldLanguage) {
            return;
        }
        oldSearch = currentSearch;
        oldLanguage = currentLang;
        //our text was indexed with the normal (english) analyser.
        //so the english stop words aren't indexed and
        //phrases that contain the english stop words
        //(for ex: the) may cause false positives to fill the cache
        //(for ex: the -> theo)
        for (String stopWord : StandardAnalyzer.STOP_WORDS) {
            Pattern p = Pattern.compile("(?:^| )" + stopWord + "(?:$| )", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            currentSearch = p.matcher(currentSearch).replaceAll(" ");
        }

        //save/remove the topic metatag if it is found...
        final StringBuilder topicsBuilder = new StringBuilder();
        Factory<String, Matcher> factory = new Factory<String, Matcher>() {
            @Override
            public String create(Matcher arg) throws Exception {
//                System.out.println(arg.group(1));
                topicsBuilder.append(arg.group(1)).append(' ');
                return "";
            }
        };
        currentSearch = IoUtils.replaceAll(currentSearch, singleTopic, factory).toString();
        currentSearch = IoUtils.replaceAll(currentSearch, multipleTopic, factory).toString();
//        System.out.println(currentSearch);
//        System.out.println(topicsBuilder);
        final String topics = topicsBuilder.toString().trim();
        final String processedSearch = currentSearch;
        Runnable reFillList = new Runnable() {
            @Override
            public void run() {
                list.clear();
                if (processedSearch.length() < 3 && topics.length() < 3) {
                    return;
                }

                lastTask = new LuceneQuerySwingWorker(processedSearch, topics, currentLang);
                lastTask.execute();
            }
        };

        //something changed, always cancel
        if (lastTask != null && !lastTask.isDone()) {
            lastTask.cancel(true);
            //give time for the old worker to stop adding books.
            SwingUtilities.invokeLater(reFillList);
        } else {
            reFillList.run();
        }
    }

    private class LuceneQuerySwingWorker extends SwingWorker<List<GutenbergBook>, GutenbergBook> {

        private final String query, requestedSubjects;
        private final Locale queryLocale;

        public LuceneQuerySwingWorker(String search, String requestedSubjects, Locale queryLocale) {
            this.query = search;
            this.queryLocale = queryLocale;
            this.requestedSubjects = requestedSubjects;
        }

        @Override
        @SuppressWarnings( "unchecked")
        protected List<GutenbergBook> doInBackground() {
            Hits hits = null;
            try {
                hits = search.query(query, requestedSubjects, queryLocale);
                if (hits == null) {
                    return Collections.emptyList();
                }
                Iterator<Hit> result = (Iterator<Hit>) hits.iterator();
                while (result.hasNext() && !isCancelled()) {
                    Hit s = result.next();
                    String titles = s.get("title");
                    String authors = s.get("creator");
                    String contributors = s.get("contributor");
                    String subjects = s.get("subject");
                    String languages = s.get("language");
                    String metadata = s.get("metadata");
                    publish(new GutenbergBook(authors, contributors, titles, languages, subjects, metadata));
                }
            } catch (IOException | ParseException ex) {
                Bookjar.log.log(Level.SEVERE, "Error querying database: ", ex);
            }
            return Collections.emptyList();
        }

        @Override
        protected void process(List<GutenbergBook> chunks) {
            for (GutenbergBook book : chunks) {
                list.addElement(book);
            }
        }
    }

    private class ListenToClickOnGutenbergList extends MouseAdapter {

        private final ImageList imageList;

        public ListenToClickOnGutenbergList(ImageList imageList) {
            this.imageList = imageList;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() <= 1 || e.getButton() != MouseEvent.BUTTON1) {
                return;
            }
            GutenbergBook book = (GutenbergBook) imageList.locationToObject(e.getPoint());
            if (book != null) {

                DownloadState download = downloads.get(book.getURL());
                if (download == null) {
                    addGutenbergBookToDownloadList(book);
                } else if (download.isDone()) {
                    downloads.open(book, download);
                }
            }
        }

        private void addGutenbergBookToDownloadList(GutenbergBook book) {
            try {
                Path bookFile = IoUtils.getSafeFileSystemFile(libraryDir, book.getFileName(" & ", " - "));
                downloads.add(book, book.getURL(), bookFile, book.getExtent(), book.getMimeType());
            } catch (IOException ex) {
                Bookjar.log.log(Level.SEVERE, "Could not start download", ex);
            }
        }
    }

    private class ReindexAction extends AbstractAction {

        public ReindexAction(String name) {
            super(name);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            startIndexing();
        }
    }

    private class UpdateList extends AbstractAction {

        @Override
        public void actionPerformed(ActionEvent e) {
            updateList();
        }
    }
}