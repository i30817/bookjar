/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package i3.decompress;

import java.util.Comparator;

/**
 * The interface for compressed file comparators. There is
 * a enum implementation util.io.compressed.Content
 * that has most of the possible sortings.
 */
public interface FileComparator {

    /**
     * Produce a comparator for the Content, where the Comparator arguments
     * are Object headers and the content instance must be extracted
     * using the given extractor.
     * @param extractor
     * @return a comparator natural order.
     */
    Comparator getComparator(final Extractor extractor);
}
