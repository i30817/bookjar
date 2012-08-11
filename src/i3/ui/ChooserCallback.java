package i3.ui;

import java.awt.Frame;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import i3.parser.BookLoader;
import i3.util.Strings;
import i3.io.FileVisitors.ListFileVisitor;
import i3.io.IoUtils;
import i3.swing.SwingUtils;

final class ChooserCallback extends SwingUtils.ChooserCallback<Path, Void> {

    @Override
    public Frame getParentFrame() {
        return Application.app.frame;
    }

    @Override
    public File getStartFile() {
        return Application.app.chooserStartFile;
    }

    @Override
    protected Path doInBackground() throws Exception {
        ListFileVisitor g = new ListFileVisitor() {

            public boolean accepts(Path file) {
                return Files.isReadable(file) && BookLoader.acceptsFiles(file.getFileName().toString());
            }
        };
        for (File p : selectedPaths) {
            Files.walkFileTree(p.toPath(), g);
        }
        if (!g.paths.isEmpty()) {
            Collections.sort(g.paths, new Comparator<Path>() {

                @Override
                public int compare(Path o1, Path o2) {
                    return Strings.compareNatural(o1.getFileName().toString(), o2.getFileName().toString());
                }
            });
            Application.app.getBookMarks().putAll(g.paths);
        }
        return g.paths.size() == 1 ? g.paths.get(0) : null;
    }

    @Override
    protected void done(Path first) {
        if (!selectedPaths.isEmpty()) {
            //save for next execution
            Application.app.chooserStartFile = selectedPaths.iterator().next();
        }
        if (first == null) {
            Application.app.showList(true);
        } else {
            Application.app.read(IoUtils.toURL(first));
        }
    }

}
