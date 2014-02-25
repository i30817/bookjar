package i3.main;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author i30817
 */
public final class LibraryUpdate {

    public final boolean available;
    public final int previousBooks;
    public final int addedBooks;
    public final int repairedBooks;
    public final Path libraryRoot;
    /**
     * Collects the broken books. If available is false, this is empty, not full
     * since the books are not 'broken' but in a undefined state.
     */
    public final List<LocalBook> broken;

    public static LibraryUpdate createBrokenLibraryEvent(Library lib) {
        return new LibraryUpdate(false, lib.libraryRoot, lib.size(), 0, 0, Collections.emptyList());
    }

    public static LibraryUpdate createEvent(Path root, int previousBooks, int addedBooks, int repairedBooks, List broken) {
        return new LibraryUpdate(true, root, previousBooks, addedBooks, repairedBooks, broken);
    }

    private LibraryUpdate(boolean available, Path root, int previousBooks, int addedBooks, int repairedBooks, List broken) {
        this.libraryRoot = root;
        this.available = available;
        this.previousBooks = previousBooks;
        this.addedBooks = addedBooks;
        this.repairedBooks = repairedBooks;
        this.broken = broken;
    }
}
