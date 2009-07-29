/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.apache.log4j.Logger;
import org.orbeon.oxf.util.LoggerFactory;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.*;

/**
 * The cache contains two entry caches, one with strong references, the other
 * one with soft references. The latter should be kept by the VM as long as
 * possible, according to memory usage. The strong cache has a limited size,
 * but the soft cache can grow indefinitely if strong references are kept on the
 * objects from outside the cache.
 *
 * The cache can contain more elements than the maximum size passed during
 * construction, since elements can be moved to the soft cache and stay there
 * if memory conditions allow it.
 */
public class SoftCacheImpl {

    static private Logger logger = LoggerFactory.createLogger(SoftCacheImpl.class);

    private int maximumSize;
    private String[] keyNames;
    private HashMap<String, Integer> keyNameToIndex;

    /*
     * Strong and soft caches.
     */
    private EntryCache strongCache;
    private EntryCache softCache;

    /**
     * Simple Entry cache.
     */
    protected static class EntryCache {

        private int keyNum;
        private HashMap<Object, Key>[] keyMaps;
        private TreeMap<Key, Object>[] maps;

        public EntryCache(int keyNum) {
            this.keyNum = keyNum;
            keyMaps = new HashMap[keyNum];
            maps = new TreeMap[keyNum];

            for (int i = 0; i < keyNum; i++) {
                keyMaps[i] = new HashMap<Object, Key>();
                maps[i] = new TreeMap<Key, Object>(Key.getComparator());
            }
        }

        public void put(Entry entry) {
            Object[] keys = entry.getKeys();
            for (int i = 0; i < keyNum; i++) {
                Key key = new Key(keys[i]);
                keyMaps[i].put(keys[i], key);
                maps[i].put(key, entry);
            }
        }

        public Entry get(int keyIndex, Object key) {
            return (Entry) maps[keyIndex].get(keyMaps[keyIndex].get(key));
        }

        public Entry remove(int keyIndex, Object key) {
            return remove(get(keyIndex, key));
        }

        public Entry remove(Entry entry) {
            if (entry == null) return null;
            return remove(entry.getKeys());
        }

        public Entry remove(Object[] keys) {
            Entry entry = null;
            for (int i = 0; i < keyNum; i++) {
                Entry e = (Entry) maps[i].remove(keyMaps[i].get(keys[i]));
                if (entry != null && !entry.equals(e))
                    throw new IllegalStateException();
                entry = e;
                keyMaps[i].remove(keys[i]);
            }
            return entry;
        }

        public Entry removeOne() {
            Entry entry = (Entry) maps[0].get(maps[0].firstKey());
            return remove(entry);
        }

        public int flush() {
            int count = maps[0].size();
            for (int i = 0; i < keyNum; i++) {
                keyMaps[i].clear();
                maps[i].clear();
            }
            return count;
        }

        public boolean contains(Object[] keys) {
            for (int i = 0; i < keys.length; i++) {
                Entry entry = get(i, keys[i]);
                if (entry != null && entry.getObject() != null)
                    return true;
            }
            return false;
        }

        public int size() {
            return maps[0].size();
        }

        /**
         * Return an iteration of all non-null objects (not entries) contained in the cache.
         */
        public Iterator elements() {
            return new ObjectIterator(maps[0].values().iterator());
        }

        public void applyOnKeys(Action action) {
            for (Object o: keyMaps[0].keySet()) {
                action.perform(o);
            }
        }

        protected static class ObjectIterator implements Iterator {
            public ObjectIterator(Iterator iterator) {
                this.iterator = iterator;
            }

            public boolean hasNext() {
                while (current == null || current.getObject() == null) {
                    if (!iterator.hasNext())
                        return false;
                    current = (Entry) iterator.next();
                }
                return true;
            }

            public Object next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                Entry result = current;
                current = null;
                return result.getObject();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            private Entry current;
            private Iterator iterator;
        }

        public String dump() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keyNum; i++) {
                int j = 0;
                for (Object key: maps[i].keySet()) {
                    sb.append("Key[" + j++ + "]="
                            + ((Key) key).getKey()
                            + ", element="
                            + ((Entry) maps[i].get(key)).getObject()
                            + "\n");
                }
            }
            return sb.toString();
        }
    }

    /**
     * Key encapsulate a key object and defines a Comparator. This is
     * used to implement LRU behavior.
     */
    protected static class Key {
        private static int globalOrder;
        private static Comparator comparator = new Comparator();

        public static Comparator getComparator() {
            return comparator;
        }

        private Object key;
        private int order;
        private int hash;

        /**
         * Create a new Key.
         */
        public Key(Object key) {
            if (key == null)
                throw new IllegalArgumentException("Key must not be null.");
            this.key = key;
            this.order = globalOrder++;
            this.hash = key.hashCode();
        }

        public Object getKey() {
            return key;
        }

        public int getOrder() {
            return order;
        }

        /*
        public boolean equals(Object obj) {
            System.err.println("Key 1");
            if (obj == this)
                return true;
            System.err.println("Key 2");
            if (!(obj instanceof Key))
                return false;
            System.err.println("Key 3");
            return key.equals(((Key)obj).key);
        }
        */

        public int hashCode() {
            return hash;
        }

        protected static class Comparator implements java.util.Comparator<Key> {
            public int compare(Key o1, Key o2) {
                if (o1 == null || o2 == null) return -1;// what else?
                int order1 = o1.getOrder();
                int order2 = o2.getOrder();

                if (order1 == order2)
                    return 0;
                else if (order1 < order2)
                    return -1;
                else
                    return 1;
            }
        }
    }

    public static interface Action {
        public void perform(Object o);
    }

    /**
     * Entry encapsulate an array of keys and an object.
     */
    public static class Entry {
        private Object[] keys;
        private Object object;

        public Entry(Entry entry) {
            this(entry.getKeys(), entry.getObject());
        }

        public Entry(Object[] keys, Object object) {
            this.keys = keys;
            this.object = object;
        }

        public Object[] getKeys() {
            return keys;
        }

        public Object getObject() {
            return object;
        }
    }

    private ReferenceQueue queue = new ReferenceQueue();

    public abstract class Stats {
        public abstract String getFormatString();
    }

    private class LocalStats extends Stats {

        public int strongSize;
        public int softSize;

        public int movedToSoft;
        public int movedToStrong;
        public int collectedSoft;

        public String getFormatString() {
            return "(strongSize, softSize, movedToSoft, movedToStrong, collectedSoft)";
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("(").append(strongSize);
            sb.append(", ").append(softSize);
            sb.append(", ").append(movedToSoft);
            sb.append(", ").append(movedToStrong);
            sb.append(", ").append(collectedSoft).append(")");
            return sb.toString();
        }
    }

    private LocalStats stats = new LocalStats();

    /**
     * Return all stats.
     */
    public Stats getStats() {
        stats.strongSize = strongCache.size();
        stats.softSize = softCache.size();
        return stats;
    }

    protected void checkQueue() {
        //System.out.println("In checkQueue");
        SmartReference ref;
        int i = 0;
        while ((ref = (SmartReference) queue.poll()) != null) {
//            System.out.println("checkQueue: " + ref.getKeys());
//            System.out.println("Removing soft reference: " + ref.getKeys()[0].toString());
            softCache.remove(ref.getKeys());
            i++;
        }
        if (i > 0) {
            stats.collectedSoft += i;
            if(logger.isInfoEnabled())
                logger.info("Removed soft entries: " + i
                        + " (" + (100*i/(softCache.size() + i)) + "%)");
        }
    }

    /**
     * SmartReference extends SoftReference and encapsulates an array
     * of keys, needed to remove entries from the soft cache when soft
     * entries are freed by the VM.
     */
    protected static class SmartReference extends SoftReference {
        private Object[] keys;

        public SmartReference(Object referent, ReferenceQueue queue, Object[] keys) {
            super(referent, queue);
            this.keys = keys;
        }

        public Object[] getKeys() {
            return keys;
        }
    }

    /**
     * SoftEntry is an entry that encapsulates its object in a SoftReference.
     */
    protected static class SoftEntry extends Entry {

        public SoftEntry(ReferenceQueue queue, Entry entry) {
            this(queue, entry.getKeys(), entry.getObject());
        }

        public SoftEntry(ReferenceQueue queue, Object[] keys, Object object) {
            super(keys, new SmartReference(object, queue, keys));
        }

        public Object[] getKeys() {
            return super.getKeys();
        }

        public Object getObject() {
            return ((SoftReference) super.getObject()).get();
        }
    }

    /**
     * Create a cache of the specified maximum strong cache size.
     */
    public SoftCacheImpl(int maximumSize) {
        this(maximumSize, new String[]{""});
    }

    /**
     * Create a cache of the specified maximum strong cache size.
     * Elements can be indexed using keyNum keys.
     */
    public SoftCacheImpl(int maximumSize, String[] keyNames) {
        this.maximumSize = maximumSize;
        this.keyNames = keyNames;

        // Map key names to key indices
        keyNameToIndex = new HashMap<String, Integer>();
        for (int i = 0; i < keyNames.length; i++)
            keyNameToIndex.put(keyNames[i], i);

        // Create entry caches
        strongCache = new EntryCache(keyNames.length);
        softCache = new EntryCache(keyNames.length);
    }

    /**
     * Set the strong cache's maximum size.
     */
    public void setMaximumSize(int maximumSize) {
        this.maximumSize = maximumSize;
    }

    /**
     * Put an object in the cache.
     * The object can be accessed using the given key.
     */
    public synchronized void put(Object key, Object value) {
        put(new Object[]{key}, value);
    }

    /**
     * Put an object in the cache.
     * The object can be accessed using any of the given keys and a key index.
     */
    public synchronized void put(Object[] keys, Object value) {

        checkQueue();

        if(logger.isDebugEnabled())
            logger.debug("put(Object[] keys, Object value)" + arrayToString(keys) + ", " + value);

        // Check params
        if (keys.length != keyNames.length)
            throw new IllegalArgumentException("Bad number of keys, should be " + keyNames.length);

        //if (maximumSize == 0) return;

        if (strongCache.contains(keys) || softCache.contains(keys))
            throw new IllegalArgumentException("Object already in cache for keys: " + arrayToString(keys));

        // Check for cache limit and move one element to soft cache if needed
        checkLimit();

        // Add element
        if (maximumSize == 0)
            softCache.put(new SoftEntry(queue, keys, value));
        else
            strongCache.put(new Entry(keys, value));

        //Logger.log("CACHE", logger.debug_L2, "dump after put: " + dump());
    }

    /**
     * Check the size limit of the strong cache and move as much as
     * needed to the soft cache. Typically called before adding
     * entries to the strong cache.
     */
    protected void checkLimit() {
        if (maximumSize == 0)
            return;
        while (strongCache.size() >= maximumSize) {
            stats.movedToSoft++;
            softCache.put(new SoftEntry(queue, strongCache.removeOne()));
        }
    }

    /**
     * Remove the specified object.
     */
    public synchronized Object remove(Object key) {
        return remove("", key);
    }

    /**
     * Remove the specified object.
     */
    public synchronized Object remove(String keyName, Object key) {

        //if (maximumSize == 0) return null;

        checkQueue();

        try {
            if (logger.isDebugEnabled())
                logger.debug("remove(String keyName, Object key), keyName = " + keyName + ", key = " + key);

            int keyIndex = getKeyIndex(keyName);
            Entry entry = strongCache.remove(keyIndex, key);
            if (entry != null)
                return entry.getObject();
            entry = softCache.remove(keyIndex, key);
            if (entry != null)
                return entry.getObject();
            throw new IllegalArgumentException("Object not found for: keyName = " + keyName + ", key = " + key);
        } finally {
            // avernet 03/01/21
            checkQueue();
        }
    }

    /**
     * Refresh the given entry.
     * The entry will be put at the beginning of the cache.
     */
    public synchronized Object refresh(Object key) {
        return refresh("", key);
    }

    public synchronized Object refresh(String keyName, Object key) {

        //if (maximumSize == 0) return null;

        checkQueue();

        int keyIndex = getKeyIndex(keyName);
        Entry entry = strongCache.remove(keyIndex, key);
        if (entry == null) {
            stats.movedToStrong++;
            entry = softCache.remove(keyIndex, key);
        }
        if (entry == null || entry.getObject() == null)
            throw new IllegalArgumentException("Object not found for: keyName = " + keyName + ", key = " + key);
        checkLimit();
        if (maximumSize == 0)
            softCache.put(new SoftEntry(queue, entry));
        else
            strongCache.put(new Entry(entry));
        return entry.getObject();
    }

    /**
     * Get the specified object.
     */
    public synchronized Object get(Object key) {
        return get("", key);
    }

    /**
     * Get the specified object.
     */
    public synchronized Object get(String keyName, Object key) {

        //if (maximumSize == 0) return null;

        checkQueue();

        if(logger.isDebugEnabled())
            logger.debug("get(String keyName, Object key) " + keyName + ", " + key);

        Entry entry = getEntry(keyName, key);

        if (entry != null && entry.getObject() != null) {
            refresh(keyName, key);
            return entry.getObject();
        } else
            return null;
    }

    /**
     * Replace the object associated with the given key by the given value.
     */
    public synchronized Object replace(Object key, Object value) {
        return replace(new Object[]{key}, value);
    }

    /**
     * Replace the object associated with the given keys by the given value.
     */
    public synchronized Object replace(Object[] keys, Object value) {

        //if (maximumSize == 0) return null;

        Object oldValue = get(keyNames[0], keys[0]);
        if (oldValue != null)
            remove(keyNames[0], keys[0]);
        put(keys, value);
        return oldValue;
    }

    /**
     * Flush the whole cache.
     *
     * WARNING: The objects are completely removed from the cache, even
     * if they still have outside references on them.
     */
    public synchronized int flush() {
        int count = strongCache.flush();
        return count + softCache.flush();
    }

    public synchronized void applyOnSoftCacheKeys(Action action) {
        softCache.applyOnKeys(action);
    }

    /**
     * Return an iteration of all elements in the cache in LRU order.
     *
     * NOTE: the cache should not be modified while the Iteration is read.
     */
    public Iterator elements() {
        return new SequenceIterator(strongCache.elements(), softCache.elements());
    }

    protected Entry getEntry(String keyName, Object key) {

        //System.err.println("getEntry: " + keyName + ", " + key);
        //System.err.println("getEntry: " + getKeyIndex(keyName));
        //System.err.println("getEntry: " + strongMaps[getKeyIndex(keyName)]);
        // Try the strong cache first, then the soft cache
        int keyIndex = getKeyIndex(keyName);
        Entry entry = strongCache.get(keyIndex, key);
        if (entry != null)
            return entry;
        return softCache.get(keyIndex, key);
    }

    protected int getKeyIndex(String keyName) {
        return keyNameToIndex.get(keyName);
    }

    protected String[] getKeyNames() {
        return keyNames;
    }

    public synchronized String dump() {
        StringBuffer sb = new StringBuffer();
        if (true) {
            for (Iterator it = elements(); it.hasNext();) {
                Object o = it.next();
                System.err.println(o);
                sb.append("[");
                sb.append(o);
                sb.append("]");
            }
        } else {
            sb.append("-- Strong Cache --------------------------------------\n");
            sb.append(strongCache.dump());
            sb.append("-- Soft Cache ----------------------------------------\n");
            sb.append(softCache.dump());
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String arrayToString(Object[] array) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < array.length; i++)
            sb.append("[element[").append(i).append("]=").append(array[i]).append("]");
        return sb.toString();
    }

    private class SequenceIterator implements Iterator {

        public SequenceIterator(Iterator iterators) {
            this.iterators = iterators;
        }

        public SequenceIterator(Iterator[] array) {
            this(Arrays.asList(array).iterator());
        }

        public SequenceIterator(Iterator e1, Iterator e2) {
            this(new Iterator[]{e1, e2});
        }

        public boolean hasNext() {
            while (current == null || !current.hasNext()) {
                if (!iterators.hasNext())
                    return false;
                current = (Iterator) iterators.next();
            }
            return true;
        }

        public Object next() {
            if (!hasNext())
                throw new NoSuchElementException();
            return current.next();
        }

        public void remove() {
            if (current == null)
                throw new NoSuchElementException();
            current.remove();
        }

        private Iterator iterators;
        private Iterator current;
    }
}
