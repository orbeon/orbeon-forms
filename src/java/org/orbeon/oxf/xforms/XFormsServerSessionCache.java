/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xforms.processor.XFormsServer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * This cache stores XFormsState instances into a session.
 */
public class XFormsServerSessionCache {

    private static final String SESSION_STATE_CACHE_SESSION_KEY = "oxf.xforms.state.repository";

    private int maxSize = XFormsUtils.getSessionCacheSize();
    private int currentCacheSize = 0;

    private Map keyToEntryMap = new HashMap();
//    private Map staticStateToEntryMap = new HashMap();
    private LinkedList linkedList = new LinkedList();

    public static XFormsServerSessionCache instance(ExternalContext.Session session, boolean create) {
        if (session != null) {
            final XFormsServerSessionCache currentCache = (XFormsServerSessionCache) session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE).get(SESSION_STATE_CACHE_SESSION_KEY);

            if (currentCache != null)
                return currentCache;

            if (!create)
                return null;

            synchronized (XFormsServerSessionCache.class) {
                XFormsServerSessionCache newCache = (XFormsServerSessionCache) session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE).get(SESSION_STATE_CACHE_SESSION_KEY);

                if (newCache != null)
                    return newCache;

                XFormsServer.logger.debug("XForms - session cache: creating new cache.");

                newCache = new XFormsServerSessionCache();
                session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE).put(SESSION_STATE_CACHE_SESSION_KEY, newCache);
                return newCache;
            }
        } else {
            return null;
        }
    }

    public synchronized void add(String pageGenerationId, String requestId, XFormsServer.XFormsState xformsState) {
        addOne(pageGenerationId, xformsState.getStaticState());
        addOne(requestId, xformsState.getDynamicState());
    }

    private void addOne(String key, String value) {

        // Remove existing entry if possible
        {
            final CacheEntry existingCacheEntry = (CacheEntry) keyToEntryMap.get(key);
            if (existingCacheEntry != null) {
                removeCacheEntry(existingCacheEntry);
                final int size = existingCacheEntry.value.length() * 2;
                XFormsServer.logger.debug("XForms - session cache: removed entry of " + size + " bytes.");
            }
        }

        // Make room if needed
        final int size = value.length() * 2;
        final int cacheSizeBeforeExpire = currentCacheSize;
        int expiredCount = 0;
        while (currentCacheSize != 0 && (currentCacheSize + size) > maxSize) {
            expireOne();
            expiredCount++;
        }

        if (cacheSizeBeforeExpire != currentCacheSize && XFormsServer.logger.isDebugEnabled())
           XFormsServer.logger.debug("XForms - session cache: expired " + expiredCount + " entries (" + (cacheSizeBeforeExpire - currentCacheSize) + " bytes).");

        // Add new element to cache
        final CacheEntry newCacheEntry = new CacheEntry(key, value);

        linkedList.addFirst(newCacheEntry);
        keyToEntryMap.put(key, newCacheEntry);

        if (XFormsServer.logger.isDebugEnabled())
            XFormsServer.logger.debug("XForms - session cache: added entry of " + size + " bytes.");

        // Update cache size
        currentCacheSize += size;
    }

    public synchronized XFormsServer.XFormsState find(String pageGenerationId, String requestId) {
        final String staticState = findOne(pageGenerationId);
        if (staticState == null)
            return null;
        final String dynamicState = findOne(requestId);
        if (dynamicState == null)
            return null;
        return new XFormsServer.XFormsState(staticState, dynamicState);
    }

    private String findOne(String key) {
        final CacheEntry existingCacheEntry = (CacheEntry) keyToEntryMap.get(key);
        if (existingCacheEntry != null) {
            // Move to the front (is this useful in our use case?)
            if (linkedList.getFirst() != existingCacheEntry) {
                linkedList.remove(existingCacheEntry);
                linkedList.addFirst(existingCacheEntry);
            }
            XFormsServer.logger.debug("XForms - session cache: found and refreshed entry.");
            return existingCacheEntry.value;
        } else {
            // Not found
            XFormsServer.logger.debug("XForms - session cache: did not find entry.");
            return null;
        }
    }

    private void removeCacheEntry(CacheEntry existingCacheEntry) {

        final int stateSize = existingCacheEntry.value.length() * 2;

        linkedList.remove(existingCacheEntry);
        keyToEntryMap.remove(existingCacheEntry.key);

        // Update cache size
        currentCacheSize -= stateSize;
    }

    private void expireOne() {
        if (linkedList.size() > 0) {
            final CacheEntry lastCacheEntry = (CacheEntry) linkedList.removeLast();
            removeCacheEntry(lastCacheEntry);
        }
    }

    private static class CacheEntry {
        public String key;
        public String value;

        public CacheEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
