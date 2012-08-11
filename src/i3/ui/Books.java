package i3.ui;

import ca.odell.glazedlists.BasicEventList;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.logging.Level;
import i3.main.Bookjar;
import i3.main.LocalBook;
import i3.io.FileVisitors;
import i3.io.IoUtils;
import i3.io.ObjectsReader;

class Books implements Serializable {

    private static final Path libraryState = Bookjar.programLocation.resolve("library.bin");
    private static final Path partialLibraryDir = Bookjar.programLocation.resolve("b");
    //this is seperate from the normal serialization graph to be able to be
    //independent of other program changes that would trip readObject
    //(but not writeobject as that is a programmer error)
    static BasicEventList<LocalBook> eventList;

    static {
        eventList = readState();
    }

    private static BasicEventList<LocalBook> readState() throws AssertionError {
        FileVisitors.ListFileVisitor v = new FileVisitors.ListFileVisitor();
        try (ObjectsReader it = new ObjectsReader(libraryState)) {
            eventList = it.readOrReturn(new BasicEventList<LocalBook>());
            Files.walkFileTree(partialLibraryDir, EnumSet.noneOf(FileVisitOption.class), 1, v);
        } catch (IOException ex) {
            throw new AssertionError("Exception reading state", ex);
        }
        Collections.sort(v.paths, new LastModifiedPath());
        //the partial file if it exists is the most recent change
        for (Path p : v.paths) {
            try (ObjectsReader it = new ObjectsReader(p)) {
                LocalBook b = it.read();
                eventList.remove(b);
                eventList.add(0, b);
            } catch (IOException ex) {
                Bookjar.log.warning("Localbook file corrupt");
            }
        }
        //save the rebuilt state if needed (some number of file touches that may slowdown startup)
        if (v.paths.size() > 5) {
            saveMultipleRecords();
        }
        return eventList;
    }

    private static void saveMultipleRecords() {
        //delete partial state and save the complete state
        IoUtils.deleteFileOrDir(partialLibraryDir);
        try {
            ObjectsReader.writeObjects(libraryState, eventList);
        } catch (IOException ex) {
            Bookjar.log.log(Level.SEVERE, "Problems creating", ex);
        }
    }

    private void saveSingularRecord(LocalBook lb) {
        try {
            if (!Files.isDirectory(partialLibraryDir)) {
                Files.createDirectories(partialLibraryDir);
            }
            Path p = partialLibraryDir.resolve(lb.getFile().getFileName() + ".b");
            ObjectsReader.writeObjects(p, lb);
        } catch (IOException ex) {
            Bookjar.log.log(Level.SEVERE, "Problems creating", ex);
        }
    }

    private void removeSingularRecord(LocalBook lb) {
        if (lb == null) {
            return;
        }
        Path p = partialLibraryDir.resolve(lb.getFile().getFileName() + ".b");
        try {
            Files.deleteIfExists(p);
        } catch (IOException ex) {
            Bookjar.log.warning("Problems deleting");
        }
    }

    /**not threadsafe*/
    private LocalBook removeBook(Path key) {
        for (Iterator<LocalBook> it = eventList.iterator(); it.hasNext();) {
            LocalBook l = it.next();
            if (l.getFile().equals(key)) {
                it.remove();
                return l;
            }
        }
        return null;
    }

    /**
     * @ThreadSafe
     */
    public LocalBook get(URL key) {
        Path fileKey = precondictions(key);
        if (fileKey == null) {
            return null;
        }
        eventList.getReadWriteLock().readLock().lock();
        try {
            for (LocalBook b : eventList) {
                if (b.getFile().equals(fileKey)) {
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
    public void remove(URL key) {
        if (key != null) {
            Path fileKey = IoUtils.toFile(key);
            if (fileKey == null) {
                return;
            }
            LocalBook l = null;
            eventList.getReadWriteLock().writeLock().lock();
            try {
                l = removeBook(fileKey);
            } finally {
                eventList.getReadWriteLock().writeLock().unlock();
            }
            removeSingularRecord(l);
        }
    }

    /**
     * If the key is abstent create a new bookmark,
     * otherwise use the existing bookmark.
     * In both cases move the bookmark to the first position
     * and return it.
     * @param key
     * @return the old bookmark or a new one
     * with default values if it doesn't exist yet
     * @ThreadSafe
     */
    public LocalBook createIfAbsent(final URL key) {
        Path fileKey = precondictions(key);
        LocalBook bookmark = null;
        eventList.getReadWriteLock().writeLock().lock();
        try {
            //to avoid possible duplicate when adding later
            bookmark = removeBook(fileKey);
            if (bookmark == null) {
                bookmark = new LocalBook(fileKey, null, 0, 0.0F, false);
            }
            eventList.add(0, bookmark);
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
        saveSingularRecord(bookmark);
        return bookmark;
    }

    /**
     * Completly replace the current bookmark if any
     * @param key
     * @param value
     * @threadSafe
     */
    public void put(final URL key, final LocalBook value) {
        Path fileKey = precondictions(key);
        eventList.getReadWriteLock().writeLock().lock();
        try {
            removeBook(fileKey);
            eventList.add(0, value);
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
        saveSingularRecord(value);
    }

    /**
     * Puts all keys in the bookmarks. If they aren't already
     * there, it sets the index and percentage read to 0.
     * Else, it preserves the index and percentage and may move them
     * @param keys
     * @ThreadSafe
     */
    public void putAll(final Collection<Path> keys) {
        eventList.getReadWriteLock().writeLock().lock();
        try {
            int stride = 0;
            int newBooksIndex = -1;
            for (LocalBook b : eventList) {
                //remove duplicates
                keys.remove(b.getFile());
                if (b.getReadIndex() == 0 || b.getReadPercentage() > 0.98F) {
                    //probably haven't started reading or already finished...
                    //finish less than 1F to account for unread appendixes & stuff.
                    //using index instead of percentage for 0 because
                    //get'Read'Percentage returns the index+visible chars
                    if (newBooksIndex == -1) {
                        newBooksIndex = stride;
                    }
                }
                stride++;
            }
            //since we are reading everything (huh!) put the new ones at the end
            if (newBooksIndex == -1) {
                newBooksIndex = eventList.size();
            }
            ArrayList<LocalBook> newBooks = new ArrayList<>(keys.size());
            for (Path p : keys) {
                newBooks.add(new LocalBook(p, null, 0, 0.0F, false));
            }
            eventList.addAll(newBooksIndex, newBooks);
        } finally {
            eventList.getReadWriteLock().writeLock().unlock();
        }
        saveMultipleRecords();
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

    private Path precondictions(final URL key) throws IllegalArgumentException {
        if (key == null) {
            throw new IllegalArgumentException("Given a null key url");
        }
        Path fileKey = IoUtils.toFile(key);
        if (fileKey == null) {
            throw new IllegalArgumentException("Given a non local url");
        }
        return fileKey;
    }

    private static class LastModifiedPath implements Comparator<Path> {

        @Override
        public int compare(Path o1, Path o2) {
            try {
                long x = Files.getLastModifiedTime(o1, LinkOption.NOFOLLOW_LINKS).toMillis();
                long y = Files.getLastModifiedTime(o2, LinkOption.NOFOLLOW_LINKS).toMillis();
                return (x < y) ? -1 : ((x == y) ? 0 : 1);
            } catch (IOException ex) {
                return Integer.MIN_VALUE;
            }
        }
    }
}
