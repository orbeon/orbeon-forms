package org.orbeon.oxf.cache;

import java.util.NoSuchElementException;

/**
 * This is a minimal linked list optimized for the cache.
 */
public class CacheLinkedList {

    public static class ListEntry {
        public Object element;
        public ListEntry next;
        public ListEntry prev;

        public ListEntry() {
        }

        public ListEntry(Object element, ListEntry next, ListEntry previous) {
            this.element = element;
            this.next = next;
            this.prev = previous;
        }
    }

    private ListEntry listHeader = new ListEntry();
    private int size;

    public CacheLinkedList() {
        listHeader.next = listHeader.prev = listHeader;
    }

    public Object getFirst() {
        return listHeader.next.element;
    }

    public Object getLast() {
        return listHeader.prev.element;
    }

    public ListEntry getLastEntry() {
        return listHeader.prev;
    }

    public Object removeLast() {
        final Object last = listHeader.prev.element;
        remove(listHeader.prev);
        return last;
    }

    public ListEntry addFirst(Object o) {
        return addBefore(o, listHeader.next);
    }

    private ListEntry addBefore(Object o, ListEntry e) {
        final ListEntry newEntry = new ListEntry(o, e, e.prev);
        newEntry.prev.next = newEntry;
        newEntry.next.prev = newEntry;
        size++;
        return newEntry;
    }

    public void remove(ListEntry e) {
        if (e == listHeader)
            throw new NoSuchElementException();

        e.prev.next = e.next;
        e.next.prev = e.prev;
        size--;
    }

    public int size() {
        return size;
    }
}
