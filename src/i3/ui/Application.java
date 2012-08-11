package i3.ui;

import i3.swing.dynamic.DynamicSwingWorker;
import i3.swing.dynamic.DynamicListener;
import i3.swing.dynamic.DynamicRunnable;
import i3.swing.SearchIterator;
import i3.swing.Bind;
import de.spieleck.app.cngram.NGramProfiles;
import i3.main.Book;
import i3.main.Bookjar;
import i3.main.LocalBook;
import i3.ui.controller.MovingPane;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.*;
import java.net.*;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.swing.*;
import javax.swing.text.Position.Bias;
import org.jdesktop.swingx.JXCollapsiblePane;
import org.jdesktop.swingx.StackLayout;
import i3.util.Call;
import i3.util.DefaultUndoManager;
import i3.io.FastURLEncoder;
import i3.io.IoUtils;
import static i3.io.IoUtils.*;
import i3.net.AuthentificationProxySelector;
import static i3.swing.SwingUtils.*;
import i3.swing.component.ClockField;
import i3.swing.component.FlowPanelBuilder;
import i3.swing.component.FullScreenFrame;
import i3.swing.component.GlassPane;
import i3.swing.component.LabelButton;

public final class Application implements Serializable {

    private static final long serialVersionUID = -17574584503570422L;
    //initialized in readObject
    private final BookMarks bookList = new BookMarks();
    final Bind actions = new Bind();
    final FullScreenFrame frame = new FullScreenFrame();
    final MovingPane pane = new MovingPane();
    boolean showClock = false;
    File chooserStartFile = new File("");
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
        //add the inverse (show the list)
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
        undoButton.setText("[ \u25c0 ]");
        redoButton = new LabelButton(Key.Redo.getAction());
        redoButton.setText("[ \u25b6 ]");
        //substance is stubborn (empty border will do nothing)
        percentageButton.putClientProperty("substancelaf.buttonnominsize", true);
        undoButton.putClientProperty("substancelaf.buttonnominsize", true);
        redoButton.putClientProperty("substancelaf.buttonnominsize", true);
        //Add actions to a popupmenu.
        JPopupMenu rightClickMenu = new JPopupMenu();
        rightClickMenu.add(Key.Add_books.getAction());
        rightClickMenu.add(Key.Toggle_library.getAction());
        rightClickMenu.add(Key.Toggle_gutenberg.getAction());
        rightClickMenu.add(Key.Toggle_fullscreen.getAction());
        rightClickMenu.add(Key.Increase_font.getAction());
        rightClickMenu.add(Key.Decrease_font.getAction());
        rightClickMenu.addSeparator();
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

        Action forward = Key.Move_forward.getAction();
        Action backward = Key.Move_backward.getAction();
        Action open = Key.Add_books.getAction();

        final JButton back = new JButton(backward);
        mainButton.setAction(forward);
        final JButton openFiles = new JButton(open);
        final Font font = new java.awt.Font("Monospaced", java.awt.Font.BOLD, percentageButton.getFont().getSize() + 2);
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
        topPanel.add(openFiles);
        topPanel.setOpaque(false);

        final JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
        int gap = LayoutStyle.getInstance().getContainerGap(percentageButton, SwingConstants.WEST, bottomPanel);
        bottomPanel.add(Box.createRigidArea(new Dimension(gap, 0)));
        bottomPanel.add(percentageButton);
        bottomPanel.add(undoButton);
        bottomPanel.add(redoButton);
        bottomPanel.add(Box.createHorizontalGlue());
        bottomPanel.add(clock);
        gap = LayoutStyle.getInstance().getContainerGap(clock, SwingConstants.EAST, bottomPanel);
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
        final URLDropCallback callback = new URLDropCallback();
        listenToDrop(pane.getView(), callback);
        listenToDrop(bookList.getView(), callback);
        mainPanel.addMouseWheelListener(new WheelPageMovement());

        bookList.addMouseListener(new MouseClickRead());
        bookList.getInputMap().put(KeyStroke.getKeyStroke("pressed ENTER"), "click");
        bookList.getActionMap().put("click", new ClickRead());

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
    }

    public void read(final String fileName) {
        URL url = IoUtils.toURL(Paths.get(fileName));
        read(url);
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

    public void read(final URL uri) {
        saveCurrentBookIndex();
        String name = getName(uri);
        blockView(name, "Reading " + name);
        DynamicSwingWorker.createWithFinished(this, "swingWorkerReadFinished", "swingWorkerRead", uri).execute();
    }

    /**
     * Returns if it had success or not
     */
    public Boolean swingWorkerRead(URL url) {
        try {
            LocalBook bookmark = bookList.createIfAbsent(url);
            pane.read(url, bookmark.getReadIndex(), bookmark.getMetadata());
            startLanguageFinderThread(bookmark);
            return true;
        } catch (IOException excp) {
            bookList.remove(url);
            Bookjar.log.log(Level.WARNING, "Removing bookmark because of IoException while attempting to read it");
        } catch (Exception e) {
            Bookjar.log.log(Level.SEVERE, "Non I/O bug", e);
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

    public BookMarks getBookMarks() {
        return bookList;
    }

    private void saveCurrentBookIndex() {
        final URL key = pane.getURL();
        if (key == null) {
            return;
        }
        final int index = pane.getIndex();
        final float lastVisiblePercentage = pane.getLastVisiblePercentage();
        Bookjar.log.info("Saving " + key.getFile() + " " + index + " " + lastVisiblePercentage);
        bookList.withLock(new Runnable() {
            @Override
            public void run() {
                LocalBook book = bookList.get(key);
                //key was removed, probably in the exception catch in read
                if (book != null) {
                    bookList.put(key, book.setReadIndex(index).setReadPercentage(lastVisiblePercentage));
                }
            }
        });
    }

    private class URLDropCallback implements Call<java.util.List<URL>> {

        @Override
        public void run(java.util.List<URL> arguments) {
            read(arguments.get(0));
        }
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

    public void removeSelectedBooks() {
        if (bookList.isViewVisible()) {
            for (LocalBook selected : bookList.getSelected()) {
                bookList.remove(selected.getURL());
            }
        }
    }

    public void openSelectedBookFolders() {
        if (bookList.isViewVisible()) {
            for (LocalBook selected : bookList.getSelected()) {
                try {
                    Desktop.getDesktop().open(selected.getFile().getParent().toFile());
                } catch (IOException ex) {
                    Bookjar.log.log(Level.SEVERE, "Exception opening folder", ex);
                }
            }
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
            Bookjar.log.log(Level.SEVERE, "Exception browing to librarything", ex);
        }
    }
    private transient Executor exe;
    private transient NGramProfiles profiles;

    private void startLanguageFinderThread(LocalBook bookmark) throws IOException {
        if (bookmark.getDisplayLanguage() != null) {
            return;
        }
        if (profiles == null) {
            profiles = new NGramProfiles();
        }
        if (exe == null) {
            exe = Executors.newSingleThreadExecutor();
        }
        exe.execute(i3.swing.dynamic.DynamicRunnable.create(this, "discoverLanguage", bookmark.getURL()));
    }

    public void discoverLanguage(final URL book) {
        NGramProfiles.Ranker ranker = profiles.getRanker();
        int end = Math.min(pane.getLength(), 2500);
        ranker.account(pane.getText(0, end));
        NGramProfiles.RankResult result = ranker.getRankResult();
        if (result.getScore(0) < 0.7F) {
            //too low to be conclusive
            return;
        }
        final BookMarks marks = getBookMarks();
        final String language = result.getName(0);
        marks.withLock(new Runnable() {
            @Override
            public void run() {
                LocalBook currentBook = marks.get(book);
                if (book != null) {
                    marks.put(book, currentBook.setLanguage(language));
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

    private class MouseClickRead extends MouseAdapter {

        @Override
        public void mouseClicked(MouseEvent evt) {
            if (evt.getClickCount() <= 1 || evt.getButton() != MouseEvent.BUTTON1) {
                return;
            }
            URL clicked = bookList.locationToURL(evt.getPoint());
            if (clicked != null) {
                showList(false);
                read(clicked);
            }
        }
    }

    private class ClickRead extends AbstractAction {

        @Override
        public void actionPerformed(ActionEvent evt) {
            for (LocalBook b : bookList.getSelected()) {
                showList(false);
                read(b.getURL());
                break;
            }
        }
    }
}
