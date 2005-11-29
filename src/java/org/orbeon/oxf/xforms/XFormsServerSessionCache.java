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
    private Map staticStateToEntryMap = new HashMap();
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

        // Remove existing entry if possible
        {
            final CacheEntry existingCacheEntry = (CacheEntry) keyToEntryMap.get(pageGenerationId);
            if (existingCacheEntry != null) {
                removeCacheEntry(existingCacheEntry);
                final int stateSize = (existingCacheEntry.xformsState.getStaticState().length() + existingCacheEntry.xformsState.getDynamicState().length()) * 2;// TODO
                XFormsServer.logger.debug("XForms - session cache: removed entry of " + stateSize + " bytes.");
            }
        }

        final String staticState = xformsState.getStaticState();
        final String dynamicState = xformsState.getDynamicState();

        // Make room if needed
        final int stateSize = (staticState.length() + dynamicState.length()) * 2;// TODO
        final int cacheSizeBeforeExpire = currentCacheSize;
        int expiredCount = 0;
        while (currentCacheSize != 0 && (currentCacheSize + stateSize) > maxSize) {
            expireOne();
            expiredCount++;
        }

        if (cacheSizeBeforeExpire != currentCacheSize && XFormsServer.logger.isDebugEnabled())
           XFormsServer.logger.debug("XForms - session cache: expired " + expiredCount + " entries (" + (cacheSizeBeforeExpire - currentCacheSize) + " bytes).");

        // Add new element to cache
        final CacheEntry newCacheEntry = new CacheEntry(pageGenerationId, requestId, xformsState);

        linkedList.addFirst(newCacheEntry);
        keyToEntryMap.put(pageGenerationId, newCacheEntry);

        if (XFormsServer.logger.isDebugEnabled())
            XFormsServer.logger.debug("XForms - session cache: added entry of " + stateSize + " bytes.");

        // Update cache size
        currentCacheSize += stateSize;// TODO: not correct if we reuse static state String
    }

    public synchronized XFormsServer.XFormsState find(String pageGenerationId, String requestId) {
        final CacheEntry existingCacheEntry = (CacheEntry) keyToEntryMap.get(pageGenerationId);
        if (existingCacheEntry != null) {
            // Move to the front (is this useful in our use case?)
            if (linkedList.getFirst() != existingCacheEntry) {
                linkedList.remove(existingCacheEntry);
                linkedList.addFirst(existingCacheEntry);
            }
            XFormsServer.logger.debug("XForms - session cache: found and refreshed entry.");
            return existingCacheEntry.xformsState;
        } else {
            // Not found
            XFormsServer.logger.debug("XForms - session cache: did not find entry.");
            return null;
        }
    }

    private void removeCacheEntry(CacheEntry existingCacheEntry) {
        final String staticState = existingCacheEntry.xformsState.getStaticState();
        final String dynamicState = existingCacheEntry.xformsState.getDynamicState();

        final int stateSize = (staticState.length() + dynamicState.length()) * 2;// TODO

        linkedList.remove(existingCacheEntry);
        keyToEntryMap.remove(existingCacheEntry.pageGenerationId);

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
        public String pageGenerationId;
        public String requestId;
        public XFormsServer.XFormsState xformsState;

        public CacheEntry(String pageGenerationId, String requestId, XFormsServer.XFormsState xformsState) {
            this.pageGenerationId = pageGenerationId;
            this.requestId = requestId;
            this.xformsState = xformsState;
        }
    }
}
