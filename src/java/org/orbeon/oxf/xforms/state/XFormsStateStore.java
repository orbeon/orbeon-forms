/**
 *  Copyright (C) 2005-2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.state;

import org.orbeon.oxf.cache.CacheLinkedList;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for XFormsState stores. This store only deals with storing items in memory
 */
public abstract class XFormsStateStore {

    private int currentStoreSize = 0;

    private Map keyToEntryMap = new HashMap();
    private CacheLinkedList linkedList = new CacheLinkedList();

    protected XFormsStateStore() {
        debug("created new store.");
    }

    protected abstract int getMaxSize();

    protected abstract String getStoreDebugName();

    public synchronized void add(String pageGenerationId, String oldRequestId, String requestId, XFormsState xformsState, String currentSessionId) {

         // Whether this is an initial dynamic state entry which has preferential treatment
        final boolean isInitialEntry = oldRequestId == null;

        // Remove old dynamic state if present as we keep only one entry per page generation
        // NOTE: We try to keep the initial dynamic state entry in the store however, because the client is still likely to request it
        if (!isInitialEntry) {
            final CacheLinkedList.ListEntry oldListEntry = (CacheLinkedList.ListEntry) keyToEntryMap.get(oldRequestId);
            if (oldListEntry != null) {
                final StoreEntry oldStoredEntry = (StoreEntry) oldListEntry.element;
                if (!oldStoredEntry.isInitialEntry)
                    removeStoreEntry(oldListEntry);
            }
        }

        // Add static state and move it to the front
        addOrReplaceOne(pageGenerationId, xformsState.getStaticState(), false, currentSessionId);

        // Add new dynamic state and move it to the front
        addOrReplaceOne(requestId, xformsState.getDynamicState(), isInitialEntry, currentSessionId);

        if (XFormsStateManager.logger.isDebugEnabled()) {
            debug("store size after adding: " + currentStoreSize + " bytes.");
            debugDumpKeys();
        }
    }

    public synchronized XFormsState find(String pageGenerationId, String requestId) {
        if (XFormsStateManager.logger.isDebugEnabled()) {
            debug("store size before finding: " + currentStoreSize + " bytes.");
            debugDumpKeys();
        }

        final String staticState = findOne(pageGenerationId);
        if (staticState == null)
            return null;
        final String dynamicState = findOne(requestId);
        if (dynamicState == null)
            return null;
        return new XFormsState(staticState, dynamicState);
    }

    protected void addOrReplaceOne(String key, String value, boolean isInitialEntry, String currentSessionId) {

        final CacheLinkedList.ListEntry existingListEntry = (CacheLinkedList.ListEntry) keyToEntryMap.get(key);
        if (existingListEntry != null) {
            // Entry already exists, move to the front
            if (linkedList.getFirst() != existingListEntry.element) {
                linkedList.remove(existingListEntry);
                final CacheLinkedList.ListEntry listEntry = linkedList.addFirst(existingListEntry.element);
                keyToEntryMap.put(key, listEntry);

                // Add session information
                ((StoreEntry) existingListEntry.element).addSessionId(currentSessionId);
            }

            if (XFormsStateManager.logger.isDebugEnabled())
                debug("added and refreshed entry for key: " + key);
        } else {
            // Entry doesn't exist, add it
            final Map sessionIds = new HashMap();
            if (currentSessionId != null)
                sessionIds.put(currentSessionId, "");
            addOne(key, value, isInitialEntry, sessionIds);
        }
    }

    protected void addOne(String key, String value, boolean isInitialEntry, Map sessionIds) {
        // Make room if needed
        final int size = value.length() * 2;
        final int storeSizeBeforeExpire = currentStoreSize;
        int expiredCount = 0;
        while (currentStoreSize != 0 && (currentStoreSize + size) > getMaxSize()) {
            expireOne();
            expiredCount++;
        }

        if (storeSizeBeforeExpire != currentStoreSize && XFormsStateManager.logger.isDebugEnabled())
           debug("expired " + expiredCount + " entries (" + (storeSizeBeforeExpire - currentStoreSize) + " bytes).");

        // Add new element to store
        final CacheLinkedList.ListEntry listEntry = linkedList.addFirst(new StoreEntry(key, value, isInitialEntry, sessionIds));
        keyToEntryMap.put(key, listEntry);

        // Update store size
        currentStoreSize += size;

        if (XFormsStateManager.logger.isDebugEnabled())
            debug("added new entry of " + size + " bytes for key: " + key);
    }

    protected String findOne(String key) {
        final CacheLinkedList.ListEntry existingListEntry = (CacheLinkedList.ListEntry) keyToEntryMap.get(key);
        if (existingListEntry != null) {
            // Found, move to the front
            if (linkedList.getFirst() != existingListEntry.element) {
                linkedList.remove(existingListEntry);
                final CacheLinkedList.ListEntry listEntry = linkedList.addFirst(existingListEntry.element);
                keyToEntryMap.put(key, listEntry);
            }
            debug("found and refreshed entry for key: " + key);
            return ((StoreEntry) existingListEntry.element).value;
        } else {
            // Not found, try persistent store
            final String persistedEntry = findPersistedEntry(key);
            if (persistedEntry != null) {
                return persistedEntry;
            } else {
                // Not found
                debug("did not find entry for key: " + key);
                return null;
            }
        }
    }

    protected CacheLinkedList.ListEntry findEntry(String key) {
        return (CacheLinkedList.ListEntry) keyToEntryMap.get(key);
    }

    protected void removeStoreEntry(CacheLinkedList.ListEntry existingListEntry) {

        final StoreEntry existingStoreEntry = (StoreEntry) existingListEntry.element;

        final int stateSize = existingStoreEntry.value.length() * 2;

        linkedList.remove(existingListEntry);
        keyToEntryMap.remove(existingStoreEntry.key);

        // Update store size
        currentStoreSize -= stateSize;

        if (XFormsStateManager.logger.isDebugEnabled())
            debug("removed entry of " + stateSize + " bytes.");
    }

    private void expireOne() {
        if (linkedList.size() > 0) {
            // Remove last entry
            final CacheLinkedList.ListEntry lastListEntry = linkedList.getLastEntry();
            removeStoreEntry(lastListEntry);

            // Try to persist state
            persistEntry((StoreEntry) lastListEntry.element);
        }
    }

    protected void persistEntry(StoreEntry storeEntry) {
        // NOP by default
    }

    protected String findPersistedEntry(String key) {
        // NOP by default
        return null;
    }

    protected int getCurrentStoreSize() {
        return currentStoreSize;
    }

    protected void debug(String message) {
        XFormsStateManager.logger.debug("XForms - " + getStoreDebugName() + " store: " + message);
    }

    protected void debugDumpKeys() {
//        int index = 1;
//        for (final Iterator i = linkedList.iterator(); i.hasNext(); index++) {
//            final StoreEntry currentEntry = (StoreEntry) i.next();
//            debug("key in store: " + index + ": " + currentEntry.key);
//        }
    }

    protected static class StoreEntry {
        public String key;
        public String value;
        public boolean isInitialEntry;
        public Map sessionIds;

        public StoreEntry(String key, String value, boolean isInitialEntry, Map sessionIds) {
            this.key = key;
            this.value = value;
            this.isInitialEntry = isInitialEntry;
            this.sessionIds = sessionIds;
        }

        public void addSessionId(String sessionId) {
            if (sessionId != null) {
                if (sessionIds == null)
                    sessionIds = new HashMap();
                sessionIds.put(sessionId, "");
            }
        }
    }
}
