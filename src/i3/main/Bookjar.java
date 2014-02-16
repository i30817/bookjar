package i3.main;

import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import i3.ui.Application;
import i3.io.IoUtils;
import i3.io.ObjectsReader;

/**
 * The class that starts it all, and has a few hacks too.
 */
public class Bookjar implements Runnable {

    public static final Path programLocation = getProgramLocation();
    private static final Path programStateLocation = programLocation.resolve("bookjarstate.bin");
    public static Logger log = Logger.getAnonymousLogger();
    public static Handler handle;

    private static Path getProgramLocation() {
        Path programDir = IoUtils.getApplicationDirectory(Bookjar.class);
        Path otherDir = programDir.resolve("bookjar");
        try {
            if (Files.exists(otherDir) && Files.isWritable(otherDir) && Files.isDirectory(otherDir)) {
                return otherDir;
            }
        } catch (SecurityException ex) {
            log.log(Level.INFO, "Access denied while checking for a mobile write dir");
        }
        Path stateDir = Paths.get(System.getProperty("user.home"), ".config/bookjar");
        if (IoUtils.validateOrCreateDir(stateDir, "Can't get home write dir; trying to create in mobile write dir...")) {
            return stateDir;
        }
        if (IoUtils.validateOrCreateDir(otherDir, "Can't create mobile write dir - using tmp dir.")) {
            return otherDir;
        }
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }

    public static void main(final String[] args) throws IOException {
        EventQueue.invokeLater(new Bookjar());
    }

    public void run() {
        setProperties();
        Application app;
        try (ObjectsReader it = new ObjectsReader(programStateLocation)) {
            String savedLookAndFeel = it.readOrReturn(UIManager.getSystemLookAndFeelClassName());
            setLookAndFeel(savedLookAndFeel);
            app = it.readOrLazyCreate(Application.class);
        } catch (IOException e) {//shouldn't happen
            throw new AssertionError(e);
        }

        final LocalBook head = app.getLibraryView().getFirst();
        if (head == null) {
            return;
        }
        if (head.getReadPercentage() != 1F && head.getReadPercentage() != 0F) {
            app.read(head);
        } else {
            app.toggleList();
        }
    }

    private static void setProperties() {
        //there is a bug in linux where x is stopped and the file is not written
        //i'm still unsure of the cause
        IoUtils.addShutdownHook(new SaveRunnable());

        try {
            handle = new FileHandler(programLocation.resolve("log.txt").toFile().toString(), 1024 * 1024, 2, false);
            handle.setFormatter(new SimpleFormatter());
            handle.setLevel(Level.INFO);
            log.addHandler(handle);
        } catch (IOException | SecurityException ex) {
            log.warning("Could not set the log to go to file");
        }

        //javax.swing.RepaintManager.setCurrentManager(new org.jdesktop.swinghelper.debug.CheckThreadViolationRepaintManager(true));
        //org.jdesktop.swinghelper.debug.EventDispatchThreadHangMonitor.initMonitoring();
        //System.setProperty("sun.java2d.noddraw", "true");
        System.setProperty("http.agent", "Opera/7.10 (UNIX; U) [en]");
        System.setProperty("swing.boldMetal", "true");
        System.setProperty("swing.aatext", "true");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("sun.swing.enableImprovedDragGesture", "true");
        System.setProperty("apple.menu.about.name", "BookJar");
        UIManager.put("FileChooser.readOnly", Boolean.TRUE);
        UIManager.put("substancelaf.colorizationFactor", 1.0D);

        //windows zip filechooser bug, disable reading zip files, if you can.
        if (System.getProperty("os.name").contains("Windows")) {
            try {
                new ProcessBuilder("regsvr32", "/u", "/s", System.getenv("windir") + "\\system32\\zipfldr.dll").start();
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Could not disable windows zip file handling", ex);
            }
        }
    }

    private void setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            log.log(Level.SEVERE, "Could not set look and feel", ex);
        }
    }

    private static class SaveRunnable implements Runnable {

        @Override
        public void run() {
            log.info("Started serialization");
            handle.flush();
            try {
                String lafName = UIManager.getLookAndFeel().getClass().getCanonicalName();
                ObjectsReader.writeObjects(programStateLocation, lafName, Application.app);
                log.info("Finished serialization");
                handle.flush();
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Serialization exception", ex);
            } finally {
                handle.flush();
            }
        }
    }
}
