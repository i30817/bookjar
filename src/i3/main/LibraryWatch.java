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
                    Path file = ((Path) key.watchable()).resolve((Path) event.context());

                    //siblings are pulled as a side effect of needing to register the parent of root
                    boolean rootSibling = file.getParent().equals(libraryRoot.getParent());
                    if (rootSibling) {
                        assert kind == StandardWatchEventKinds.ENTRY_DELETE;
                        if (file.getFileName().equals(libraryRoot.getFileName())) {
                            library.setLibraryAvailable(LibraryUpdate.createBrokenLibraryEvent(library));
                            throw new InterruptedException();
                        }
                    }

                    if (!rootSibling && kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (Files.isDirectory(file)) {
                            file.register(dirTreeWatcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                        } else {
                            library.createIfAbsent(file, null, false);
                        }
                    } else if (!rootSibling && kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        //warning: Files.isDirectory will never be true on ENTRY_DELETE
                        //if it is not a book in the library but was registered it was a directory
                        if (!library.breakBook(file)) {
                            key.cancel();
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
