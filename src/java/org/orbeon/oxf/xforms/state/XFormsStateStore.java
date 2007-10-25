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

import org.orbeon.oxf.xforms.processor.XFormsServer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

/**
 * Base class for XFormsState stores. This store only deals with storing items in memory
 */
public abstract class XFormsStateStore {

    private int currentStoreSize = 0;

    private Map keyToEntryMap = new HashMap();
    private LinkedList linkedList = new LinkedList();

    protected XFormsStateStore() {
        debug("created new store.");
    }

    protected abstract int getMaxSize();

    protected abstract String getStoreDebugName();

    public synchronized void add(String pageGenerationId, String oldRequestId, String requestId, XFormsState xformsState) {
        // Add static state and move it to the front
        addOne(pageGenerationId, xformsState.getStaticState(), false);

        // Remove old dynamic state if present as we keep only one entry per page generation
        // NOTE: We try to keep the initial dynamic state entry in the store however, because the client is still likely to request it
        if (oldRequestId != null) {
            final StoreEntry oldStoredEntry = (StoreEntry) keyToEntryMap.get(oldRequestId);
            if (oldStoredEntry != null && !oldStoredEntry.isInitialEntry)
                removeStoreEntry(oldStoredEntry);
        }

        // Add new dynamic state
        final boolean isInitialEntry = oldRequestId == null; // tell whether this is an initial dynamic state entry which has preferential treatment
        addOne(requestId, xformsState.getDynamicState(), isInitialEntry);

        if (XFormsServer.logger.isDebugEnabled()) {
            debug("store size after adding: " + currentStoreSize + " bytes.");
            debugDumpKeys();
        }
    }

    public synchronized XFormsState find(String pageGenerationId, String requestId) {
        if (XFormsServer.logger.isDebugEnabled()) {
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

    protected void addOne(String key, String value, boolean isInitialEntry) {

        // Remove existing entry if present
        {
            final StoreEntry existingStoreEntry = (StoreEntry) keyToEntryMap.get(key);
            if (existingStoreEntry != null)
                removeStoreEntry(existingStoreEntry);
        }

        // Make room if needed
        final int size = value.length() * 2;
        final int storeSizeBeforeExpire = currentStoreSize;
        int expiredCount = 0;
        while (currentStoreSize != 0 && (currentStoreSize + size) > getMaxSize()) {
            expireOne();
            expiredCount++;
        }

        if (storeSizeBeforeExpire != currentStoreSize && XFormsServer.logger.isDebugEnabled())
           debug("expired " + expiredCount + " entries (" + (storeSizeBeforeExpire - currentStoreSize) + " bytes).");

        // Add new element to store
        final StoreEntry newStoreEntry = new StoreEntry(key, value, isInitialEntry);

        linkedList.addFirst(newStoreEntry);
        keyToEntryMap.put(key, newStoreEntry);

        if (XFormsServer.logger.isDebugEnabled())
            debug("added entry of " + size + " bytes.");

        // Update store size
        currentStoreSize += size;
    }

    protected String findOne(String key) {
        final StoreEntry existingStoreEntry = (StoreEntry) keyToEntryMap.get(key);
        if (existingStoreEntry != null) {
            // Move to the front (is this useful in our use case?)
            if (linkedList.getFirst() != existingStoreEntry) {
                linkedList.remove(existingStoreEntry);
                linkedList.addFirst(existingStoreEntry);
            }
            debug("found and refreshed entry for key: " + key);
            return existingStoreEntry.value;
        } else {
            // Not found
            debug("did not find entry in memory for key: " + key);
            return null;
        }
    }

    private void removeStoreEntry(StoreEntry existingStoreEntry) {

        final int stateSize = existingStoreEntry.value.length() * 2;

        linkedList.remove(existingStoreEntry);
        keyToEntryMap.remove(existingStoreEntry.key);

        // Update store size
        currentStoreSize -= stateSize;

        if (XFormsServer.logger.isDebugEnabled())
            debug("removed entry of " + stateSize + " bytes.");
    }

    private void expireOne() {
        if (linkedList.size() > 0) {
            final StoreEntry lastStoreEntry = (StoreEntry) linkedList.getLast();
            removeStoreEntry(lastStoreEntry);

            // Try to persist state
            persistEntry(lastStoreEntry);
        }
    }

    protected void persistEntry(StoreEntry storeEntry) {
        // NOP by default
    }

    protected void debug(String message) {
        XFormsServer.logger.debug("XForms - " + getStoreDebugName() + " store: " + message);
    }

    protected void debugDumpKeys() {
        int index = 1;
        for (final Iterator i = linkedList.iterator(); i.hasNext(); index++) {
            final StoreEntry currentEntry = (StoreEntry) i.next();
            debug("key in store: " + index + ": " + currentEntry.key);
        }
    }

    protected static class StoreEntry {
        public String key;
        public String value;
        public boolean isInitialEntry;

        public StoreEntry(String key, String value, boolean isInitialEntry) {
            this.key = key;
            this.value = value;
            this.isInitialEntry = isInitialEntry;
        }
    }
}
