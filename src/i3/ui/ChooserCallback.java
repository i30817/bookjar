package i3.ui;

import i3.swing.SwingUtils;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JFileChooser;

final class ChooserCallback extends SwingUtils.ChooserCallback<Boolean, Void> {

    @Override
    public Frame getParentFrame() {
        return Application.app.frame;
    }

    @Override
    public File getStartFile() {
        return null;
    }

    @Override
    protected int getFileMode() {
        return JFileChooser.DIRECTORIES_ONLY;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        Path library = selectedPaths.iterator().next().toPath().toRealPath();
        //run in this thread
        return Application.app.getLibraryView().updateLibrary(library).call();
    }

    @Override
    protected void done(Boolean returnValue) {
        //only show in user initiated validations, not on startup validation
        //problems are show in the library update listener
        if (returnValue) {
            Application.app.showList(true);
        }
    }
}
