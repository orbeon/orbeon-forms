/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.cache;

import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.iterators.TransformIterator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * Memory cache implementation.
 *
 * @noinspection SimplifiableIfStatement
 */
public class MemoryCacheImpl implements Cache {

    private int maxSize;

    private Map<CacheKey, CacheEntry> keyToEntryMap = new HashMap<CacheKey, CacheEntry>();
    private CacheLinkedList linkedList = new CacheLinkedList();
    private int currentSize;

    public MemoryCacheImpl(int maxSize) {
        this.maxSize = maxSize;
    }

    public synchronized void add(CacheKey key, Object validity, Object cacheable) {
        if (key == null || validity == null || maxSize == 0) return;
        CacheEntry entry = keyToEntryMap.get(key);
        if (entry == null) {
            // No existing entry found
            if (currentSize == maxSize) {
                // Cache is full, try to evict one entry, starting from the end
                tryEvictLast();
                // If somehow we couldn't manage to evict an entry (e.g. all were locked), the cache will grow over
                // maxsize.
            }
            currentSize++;

            entry = new CacheEntry();
            entry.key = key;
            entry.validity = validity;
            entry.cacheable = cacheable;
            keyToEntryMap.put(key, entry);
            entry.listEntry = linkedList.addFirst(entry);

            // Notify object
            notifyAdded(entry.cacheable);

        } else {
            // Update validity and move to the front
            entry.validity = validity;
            entry.cacheable = cacheable;
            linkedList.remove(entry.listEntry);
            entry.listEntry = linkedList.addFirst(entry);
        }
    }

    private boolean tryEvictLast() {
        for (final Iterator<CacheEntry> i = linkedList.reverseIterator(); i.hasNext();) {
            final CacheEntry entryToTry = i.next();
            if (tryEvict(entryToTry)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryEvict(CacheEntry entry) {

        assert keyToEntryMap.containsKey(entry.key);

        // Obtain lock if possible
        final Lock lock;
        final boolean canEvict;
        if (entry.cacheable instanceof Cacheable) {
            lock = ((Cacheable) entry.cacheable).getEvictionLock();
            canEvict = lock == null || lock.tryLock();
        } else {
            lock = null;
            canEvict = true;
        }

        // Only remove object if we are allowed to
        if (canEvict) {
            try {
                remove(entry.key, true, false);
            } finally {
                // Release lock if we got one
                if (lock != null)
                    lock.unlock();
            }
        }

        return canEvict;
    }

    public synchronized void remove(CacheKey key) {
        remove(key, false, true); // don't consider this an eviction
    }

    private synchronized void remove(CacheKey key, boolean isEvict, boolean isRemove) {
        final CacheEntry entry = keyToEntryMap.get(key);
        if (entry != null) {
            keyToEntryMap.remove(key);
            linkedList.remove(entry.listEntry);
            currentSize--;

            // Notify object
            if (isEvict) {
                notifyEvicted(entry.cacheable);
            } else if (isRemove) {
                notifyRemoved(entry.cacheable);
            }
        }
    }

    private void notifyAdded(Object object) {
        if (object instanceof Cacheable) {
            ((Cacheable) object).added();
        }
    }

    private void notifyRemoved(Object object) {
        if (object instanceof Cacheable) {
            ((Cacheable) object).removed();
        }
    }

    private void notifyEvicted(Object object) {
        if (object instanceof Cacheable) {
            ((Cacheable) object).evicted();
        }
    }

    public synchronized int removeAll() {
        final int previousSize = currentSize;

        // Notify objects
        for (final Iterator i = iterateCacheObjects(); i.hasNext();) {
            notifyRemoved(i.next());
        }

        keyToEntryMap = new HashMap<CacheKey, CacheEntry>();
        linkedList = new CacheLinkedList();
        currentSize = 0;
        return previousSize;
    }

    // Find valid entry and move it to the first position
    public Object findValid(CacheKey key, Object validity) {
        return getValid(key,  validity, false);
    }

    // Like findValid but remove from the cache (with removed() notification)
    public Object takeValid(CacheKey key, Object validity) {
        return getValid(key,  validity, true);
    }

    private synchronized Object getValid(CacheKey key, Object validity, boolean remove) {
        final CacheEntry entry = keyToEntryMap.get(key);
        if (entry != null && lowerOrEqual(validity, entry.validity)) {

            if (remove) {
                // Remove and notify
                remove(key, false, true);
            } else if (linkedList.getFirst() != entry) {
                // Place in first position and return
                linkedList.remove(entry.listEntry);
                entry.listEntry = linkedList.addFirst(entry);
            }

            return entry.cacheable;
        } else {
            // Not latest validity
            return null;
        }
    }

    public CacheEntry findAny(CacheKey key) {
        // Don't update statistics here
        return keyToEntryMap.get(key);
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public synchronized void setMaxSize(int maxSize) {
        if (maxSize != this.maxSize) {
            // Decrease size if necessary

            // Try to evict entries, but don't try more times than the number of elements initially in the cache
            int tryCount = 0;
            final int maxTries = currentSize;
            while(currentSize > maxSize && tryCount < maxTries) {
                tryEvictLast();
                tryCount++;
            }

            this.maxSize = maxSize;
        }
    }

    public Iterator<CacheKey> iterateCacheKeys() {
        return new TransformIterator(linkedList.iterator(), new Transformer() {
            public Object transform(Object o) {
                return ((CacheEntry) o).key;
            }
        });
    }

    public Iterator<Object> iterateCacheObjects() {
        return new TransformIterator(linkedList.iterator(), new Transformer() {
            public Object transform(Object o) {
                return ((CacheEntry) o).cacheable;
            }
        });
    }

    private boolean lowerOrEqual(Object left, Object right) {
        if (left instanceof List && right instanceof List) {
            List leftList = (List) left;
            List rightList = (List) right;
            if (leftList.size() != rightList.size())
                return false;
            for (Iterator leftIterator = leftList.iterator(), rightIterator = rightList.iterator();leftIterator.hasNext();) {
                Object leftObject = leftIterator.next();
                Object rightObject = rightIterator.next();
                if (!lowerOrEqual(leftObject, rightObject))
                    return false;
            }
            return true;
        } else if (left instanceof Long && right instanceof Long) {
            return (Long) left <= (Long) right;
        } else {
            return false;
        }
    }
}
