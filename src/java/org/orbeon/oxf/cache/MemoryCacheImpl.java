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
import org.orbeon.oxf.util.PropertyContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Very simple cache implementation.
 *
 * @noinspection SimplifiableIfStatement
 */
public class MemoryCacheImpl implements Cache {

    private final String cacheName;
    private int maxSize;

    private final String statisticsContextKey;

    private Map<CacheKey, CacheEntry> keyToEntryMap = new HashMap<CacheKey, CacheEntry>();
    private CacheLinkedList linkedList = new CacheLinkedList();
    private int currentSize;

    public MemoryCacheImpl(String cacheName, int maxSize) {
        this.cacheName = cacheName;
        this.maxSize = maxSize;

        this.statisticsContextKey =  "memory-cache-statistics." + cacheName;
    }

    public String getCacheName() {
        return cacheName;
    }

    private class MemoryCacheStatistics implements CacheStatistics {

        private int hitsCount;
        private int missCount;
        private int addCount;
        private int expirationCount;

        public int getMaxSize() { return maxSize; }
        public int getCurrentSize() { return currentSize; }

        public int getHitCount() { return hitsCount; }
        public int getMissCount() { return missCount; }
        public int getAddCount() { return addCount; }
        public int getExpirationCount() { return expirationCount; }

        public void incrementHitsCount() { hitsCount++; }
        public void incrementMissCount() { missCount++; }
        public void incrementAddCount() { addCount++; }
        public void incrementExpirationCount() { expirationCount++; }
    }

    public synchronized void add(PropertyContext propertyContext, CacheKey key, Object validity, Object cacheable) {
        if (key == null || validity == null || maxSize == 0) return;
        final MemoryCacheStatistics statistics = (propertyContext != null) ? (MemoryCacheStatistics) getStatistics(propertyContext) : null;
        if (statistics != null)
            statistics.incrementAddCount();
        CacheEntry entry = keyToEntryMap.get(key);
        if (entry == null) {
            // No existing entry found
            if (currentSize == maxSize) {
                entry = (CacheEntry) linkedList.getLast();

                // Notify object
                expire(propertyContext, entry.cacheable);

                keyToEntryMap.remove(entry.key);
                linkedList.removeLast();

                if (statistics != null)
                    statistics.incrementExpirationCount();
            } else {
                currentSize++;
                entry = new CacheEntry();
            }
            entry.key = key;
            entry.validity = validity;
            entry.cacheable = cacheable;
            keyToEntryMap.put(key, entry);
            entry.listEntry = linkedList.addFirst(entry);
        } else {
            // Update validity and move to the front
            entry.validity = validity;
            entry.cacheable = cacheable;
            linkedList.remove(entry.listEntry);
            entry.listEntry = linkedList.addFirst(entry);
        }
    }

    public synchronized void remove(PropertyContext propertyContext, CacheKey key) {
        final CacheEntry entry = keyToEntryMap.get(key);
        if (entry != null) {

            // Notify object
            expire(propertyContext, entry.cacheable);

            keyToEntryMap.remove(key);
            linkedList.remove(entry.listEntry);
            currentSize--;
        }
    }

    private void expire(PropertyContext propertyContext, Object object) {
        if (object instanceof Cacheable) {
            ((Cacheable) object).evict(propertyContext);
        }
    }

    public synchronized int removeAll(PropertyContext propertyContext) {
        final int previousSize = currentSize;

        // Notify objects
        for (final Iterator i = iterateCacheObjects(propertyContext); i.hasNext();) {
            expire(propertyContext, i.next());
        }

        keyToEntryMap = new HashMap<CacheKey, CacheEntry>();
        linkedList = new CacheLinkedList();
        currentSize = 0;
        return previousSize;
    }

    public synchronized Object findValid(PropertyContext propertyContext, CacheKey key, Object validity) {

        final CacheEntry entry = keyToEntryMap.get(key);
        if (entry != null && lowerOrEqual(validity, entry.validity)) {
            // Place in first position and return
            if (propertyContext != null)
                ((MemoryCacheStatistics) getStatistics(propertyContext)).incrementHitsCount();
            if (linkedList.getFirst() != entry) {
                linkedList.remove(entry.listEntry);
                entry.listEntry = linkedList.addFirst(entry);
            }
            return entry.cacheable;
        } else {
            // Not latest validity
            if (propertyContext != null)
                ((MemoryCacheStatistics) getStatistics(propertyContext)).incrementMissCount();
            return null;
        }
    }

    public synchronized void setMaxSize(PropertyContext propertyContext, int maxSize) {
        if (maxSize != this.maxSize) {
            // Decrease size if necessary
            while(currentSize > maxSize)
                remove(propertyContext, ((CacheEntry) linkedList.getLast()).key);
            this.maxSize = maxSize;
        }
    }

    public Iterator iterateCacheKeys(PropertyContext propertyContext) {
        return keyToEntryMap.keySet().iterator();
    }

    public Iterator iterateCacheObjects(PropertyContext propertyContext) {
        return new TransformIterator(keyToEntryMap.keySet().iterator(), new Transformer() {
            public Object transform(Object o) {
                return (keyToEntryMap.get(o)).cacheable;
            }
        });
    }

    public synchronized CacheStatistics getStatistics(PropertyContext propertyContext) {
        MemoryCacheStatistics statistics = (MemoryCacheStatistics) propertyContext.getAttribute(statisticsContextKey);
        if (statistics == null) {
            statistics = new MemoryCacheStatistics();
            propertyContext.setAttribute(statisticsContextKey, statistics);
        }

        return statistics;
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
