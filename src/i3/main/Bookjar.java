package i3.main;

import i3.io.IoUtils;
import i3.io.ObjectsReader;
import i3.ui.Application;
import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * The class that starts it all, and has a few hacks too.
 */
public class Bookjar implements Runnable {
    
    public static void main(final String[] args) throws Exception {
        new Bookjar();
    }

    public static Path programLocation;
    private final Path programStateLocation;

    public Bookjar() {
        //First shutdown hook of the app to be added after the system/native ones
        IoUtils.addShutdownHook(new SaveRunnable());
        programLocation = getProgramLocation();
        programStateLocation = programLocation.resolve("bookjarstate.bin");
        //can only start to write to file after having the program location
        System.setProperty("bookjar.log", programLocation.resolve("bookjar.log").toString());
        LogManager.getLogger().info("program directory is " + programLocation);
        EventQueue.invokeLater(this);
    }

    private static Path getProgramLocation() {
        //don't log while not inited
        Path programDir = IoUtils.getApplicationDirectory(Bookjar.class);
        Path otherDir = programDir.resolve("bookjar");
        try {
            if (Files.exists(otherDir) && Files.isWritable(otherDir) && Files.isDirectory(otherDir)) {
                return otherDir;
            }
        } catch (SecurityException ex) {
        }
        Path stateDir = Paths.get(System.getProperty("user.home"), ".config/bookjar");
        if (IoUtils.validateOrCreateDir(stateDir)) {
            return stateDir;
        }
        if (IoUtils.validateOrCreateDir(otherDir)) {
            return otherDir;
        }
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }

    public void run() {
        setProperties();
        Application app;
        try (ObjectsReader it = new ObjectsReader(programStateLocation)) {
            String savedLookAndFeel = it.readOrReturn(UIManager.getSystemLookAndFeelClassName());
            setLookAndFeel(savedLookAndFeel);
            app = it.readOrLazyCreate(Application.class);
        } catch (IOException e) {
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

    private void setProperties() {
        //javax.swing.RepaintManager.setCurrentManager(new org.jdesktop.swinghelper.debug.CheckThreadViolationRepaintManager(true));
        //org.jdesktop.swinghelper.debug.EventDispatchThreadHangMonitor.initMonitoring();
        //System.setProperty("sun.java2d.noddraw", "true");
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
                LogManager.getLogger().warn("could not disable windows zip file handling", ex);
            }
        }
    }

    private void setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            LogManager.getLogger().error("could not set look and feel", ex);
        }
    }

    private class SaveRunnable implements Runnable {

        @Override
        public void run() {
            Logger shutdownLogger = LogManager.getLogger("syslogger");
            shutdownLogger.info("started serialization");
            try {
                String lafName = UIManager.getLookAndFeel().getClass().getCanonicalName();
                ObjectsReader.writeObjects(programStateLocation, lafName, Application.app);
            } catch (IOException ex) {
                shutdownLogger.error("something wrong during serialization", ex);
            }
            shutdownLogger.info("ended serialization");
            //it was configured not to have a 'flush' shutdown hook
            Configurator.shutdown((org.apache.logging.log4j.core.LoggerContext) LogManager.getContext());
        }
    }
}
