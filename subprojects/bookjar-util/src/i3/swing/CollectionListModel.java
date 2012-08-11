package i3.swing;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import javax.swing.AbstractListModel;

/**
 * This ListModel will be faster if the collection implementation
 * is a list, navigatable set or returns ListIterator on iterator().
 * @author Owner
 * @param <T> the collection type
 */
public class CollectionListModel<T> extends AbstractListModel {

    private Collection<T> collection;
    //different traversal stategies.
    private Strategy<T> strategy;

    //the only method that needs to be implemented is getValue
    //but the other are to use this class implementation
    private abstract class Strategy<T> implements Serializable {

        private int nextIndex;
        private Iterator<T> iterator = resetIterator();

        T getValue(int requiredIndex) {
            if (nextIndex > requiredIndex && supportsPrevious()) {
                int worthinessBoundary = nextIndex / 2;
                if (worthinessBoundary <= requiredIndex) {
                    while (nextIndex != requiredIndex) {
                        previous(iterator);
                        nextIndex--;
                    }
                } else {
                    iterator = resetIterator();
                    nextIndex = 0;
                }
            }
            while (nextIndex != requiredIndex) {
                iterator.next();
                nextIndex++;
            }
            nextIndex++;
            return iterator.next();
        }

        /**
         * Return null if you don't use the getValue above.
         * @return
         */
        protected abstract Iterator<T> resetIterator();

        protected boolean supportsPrevious() {
            throw new UnsupportedOperationException("Not supported.");
        }

        protected void previous(Iterator<T> it) {
            throw new UnsupportedOperationException("Not supported.");
        }
    }

    private final class RandomAcessListStrategy extends Strategy<T> {

        public T getValue(int requestedIndex) {
            return ((List<T>) collection).get(requestedIndex);
        }

        protected Iterator<T> resetIterator() {
            return null;
        }
    }

    private final class ListStrategy extends Strategy<T> {

        protected Iterator<T> resetIterator() {
            return ((List<T>) collection).listIterator();
        }

        protected boolean supportsPrevious() {
            return true;
        }

        protected void previous(Iterator<T> it) {
            ((ListIterator) it).previous();
        }
    }

    private final class ListIteratorStrategy extends Strategy<T> {

        protected Iterator<T> resetIterator() {
            return collection.iterator();
        }

        protected boolean supportsPrevious() {
            return true;
        }

        protected void previous(Iterator<T> it) {
            ((ListIterator<T>) it).previous();
        }
    }

    private final class CollectionStrategy extends Strategy<T> {

        protected Iterator<T> resetIterator() {
            return collection.iterator();
        }

        protected boolean supportsPrevious() {
            return false;
        }
    }

    public CollectionListModel(Collection<T> iterable) {
        super();
        this.collection = iterable;
        //efficiency order...
        if (collection instanceof RandomAccess) {
            strategy = new RandomAcessListStrategy();
        } else if (collection instanceof List) {
            strategy = new ListStrategy();
        } else if (collection.iterator() instanceof ListIterator) {
            strategy = new ListIteratorStrategy();
        } else {
            strategy = new CollectionStrategy();
        }
    }

    public int getSize() {
        return collection.size();
    }

    public T getElementAt(int index) {
        return strategy.getValue(index);
    }
}
