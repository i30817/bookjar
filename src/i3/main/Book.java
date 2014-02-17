package i3.main;

import i3.util.Tuples;

public interface Book {

    /**
     * Use heuristics to extract the authors names and title from a file name
     *
     * @return a tuple containing a array with the authors, possibly with zero
     * elements and the title
     */
    Tuples.T2<String[], String> authorsAndTitle();
}
