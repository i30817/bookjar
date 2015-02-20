package i3.ui;

import i3.swing.Bind;
import i3.swing.dynamic.DynamicAction;
import i3.swing.dynamic.LazyObjectCall;
import java.awt.Font;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

/**
 * Application actions container. Used on Bind and ShortcutOptions to link
 * actions to descriptions and keystrokes (for global shortcut actions)
 */
public enum Key implements Bind.Binding {

    /**
     * Keys with global shortcuts (have a keystroke). Will be used both as
     * bindings and action containers
     */
    Toggle_fullscreen(
            DynamicAction.createAction(
                    "Set/Reset fullscreen",
                    "Toggles full screen",
                    App.app, "fullScreen"), "alt ENTER"),
    Select_library_directory(
            i3.swing.SwingUtils.openFileChooser(
                    "Select library directory",
                    "Monitors a directory for added and removed books - these include compressed files, but those may fail when reading",
                    null, new ChooserCallback()), "pressed A"),
    Move_forward(
            DynamicAction.createAction(
                    "Forward",
                    "Move Forward when reading a book",
                    App.app, "moveForward"), "pressed RIGHT"),
    Move_backward(
            DynamicAction.createAction(
                    "Backward",
                    "Move Backward when reading a book",
                    App.app, "moveBackward"), "pressed LEFT"),
    Toggle_library(
            DynamicAction.createAction(
                    "Show/Hide personal library",
                    "Hide/show known books",
                    App.app, "toggleList"), "pressed ESCAPE"),
    Toggle_gutenberg(
            DynamicAction.createAction(
                    "Show/Hide gutenberg search",
                    "Search for books of the Gutenberg project with a local database",
                    App.app, "toggleGutenbergList"), "pressed G"),
    Options(
            DynamicAction.createAction(
                    "Options",
                    "Show options dialog",
                    App.app, "createAndShowOptions"), "pressed C"),
    Increase_font(
            DynamicAction.createAction(
                    "Plus font size",
                    "Increase font",
                    App.app, "increaseFontSize"), "control PLUS"),
    Decrease_font(
            DynamicAction.createAction(
                    "Minus font size",
                    "Decrease font",
                    App.app, "decreaseFontSize"), "control MINUS"),
    Undo_move(
            DynamicAction.createAction(
                    "[ \u25C0 ]",//◀
                    "Return to a previously clicked link",
                    App.app, "undo"), "pressed BACK_SPACE") {

                        @Override
                        public String toString() {
                            //need to override toString since the action will be used
                            //as a binding and the name is not displayable in normal fonts
                            return "Undo move";
                        }

                    },
    Redo_move(
            DynamicAction.createAction(
                    "[ \u25B6 ]",//▶
                    "Return to the destination of a clicked link after a undo",
                    App.app, "redo"), "shift BACK_SPACE") {

                        @Override
                        public String toString() {
                            //need to override toString since the action will be used
                            //as a binding and the name is not displayable in normal fonts
                            return "Redo move";
                        }

                    },
    Toggle_bottom_panel(
            DynamicAction.createAction(
                    "Show/Hide panel",
                    "Show bottom bar",
                    App.app, "toggleBottomBar"), "pressed M"),
    Show_find(
            DynamicAction.createAction(
                    "Find",
                    "Find contextual shortcut",
                    App.app, "showFind"), "control F"),
    Remove_books(
            DynamicAction.createAction(
                    "Remove books",
                    "Remove selected books (on the added menu list)",
                    App.app, "removeSelectedBooks"), "pressed DELETE"),
    Open_folders(
            DynamicAction.createAction(
                    "Open directory",
                    "Open selected books folders (on the added menu list)",
                    App.app, "openSelectedBookFolders"), "pressed O"),
    Browse_libraryThing(
            DynamicAction.createAction(
                    "LibraryThing book page",
                    "Browse the selected books pages on LibraryThing",
                    App.app, "linkLibraryThing"), "pressed ADD"),
    /**
     * Keys with no global shortcut (there may be a local one). These will not
     * be used as bindings, but just as a action container.
     */
    Popup_percent(
            DynamicAction.createEventAction(
                    "[ \u25B2 ]",//▲
                    null,
                    App.app, "showPopup"), null),
    Find(
            DynamicAction.createAction(
                    "Find",
                    App.app, "find"), null),
    Find_previous(
            DynamicAction.createAction(
                    "Previous",
                    App.app, "previous"), null),
    Select_book(
            DynamicAction.createAction(
                    "Read book",
                    App.app, "bookSelected"), null),
    Sort_library(
            DynamicAction.createAction(
                    "Sort library",
                    App.app, "sortLibrary"), null),
    Hide_find(
            DynamicAction.createAction(
                    " \u25CF ",//●
                    App.app, "hideFind"), null),
    Close_gutenberg(
            DynamicAction.createAction(
                    " \u25CF ",//●
                    null,
                    App.app, "toggleGutenbergList"), null),
    Close_library(
            DynamicAction.createAction(
                    " \u25CF ",//●
                    null,
                    App.app, "toggleList"), null);

    private static class App implements LazyObjectCall {

        static final App app = new App();

        //this is needed because app is not a true singleton.
        //If any expection happens during deserialization, using Application.app
        //directly in the dynamic classes (like the ones above) would bind them
        //to the wrong instance or null (not to mention, cause a memory leak).
        //TODO when the Dynamic classes accept lambdas, fix this.
        @Override
        public Object call() throws Exception {
            return Application.app;
        }
    }

    private Key(Action action, String defaultKeystroke) {
        this.action = action;

        if (defaultKeystroke == null) {
            keyStroke = null;
        } else {
            keyStroke = KeyStroke.getKeyStroke(defaultKeystroke);
            if (keyStroke == null) {
                throw new IllegalArgumentException(defaultKeystroke + " is an invalid keystroke");
            }
        }
    }

    @Override
    public KeyStroke getDefaultKeyStroke() {
        return keyStroke;
    }

    @Override
    public Action getAction() {
        return action;
    }

    @Override
    public String toString() {
        return (String) action.getValue(Action.NAME);
    }

    private final transient KeyStroke keyStroke;
    private final transient Action action;
    /**
     * unfortunately there is no way to bind a font to the action configure,
     * since components follow the 'hollywood principle when configurating from
     * actions. Add setFont calls for all the components that need this font
     */
    final static Font EMBEDDED_FONT;

    static {
        try {
            Font defaultFont = UIManager.getDefaults().getFont("Button.font");
            EMBEDDED_FONT = Font.createFont(java.awt.Font.TRUETYPE_FONT,
                    //fontforge cutdown version of gpl2 font from
                    //http://savannah.gnu.org/projects/freefont/
                    //which supports the special chars above
                    Key.class.getResourceAsStream("bookjar.otf")
            ).deriveFont(defaultFont == null ? 20F : defaultFont.getSize2D() + 5F);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
