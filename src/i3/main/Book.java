/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package i3.main;

import i3.util.Tuples;

/**
 *
 * @author fc30817
 */
public interface Book {

    /**
     * Use heuristics to extract the authors names and title from a file name
     * @return a tuple containing a array with the authors, possibly with zero elements
     * and the title
     */
    Tuples.T2<String[], String> authorsAndTitle();
}
