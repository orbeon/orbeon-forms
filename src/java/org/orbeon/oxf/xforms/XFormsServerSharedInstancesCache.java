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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.saxon.om.DocumentInfo;

import java.net.URL;
import java.net.MalformedURLException;

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

    private void add(PipelineContext pipelineContext, String instanceSourceURI, SharedXFormsInstance sharedXFormsInstance) {

        if (XFormsServer.logger.isDebugEnabled())
            XFormsServer.logger.debug("XForms - adding application shared instance with id '" + sharedXFormsInstance.getEffectiveId() + "' to cache for URI: " + instanceSourceURI);

        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final InternalCacheKey cacheKey = new InternalCacheKey(SHARED_INSTANCE_KEY_TYPE, instanceSourceURI);

        cache.add(pipelineContext, cacheKey, CONSTANT_VALIDITY, new CacheEntry(sharedXFormsInstance, System.currentTimeMillis()));
    }

    public synchronized SharedXFormsInstance find(PipelineContext pipelineContext, String instanceId, String modelId, String sourceURI, long timeToLive, String validation) {
        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final InternalCacheKey cacheKey = new InternalCacheKey(SHARED_INSTANCE_KEY_TYPE, sourceURI);
        final CacheEntry cacheEntry = (CacheEntry) cache.findValid(pipelineContext, cacheKey, CONSTANT_VALIDITY);

        // Whether there is an entry but it has expired
        boolean isExpired = cacheEntry != null && cacheEntry.sharedInstance.getTimeToLive() >= 0
                && ((cacheEntry.timestamp + cacheEntry.sharedInstance.getTimeToLive()) < System.currentTimeMillis());

        // Remove expired entry if any
        if (isExpired) {
            if (XFormsServer.logger.isDebugEnabled())
                XFormsServer.logger.debug("XForms - expiring application shared instance: " + sourceURI);
            cache.remove(pipelineContext, cacheKey);
        }

        if (cacheEntry != null && !isExpired) {
            // Instance was found
            if (XFormsServer.logger.isDebugEnabled())
                XFormsServer.logger.debug("XForms - application shared instance with id '" + instanceId + "' found in cache for URI: " + sourceURI);

            final SharedXFormsInstance sharedInstance = cacheEntry.sharedInstance;

            // Return a copy because id, etc. can be different
            return new SharedXFormsInstance(modelId, instanceId, sharedInstance.getDocumentInfo(), true,
                        sourceURI, null, null, sharedInstance.isApplicationShared(), sharedInstance.getTimeToLive(), sharedInstance.getValidation());
        } else {
            // Instance was not found or has expired, load from URI and add to cache

            final URL sourceURL;
            try {
                sourceURL = URLFactory.createURL(sourceURI);
            } catch (MalformedURLException e) {
                throw new OXFException(e);
            }

            if (XFormsServer.logger.isDebugEnabled())
                XFormsServer.logger.debug("XForms - loading application shared instance from URI for: " + sourceURI);

            final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            final XFormsModelSubmission.ConnectionResult connectionResult = XFormsSubmissionUtils.doRegular(externalContext,
                    "get", sourceURL, null, null, null, null);

            // Handle connection errors
            if (connectionResult.resultCode != 200) {
                connectionResult.close();
                throw new OXFException("Got invalid return code while loading instance from URI: " + sourceURI + ", " + connectionResult.resultCode);
            }

            try {
                // Read result as XML and create new shared instance
                final DocumentInfo documentInfo = TransformerUtils.readTinyTree(connectionResult.getResultInputStream(), connectionResult.resourceURI);
                final SharedXFormsInstance newInstance = new SharedXFormsInstance(modelId, instanceId, documentInfo, true, sourceURI, null, null, true, timeToLive, validation);

                // Add result to cache
                add(pipelineContext, sourceURI, newInstance);

                return newInstance;
            } catch (Exception e) {
                throw new OXFException("Got exception while loading instance from URI: " + sourceURI, e);
            } finally {
                // Clean-up
                connectionResult.close();
            }
        }
    }

    public synchronized void remove(PipelineContext pipelineContext, String instanceSourceURI) {

        if (XFormsServer.logger.isDebugEnabled())
            XFormsServer.logger.debug("XForms - removing application shared instance with URI '" + instanceSourceURI);

        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final InternalCacheKey cacheKey = new InternalCacheKey(SHARED_INSTANCE_KEY_TYPE, instanceSourceURI);

        cache.remove(pipelineContext, cacheKey);
    }

    private static class CacheEntry {
        public SharedXFormsInstance sharedInstance;
        public long timestamp;

        public CacheEntry(SharedXFormsInstance sharedInstance, long timestamp) {
            this.sharedInstance = sharedInstance;
            this.timestamp = timestamp;
        }
    }
}
