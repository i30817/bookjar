package i3.swing;

import i3.util.Call;
import i3.util.Factory;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.SwingWorker;
import i3.dragndrop.DefaultURLDropFactory;
import i3.dragndrop.DropStrategy;
import i3.io.IoUtils;

/**
 * A static swing util class. All methods of this class are not thread safe, as
 * should be obvious from the name. Swing, you know... like almost all GUI is
 * not thread safe.
 *
 * @author i30817
 */
public final class SwingUtils {

    private SwingUtils() {
    }

    /**
     * Call this to install a url oriented drop listener that waits for some
     * common drop events (files, url and text). It invokes the URL callback
     * with 1 url.
     */
    public static void listenToDrop(final Component c, final Call<List<URL>> urlCallback) {
        final Factory<DropStrategy, DataFlavor[]> f = new DefaultURLDropFactory(urlCallback);
        listenToDrop(c, f);
    }

    /**
     * Call this to install a drop listener that waits for drop events for the
     * chosen "best" DataFlavor. The factory must choose the DataFlavor it is
     * interested in by sorting and/or selection for example, then return a drop
     * strategy that handles that type. If it doesn't handle the type, it should
     * throw IllegalArgumentException
     */
    public static void listenToDrop(final Component c, final Factory<DropStrategy, DataFlavor[]> factory) {
        DropTargetListener listener = new DropTargetAdapter() {

            DropStrategy chosenDropStrategy;

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                DataFlavor[] flavors = dtde.getCurrentDataFlavors();
                if (flavors == null || flavors.length == 0) {
                    dtde.rejectDrag();
                    return;
                }

                try {
                    //choose the "best" flavor and return a correct drop strategy
                    chosenDropStrategy = factory.create(flavors);
                } catch (Exception ex) {
                    IoUtils.log.warning("Drop rejected: " + ex.getMessage());
                    dtde.rejectDrag();
                }
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                if (chosenDropStrategy == null) {
                    dtde.dropComplete(false);
                    return;
                }
                try {
                    chosenDropStrategy.drop(dtde);
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    IoUtils.log.warning("Couldn't drop object: " + ex.getMessage());
                    dtde.dropComplete(false);
                }
            }
        };
        c.setDropTarget(new DropTarget(c, listener));
    }

    /**
     * Returns an action that opens the filechooser ands invokes call.call(File
     * ... f) with the selected files
     *
     * @param actionName
     * @param longDescription
     * @param mnemomic can be null
     * @param call the file callback, set with the selected files and queried
     * for required arguments
     * @return a action that opens the filechooser
     */
    public static <ReturnValue, PublishValues> Action openFileChooser(final String actionName, String longDescription, Character mnemomic, final ChooserCallback<ReturnValue, PublishValues> call) {
        Action a = new OpenFileChooserAction<>(actionName, call);
        a.putValue(Action.MNEMONIC_KEY, mnemomic == null ? null : (int) mnemomic);
        a.putValue(Action.LONG_DESCRIPTION, longDescription);
        return a;
    }

    /**
     * a swingworker delegation object that is initialized with the selected
     * files before being executed, and that queries for some lazy config values
     */
    public static abstract class ChooserCallback<RET, CHUNK> {

        protected final CopyOnWriteArraySet<File> selectedPaths = new CopyOnWriteArraySet<>();

        void setSelectedFiles(File... files) {
            selectedPaths.clear();
            selectedPaths.addAll(Arrays.asList(files));
        }

        /**
         * Get the frame used for the current opening of the chooser
         */
        protected abstract Frame getParentFrame();

        /**
         * Get the current start used for the current opening of the chooser
         */
        protected abstract File getStartFile();

        /**
         * @see javax.swing.JFileChooser#isMultiSelectionEnabled() default false
         */
        protected boolean getMultiSelectionEnabled() {
            return false;
        }

        /**
         * @see javax.swing.JFileChooser#getFileMode() default
         * JFileChooser.FILES_AND_DIRECTORIES
         */
        protected int getFileMode() {
            return JFileChooser.FILES_AND_DIRECTORIES;
        }

        /**
         * User accepted file chooser and now is working on a swingworker thread
         *
         * @see javax.swing.SwingWorker#doInBackground()
         */
        protected abstract RET doInBackground() throws Exception;

        /**
         * User finished working on a swingworker thread and now receives the
         * final value on the EDT.
         *
         * {@link javax.swing.SwingWorker#done()}
         * {@link javax.swing.SwingWorker#get()}
         *
         */
        protected void done(RET returnValue) {
        }

        /**
         * User canceled the chooser.
         */
        protected void cancelChooser() {

        }
    }

    private static final class OpenFileChooserAction<T, V> extends AbstractAction {

        private JFileChooser chooser;
        private final ChooserCallback<T, V> call;

        public OpenFileChooserAction(String name, ChooserCallback<T, V> call) {
            super(name);
            Objects.requireNonNull(call);
            this.call = call;
        }

        private boolean initFileChooser() {
            if (chooser == null) {
                chooser = new JFileChooser(call.getStartFile());
                chooser.setDialogType(JFileChooser.OPEN_DIALOG);
                chooser.setDialogTitle((String) getValue(Action.NAME));
                chooser.setMultiSelectionEnabled(call.getMultiSelectionEnabled());
                chooser.setFileSelectionMode(call.getFileMode());
            }
            return true;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Frame parentRef = call.getParentFrame();
            if (parentRef != null && initFileChooser() && JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(parentRef)) {
                if (chooser.isMultiSelectionEnabled()) {
                    call.setSelectedFiles(chooser.getSelectedFiles());
                } else {
                    call.setSelectedFiles(chooser.getSelectedFile());
                }
                new SwingWorker<T, V>() {

                    @Override
                    protected void done() {
                        try {
                            call.done(get());
                        } catch (InterruptedException | ExecutionException ex) {
                            throw new AssertionError("Error getting a swingworker value", ex);
                        }
                    }

                    @Override
                    protected T doInBackground() throws Exception {
                        return call.doInBackground();
                    }
                }.execute();
            } else {
                call.cancelChooser();
            }

        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            //disable serialization.
            throw new NotSerializableException();
        }
    }

    /**
     * Run the runnable in the edt, and awaits completion If the runnable throws
     * an exception it is wrapped in a IllegalStateException
     *
     * @throws IllegalStateException
     */
    public static void runInEDTAndWait(Runnable r) {
        if (EventQueue.isDispatchThread()) {
            r.run();
        } else {
            try {
                EventQueue.invokeAndWait(r);
            } catch (InterruptedException | InvocationTargetException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    /**
     * Run the runnable in the edt. If we are in the edt it awaits completion,
     * otherwise, it invokes it later in the edt and continues.
     */
    public static void runInEDT(Runnable r) {
        if (EventQueue.isDispatchThread()) {
            r.run();
        } else {
            EventQueue.invokeLater(r);
        }
    }

}
