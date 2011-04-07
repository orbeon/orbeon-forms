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

import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsStaticState;

/**
 * This cache stores mappings XFormsState -> XFormsContainingDocument into a global cache.
 */
public class XFormsDocumentCache {

    private static final String XFORMS_DOCUMENT_CACHE_NAME = "xforms.cache.documents";
    private static final int XFORMS_DOCUMENT_CACHE_DEFAULT_SIZE = 50;

    private static final Long CONSTANT_VALIDITY = 0L;
    private static final String CONTAINING_DOCUMENT_KEY_TYPE = XFORMS_DOCUMENT_CACHE_NAME;

    private static XFormsDocumentCache instance = new XFormsDocumentCache();

    public static XFormsDocumentCache instance() {
        return instance;
    }

    private final Cache cache = ObjectCache.instance(XFORMS_DOCUMENT_CACHE_NAME, XFORMS_DOCUMENT_CACHE_DEFAULT_SIZE);

    private XFormsDocumentCache() {}

    /**
     * Whether the cache is enabled for this static state.
     *
     * @param staticState   static state to check
     * @return              true if cache enabled
     */
    public boolean isEnabled(XFormsStaticState staticState) {
        return staticState.isCacheDocument() && cache.getMaxSize() > 0;
    }

    /**
     * Add a document to the cache using the document's UUID as cache key.
     *
     * @param containingDocument    document to store
     */
    public void storeDocument(XFormsContainingDocument containingDocument) {
        final InternalCacheKey cacheKey = createCacheKey(containingDocument.getUUID());
        cache.add(cacheKey, CONSTANT_VALIDITY, containingDocument);
    }

    /**
     * Find a document in the cache. If found, the document is removed from the cache. If not found, return null.
     *
     * @param uuid                  UUID used to search cache
     * @return                      document or null
     */
    public XFormsContainingDocument takeDocument(String uuid) {
        final InternalCacheKey cacheKey = createCacheKey(uuid);
        return (XFormsContainingDocument) cache.takeValid(cacheKey, CONSTANT_VALIDITY);
    }

    /**
     * Remove a document from the cache. This does not cause the document state to be serialized to store.
     *
     * @param uuid  UUID used to search cache
     */
    public void removeDocument(String uuid) {
        final InternalCacheKey cacheKey = createCacheKey(uuid);
        cache.remove(cacheKey);
    }

    private InternalCacheKey createCacheKey(String uuid) {

        // Make sure that we are getting a UUID back
        assert uuid.length() == UUIDUtils.UUID_LENGTH;

        return new InternalCacheKey(CONTAINING_DOCUMENT_KEY_TYPE, uuid);
    }

    public int getCurrentSize() {
        return cache.getCurrentSize();
    }

    public int getMaxSize() {
        return cache.getMaxSize();
    }
}
