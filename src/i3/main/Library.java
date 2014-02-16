package i3.main;

import ca.odell.glazedlists.BasicEventList;
import i3.io.FileVisitors;
import i3.io.IoUtils;
import i3.io.ObjectsReader;
import i3.parser.BookLoader;
import i3.thread.Threads;
import i3.util.Strings;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Comparator;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;

/**
 * methods who return Paths, return them absolute methods who take URLs or Paths
 * as arguments, relativize them to the library root first. Therefore, it is a
 * invariant of this class that the library root must be set to add books to it.
 *
 * @author i30817
 */
public final class Library implements Externalizable {

    private static final Path libraryState = Bookjar.programLocation.resolve("library.bin");
    private static final Path partialLibraryDir = Bookjar.programLocation.resolve("b");
    private final ExecutorService libraryWatcher = Executors.newSingleThreadExecutor(IoUtils.createThreadFactory(true));
    private Future watchDogTask = Threads.newObjectFuture(null);
    //TODO remove both of these singletons
    static volatile boolean rootAvailable = false;
    //these are serialized asides from the normal graph to be able to be
    //independent of other program changes that would trip readObject
    static volatile Path libraryRoot = null;
    public BasicEventList<LocalBook> eventList;
//    public DebugList<LocalBook> eventList;

    public Library() {
        String libRoot;
        try (final ObjectsReader it = new ObjectsReader(libraryState)) {
            libRoot = it.readOrReturn("");
//            eventList = it.readOrReturn(new DebugList<LocalBook>());
//            eventList.setLockCheckingEnabled(true);
            eventList = it.readOrReturn(new BasicEventList<LocalBook>());
            if (Files.isReadable(partialLibraryDir)) {
                FileVisitors.Files singleBookRecords = new FileVisitors.Files();
                Files.walkFileTree(partialLibraryDir, singleBookRecords);
                TreeSet<Path> records = new TreeSet<>(new LastModifiedPath());
                records.addAll(singleBookRecords.canonicalMap.keySet());
                eventList.getReadWriteLock().writeLock().lock();
                try {
                    for (Path p : records) {
                        try (ObjectsReader recIt = new ObjectsReader(p)) {
                            LocalBook b = recIt.read();
                            eventList.remove(b);
                            eventList.add(0, b);
                        } catch (IOException ex) {
                            Bookjar.log.warning("Localbook file corrupt");
                        }
                    }
                } finally {
                    eventList.getReadWriteLock().writeLock().unlock();
                }
            }
            try {
                libraryRoot = libRoot.equals("") ? null : Paths.get(libRoot);
            } catch (InvalidPathException ex) {
                libraryRoot = null;
            }
        } catch (IOException ex) {
            throw new AssertionError("unrecoverable library state", ex);
        }
    }

    /**
     * Validates the library state and sends events about it
     */
    public void validateLibrary() {
        Path root = libraryRoot;
        if (root == null || !Files.isDirectory(root)) {
            Bookjar.log.severe("Invalid library (" + root + ") requires reseting");
            int size = size();
            setLibraryAvailable(new LibraryUpdate(false, size, 0, 0, size));
            libraryRoot = null;
        } else {
            //Possibly rescue broken metadata and start watching
            libraryWatcher.submit(updateLibrary(root));
        }
    }

    private void saveMultipleRecords() {
        //delete partial state and save the complete state
        Path lib = libraryRoot;
        String saved = lib == null ? "" : lib.toString();
        IoUtils.deleteFileOrDir(partialLibraryDir);
        eventList.getReadWriteLock().readLock().lock();
        try {
            ObjectsReader.writeObjects(libraryState, saved, eventList);
        } catch (IOException ex) {
            Bookjar.log.log(Level.SEVERE, "during saving", ex);
        } finally {
            eventList.getReadWriteLock().readLock().unlock();
        }
    }

    private void saveSingularRecord(LocalBook lb) {
        try {
            if (!Files.isDirectory(partialLibraryDir)) {
                Files.createDirectories(partialLibraryDir);
            }
            Path p = partialLibraryDir.resolve(lb.getFileName() + ".b");
            ObjectsReader.writeObjects(p, lb);
        } catch (IOException ex) {
            Bookjar.log.log(Level.SEVERE, "during saving", ex);
        }
    }

    private synchronized void replaceWatchdog(WatchService result, Path dirToWatch) {
        watchDogTask.cancel(true);
        watchDogTask = libraryWatcher.submit(LibraryWatch.startWatchdog(result, dirToWatch, this));
    }

    /**
     * Trying to call() the result of this method will add all books in the
     * given library dir, and repairs broken ones if found, mark as broken if
     * not found; it will also start the WatchService thread that adds and
     * removes books to the library when the user does.
     *
     * You can run this on executors and 'get' the returned future to run in
     * other threads.
     *
     * @param parent the parent directory of all the books
     * @return if new books were added or repaired true. If not or uncertain,
     * false
     * @throws IllegalArgumentException if given a null or not a directory or
     * non existent path
     * @ThreadSafe
     */
    public Callable<Boolean> updateLibrary(final Path parent) {
        final boolean libChanged = setLibrary(parent);
        return new Callable<Boolean>() {
            @Override
            public Boolean call() throws IOException {
                final BooksAndWatcherCollector result = new BooksAndWatcherCollector(parent);
                Files.walkFileTree(parent, result);
                replaceWatchdog(result.watchTheLib, parent);
                LibraryUpdate update = Library.this.validateLibrary(result.canonicalMap);
                boolean booksChanged = update.addedBooks > 0 || update.repairedBooks > 0;
                if (booksChanged || libChanged) {
                    saveMultipleRecords();
                }
                Library.setLibraryAvailable(update);
                return booksChanged;
            }
        };
    }

    /**
     * invariant, key is relative to the library dir not threadsafe return null
     * (not found) or a listIterator at the right index to get (use previous())
     * remove() or replace()
     */
    private ListIterator<LocalBook> getFirstBookForFileName(Path key) {
        //be careful here not to compare 'Path.getFileName with LocalBook.getFileName - Path vs String
        for (ListIterator<LocalBook> it = eventList.listIterator(); it.hasNext();) {
            LocalBook l = it.next();
            if (l.getRelativeFile().getFileName().equals(key.getFileName())) {
                return it;
            }
        }
        return null;
    }

    void breakBookIfItExists(Path file) {
        eventList.getReadWriteLock().writeLock().lock();
        try {
            ListIterator<LocalBook> it = getFirstBookForFileName(file);
            if (it != null) {
                LocalBook old = it.previous();
                it.set(old.setBroken(true));
                Bookjar.log.log(Level.WARNING, "{0} was broken on library dir", old.getFileName());
            }
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
    }

    /**
     * @ThreadSafe
     */
    public LocalBook get(Path key) {
        //be careful here not to compare 'Path.getFileName with LocalBook.getFileName - Path vs String
        eventList.getReadWriteLock().readLock().lock();
        try {
            Path fileKey = invariants(key);
            for (LocalBook b : eventList) {
                if (b.getRelativeFile().getFileName().equals(fileKey.getFileName())) {
                    return b;
                }
            }
            return null;
        } finally {
            eventList.getReadWriteLock().readLock().unlock();
        }
    }

    /**
     * @ThreadSafe
     */
    int size() {
        eventList.getReadWriteLock().readLock().lock();
        try {
            return eventList.size();
        } finally {
            eventList.getReadWriteLock().readLock().unlock();
        }
    }

    /**
     * @ThreadSafe
     */
    public LocalBook getFirst() {
        eventList.getReadWriteLock().readLock().lock();
        try {
            LocalBook first = null;
            if (!eventList.isEmpty()) {
                first = eventList.get(0);
            }
            return first;
        } finally {
            eventList.getReadWriteLock().readLock().unlock();
        }
    }

    /**
     * @ThreadSafe
     */
    public void removeBooks(Collection<LocalBook> keys) {
        eventList.getReadWriteLock().writeLock().lock();
        try {
            eventList.removeAll(keys);
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
        saveMultipleRecords();
    }

    /**
     * If the key is absent create a new bookmark, otherwise use the existing
     * bookmark. If the existing bookmark is broken and the given file is valid,
     * repair it. If it is new move the bookmark to the first position. In both
     * cases return it.
     *
     * @param key must be absolute and a leaf of libraryRoot
     * @param language
     * @param gutenberg
     * @return the old bookmark or a new one with default values if it doesn't
     * exist yet
     * @ThreadSafe
     */
    public LocalBook createIfAbsent(Path key, String language, boolean gutenberg) {
        LocalBook bookmark = null;
        eventList.getReadWriteLock().writeLock().lock();
        try {
            Path fileKey = invariants(key);
            //to avoid possible duplicate when adding later
            ListIterator<LocalBook> it = getFirstBookForFileName(fileKey);
            if (it == null) {
                bookmark = new LocalBook(fileKey, language, 0, 0.0F, gutenberg, false);
                eventList.add(0, bookmark);
            } else {
                bookmark = it.previous();
                if (bookmark.isBroken() && Files.exists(key)) {
                    bookmark = bookmark.setRelativeFile(fileKey).setBroken(false);
                    Bookjar.log.log(Level.INFO, "{0} was repaired", bookmark.getFileName());
                }
            }
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
        saveSingularRecord(bookmark);
        return bookmark;
    }

    /**
     * Completely replace the current bookmark if any
     *
     * @param value
     * @threadSafe
     */
    public void replace(final LocalBook value) {
        eventList.getReadWriteLock().writeLock().lock();
        try {
            eventList.remove(value);
            eventList.add(0, value);
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
        saveSingularRecord(value);
    }

    private LibraryUpdate validateLibrary(Map<LocalBook, LocalBook> fileBooks) {
        int stride = 0;
        int newBooksIndex = -1;
        int repairedNumber = 0;
        int brokenNumber = 0;
        int listSize = 0;
        eventList.getReadWriteLock().writeLock().lock();
        try {
            listSize = eventList.size();
            ListIterator<LocalBook> it = eventList.listIterator();
            while (it.hasNext()) {
                LocalBook canonical = it.next();
                if (newBooksIndex == -1 && (canonical.getBookmark() == 0
                        || canonical.getReadPercentage() > 0.98F)) {
                    //to add at a index that is before first unread or all read book
                    //finish less than 1F to account for unread appendixes & stuff.
                    newBooksIndex = stride;
                }
                stride++;

                //remove duplicates, remember they are equal with the same filename
                LocalBook fileBased = fileBooks.remove(canonical);
                if (fileBased == null) {
                    it.set(canonical.setBroken(true));
                    brokenNumber++;
                } else if (!fileBased.haveEqualParents(canonical) && canonical.notExists()) {
                    canonical = canonical.setRelativeFile(fileBased.getRelativeFile()).setBroken(false);
                    it.set(canonical);
                    repairedNumber++;
                }
            }
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
        //since we are in the middle of reading everything (huh!)
        //put the new ones at the end
        if (newBooksIndex == -1) {
            newBooksIndex = listSize;
        }

        if (repairedNumber > 0 && repairedNumber == listSize) {
            Bookjar.log.info("library repaired");
        } else if (repairedNumber > 0) {
            Bookjar.log.log(Level.INFO, "repaired {0} library books, {1} remain broken", new Object[]{repairedNumber, listSize - repairedNumber});
        }

        if (fileBooks.isEmpty()) {
            return new LibraryUpdate(true, listSize, 0, repairedNumber, brokenNumber);
        }
        //this is done here and not before because it's likely that the cicle
        //above cut on a lot of books
        TreeSet<LocalBook> sortedBooks = new TreeSet<>(
                new Comparator<LocalBook>() {

                    @Override
                    public int compare(LocalBook o1, LocalBook o2) {
                        return Strings.compareNatural(o1.getRelativeFile().toString(), o2.getRelativeFile().toString());
                    }
                });
        sortedBooks.addAll(fileBooks.keySet());

        eventList.getReadWriteLock().writeLock().lock();
        try {
            eventList.addAll(newBooksIndex, sortedBooks);
            return new LibraryUpdate(true, listSize, sortedBooks.size(), repairedNumber, brokenNumber);
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
    }

    /**
     * To compose operations from this class for mutual exclusion
     */
    public void withLock(Runnable composition) {
        eventList.getReadWriteLock().writeLock().lock();
        try {
            composition.run();
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
    }

    /**
     * Tests invariants required to add the given key to the collection
     *
     * @param key non-null, absolute, library must be set and must relativize to
     * the library
     * @return
     * @throws IllegalArgumentException
     */
    private Path invariants(final Path key) throws IllegalArgumentException {
        if (key == null) {
            throw new IllegalArgumentException("Given a null path key");
        }
        if (!key.isAbsolute()) {
            throw new IllegalArgumentException("Given a non absolute path key");
        }
        Path root = libraryRoot;
        if (root == null) {
            throw new IllegalStateException("The library is not set");
        }
        return root.relativize(key);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        //NOP
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        //NOP
    }

    /**
     * @param newLib
     * @return library changed
     * @throws IllegalArgumentExceptio not a directory or null
     */
    static boolean setLibrary(final Path newLib) throws IllegalArgumentException {
        if (newLib == null || !Files.isDirectory(newLib)) {
            throw new IllegalArgumentException("null, non existent or non directory given " + newLib);
        }
        Path old = libraryRoot;
        libraryRoot = newLib;
        return !newLib.equals(old);
    }

    /**
     *
     * @return the library root, can be null
     */
    public static Path getRoot() {
        return libraryRoot;
    }

    /**
     * Sends a property change with available as the 'new' value.
     *
     * All books managed by a library will be 'broken' if this is set false, but
     * when it is set true, they will recover, if not set to broken meanwhile.
     */
    static void setLibraryAvailable(LibraryUpdate update) {
        rootAvailable = update.available;
        Book.pipe.firePropertyChange(Book.LIBRARY_CHANGE, null, update);
    }

    /**
     * If true accesses to the underlying file system from the files managed by
     * this library will throw exceptions (they may still throw if it exists of
     * course).
     *
     * @return if the library currently not exists.
     */
    public static boolean libraryNotExists() {
        return !rootAvailable;
    }

    /**
     * Create a path relative to the library, if path given is non-absolute and
     * the library is available.
     *
     * The file returned doesn't have to exist yet.
     *
     * @param relativePath path
     * @return Absolute path
     * @throws IllegalArgumentException if given a absolute path
     * @throws IllegalStateException if the library is not currently available
     */
    public static Path fromLibrary(String relativePath) {
        Path relative = Paths.get(relativePath);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("given absolute path");
        }
        if (!rootAvailable) {
            throw new IllegalStateException("library dir is currently unavailable");
        }
        return libraryRoot.resolve(relative);
    }

    private static class LastModifiedPath implements Comparator<Path> {

        @Override
        public int compare(Path o1, Path o2) {
            try {
                long x = Files.getLastModifiedTime(o1).toMillis();
                long y = Files.getLastModifiedTime(o2).toMillis();
                return (x < y) ? -1 : ((x == y) ? 0 : 1);
            } catch (IOException ex) {
                return Integer.MIN_VALUE;
            }
        }
    }

    private static class BooksAndWatcherCollector extends FileVisitors.FilesTransformer {

        private final Path parent;
        public final WatchService watchTheLib;

        public BooksAndWatcherCollector(Path parent) throws IOException {
            this.watchTheLib = FileSystems.getDefault().newWatchService();
            this.parent = parent;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            FileVisitResult r = super.preVisitDirectory(dir, attrs);
            if (r == FileVisitResult.CONTINUE && watchTheLib != null) {
                dir.register(watchTheLib, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            }
            return r;
        }

        @Override
        public LocalBook accepts(Path file) {
            if (BookLoader.acceptsFiles(file.getFileName().toString())) {
                //book found here should be relative to the library dir!
                return new LocalBook(parent.relativize(file), null, 0, 0.0F, false, false);
            }
            return null;
        }
    }

    public static class LibraryUpdate {

        public final boolean available;
        public final int previousBooks;
        public final int addedBooks;
        public final int repairedBooks;
        public final int missingBooks;

        public LibraryUpdate(boolean available, int previousBooks, int addedBooks, int repairedBooks, int missingBooks) {
            this.available = available;
            this.previousBooks = previousBooks;
            this.addedBooks = addedBooks;
            this.repairedBooks = repairedBooks;
            this.missingBooks = missingBooks;
        }
    }
}
