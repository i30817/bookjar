package i3.ui;

import javax.swing.Action;
import javax.swing.KeyStroke;
import i3.swing.Bind;
import i3.swing.dynamic.DynamicAction;
import i3.swing.dynamic.LazyObjectCall;

/**
 * Used on Bind and ShortcutOptions to link actions to descriptions and
 * keystrokes
 */
public enum Key implements Bind.Binding {

    //these actions target needs to be late binded due to the way enum deserialization works.
    //actions with globally bindable shortcuts (have a keystroke in addDefaultShortcuts)
//these actions target needs to be late binded due to the way enum deserialization works.
    //actions with globally bindable shortcuts (have a keystroke in addDefaultShortcuts)
//these actions target needs to be late binded due to the way enum deserialization works.
    //actions with globally bindable shortcuts (have a keystroke in addDefaultShortcuts)
//these actions target needs to be late binded due to the way enum deserialization works.
    //actions with globally bindable shortcuts (have a keystroke in addDefaultShortcuts)
//these actions target needs to be late binded due to the way enum deserialization works.
    //actions with globally bindable shortcuts (have a keystroke in addDefaultShortcuts)
//these actions target needs to be late binded due to the way enum deserialization works.
    //actions with globally bindable shortcuts (have a keystroke in addDefaultShortcuts)
//these actions target needs to be late binded due to the way enum deserialization works.
    //actions with globally bindable shortcuts (have a keystroke in addDefaultShortcuts)
//these actions target needs to be late binded due to the way enum deserialization works.
    //actions with globally bindable shortcuts (have a keystroke in addDefaultShortcuts)
    Toggle_fullscreen(DynamicAction.createAction("Set/Reset fullscreen", "Toggles full screen", LazyApp.instance, "fullScreen"), "alt ENTER"),
    Select_library_directory(i3.swing.SwingUtils.openFileChooser("Select library directory", "Monitors a directory for added and removed books - these include compressed files, but those may fail when reading", null, new ChooserCallback()), "pressed A"),
    Move_forward(DynamicAction.createAction("Forward", "Move Forward when reading a book", LazyApp.instance, "moveForward"), "pressed RIGHT"),
    Move_backward(DynamicAction.createAction("Backward", "Move Backward when reading a book", LazyApp.instance, "moveBackward"), "pressed LEFT"),
    Toggle_library(DynamicAction.createAction("Show/Hide personal library", "Hide/show known books", LazyApp.instance, "toggleList"), "pressed ESCAPE"),
    Toggle_gutenberg(DynamicAction.createAction("Show/Hide gutenberg search", "Search for books of the Gutenberg project with a local database", LazyApp.instance, "toggleGutenbergList"), "pressed G"),
    Options(DynamicAction.createAction("Options", "Show options dialog", LazyApp.instance, "createAndShowOptions"), "pressed C"),
    Increase_font(DynamicAction.createAction("Plus font size", "Increase font", LazyApp.instance, "increaseFontSize"), "control PLUS"),
    Decrease_font(DynamicAction.createAction("Minus font size", "Decrease font", LazyApp.instance, "decreaseFontSize"), "control MINUS"),
    Undo(DynamicAction.createAction("Undo move", "Return to a previously clicked link", LazyApp.instance, "undo"), "pressed BACK_SPACE"),
    Redo(DynamicAction.createAction("Redo move", "Return to the destination of a clicked link after a undo", LazyApp.instance, "redo"), "shift BACK_SPACE"),
    Toggle_bottom_panel(DynamicAction.createAction("Show/Hide panel", "Show bottom bar", LazyApp.instance, "toggleBottomBar"), "pressed M"),
    Show_find(DynamicAction.createAction("Find", "Find contextual shortcut", LazyApp.instance, "showFind"), "control F"),
    Remove_books(DynamicAction.createAction("Remove books", "Remove selected books (on the added menu list)", LazyApp.instance, "removeSelectedBooks"), "pressed DELETE"),
    Open_folders(DynamicAction.createAction("Open directory", "Open selected books folders (on the added menu list)", LazyApp.instance, "openSelectedBookFolders"), "pressed O"),
    Browse_libraryThing(DynamicAction.createAction("LibraryThing book page", "Browse the selected books pages on LibraryThing", LazyApp.instance, "linkLibraryThing"), "pressed ADD"),
    //actions with no global shortcut (there may be a local one)
    Popup_percent(DynamicAction.createEventAction("[ \u25b2 ]", null, LazyApp.instance, "showPopup"), null),
    Find(DynamicAction.createAction("Find", LazyApp.instance, "find"), null),
    Find_previous(DynamicAction.createAction("Previous", LazyApp.instance, "previous"), null),
    Hide_find(DynamicAction.createAction(" \u2297 ", LazyApp.instance, "hideFind"), null),
    List_select_book(DynamicAction.createAction("Read book", LazyApp.instance, "listSelected"), null),
    Sort_library(DynamicAction.createAction("Sort library", LazyApp.instance, "sortLibrary"), null),
    //don't rebing the original actions directly because they need the right name to display (and so do the original)
    Close_gutenberg(DynamicAction.createEventAction(" \u2297 ", null, Toggle_gutenberg.getAction(), "actionPerformed"), null),
    Close_library(DynamicAction.createEventAction(" \u2297 ", null, Toggle_library.getAction(), "actionPerformed"), null);

    private Key(Action action, String defaultKeystroke) {
        this.action = action;

        if (defaultKeystroke != null) {
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
        Object v = action.getValue(Action.NAME);
        return (String) (v == null ? "(null)" : v);
    }

    private transient KeyStroke keyStroke;
    private final transient Action action;

    static class LazyApp implements LazyObjectCall {

        static LazyApp instance = new LazyApp();

        //this is needed (even if app is already a singleton) because app is not a
        //_true_ singleton because it gets deserialized. If any expection happens
        //during deserialization, using Application.app directly in the dynamic classes
        //before the exception (like the ones above, that get binded when anything accesses
        //the enum - including deserialization) would bind them to the wrong
        //instance or null (not to mention, cause a memory leak).
        @Override
        public Object call() throws Exception {
            return Application.app;
        }
    }
}
