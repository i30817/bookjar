/*
 */
package i3.util;

import java.util.Iterator;

/**
 * A SetIterator is a object that functions as a ListIterator
 * for a ordered Set except that the equivalent of the mutative operations
 * set and add can move elements of the set if they already exist,
 * thus changing it's iteration order (as it is iterated)
 *
 * @param <E>
 */
public interface SetIterator<E> extends Iterator<E> {

    public boolean hasPrevious();

    public E previous();

    public void setOrMove(E e);

    public void addOrMove(E e);

}
