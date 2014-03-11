package i3.ui;

import de.spieleck.app.cngram.NGramProfiles;
import i3.io.FastURLEncoder;
import i3.io.IoUtils;
import i3.main.Book;
import i3.main.Library;
import i3.main.LibraryUpdate;
import i3.main.LocalBook;
import i3.net.AuthentificationProxySelector;
import i3.notifications.Notification;
import i3.notifications.NotificationDisplayer;
import i3.notifications.NotificationDisplayer.Category;
import static i3.notifications.NotificationDisplayer.Category.*;
import static i3.notifications.NotificationDisplayer.Priority.HIGH;
import i3.notifications.StatusLineElement;
import i3.swing.Bind;
import i3.swing.SearchIterator;
import i3.swing.component.ClockField;
import i3.swing.component.FlowPanelBuilder;
import i3.swing.component.FullScreenFrame;
import i3.swing.component.GlassPane;
import i3.swing.component.LabelButton;
import i3.swing.dynamic.DynamicAction;
import i3.swing.dynamic.DynamicListener;
import i3.swing.dynamic.DynamicRunnable;
import i3.swing.dynamic.DynamicSwingWorker;
import i3.ui.controller.MovingPane;
import i3.util.DefaultUndoManager;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.text.Position.Bias;
import org.apache.logging.log4j.LogManager;
import org.jdesktop.swingx.JXCollapsiblePane;
import org.jdesktop.swingx.StackLayout;

public final class Application implements Serializable {

    private static final long serialVersionUID = -17574584503570422L;
    //initialized in readObject
    private final LibraryView bookList = new LibraryView();
    final Bind actions = new Bind();
    final FullScreenFrame frame = new FullScreenFrame();
    final MovingPane pane = new MovingPane();
    boolean showClock = false;
    //singleton, used throught the program
    public transient static Application app;
    //Objects default initialized (in addComponents)
    transient ClockField clock;
    private transient JPanel mainPanel;
    private transient JButton mainButton;
    private transient GutenbergPanel gutenbergPanel;
    private transient JXCollapsiblePane buttonsPane;
    private transient JXCollapsiblePane searchPane;
    private transient JTextField searchText;
    private transient GlassPane glass;
    private transient DefaultUndoManager undoRedo;
    private transient JPopupMenu popup;
    private transient LabelButton percentageButton;
    private transient LabelButton undoButton;
    private transient LabelButton redoButton;

    private void writeObject(java.io.ObjectOutputStream output) throws IOException {
        saveCurrentBookIndex();
        output.defaultWriteObject();
    }

    private void readObject(java.io.ObjectInputStream input) throws IOException, ClassNotFoundException {
        app = this;
        input.defaultReadObject();
        constructApplication();
    }

    public Application() {
        app = this;
        constructApplication();
    }

    private void constructApplication() {
        actions.setup(Key.values());
        addComponents();
        addListeners();
        frame.setVisible(true);
    }

    private void addComponents() {
        ProxySelector.setDefault(new AuthentificationProxySelector());
        mainPanel = new JPanel(new BorderLayout());
        mainButton = new JButton();
        gutenbergPanel = new GutenbergPanel();
        buttonsPane = new JXCollapsiblePane();
        searchPane = new JXCollapsiblePane();
        searchPane.setCollapsed(true);

        searchText = new JTextField();
        searchText.setAction(Key.Find.getAction());

        FlowPanelBuilder flowFactory = new FlowPanelBuilder(searchPane);
        flowFactory.addEscapeAction(Key.Hide_find.getAction());
        flowFactory.add(new JButton(Key.Hide_find.getAction()), FlowPanelBuilder.SizeConfig.PreferredSize);
        flowFactory.add(searchText, FlowPanelBuilder.SizeConfig.FillSize);
        flowFactory.add(new JButton(Key.Find.getAction()), FlowPanelBuilder.SizeConfig.PreferredSize);
        flowFactory.add(new JButton(Key.Find_previous.getAction()), FlowPanelBuilder.SizeConfig.PreferredSize);
        glass = new GlassPane(new BorderLayout());
        undoRedo = new DefaultUndoManager();
        popup = new JPopupMenu();
        percentageButton = new LabelButton(Key.Popup_percent.getAction());
        undoButton = new LabelButton(Key.Undo.getAction());
        redoButton = new LabelButton(Key.Redo.getAction());
        //override the normal action name to be symbolic here (textual in shortcuts)
        undoButton.setText("[ \u25c0 ]");
        redoButton.setText("[ \u25b6 ]");
        //substance is stubborn (empty border will do nothing)
        percentageButton.putClientProperty("substancelaf.buttonnominsize", true);
        undoButton.putClientProperty("substancelaf.buttonnominsize", true);
        redoButton.putClientProperty("substancelaf.buttonnominsize", true);
        //Add actions to a popupmenu.
        JPopupMenu rightClickMenu = new JPopupMenu();
        JMenuItem toggleLib = new JMenuItem(Key.Toggle_library.getAction());
        JMenuItem toggleGut = new JMenuItem(Key.Toggle_gutenberg.getAction());
        rightClickMenu.add(toggleLib);
        rightClickMenu.add(toggleGut);
        rightClickMenu.add(Key.Toggle_fullscreen.getAction());
        rightClickMenu.add(Key.Increase_font.getAction());
        rightClickMenu.add(Key.Decrease_font.getAction());
        rightClickMenu.addSeparator();
        rightClickMenu.add(Key.Select_library_directory.getAction());
        rightClickMenu.add(Key.Options.getAction());
        pane.getView().setComponentPopupMenu(rightClickMenu);
        bookList.getView().setComponentPopupMenu(rightClickMenu);
        gutenbergPanel.getView().setComponentPopupMenu(rightClickMenu);
        buttonsPane.setComponentPopupMenu(rightClickMenu);

        frame.setTitle("BookJar");
        frame.setIconImage(Toolkit.getDefaultToolkit().getImage(Application.class.getResource("bookjar.png")));
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setGlassPane(glass);
        frame.add(mainPanel);
        mainPanel.add(pane.getView());
        mainPanel.add(searchPane, BorderLayout.NORTH);
        mainPanel.add(buttonsPane, BorderLayout.SOUTH);

        popup.add(new PopupPanel(pane, undoRedo));

        JButton back = new JButton(Key.Move_backward.getAction());
        mainButton.setAction(Key.Move_forward.getAction());
        JButton options = new JButton(Key.Options.getAction());

        Font font = new java.awt.Font("Monospaced", java.awt.Font.BOLD, percentageButton.getFont().getSize() + 2);
        percentageButton.setFont(font);
        undoButton.setFont(font);
        redoButton.setFont(font);
        undoButton.setEnabled(false);
        redoButton.setEnabled(false);
        clock = new ClockField(ClockField.HH_MM);
        clock.setVisible(showClock);

        FlowLayout flowLayout = new FlowLayout(FlowLayout.CENTER);
        flowLayout.setAlignOnBaseline(true);
        final JPanel topPanel = new JPanel(flowLayout);
        topPanel.add(back);
        topPanel.add(mainButton);
        topPanel.add(options);
        topPanel.setOpaque(false);

        final JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
        int gap = LayoutStyle.getInstance().getContainerGap(percentageButton, SwingConstants.WEST, bottomPanel);
        bottomPanel.add(Box.createRigidArea(new Dimension(gap, 0)));
        bottomPanel.add(percentageButton);
        bottomPanel.add(undoButton);
        bottomPanel.add(redoButton);
        bottomPanel.add(Box.createHorizontalGlue());

        bottomPanel.add(new StatusLineElement().getStatusLineElement());
        gap = LayoutStyle.getInstance().getContainerGap(clock, SwingConstants.EAST, bottomPanel);
        bottomPanel.add(Box.createRigidArea(new Dimension(gap, 0)));
        bottomPanel.add(clock);
        bottomPanel.add(Box.createRigidArea(new Dimension(gap, 0)));

        buttonsPane.setLayout(new StackLayout());
        buttonsPane.add(topPanel, StackLayout.TOP);
        buttonsPane.add(bottomPanel, StackLayout.BOTTOM);

        glass.setVisible(true);
        //don't use pack so saved size information is not nuked to the minimal required size
        frame.validate();
        //set the forward button as the focused component (invoke later to process after all events)
        SwingUtilities.invokeLater(DynamicRunnable.create(mainButton, "requestFocus"));
    }

    private void addListeners() {
        mainPanel.addMouseWheelListener(new WheelPageMovement());
        bookList.setAction(Key.Select_book.getAction());

        undoRedo.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                undoButton.setEnabled(undoRedo.canUndo());
                redoButton.setEnabled(undoRedo.canRedo());
            }
        });

        pane.addPropertyChangeListener(MovingPane.DOCUMENT_CHANGED, DynamicListener.createListener(undoRedo, "discardAllEdits"));
        pane.addPropertyChangeListener(MovingPane.MOUSE_CLICK_HYPERLINK, DynamicListener.createEventListener(this, "hyperLinkClicked"));
        pane.addPropertyChangeListener(MovingPane.MOUSE_ENTER_HYPERLINK, DynamicListener.createEventListener(this, "hyperLinkEntered"));
        pane.addPropertyChangeListener(MovingPane.MOUSE_EXIT_HYPERLINK, DynamicListener.createEventListener(this, "hyperLinkExited"));

        Library.addPropertyChangeListener(Library.LIBRARY_CHANGE, new LibraryUpdateListener());
        //after all registation, read the library state and possibly send events
        bookList.validateLibrary();
    }

    public void moveForward() {
        pane.moveForward();
    }

    public void moveBackward() {
        pane.moveBackward();
    }

    private void blockView(String title, String text) {
        if (title == null || title.equals("")) {
            frame.setTitle("BookJar");
        } else {
            frame.setTitle("BookJar - " + title);
        }
        glass.setText(text);
        glass.block();
        showList(false);
    }

    public void read(final LocalBook book) {
        if (book.notExists()) {
            //error path to prevent most 'file removed under us' errors
            return;
        }
        saveCurrentBookIndex();
        String name = book.getFileName();
        blockView(name, "Reading " + name);
        DynamicSwingWorker.createWithFinished(this, "swingWorkerReadFinished", "swingWorkerRead", book).execute();
    }

    /**
     * @param book to read
     * @return returns if it had success or not
     */
    public Boolean swingWorkerRead(LocalBook book) {
        try {
            pane.read(book.getURL(), book.getBookmark(), book.getMetadata());
            startLanguageFinderThread(book);
            return true;
        } catch (IOException e) {
            //do not remove books since they can be repaired
            LogManager.getLogger().error("fatal error reading " + book, e);
        }
        return false;
    }

    public void swingWorkerReadFinished(Boolean hadSuccess) {
        glass.unBlock();
        showList(!hadSuccess);
        mainButton.requestFocus();
    }

    public void toggleList() {
        showList(!bookList.isViewVisible());
    }

    public void showList(boolean showList) {
        boolean inList = bookList.isViewVisible();
        if (showList && !inList) {
            //to allow the percentage to show correct values
            saveCurrentBookIndex();
            mainPanel.removeAll();
            mainPanel.add(bookList.getView(), BorderLayout.CENTER);
            bookList.getView().requestFocusInWindow();
            mainPanel.validate();
            mainPanel.repaint();
        } else if (!showList && inList) {
            searchPane.setCollapsed(true);
            mainPanel.removeAll();
            mainPanel.add(searchPane, BorderLayout.NORTH);
            mainPanel.add(buttonsPane, BorderLayout.SOUTH);
            mainPanel.add(pane.getView(), BorderLayout.CENTER);
            mainButton.requestFocusInWindow();
            mainPanel.validate();
            mainPanel.repaint();
        }
    }

    public void toggleGutenbergList() {
        boolean inList = gutenbergPanel.isViewVisible();
        if (inList) {
            searchPane.setCollapsed(true);
            mainPanel.removeAll();
            mainPanel.add(searchPane, BorderLayout.NORTH);
            mainPanel.add(buttonsPane, BorderLayout.SOUTH);
            mainPanel.add(pane.getView());
            mainButton.requestFocusInWindow();
            mainPanel.validate();
            mainPanel.repaint();
        } else {
            mainPanel.removeAll();
            mainPanel.add(gutenbergPanel.getView());
            gutenbergPanel.getView().requestFocusInWindow();
            mainPanel.validate();
            mainPanel.repaint();
        }
    }

    public void toggleBottomBar() {
        buttonsPane.setCollapsed(!buttonsPane.isCollapsed());
    }

    public LibraryView getLibraryView() {
        return bookList;
    }

    private void saveCurrentBookIndex() {
        final Path key = IoUtils.toFile(pane.getURL());
        if (key == null) {
            return;
        }
        final int index = pane.getIndex();
        final float lastVisiblePercentage = pane.getLastVisiblePercentage();
        bookList.withLock(new Runnable() {
            @Override
            public void run() {
                LocalBook book = bookList.get(key);
                if (book != null) {
                    book = book.setBookmark(index).setReadPercentage(lastVisiblePercentage);
                    bookList.replace(book);
                }
            }
        });
    }

    public void showPopup(ActionEvent evt) {
        JComponent label = (JComponent) evt.getSource();
        popup.show(label, label.getWidth() / 2, label.getHeight() / 2);
    }

    public void increaseFontSize() {
        pane.getDocumentStyle().setFontSize(pane.getDocumentStyle().getFontSize() + 1);
        pane.getDocumentStyle().change();
    }

    public void decreaseFontSize() {
        pane.getDocumentStyle().setFontSize(pane.getDocumentStyle().getFontSize() - 1);
        pane.getDocumentStyle().change();
    }

    public void addWindowListener(WindowListener listener) {
        frame.addWindowListener(listener);
    }

    public void fullScreen() {
        frame.setFullScreen(!frame.isFullScreen());
    }

    public void createAndShowOptions() {
        Options optionsPanel = new Options(this);
        optionsPanel.setLocationRelativeTo(frame);
        optionsPanel.setVisible(true);
    }

    public void hyperLinkClicked(final PropertyChangeEvent evt) {
        Integer oldValue = (Integer) evt.getOldValue();
        Integer newValue = (Integer) evt.getNewValue();
        pane.setIndex(newValue);
        undoRedo.addEdit(new MovementUndoableEdit(oldValue, newValue, pane));
    }

    public void hyperLinkEntered(final PropertyChangeEvent evt) {
        Cursor linkCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        frame.setCursor(linkCursor);
    }

    public void hyperLinkExited(final PropertyChangeEvent evt) {
        Cursor defaultCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
        frame.setCursor(defaultCursor);
    }

    public void sortLibrary() {
        bookList.sortLibrary();
    }

    public void showFind() {
        if (gutenbergPanel.isViewVisible()) {
            gutenbergPanel.requestSearchFocusInWindow();
        } else if (bookList.isViewVisible()) {
            bookList.requestSearchFocusInWindow();
        } else {
            if (searchPane.isCollapsed()) {
                //start from visible...
                searchPane.setCollapsed(false);
            }
            searchText.selectAll();
            searchText.requestFocusInWindow();
        }
    }

    public void find() {
        String searchWord = searchText.getText();
        int index = pane.getIndex();
        SearchIterator search = pane.getSearchIterator(searchWord, index + 1, true);
        if (!search.hasNext()) {
            //try from the start...
            search = pane.getSearchIterator(searchWord, 0, true);
        }

        //only interrested in searches that are not in the same word
        while (search.hasNext()) {
            int next = search.next();
            int wordLoc = pane.setWordIndex(next, Bias.Forward);
            if (wordLoc != index) {
                break;
            }
        }
    }

    public void previous() {
        String searchWord = searchText.getText();
        SearchIterator search = pane.getSearchIterator(searchWord, pane.getIndex(), true);

        if (!search.hasPrevious()) {
            //try from the end
            search = pane.getSearchIterator(searchWord, pane.getLength(), true);
        }

        if (search.hasPrevious()) {
            int previous = search.previous();
            pane.setWordIndex(previous, Bias.Backward);
        }
    }

    public void undo() {
        undoRedo.undo();
    }

    public void redo() {
        undoRedo.redo();
    }

    public void hideFind() {
        searchPane.setCollapsed(true);
        mainButton.requestFocusInWindow();
    }

    public void removeSelectedBooks() throws Exception {
        if (bookList.isViewVisible()) {
            bookList.removeBooks(bookList.getSelected());
        }
    }

    public void openSelectedBookFolders() {
        if (!bookList.isViewVisible()) {
            return;
        }
        Set<Path> books = new HashSet<>();
        for (LocalBook selected : bookList.getSelected()) {
            Path fileToOpen = selected.getAbsoluteFile();
            fileToOpen = fileToOpen.getParent();
            if (fileToOpen != null) {
                books.add(fileToOpen);
            }
        }
        for (Path file : books) {
            try {
                Desktop.getDesktop().open(file.toFile());
            } catch (IOException ex) {
                LogManager.getLogger().error("exception opening folder", ex);
            }
        }
    }

    public void bookSelected() {
        for (LocalBook book : bookList.getSelected()) {
            showList(false);
            read(book);
            break;
        }
    }

    public void linkLibraryThing() {
        //either the gutenberg panel or the bookmarks panel is visible or none, not both
        Iterable<? extends Book> selected;
        if (bookList.isViewVisible()) {
            selected = bookList.getSelected();
        } else if (gutenbergPanel.isViewVisible()) {
            selected = gutenbergPanel.getSelected();
        } else {
            return;
        }
        try {
            for (Book book : selected) {
                i3.util.Tuples.T2<String[], String> authorsAndTitle = book.authorsAndTitle();
                String[] authors = authorsAndTitle.getFirst();
                String title = authorsAndTitle.getSecond();
                //only first author needed, just to disambiguate
                String name = authors.length > 0 ? authors[0] : "";
                URI likelyBookURI = new URI("http://www.librarything.com/title/" + FastURLEncoder.encode(name + " " + title));
                Desktop.getDesktop().browse(likelyBookURI);
            }
        } catch (URISyntaxException | IOException ex) {
            LogManager.getLogger().error("exception browing to librarything", ex);
        }
    }
    private transient Executor exe;
    private transient NGramProfiles profiles;

    private void startLanguageFinderThread(LocalBook book) throws IOException {
        if (book.getDisplayLanguage() != null) {
            return;
        }
        if (profiles == null) {
            profiles = new NGramProfiles();
            exe = Executors.newSingleThreadExecutor();
        }
        exe.execute(i3.swing.dynamic.DynamicRunnable.create(this, "discoverLanguage", book));
    }

    public void discoverLanguage(final LocalBook book) {
        NGramProfiles.Ranker ranker = profiles.getRanker();
        int end = Math.min(pane.getLength(), 5500);
        ranker.account(pane.getText(0, end));
        NGramProfiles.RankResult result = ranker.getRankResult();
        if (result.getScore(0) < 0.6F) {
            //too low to be conclusive
            return;
        }
        final LibraryView marks = getLibraryView();
        final String language = result.getName(0);
        marks.withLock(new Runnable() {
            @Override
            public void run() {
                LocalBook currentBook = marks.get(book.getAbsoluteFile());
                if (currentBook != null) {
                    marks.replace(currentBook.setLanguage(language));
                }
            }
        });
    }

    private class WheelPageMovement implements MouseWheelListener {

        @Override
        public void mouseWheelMoved(MouseWheelEvent evt) {
            if (pane.isViewVisible()) {
                if (evt.getWheelRotation() > 0) {
                    moveForward();
                } else {
                    moveBackward();
                }
            }
        }
    }

    public class LibraryUpdateListener implements PropertyChangeListener {

        Notification libraryNotif;

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            //only one notification for this property. It either is solved or not
            if (libraryNotif != null) {
                libraryNotif.clear();
            }

            boolean libExists = !Library.libraryNotExists();
            Key.Toggle_gutenberg.getAction().setEnabled(libExists);
            Key.Close_gutenberg.getAction().setEnabled(libExists);
            Key.Toggle_library.getAction().setEnabled(libExists);
            Key.Close_library.getAction().setEnabled(libExists);
            Key.Remove_books.getAction().setEnabled(libExists);
            Key.Open_folders.getAction().setEnabled(libExists);
            Key.Sort_library.getAction().setEnabled(libExists);
            Key.Select_book.getAction().setEnabled(libExists);
            EventQueue.invokeLater(DynamicRunnable.create(this, "libNotification", evt.getNewValue()));
        }

        public void libNotification(LibraryUpdate update) {
            String shortMsg = null;
            String longMsg = null;
            Category category = null;
            ActionListener resolve = Key.Select_library_directory.getAction();

            if (update.available) {
                //1: show info if it didn't add/repair any books and the user has no books before/after calling
                boolean wasEmptyIsEmpty = update.previousBooks == 0 && update.addedBooks == 0 && update.repairedBooks == 0;
                //2a: special warning if not found any book the user had
                boolean wasFullIsEmpty = update.previousBooks != 0 && update.broken.size() == update.previousBooks;
                //2b: show warning it didn't find all books that the user had before calling
                boolean wasFullIsMissing = update.previousBooks != 0 && !update.broken.isEmpty();
                if (wasEmptyIsEmpty) {
                    shortMsg = "The library is empty";
                    longMsg = "(" + update.libraryRoot + ") did not add books, please C&P book files or select a new directory";
                    category = INFO;
                } else if (wasFullIsEmpty) {
                    shortMsg = "The library is missing all previous books";
                    longMsg = "(" + update.libraryRoot + ") is missing all the previous books, click here to repair";
                    category = WARNING;
                } else if (wasFullIsMissing) {
                    shortMsg = "The library is missing books";
                    longMsg = "(" + update.libraryRoot + ") is missing " + update.broken.size() + " out of " + update.previousBooks + " previous books, click to remove permanently";
                    category = WARNING;
                    resolve = DynamicAction.createAction(null, this, "booksMissing", update);
                    LogManager.getLogger().warn("missing " + update.broken);
                }
                if (wasEmptyIsEmpty || wasFullIsEmpty || wasFullIsMissing) {
                    showNotificaton(shortMsg, longMsg, resolve, category);
                }
            } else {
                //1: show warning if it doesn't exist and the user has no books in lib
                //2: show error if it doesn't exist and the user has books in lib (everything broken)
                if (update.previousBooks == 0) {
                    shortMsg = "The library is not set";
                    longMsg = "Click to select a directory to use as a library";
                    category = WARNING;
                } else {
                    shortMsg = "The library directory is invalid";
                    longMsg = "(" + update.libraryRoot + ") is invalid, click to select a directory to repair";
                    category = ERROR;
                }
                showNotificaton(shortMsg, longMsg, resolve, category);
            }
        }

        private void showNotificaton(String shortMsg, String longMsg, ActionListener resolve, Category category) {
            showList(false);
            buttonsPane.setAnimated(false);
            buttonsPane.setCollapsed(false);
            buttonsPane.setAnimated(true);
            NotificationDisplayer n = NotificationDisplayer.getDefault();
            libraryNotif = n.notify(shortMsg, null, longMsg, resolve, HIGH, category);
        }

        public void booksMissing(LibraryUpdate update) {
            bookList.removeBooks(update.broken);
            //unlike the Select_library_directory action, this won't trigger a update
            if (libraryNotif != null) {
                libraryNotif.clear();
            }
        }
    }
}
