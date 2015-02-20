package i3.main;

import ca.odell.glazedlists.BasicEventList;
import i3.io.FileVisitors;
import i3.io.IoUtils;
import i3.io.ObjectsReader;
import i3.parser.BookLoader;
import i3.thread.Threads;
import i3.util.Strings;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
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
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.event.SwingPropertyChangeSupport;
import org.apache.logging.log4j.LogManager;

/**
 * The library collects the book metadata. It identifies books by their
 * filename, not their complete path. To actually use the books, it joins a root
 * dir to a book relative dir. This way, it is resistant to moving files to
 * other places inside the library, but not renames. When validated it will
 * 'break' non-existing books and send out a event that specifies a library
 * update, which includes broken books how many were added/repaired or exist on
 * the library. This can be used by clients to implement repair strategies such
 * as asking for a new library root dir or deleting metadata completely. It also
 * installs a file-system watcher on the library dir that breaks files if
 * deleted or adds new ones if added. Deletion/move of the lib dir will trigger
 * a notification.
 *
 * methods who return Paths, return them absolute; methods who take URLs or
 * Paths as arguments, relativize them to the library root first. Therefore, it
 * is a invariant of this class that the library root must be set to add books
 * to it.
 *
 * This class is only externalizable to be able to be used on serializable
 * classes without transient, which complicates deserialization, it uses its own
 * serialization protocol: on shutdown or used book change you should call
 * 'replace' with the updated book you were using, 'CreateIfAbsent' serializes a
 * default version of the book and 'removeBooks' and 'validateLibrary' save all
 * of the collection. This was done since the updates would need to happen
 * explicitly anyway, since LocalBook is immutable, and it's much safer and
 * faster to serialize piecemeal, preventing corruption bugs of the whole
 * collection from impatient O.S. shutdown killing the process. This uses a
 * different serialization file backend than the normal one automatically, to
 * prevent modifications from other classes invalidating the stream.
 *
 * @author i30817
 */
public final class Library implements Externalizable {

    /**
     * Property change key, LibraryUpdate in new value
     */
    public static final String LIBRARY_CHANGE = "LIBRARY_CHANGE";
    private static final PropertyChangeSupport pipe = new SwingPropertyChangeSupport(LIBRARY_CHANGE, true);
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
            eventList = it.readOrLazyCreate(BasicEventList.class);
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
                            LogManager.getLogger().warn("file corrupt " + ex.getMessage());
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
            LogManager.getLogger().error("invalid library (" + root + ") requires reseting");
            sendUpdate(LibraryUpdate.createBrokenLibraryEvent(this));
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
            LogManager.getLogger().error("during saving", ex);
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
            LogManager.getLogger().error("during saving", ex);
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
                LibraryUpdate update = Library.this.validateBooks(result.canonicalMap);
                boolean booksChanged = update.addedBooks > 0 || update.repairedBooks > 0;
                if (booksChanged || libChanged) {
                    saveMultipleRecords();
                }
                Library.sendUpdate(update);
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

    /**
     *
     * @return if the book was broken, if not it didn't exist in the Library.
     */
    boolean breakBook(Path file) {
        eventList.getReadWriteLock().writeLock().lock();
        try {
            ListIterator<LocalBook> it = getFirstBookForFileName(file);
            if (it != null) {
                LocalBook old = it.previous();
                it.set(old.setBroken(true));
                LogManager.getLogger().warn("broke " + old.getFileName());
                return true;
            }
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
        return false;
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
                    LogManager.getLogger().info("repaired " + bookmark.getFileName());
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

    private LibraryUpdate validateBooks(Map<LocalBook, LocalBook> fileBooks) {
        int stride = 0;
        int newBooksIndex = -1;
        int repairedNumber = 0;
        int listSize = 0;
        List<LocalBook> broken = new LinkedList<>();
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
                    canonical = canonical.setBroken(true);
                    broken.add(canonical);
                    it.set(canonical);
                } else if (!fileBased.haveEqualParents(canonical) || canonical.isBroken()) {
                    //on startup, isBroken will be false, because it is initially set
                    //in the branch above, but the parents might already be different
                    //(with user moves which don't affect filename, otherwise the info is lost)
                    //after startup, it is possible that by moving the library dir
                    //some previously broken book (by above or filewatcher) becomes available
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
            LogManager.getLogger().info("library repaired");
        } else if (repairedNumber > 0) {
            LogManager.getLogger().info("repaired {0} library books, {1} remain broken", repairedNumber, listSize - repairedNumber);
        }

        if (fileBooks.isEmpty()) {
            return LibraryUpdate.createEvent(libraryRoot, listSize, 0, repairedNumber, broken);
        }
        //this is done here and not before because it's likely that the cycle
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
            return LibraryUpdate.createEvent(libraryRoot, listSize, sortedBooks.size(), repairedNumber, broken);
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
    private static boolean setLibrary(final Path newLib) throws IllegalArgumentException {
        if (newLib == null || !Files.isDirectory(newLib)) {
            throw new IllegalArgumentException("null, non existent or non directory given " + newLib);
        }
        Path old = libraryRoot;
        libraryRoot = newLib;
        rootAvailable = true;
        return !newLib.equals(old);
    }

    /**
     * Sends a property change with the library state.
     *
     * If the available field of the update is set to false, the library will
     * not be available until updateLibrary is called with a valid directory.
     */
    static void sendUpdate(LibraryUpdate update) {
        rootAvailable = update.available;
        pipe.firePropertyChange(LIBRARY_CHANGE, null, update);
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

    public static final void addPropertyChangeListener(String property, PropertyChangeListener l) {
        pipe.addPropertyChangeListener(property, l);
    }

    public static final void removePropertyChangeListener(String property, PropertyChangeListener l) {
        pipe.removePropertyChangeListener(property, l);
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

    private static class BooksAndWatcherCollector extends FileVisitors.FilesTransformer<LocalBook> {

        private final Path library;
        public final WatchService watchTheLib;

        public BooksAndWatcherCollector(Path library) throws IOException {
            this.watchTheLib = FileSystems.getDefault().newWatchService();
            this.library = library;
            //watchservice wont watch a dir itself, only its immediate children
            //(yes, previsitdirectory will visit library)
            //work around this on the watch thread (filter siblings of library root)
            library.getParent().register(watchTheLib, StandardWatchEventKinds.ENTRY_DELETE);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            FileVisitResult r = super.preVisitDirectory(dir, attrs);
            if (r == FileVisitResult.CONTINUE) {
                dir.register(watchTheLib, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            }
            return r;
        }

        @Override
        public LocalBook accepts(Path file) {
            if (BookLoader.acceptsFiles(file.getFileName().toString())) {
                //book found here should be relative to the library dir!
                return new LocalBook(library.relativize(file), null, 0, 0.0F, false, false);
            }
            return null;
        }
    }

}
