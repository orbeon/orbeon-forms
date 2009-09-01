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
package org.orbeon.oxf.xforms.state;

import org.orbeon.oxf.cache.CacheLinkedList;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for XFormsState stores. This store only deals with storing items in memory
 */
public abstract class XFormsStateStore {

    private int currentStoreSize = 0;

    private Map<String, CacheLinkedList.ListEntry> keyToEntryMap = new HashMap<String, CacheLinkedList.ListEntry>();
    private CacheLinkedList linkedList = new CacheLinkedList();

    protected XFormsStateStore() {
        debug("created new store.");
    }

    protected abstract int getMaxSize();

    protected abstract String getStoreDebugName();

    /**
     * Add an XForms state to the store.
     *
     * @param pageGenerationId  page generation id
     * @param oldRequestId      old request id if available
     * @param newRequestId      new request id
     * @param xformsState       state to store
     * @param currentSessionId  current session id
     * @param isInitialEntry    whether this is an initial dynamic state entry which has preferential treatment
     */
    public synchronized void add(String pageGenerationId, String oldRequestId, String newRequestId, XFormsState xformsState, String currentSessionId, boolean isInitialEntry) {

        // Remove old dynamic state if present as we keep only one entry per page generation
        // NOTE: We try to keep the initial dynamic state entry in the store however, because the client is still likely to request it

        // Here we attempt to remove the "previous previous" dynamic state. The idea is that we want to keep the last
        // two dynamic states to handle funny synchronization cases between client and server, including two-pass
        // submission and clicks on links when a concurrent Ajax request is fired. Example:
        //
        // o Submission with replace="all" is started.
        // o Ajax response reaches the client with dynamic state "foo".
        // o Client does HTML form submission (second pass of submission) with dynamic state "foo".
        // o While this is happening user triggers new Ajax request with dynamic state "foo", replaced on the server by "bar" (and possibly other states).
        // o The new page finally shows up. The server will have "bar", but the client may not have it.
        // o User does a page back. This requests "foo", which has disappeared if we just expired it when replacing it with "bar".
        //
        // A smart algorithm to fix this could be:
        //
        // o Pin state for second pass (pin "foo")
        // o Unpin the state (but do not delete it) when the second pass reaches the server.
        //   NOTE: the HTML submission could be very slow and arrive to the server before or after the series of Ajax requests
        // o If a submission is going on for a state (i.e. state was created while processing first pass), then keep last two states,
        //   otherwise keep just one last state + initial.
        //   NOTE: "second pass going on" for a state should be kept in the dynamic state.
        // o Once second pass reaches the server, the "second pass going on" flag can be removed for the document.
        //   NOTE: The document can have evolved since then - and you may not be able to find it. So the flag may never be cleared. This can also
        //   happen if the HTML submission gets lost. But this should not happen too often.
        //
        // But we prefer the simpler solution:
        //
        // o Just keep the last two dynamic states
        //   -> drawback is this uses more memory, but it solves the problem
        //
        // A non-working solution: you would think that you could prevent any user input on the client using a mask
        // (container.js) and preventing Ajax requests while a submission is going on. BUT this wouldn't work for
        // cases where the submission results in a new tab/window, it would only work when the target == '' AND the
        // browser decides to create a new history entry. You can STILL have cases where the browser picks either
        // way (case of PDF file with or w/o Adobe plugin). So this does not seem to be a real solution.

        // NOTE: We don't remove old entries if they are already persisted. Is this a good strategy?
        if (!isInitialEntry) {
            final CacheLinkedList.ListEntry previousListEntry = keyToEntryMap.get(oldRequestId);
            if (previousListEntry != null) {
                // Found previous entry
                final StoreEntry previousStoredEntry = (StoreEntry) previousListEntry.element;

                if (previousStoredEntry.previousKey != null) {
                    // Found "previous previous" entry
                    final CacheLinkedList.ListEntry previousPreviousListEntry = keyToEntryMap.get(previousStoredEntry.previousKey);
                    if (previousPreviousListEntry != null) {
                        final StoreEntry previousPreviousStoredEntry = (StoreEntry) previousPreviousListEntry.element;

                        if (!previousPreviousStoredEntry.isPinned)// remove unless pinned
                            removeStoreEntry(previousPreviousListEntry);
                    }
                }
            }
        }

        // Add static state and move it to the front
        addOrReplaceOne(pageGenerationId, xformsState.getStaticState(), false, currentSessionId, null);

        // Add new dynamic state and move it to the front
        addOrReplaceOne(newRequestId, xformsState.getDynamicState(), isInitialEntry, currentSessionId, oldRequestId);

        if (isDebugEnabled()) {
            debug("store size after adding: " + currentStoreSize + " bytes.");
            debugDumpKeys();
        }
    }

    public synchronized XFormsState find(String pageGenerationId, String requestId) {
        if (isDebugEnabled()) {
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

    protected void addOrReplaceOne(String key, String value, boolean isPinned, String currentSessionId, String previousKey) {

        final CacheLinkedList.ListEntry existingListEntry = keyToEntryMap.get(key);
        if (existingListEntry != null) {
            // Entry already exists, move to the front
            if (linkedList.getFirst() != existingListEntry.element) {
                linkedList.remove(existingListEntry);
                final CacheLinkedList.ListEntry listEntry = linkedList.addFirst(existingListEntry.element);
                keyToEntryMap.put(key, listEntry);

                // Add session information
                ((StoreEntry) existingListEntry.element).addSessionId(currentSessionId);
            }

            if (isDebugEnabled())
                debug("added and refreshed entry for key: " + key);
        } else {
            // Entry doesn't exist, add it
            final Map<String, String> sessionIds = new HashMap<String, String>();
            if (currentSessionId != null)
                sessionIds.put(currentSessionId, "");
            addOne(key, value, isPinned, sessionIds, previousKey);
        }
    }

    protected void addOne(String key, String value, boolean isPinned, Map<String, String> sessionIds, String previousKey) {
        // Make room if needed
        final int size = value.length() * 2;
        final int storeSizeBeforeExpire = currentStoreSize;
        int expiredCount = 0;
        while (currentStoreSize != 0 && (currentStoreSize + size) > getMaxSize()) {
            expireOne();
            expiredCount++;
        }

        if (storeSizeBeforeExpire != currentStoreSize && isDebugEnabled())
           debug("expired " + expiredCount + " entries (" + (storeSizeBeforeExpire - currentStoreSize) + " bytes).");

        // Add new element to store
        final CacheLinkedList.ListEntry listEntry = linkedList.addFirst(new StoreEntry(key, value, isPinned, sessionIds, previousKey));
        keyToEntryMap.put(key, listEntry);

        // Update store size
        currentStoreSize += size;

        if (isDebugEnabled())
            debug("added new entry of " + size + " bytes for key: " + key);
    }

    protected String findOne(String key) {
        final CacheLinkedList.ListEntry existingListEntry = keyToEntryMap.get(key);
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
        return keyToEntryMap.get(key);
    }

    protected void removeStoreEntry(CacheLinkedList.ListEntry existingListEntry) {

        final StoreEntry existingStoreEntry = (StoreEntry) existingListEntry.element;

        final int stateSize = existingStoreEntry.value.length() * 2;

        linkedList.remove(existingListEntry);
        keyToEntryMap.remove(existingStoreEntry.key);

        // Update store size
        currentStoreSize -= stateSize;

        if (isDebugEnabled())
            debug("removed entry of " + stateSize + " bytes for key: " + existingStoreEntry.key);
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

    protected final boolean isDebugEnabled() {
        return XFormsStateManager.getIndentedLogger().isDebugEnabled();
    }

    protected void debug(String message) {
        XFormsStateManager.getIndentedLogger().logDebug("", getStoreDebugName() + " store: " + message);
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
        public boolean isPinned;
        public Map<String, String> sessionIds;

        public String previousKey; // link to the previous key (for dynamic state only)

        public StoreEntry(String key, String value, boolean isPinned, Map<String, String> sessionIds, String previousKey) {
            this.key = key;
            this.value = value;
            this.isPinned = isPinned;
            this.sessionIds = sessionIds;
            this.previousKey = previousKey;
        }

        public void addSessionId(String sessionId) {
            if (sessionId != null) {
                if (sessionIds == null)
                    sessionIds = new HashMap<String, String>();
                sessionIds.put(sessionId, "");
            }
        }
    }
}
