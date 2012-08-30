package i3.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *
 * @author microbiologia
 */
public final class Iterators {

    private Iterators() {
    }

    /**
     * Wrapper for foreach on classes that only implement iterator and not
     * iterable
     */
    public static <E, T> Iterable<E> iterable(final Iterable<T> it, final Factory<E, T> factory) {
        return new Iterable<E>() {
            @Override
            public Iterator<E> iterator() {
                final Iterator<T> itr = it.iterator();
                return new Iterator<E>() {
                    @Override
                    public boolean hasNext() {
                        return itr.hasNext();
                    }

                    @Override
                    public E next() {
                        try {
                            return factory.create(itr.next());
                        } catch (Exception ex) {
                            throw new AssertionError("factory method shouldn't throw a exception when used in iterator", ex);
                        }
                    }

                    @Override
                    public void remove() {
                        itr.remove();
                    }
                };
            }
        };
    }

    /**
     * For for each loops, and lists that can be null, enable not checking list
     * != null by using it like this: for(String s : maybe(list)){ ... } Don't
     * use this normally, but treat the code returning a null Iterable as a bug
     * when you have control over it.
     *
     * @param <E>
     * @param iterable
     * @return a iterable. If passed iterable is null, return a empty iterable.
     */
    public static <E> Iterable<E> maybe(Iterable<E> iterable) {
        return iterable == null ? Collections.<E>emptyList() : iterable;
    }

    /**
     * Allows using the for each with a toArray of object, when you need to treat
     * part of the toArray differently.
     *
     * @param <E>
     * @param toArray
     * @param index
     * @return
     */
    public static <E> Iterable<E> iterableFromIndex(final E[] array, final int index) {

        return new Iterable<E>() {
            public Iterator<E> iterator() {
                return new Iterator<E>() {
                    int localIndex = index;

                    public boolean hasNext() {
                        return localIndex < array.length;
                    }

                    public E next() {
                        try {
                            return array[localIndex++];
                        } catch (ArrayIndexOutOfBoundsException e) {
                            throw new NoSuchElementException(e.getMessage());
                        }
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("Not supported.");
                    }
                };
            }
        };
    }
}
