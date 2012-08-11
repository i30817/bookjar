package i3.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A simple MRU map.
 * The iteration order is the opposite of the
 * JDK LinkedHashMap LRU configuration.
 * (from most to least recently accessed).
 *
 * Warning: In this class getting a value
 * will modify iteration order (this breaks the
 * list contract, so it's not a list and the
 * operations in this class avoid it, but it's
 * the point in being MRU) and putting the value
 * at the start of the iteration, and put and putAll
 * add their values to the end of the iteration
 * (like a list add and addAll).
 *
 * Setting or getting a value from the iterators,
 * will NOT change the next or current order.
 *
 *
 * The iterators of this class are not fail fast,
 * but they are not thread safe.
 * So no structural modification
 * outside of the iterators when using them,
 * or unsynchonized access to them if working
 * with threads. Ok?
 */
@SuppressWarnings(value = "unchecked")
public final class MRUMap<K, V> extends AbstractMap<K, V> implements Externalizable {

    private Map<K, Node> delegate = new HashMap<>();
    private Node head = new Node(null, null);
    private int MAX_ENTRIES;
    private static final long serialVersionUID = 3221271453222736153L;

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        //dont use get (alters order)
        if (o instanceof MRUMap) {
            MRUMap<K, V> m = (MRUMap<K, V>) o;
            return delegate.equals(m.delegate);
        } else {
            return super.equals(o);
        }
    }

    /**
     * A double linked list node.
     * by default, initialized to behave
     * as a guardian. Never remove head
     */
    private final class Node extends SimpleEntry<K, V> {

        Node previous;
        Node next;

        public Node(K key, V value) {
            super(key, value);
        }

        boolean hasNext() {
            return next != head;
        }

        boolean hasPrevious() {
            return previous != head;
        }
    }

    private void addBefore(Node toAdd, Node node) {
        toAdd.next = node;
        toAdd.previous = node.previous;
        node.previous.next = toAdd;
        node.previous = toAdd;
    }

    private void remove(Node toRemove) {
        toRemove.previous.next = toRemove.next;
        toRemove.next.previous = toRemove.previous;
    }

    public MRUMap() {
        this(Integer.MAX_VALUE);
    }

    public MRUMap(final int maxEntries) {
        super();
        if (maxEntries < 0) {
            throw new IllegalArgumentException("maxEntries must not be negative");
        }
        head.previous = head;
        head.next = head;
        MAX_ENTRIES = maxEntries;
    }

    @Override
    public V put(final K key, final V value) {
        Node node = new Node(key, value);
        addBefore(node, head);

        node = delegate.put(key, node);

        if (removeEldestEntries()) {
            delegate.remove(head.previous.getKey());
            remove(head.previous);
        }

        if (node == null) {
            return null;
        } else {
            remove(node);
            return node.getValue();
        }
    }

    @Override
    public V get(final Object key) {
        Node node = delegate.get(key);
        if (node == null) {
            return null;
        } else {
            remove(node);
            addBefore(node, head.next);
            return node.getValue();
        }
    }

    @Override
    public V remove(final Object key) {
        Node node = delegate.remove(key);
        if (node == null) {
            return null;
        } else {
            remove(node);
            return node.getValue();
        }
    }

    @Override
    public void clear() {
        head.next = head;
        head.previous = head;
        delegate.clear();
    }

    /**
     * Used to tell when no more entries are allowed
     */
    private boolean removeEldestEntries() {
        return delegate.size() > MAX_ENTRIES;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> t) {
        Node node;
        if (delegate.isEmpty()) {
            for (Map.Entry<? extends K, ? extends V> e : t.entrySet()) {
                node = new Node(e.getKey(), e.getValue());
                addBefore(node, head);
                delegate.put(node.getKey(), node);
                if (removeEldestEntries()) {
                    delegate.remove(head.previous.getKey());
                    remove(head.previous);
                }
            }
        } else {
            for (Map.Entry<? extends K, ? extends V> e : t.entrySet()) {
                node = new Node(e.getKey(), e.getValue());
                addBefore(node, head);
                node = delegate.put(node.getKey(), node);
                if (node != null) {
                    remove(node);
                }
                if (removeEldestEntries()) {
                    delegate.remove(head.previous.getKey());
                    remove(head.previous);
                }
            }
        }
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsValue(Object value) {
        boolean contains = false;

        Node current = head;
        while (current.hasNext() && !contains) {
            current = current.next;
            V mapValue = current.getValue();
            contains = mapValue == null ? value == null : mapValue.equals(value);
        }
        return contains;
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public Set<K> keySet() {
        return new AbstractSet<K>() {

            public Iterator<K> iterator() {
                return new KeyIt();
            }

            public int size() {
                return delegate.size();
            }
        };
    }

    @Override
    public Collection<V> values() {
        return new AbstractCollection<V>() {

            public Iterator<V> iterator() {
                return new ValueIt();
            }

            public int size() {
                return delegate.size();
            }
        };
    }

    @Override
    public Set<Entry<K, V>> entrySet() {

        return new AbstractSet<Entry<K, V>>() {

            public Iterator<Entry<K, V>> iterator() {
                return new EntryIt();
            }

            public int size() {
                return delegate.size();
            }
        };
    }

    private final class EntryIt extends LRUIterator {

        public Object next() {
            return nextEntry();
        }

        public Object previous() {
            return previousEntry();
        }
    }

    private final class KeyIt extends LRUIterator {

        public Object next() {
            return nextKey();
        }

        public Object previous() {
            return previousKey();
        }
    }

    private final class ValueIt extends LRUIterator {

        public Object next() {
            return nextValue();
        }

        public Object previous() {
            return previousValue();
        }
    }

    private abstract class LRUIterator implements ListIterator {

        public Node current = head;

        public boolean hasNext() {
            return current.hasNext();
        }

        public boolean hasPrevious() {
            return current.hasPrevious();
        }

        public void remove() {
            Node old = current;
            current = current.next;
            MRUMap.this.remove(old);
            delegate.remove(old.getKey());
        }

        Map.Entry<K, V> nextEntry() {
            current = current.next;
            return current;
        }

        V nextValue() {
            current = current.next;
            return current.getValue();
        }

        K nextKey() {
            current = current.next;
            return current.getKey();
        }

        Map.Entry<K, V> previousEntry() {
            current = current.previous;
            return current;
        }

        V previousValue() {
            current = current.previous;
            return current.getValue();
        }

        K previousKey() {
            current = current.previous;
            return current.getKey();
        }

        public int nextIndex() {
            throw new UnsupportedOperationException("This list iterator only suports next or previous");
        }

        public int previousIndex() {
            throw new UnsupportedOperationException("This list iterator only suports next or previous");
        }

        public void set(Object e) {
            throw new UnsupportedOperationException("This list iterator only suports next or previous");
        }

        public void add(Object e) {
            throw new UnsupportedOperationException("This list iterator only suports next or previous");
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(MAX_ENTRIES);
        out.writeInt(delegate.size());

        for (Node current = head; current.hasNext();) {
            current = current.next;
            out.writeObject(current.getKey());
            out.writeObject(current.getValue());
        }
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        MAX_ENTRIES = in.readInt();
        int nodesNumber = in.readInt();
        delegate = new HashMap<>(nodesNumber);
        Node current, old = head;
        for (int index = 0; index < nodesNumber; index++) {
            current = new Node((K) in.readObject(), (V) in.readObject());
            addBefore(current, old.next);
            delegate.put(current.getKey(), current);
            old = current;
        }
    }
}
