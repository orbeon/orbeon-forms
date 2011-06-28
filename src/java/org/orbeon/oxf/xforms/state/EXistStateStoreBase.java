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
package org.orbeon.oxf.xforms.state;

import org.orbeon.oxf.cache.CacheLinkedList;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Base class for eXist XForms state store. This store deals with storing items in memory.
 */
public abstract class EXistStateStoreBase implements XFormsStateStore {

    private static final String XFORMS_STATE_STORE_LISTENER_STATE_KEY = "oxf.xforms.state.store.has-session-listeners-key";

    // Current size of store in bytes
    private int currentSize = 0;

    // Store entries
    private Map<String, CacheLinkedList.ListEntry<StoreEntry>> keyToEntryMap = new HashMap<String, CacheLinkedList.ListEntry<StoreEntry>>();
    private final CacheLinkedList<StoreEntry> linkedList = new CacheLinkedList<StoreEntry>();

    // Map session ids -> Map of keys
    private final Map<String, Set<String>> sessionToKeysMap = new HashMap<String, Set<String>>();

    protected EXistStateStoreBase() {
        debug("created new store.");
    }

    /**
     * Clear the store entirely.
     */
    public synchronized void clear() {
        sessionToKeysMap.clear();
        currentSize = 0;
        keyToEntryMap.clear();
        linkedList.clear();
    }

    /**
     * Store the current state of the given document.
     *
     * @param containingDocument    document
     * @param session               current session
     * @param isInitialState        whether this is the document's initial state
     */
    public synchronized void storeDocumentState(XFormsContainingDocument containingDocument,
                                                ExternalContext.Session session, boolean isInitialState) {

        assert containingDocument.getStaticState().isServerStateHandling();

        assert session != null;

        if (isDebugEnabled()) {
            debug("store size before storing: " + currentSize + " bytes.");
            debugDumpKeys();
        }

        final String documentUUID = containingDocument.getUUID();
        final String staticStateDigest = containingDocument.getStaticState().digest();
        final String dynamicStateKey = getDynamicStateKey(documentUUID, isInitialState);

        // Mapping (UUID -> static state key : dynamic state key)
        addOrReplaceOne(documentUUID, staticStateDigest + ":" + dynamicStateKey, session.getId());

        // Static state
        addOrReplaceOne(staticStateDigest, containingDocument.getStaticState().encodedState(), session.getId());

        // Dynamic state
        addOrReplaceOne(dynamicStateKey, containingDocument.createEncodedDynamicState(XFormsProperties.isGZIPState(), false), session.getId());
    }

    private String getDynamicStateKey(String documentUUID, boolean isInitialState) {
        return documentUUID + (isInitialState ? "-I" : "-C");
    }

    /**
     * Find the current state for the given document UUID.
     *
     * @param session           current session
     * @param documentUUID      document UUID
     * @param isInitialState    whether this is the document's initial state
     * @return                  encoded static and dynamic state
     */
    public synchronized XFormsState findState(ExternalContext.Session session, String documentUUID, boolean isInitialState) {

        if (isDebugEnabled()) {
            debug("store size before finding: " + currentSize + " bytes.");
            debugDumpKeys();
        }

        final String staticStateKey;
        final String dynamicStateKey;
        {
            final String keys = findOne(documentUUID);
            if (keys == null)
                return null;

            final int colonIndex = keys.indexOf(':');
            assert colonIndex == XFormsStaticStateImpl.DIGEST_LENGTH();   // static state key is an hex MD5
            staticStateKey = keys.substring(0, colonIndex);
            // If isInitialState == true, force finding the initial state. Otherwise, use current state stored in mapping.
            dynamicStateKey = isInitialState ? getDynamicStateKey(documentUUID, true) : keys.substring(colonIndex + 1);
        }

        final String staticState = findOne(staticStateKey);
        if (staticState == null)
            return null;

        final String dynamicState = findOne(dynamicStateKey);
        if (dynamicState == null)
            return null;

        return new XFormsState(staticStateKey, staticState, dynamicState);
    }

    private synchronized void addOrReplaceOne(String key, String value, String sessionId) {

        assert sessionId != null;

        final CacheLinkedList.ListEntry<StoreEntry> existingListEntry = keyToEntryMap.get(key);
        if (existingListEntry != null) {
            // Entry already exists, remove it
            removeStoreEntry(existingListEntry);

            // Add new session information
            existingListEntry.element.addSessionId(sessionId);

            // Re-add entry
            addOne(key, value, existingListEntry.element.sessionIds);

            if (isDebugEnabled())
                debug("added and refreshed entry for key: " + key);
        } else {
            // Entry doesn't exist, add it
            final Set<String> sessionIds = new HashSet<String>();
            sessionIds.add(sessionId);
            addOne(key, value, sessionIds);
        }

        // Ensure that a session listener is registered
        ensureSessionListenerRegistered(sessionId);
    }

    private void ensureSessionListenerRegistered(final String sessionId) {

        assert sessionId != null;

        final ExternalContext.Session session = getExternalContext().getSession(XFormsStateManager.FORCE_SESSION_CREATION);
        if (session != null) {

            // Just a consistency check
            if (!session.getId().equals(sessionId))
                throw new OXFException("Inconsistent session ids when persisting XForms state store entry (entry session id: " + sessionId + ", actual session id: " + session.getId() + ").");

            // We want to register only one expiration listener per session
            final Map<String, Object> sessionAttributes = session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE);
            if (sessionAttributes.get(XFORMS_STATE_STORE_LISTENER_STATE_KEY) == null) {
                session.addListener(new ExternalContext.Session.SessionListener() {
                    public void sessionDestroyed() {
                        // Expire
                        expireBySessionId(sessionId);
                    }
                });
                sessionAttributes.put(XFORMS_STATE_STORE_LISTENER_STATE_KEY, "");
            }
        }
    }

    /**
     * Remove all entries which have the given session id.
     *
     * @param sessionId     session id
     */
    public synchronized void expireBySessionId(String sessionId) {

        assert sessionId != null;

        final Set<String> sessionSet = sessionToKeysMap.get(sessionId);
        if (sessionSet != null) {
            final int storeSizeBeforeExpire = getCurrentSize();
            int expiredCount = 0;
            for (final String currentKey: sessionSet) {
                final CacheLinkedList.ListEntry currentListEntry = findEntry(currentKey);
                final StoreEntry currentStoreEntry = (StoreEntry) currentListEntry.element;

                // Remove session id from list of session ids
                currentStoreEntry.sessionIds.remove(sessionId);

                // Remove entry once there is no more associated session
                if (currentStoreEntry.sessionIds.size() == 0) {
                    removeStoreEntry(currentListEntry);
                    expiredCount++;
                }
            }
            sessionToKeysMap.remove(sessionId);

            if (expiredCount > 0 && isDebugEnabled())
                debug("expired " + expiredCount + " entries for session " + sessionId + " (" + (storeSizeBeforeExpire - getCurrentSize()) + " bytes).");
        }
    }

    protected ExternalContext getExternalContext() {
        final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
        return (staticContext != null) ? staticContext.getExternalContext() : null;
    }

    protected void addOne(String key, String value, Set<String> sessionIds) {

        assert keyToEntryMap.get(key) == null;

        final int newValueSize = value.length() * 2;

        // Make room if needed
        {
            final int storeSizeBeforeExpire = currentSize;
            int expiredCount = 0;
            while (currentSize != 0 && (currentSize + newValueSize) > getMaxSize()) {
                expireOne();
                expiredCount++;
            }

            if (storeSizeBeforeExpire != currentSize && isDebugEnabled())
               debug("expired " + expiredCount + " entries (" + (storeSizeBeforeExpire - currentSize) + " bytes).");
        }

        // Add new element to store
        final CacheLinkedList.ListEntry<StoreEntry> listEntry = linkedList.addFirst(new StoreEntry(key, value, sessionIds));
        keyToEntryMap.put(key, listEntry);

        // Associate they key to the sessions
        for (final String sessionId: sessionIds) {
            mapKeyToSession(key, sessionId);
        }

        // Update store size
        currentSize += newValueSize;

        if (isDebugEnabled())
            debug("added new entry of " + newValueSize + " bytes for key: " + key);
    }

    private void mapKeyToSession(String key, String sessionId) {
        Set<String> sessionSet = sessionToKeysMap.get(sessionId);
        if (sessionSet == null) {
            sessionSet = new HashSet<String>();
            sessionToKeysMap.put(sessionId, sessionSet);
        }
        sessionSet.add(key);
    }

    private String findOne(String key) {
        final CacheLinkedList.ListEntry<StoreEntry> existingListEntry = keyToEntryMap.get(key);
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
            final StoreEntry persistedStoreEntry = findPersistedEntry(key);

            // Handle result
            if (persistedStoreEntry != null) {
                // Add the key to the list in memory
                addOne(persistedStoreEntry.key, persistedStoreEntry.value, persistedStoreEntry.sessionIds);
                debug("migrated persisted entry for key: " + key);
                return persistedStoreEntry.value;
            } else {
                // Not found
                debug("did not find entry in persistent store for key: " + key);
                return null;
            }
        }
    }

    private CacheLinkedList.ListEntry findEntry(String key) {
        return keyToEntryMap.get(key);
    }

    private void removeStoreEntry(CacheLinkedList.ListEntry<StoreEntry> existingListEntry) {

        final StoreEntry existingStoreEntry = existingListEntry.element;

        final int stateSize = existingStoreEntry.value.length() * 2;

        linkedList.remove(existingListEntry);
        keyToEntryMap.remove(existingStoreEntry.key);

        // Update store size
        currentSize -= stateSize;

        // Remove the session id -> key mappings related to this entry
        for (final String sessionId: existingStoreEntry.sessionIds) {
            final Set<String> sessionSet = sessionToKeysMap.get(sessionId);
            if (sessionSet != null) {
                sessionSet.remove(existingStoreEntry.key);
            }
        }

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

    public int getMaxSize() {
        return XFormsProperties.getApplicationStateStoreSize();
    }

    private String getStoreDebugName() {
        return "eXist state store";
    }

    public int getCurrentSize() {
        return currentSize;
    }

    /**
     * Persist entry into external storage.
     *
     * @param storeEntry    entry to persist
     */
    protected abstract void persistEntry(StoreEntry storeEntry);

    /**
     * Find entry in persistent storage.
     *
     * @param key   key for entry
     * @return      entry if found, null otherwise
     */
    protected abstract StoreEntry findPersistedEntry(String key);

    protected final boolean isDebugEnabled() {
        return XFormsStateManager.getIndentedLogger().isDebugEnabled();
    }

    protected void debug(String message) {
        XFormsStateManager.getIndentedLogger().logDebug("", getStoreDebugName() + " store: " + message);
    }

    protected void debugDumpKeys() {
//        int index = 1;
//        for (final StoreEntry entry: linkedList) {
//            debug("store entry: " + index + ": " + entry.toString());
//            index++;
//        }
    }

    // Only for unit tests
    public synchronized void addStateCombined(String staticStateUUID, String dynamicStateUUID, XFormsState xformsState, String sessionId) {

        assert sessionId != null;

        // Add static state and move it to the front
        addOrReplaceOne(staticStateUUID, xformsState.getStaticState(), sessionId);

        // Add new dynamic state and move it to the front
        addOrReplaceOne(dynamicStateUUID, xformsState.getDynamicState(), sessionId);

        if (isDebugEnabled()) {
            debug("store size after adding: " + currentSize + " bytes.");
            debugDumpKeys();
        }
    }

    // Only for unit tests
    public synchronized XFormsState findStateCombined(String staticStateUUID, String dynamicStateUUID) {

        if (isDebugEnabled()) {
            debug("store size before finding: " + currentSize + " bytes.");
            debugDumpKeys();
        }

        final String staticState = findOne(staticStateUUID);
        if (staticState == null)
            return null;
        final String dynamicState = findOne(dynamicStateUUID);
        if (dynamicState == null)
            return null;

        return new XFormsState(staticStateUUID, staticState, dynamicState);
    }

    protected static class StoreEntry {
        public final String key;
        public final String value;
        public final Set<String> sessionIds;

        public StoreEntry(String key, String value, Set<String> sessionIds) {

            assert sessionIds != null;

            this.key = key;
            this.value = value;
            this.sessionIds = sessionIds;
        }

        public void addSessionId(String sessionId) {
            sessionIds.add(sessionId);
        }

        @Override
        public String toString() {

            String decodedValue;
            try {
                decodedValue = Dom4jUtils.domToPrettyString(XFormsUtils.decodeXML(null, value));
            } catch (Exception e) {
                decodedValue = value;
            }

            return key + " -> " + decodedValue;
        }
    }
}
