package i3.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * This class is a like LinkedHashSet (insertion order) but it allows querying
 * the relative position of a element and has a ListIterator that can set and
 * insert anywhere.
 *
 * Warning: the iterator can change the order of the set by moving elements when
 * setting or adding. Elements that already exist are not ignored, but moved the
 * requested place. This changes iteration order
 *
 *
 * The iterators of this class are fail fast and will throw a
 * ConcurrentModificationException if their iterator are used with intervening
 * main class (or other iterators) mutative calls
 *
 * @author i30817 <i30817@gmail.com>
 */
public class LinkedSet<E> extends AbstractSet<E> {

    //It holds the linked list
    private Map<E, Node> m = new HashMap<E, Node>();
    //head of that
    protected Node head = new Node();
    //this is copied to the map value in increments of iteratorAddStep on set.add
    //(which only adds to the end, by insertion indexing)
    private int monotonicallyIncreasing = 0;
    //iterator add step may change when doing rebuilds of the 'space' between elements
    //for the before/after functions on LinkedKeyIterator.add
    private int iteratorAddStep = 10;
    //for fail fast iterators
    private int modCount;

    /**
     * Start iterating from elem (inclusive)
     *
     *
     * @throws NoSuchElementException if E not part of the set
     * @param elem a element of the set
     * @return a ListIterator - doesn't support nextIndex() or previousIndex()
     */
    public SetIterator<E> from(E elem) {
        Node e = m.get(elem);
        if (e == null) {
            throw new NoSuchElementException("the given element isn't part of the set");
        }
        return new LinkedKeyIterator(e);
    }

    @Override
    public SetIterator<E> iterator() {
        return new LinkedKeyIterator();
    }

    /**
     * Returns true if the value target was added before (exclusive) limitElem
     * in insertion order.
     *
     * If target or limit are not present on the set this method returns false
     *
     * @param limitElem a E that may be a element of the set or not.
     * @return if target was added before limit (can be reset by removing and
     * re-adding the target, that changes iteration order).
     */
    public boolean containsBefore(E target, E limitElem) {
        if (isEmpty()) {
            return false;
        }

        Integer targetN = m.get(target).relativeLocation;
        Integer highN = m.get(limitElem).relativeLocation;
        return targetN != null && highN != null && targetN < highN;
    }

    /**
     * Returns true if the value target was added after (exclusive) previousElem
     * in insertion order.
     *
     * If target or previous are not present on the set this method returns
     * false
     *
     * @param previousElem a E that may be a element of the set or not.
     * @return if target was added before previous (can be reset by removing and
     * re-adding the target, that changes iteration order).
     */
    public boolean containsAfter(E target, E previousElem) {
        if (isEmpty()) {
            return false;
        }

        Integer targetN = m.get(target).relativeLocation;
        Integer low = m.get(previousElem).relativeLocation;
        return targetN != null && low != null && low < targetN;
    }

    @Override
    public boolean add(E e) {
        if (!m.containsKey(e)) {
            Node n = new Node(e, monotonicallyIncreasing);
            monotonicallyIncreasing += iteratorAddStep;
            n.addBefore(head);//insertion order
            m.put(e, n);
            return true;
        }
        return false;
    }

    @Override
    public int size() {
        return m.size();
    }

    @Override
    public boolean isEmpty() {
        return m.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return m.containsKey(o);
    }

    @Override
    public Object[] toArray() {
        Object[] result = new Object[size()];
        int i = 0;
        for (E e : this) {
            result[i++] = e;
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        int size = size();
        if (a.length < size) {
            a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
        }
        int i = 0;
        Object[] result = a;
        for (E e : this) {
            result[i++] = e;
        }
        if (a.length > size) {
            //peculiar toArray contract where it doesn't care about the rest
            a[size] = null;
        }
        return a;
    }

    @Override
    public boolean remove(Object o) {
        Node n = m.remove(o);
        if (n != null) {
            n.remove();
            return true;
        }
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E e : c) {
            changed |= add(e);
        }
        return changed;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        boolean all = true;
        for (Object e : c) {
            all &= m.containsKey(e);
        }
        return all;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        Iterator<E> it = iterator();
        while (it.hasNext()) {
            E k = it.next();
            if (!c.contains(k)) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        modCount++;
        head.after = head.before = head;
        m.clear();
    }

    @Override
    public String toString() {
        return m.keySet().toString();
    }

    //linkedlist node class
    protected final class Node {

        Node before, after;
        int relativeLocation;
        //needed for map removal during iteration
        E key;

        private void remove() {
            before.after = after;
            after.before = before;
            modCount++;
        }

        private void addBefore(Node existingEntry) {
            after = existingEntry;
            before = existingEntry.before;
            before.after = this;
            after.before = this;
            modCount++;
        }

        //head const
        public Node() {
            after = before = this;
            relativeLocation = 0;
        }

        public Node(E key, int value) {
            this.key = key;
            this.relativeLocation = value;
        }
    }

    protected class LinkedKeyIterator implements SetIterator<E> {

        Node nextEntry;
        Node lastReturned;
        int expectedModCount = modCount;

        public LinkedKeyIterator() {
            nextEntry = head.after;
        }

        public LinkedKeyIterator(Node startAt) {
            nextEntry = startAt;
        }

        public boolean hasPrevious() {
            return nextEntry.before != head;
        }

        public boolean hasNext() {
            return nextEntry != head;
        }

        public E next() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            if (nextEntry == head) {
                throw new NoSuchElementException();
            }

            Node e = lastReturned = nextEntry;
            nextEntry = e.after;
            return e.key;
        }

        public E previous() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            if (nextEntry.before == head) {
                throw new NoSuchElementException();
            }

            Node e = lastReturned = nextEntry.before;
            nextEntry = e;
            return e.key;
        }

        public void remove() {
            if (lastReturned == null) {
                throw new IllegalStateException();
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            m.remove(lastReturned.key);
            nextEntry = lastReturned.after;
            lastReturned.remove();
            lastReturned = null;
            expectedModCount = modCount;
        }

        @Override
        public void setOrMove(E e) {
            if (lastReturned == null) {
                throw new IllegalStateException();
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            if (lastReturned.key.equals(e)) {
                //would not change anything
                return;
            }
            //remove mapping for key since we are changing it
            m.remove(lastReturned.key);
            //put in the new one
            lastReturned.key = e;
            Node previousKeyOwner = m.put(e, lastReturned);
            if (previousKeyOwner != null) {
                //as it is a list mutation call, guard against stale iterator
                if(nextEntry == previousKeyOwner){
                    nextEntry = nextEntry.after;
                }
                previousKeyOwner.remove();
            }
            //from m.remove and m.put, may help with 2 concurrent iterators on this instance
            //this method may not change modCount if previousKeyOwner is null
            expectedModCount = ++modCount;
        }

        @Override
        public void addOrMove(E e) {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            //nuke it, as per contract of remove()
            lastReturned = null;
            //calculate a good relative location, updating subsequent ones if needed
            int candidateLoc = nextEntry.before.relativeLocation + 1;
            //opsss, it's full
            if (candidateLoc == nextEntry.relativeLocation) {
                iteratorAddStep *= 1.6;
                for (Node current = nextEntry; current != head; current = current.after) {
                    current.relativeLocation = current.relativeLocation + iteratorAddStep;
                }
            }

            Node n = m.get(e);
            if (n == null) {
                n = new Node(e, candidateLoc);
                m.put(e, n);
            } else {
                n.relativeLocation = candidateLoc;
                //as it is a list mutation call, guard against stale iterator
                if(nextEntry == n){
                    nextEntry = nextEntry.after;
                }
                n.remove();
            }
            n.addBefore(nextEntry);
            expectedModCount = modCount;//add before changes modCount
        }
    }
}
