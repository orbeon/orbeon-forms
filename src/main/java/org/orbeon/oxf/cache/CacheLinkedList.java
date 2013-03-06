package org.orbeon.oxf.cache;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This is a minimal linked list optimized for the cache.
 */
public class CacheLinkedList<E> implements Iterable<E> {

    public static class ListEntry<E> {
        public E element;
        public ListEntry<E> next;
        public ListEntry<E> prev;

        public ListEntry() {
        }

        public ListEntry(E element, ListEntry next, ListEntry previous) {
            this.element = element;
            this.next = next;
            this.prev = previous;
        }
    }

    private ListEntry<E> listHeader;
    private int size;

    public CacheLinkedList() {
        clear();
    }

    public void clear() {
        listHeader = new ListEntry<E>();
        listHeader.next = listHeader.prev = listHeader;
        size = 0;
    }

    public E getFirst() {
        return listHeader.next.element;
    }

    public E getLast() {
        return listHeader.prev.element;
    }

    public ListEntry getLastEntry() {
        return listHeader.prev;
    }

    public E removeLast() {
        final E last = listHeader.prev.element;
        remove(listHeader.prev);
        return last;
    }

    public ListEntry<E> addFirst(E o) {
        return addBefore(o, listHeader.next);
    }

    private ListEntry<E> addBefore(E o, ListEntry<E> e) {
        final ListEntry<E> newEntry = new ListEntry<E>(o, e, e.prev);
        newEntry.prev.next = newEntry;
        newEntry.next.prev = newEntry;
        size++;
        return newEntry;
    }

    public void remove(ListEntry<E> e) {
        if (e == listHeader)
            throw new NoSuchElementException();

        e.prev.next = e.next;
        e.next.prev = e.prev;
        size--;
    }

    public int size() {
        return size;
    }

    public Iterator<E> iterator() {
        return new Iterator<E>() {

            private ListEntry<E> currentEntry = listHeader.next;

            public boolean hasNext() {
                return currentEntry != null && currentEntry.element != null;
            }

            public E next() {
                final E result = currentEntry.element;
                currentEntry = currentEntry.next;
                return result;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public Iterator<E> reverseIterator() {
        return new Iterator<E>() {

            private ListEntry<E> currentEntry = listHeader.prev;

            public boolean hasNext() {
                return currentEntry != null && currentEntry.element != null;
            }

            public E next() {
                final E result = currentEntry.element;
                currentEntry = currentEntry.prev;
                return result;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
