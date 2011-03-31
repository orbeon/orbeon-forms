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

import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsStaticState;

public class XFormsStaticStateCache {

    private static final String XFORMS_DOCUMENT_CACHE_NAME = "xforms.cache.static-state";
    private static final int XFORMS_DOCUMENT_CACHE_DEFAULT_SIZE = 50;

    private static final Long CONSTANT_VALIDITY = 0L;
    private static final String CONTAINING_DOCUMENT_KEY_TYPE = XFORMS_DOCUMENT_CACHE_NAME;

    private static XFormsStaticStateCache instance = new XFormsStaticStateCache();

    public static XFormsStaticStateCache instance() {
        return instance;
    }

    private final Cache cache = ObjectCache.instance(XFORMS_DOCUMENT_CACHE_NAME, XFORMS_DOCUMENT_CACHE_DEFAULT_SIZE);

    private XFormsStaticStateCache() {}

    /**
     * Add a state to the cache using the state's digest as cache key.
     *
     * @param staticState       state to store
     */
    public void storeDocument(XFormsStaticState staticState) {
        final InternalCacheKey cacheKey = createCacheKey(staticState.getDigest());
        cache.add(cacheKey, CONSTANT_VALIDITY, staticState);
    }

    /**
     * Find a document in the cache. If not found, return null.
     *
     *
     * @param digest            digest used to search cache
     * @return                  state or null
     */
    public XFormsStaticState getDocument(String digest) {
        final InternalCacheKey cacheKey = createCacheKey(digest);
        return (XFormsStaticState) cache.findValid(cacheKey, CONSTANT_VALIDITY);
    }

    private InternalCacheKey createCacheKey(String digest) {
        assert digest != null;
        return new InternalCacheKey(CONTAINING_DOCUMENT_KEY_TYPE, digest);
    }
}
