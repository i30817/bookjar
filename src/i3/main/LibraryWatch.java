package i3.main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Callable;

/**
 *
 * @author i30817
 */
public final class LibraryWatch {

    static Callable startWatchdog(final WatchService dirTreeWatcher, final Path libraryRoot, final Library library) {
        return new Callable() {

            @Override
            public Object call() throws IOException {
                try {
                    while (true) {
                        WatchKey key = dirTreeWatcher.take();
                        pollDir(key);
                    }
                } catch (InterruptedException x) {
                    //normal
                } finally {
                    dirTreeWatcher.close();
                }
                return null;
            }

            private void pollDir(WatchKey key) throws IOException, InterruptedException {

                for (WatchEvent event : key.pollEvents()) {
                    WatchEvent.Kind kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path file = ev.context();
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (Files.isDirectory(file)) {
                            file.register(dirTreeWatcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                        } else {
                            library.createIfAbsent(libraryRoot.resolve(file), null, false);
                        }
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        if (Files.isDirectory(file)) {
                            key.cancel();
                            //uhoh
                            if (file.equals(libraryRoot)) {
                                int size = library.size();
                                library.setLibraryAvailable(new Library.LibraryUpdate(false, size, 0, 0, size));
                                throw new InterruptedException();
                            }
                        } else {
                            library.breakBookIfItExists(file);
                        }
                    }
                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            }

        };
    }

}
