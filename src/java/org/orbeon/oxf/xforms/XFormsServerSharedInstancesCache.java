/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.cache.InternalCacheKey;

/**
 * Cache for shared and immutable XForms instances.
 */
public class XFormsServerSharedInstancesCache {

    private static final String XFORMS_SHARED_INSTANCES_CACHE_NAME = "xforms.cache.shared-instances";
    private static final int XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE = 10;

    private static final Long CONSTANT_VALIDITY = new Long(0);
    private static final String SHARED_INSTANCE_KEY_TYPE = XFORMS_SHARED_INSTANCES_CACHE_NAME;

    private static XFormsServerSharedInstancesCache instance = null;

    public static XFormsServerSharedInstancesCache instance() {
        if (instance == null) {
            synchronized (XFormsServerSharedInstancesCache.class) {
                if (instance == null) {
                    instance = new XFormsServerSharedInstancesCache();
                }
            }
        }
        return instance;
    }

    public void add(PipelineContext pipelineContext, String instanceSourceURI, SharedXFormsInstance sharedXFormsInstance) {

        if (XFormsServer.logger.isDebugEnabled())
            XFormsServer.logger.debug("XForms - adding application shared instance with id '" + sharedXFormsInstance.getEffectiveId() + "' to cache for URI: " + instanceSourceURI);

        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final InternalCacheKey cacheKey = new InternalCacheKey(SHARED_INSTANCE_KEY_TYPE, instanceSourceURI);

        cache.add(pipelineContext, cacheKey, CONSTANT_VALIDITY, sharedXFormsInstance);
    }

    public SharedXFormsInstance find(PipelineContext pipelineContext, String instanceSourceURI) {
        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final InternalCacheKey cacheKey = new InternalCacheKey(SHARED_INSTANCE_KEY_TYPE, instanceSourceURI);
        final SharedXFormsInstance sharedXFormsInstance = (SharedXFormsInstance) cache.findValid(pipelineContext, cacheKey, CONSTANT_VALIDITY);

        if (sharedXFormsInstance != null && XFormsServer.logger.isDebugEnabled())
            XFormsServer.logger.debug("XForms - application shared instance with id '" + sharedXFormsInstance.getEffectiveId() + "' found in cache for URI: " + instanceSourceURI);

        return sharedXFormsInstance;
    }
}
