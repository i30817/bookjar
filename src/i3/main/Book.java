package i3.main;

import i3.util.Tuples;
import java.beans.PropertyChangeSupport;
import javax.swing.event.SwingPropertyChangeSupport;

public interface Book {

    /**
     * Property change key, the Library dir is currently available
     */
    static final String LIBRARY_CHANGE = "LIBRARY_ROOT_IS_GONE";
    static final PropertyChangeSupport pipe = new SwingPropertyChangeSupport(LIBRARY_CHANGE, true);

    /**
     * Use heuristics to extract the authors names and title from a file name
     *
     * @return a tuple containing a array with the authors, possibly with zero
     * elements and the title
     */
    Tuples.T2<String[], String> authorsAndTitle();
}
