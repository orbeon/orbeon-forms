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

import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.saxon.om.DocumentInfo;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Cache for shared and immutable XForms instances.
 */
public class XFormsServerSharedInstancesCache {

    private static final String XFORMS_SHARED_INSTANCES_CACHE_NAME = "xforms.cache.shared-instances";
    private static final int XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE = 10;

    private static final Long CONSTANT_VALIDITY = new Long(0);
    private static final String SHARED_INSTANCE_KEY_TYPE = XFORMS_SHARED_INSTANCES_CACHE_NAME;
    
    private static final String LOG_TYPE = "instance cache";

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

    private void add(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, String instanceSourceURI, ReadonlyXFormsInstance readonlyXFormsInstance, boolean handleXInclude) {

        if (XFormsServer.logger.isDebugEnabled())
            containingDocument.logDebug(LOG_TYPE, "adding instance", new String[] { "id", readonlyXFormsInstance.getEffectiveId(), "URI", instanceSourceURI });

        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final InternalCacheKey cacheKey = createCacheKey(instanceSourceURI, handleXInclude);

        cache.add(pipelineContext, cacheKey, CONSTANT_VALIDITY, new SharedInstanceCacheEntry(readonlyXFormsInstance, System.currentTimeMillis()));
    }

    public XFormsInstance findConvert(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, String instanceStaticId,
                                     String modelEffectiveId, String instanceSourceURI, boolean isReadonly, long timeToLive, String validation, boolean handleXInclude) {

        final ReadonlyXFormsInstance tempReadonlyInstance = find(pipelineContext, containingDocument, instanceStaticId, modelEffectiveId,
                instanceSourceURI, timeToLive, validation, handleXInclude);

        final XFormsInstance newInstance;
        if (isReadonly) {
            // Keep readonly instance
            newInstance = tempReadonlyInstance;

            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug(LOG_TYPE, "returning read-only cached instance",
                            new String[] { "instance", newInstance.getEffectiveId() });
        } else {
            // Convert to mutable instance
            newInstance = tempReadonlyInstance.createMutableInstance();

            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug(LOG_TYPE, "returning read-write cached instance",
                            new String[] { "instance", newInstance.getEffectiveId() });
        }

        return newInstance;
    }

    private ReadonlyXFormsInstance find(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, String instanceStaticId,
                                      String modelEffectiveId, String instanceSourceURI, long timeToLive, String validation, boolean handleXInclude) {
        // Try to find in cache
        final ReadonlyXFormsInstance existingInstance = findInCache(pipelineContext, containingDocument, instanceStaticId, modelEffectiveId, instanceSourceURI, handleXInclude);
        if (existingInstance != null) {
            // Found from the cache
            return existingInstance;
        } else {
            // Not found from the cache, attempt to retrieve

            // Note that this method is not synchronized. Scenario: if the method is synchronized, the resource URI may
            // may reach an XForms page which itself needs to load a shared resource. The result would be a deadlock.
            // Without synchronization, what can happen is that two concurrent requests load the same URI at the same
            // time. In the worst case scenario, the results will be different, and the two requesting XForms instances
            // will be different. The instance that is retrieved first will be stored in the cache for a very short
            // amount of time, and the one retrieved last will win and be stored in the cache for a longer time.

            final URL sourceURL;
            try {
                sourceURL = URLFactory.createURL(instanceSourceURI);
            } catch (MalformedURLException e) {
                throw new OXFException(e);
            }

            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug(LOG_TYPE, "loading instance",
                        new String[] { "id", instanceStaticId, "URI", instanceSourceURI });

            final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            final ConnectionResult connectionResult = NetUtils.openConnection(externalContext,
                    containingDocument.getIndentedLogger(), "GET", sourceURL, null, null, null, null, null,
                    XFormsProperties.getForwardSubmissionHeaders(containingDocument));

            // Handle connection errors
            if (connectionResult.statusCode != 200) {
                connectionResult.close();
                throw new OXFException("Got invalid return code while loading instance from URI: " + instanceSourceURI + ", " + connectionResult.statusCode);
            }

            try {
                // Read result as XML and create new shared instance
                // TODO: Handle validating?
                final DocumentInfo documentInfo = TransformerUtils.readTinyTree(connectionResult.getResponseInputStream(), connectionResult.resourceURI, handleXInclude);
                final ReadonlyXFormsInstance newInstance = new ReadonlyXFormsInstance(modelEffectiveId, instanceStaticId, documentInfo, instanceSourceURI,
                        null, null, true, timeToLive, validation, handleXInclude);

                // Add result to cache
                add(pipelineContext, containingDocument, instanceSourceURI, newInstance, handleXInclude);

                return newInstance;
            } catch (Exception e) {
                throw new OXFException("Got exception while loading instance from URI: " + instanceSourceURI, e);
            } finally {
                // Clean-up
                connectionResult.close();
            }
        }
    }

    private synchronized ReadonlyXFormsInstance findInCache(PipelineContext pipelineContext, XFormsContainingDocument containingDocument,
                                                          String instanceStaticId, String modelEffectiveId, String instanceSourceURI, boolean handleXInclude) {
        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);

        final InternalCacheKey cacheKey = createCacheKey(instanceSourceURI, handleXInclude);
        final SharedInstanceCacheEntry sharedInstanceCacheEntry = (SharedInstanceCacheEntry) cache.findValid(pipelineContext, cacheKey, CONSTANT_VALIDITY);

        // Whether there is an entry but it has expired
        boolean isExpired = sharedInstanceCacheEntry != null && sharedInstanceCacheEntry.readonlyInstance.getTimeToLive() >= 0
                && ((sharedInstanceCacheEntry.timestamp + sharedInstanceCacheEntry.readonlyInstance.getTimeToLive()) < System.currentTimeMillis());

        // Remove expired entry if any
        if (isExpired) {
            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug(LOG_TYPE, "expiring instance", new String[] { "id", instanceStaticId, "URI", instanceSourceURI });
            cache.remove(pipelineContext, cacheKey);
        }

        if (sharedInstanceCacheEntry != null && !isExpired) {
            // Instance was found
            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug(LOG_TYPE, "found instance", new String[] { "id", instanceStaticId, "URI", instanceSourceURI });

            final ReadonlyXFormsInstance readonlyInstance = sharedInstanceCacheEntry.readonlyInstance;

            // Return a copy because id, etc. can be different
            return new ReadonlyXFormsInstance(modelEffectiveId, instanceStaticId, readonlyInstance.getDocumentInfo(),
                        instanceSourceURI, null, null, readonlyInstance.isCache(), readonlyInstance.getTimeToLive(), readonlyInstance.getValidation(), readonlyInstance.isHandleXInclude());
        } else {
            // Not found
            return null;
        }
    }

    private InternalCacheKey createCacheKey(String instanceSourceURI, boolean handleXInclude) {
        // Make key also depend on handleXInclude!
        return new InternalCacheKey(SHARED_INSTANCE_KEY_TYPE, instanceSourceURI + "|" + Boolean.toString(handleXInclude));
    }

    public synchronized void remove(PipelineContext pipelineContext, String instanceSourceURI, boolean handleXInclude) {

        if (XFormsServer.logger.isDebugEnabled())
            XFormsContainingDocument.logDebugStatic(LOG_TYPE, "removing instance", new String[] { "URI", instanceSourceURI });

        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final InternalCacheKey cacheKey = createCacheKey(instanceSourceURI, handleXInclude);

        cache.remove(pipelineContext, cacheKey);
    }

    public synchronized void removeAll(PipelineContext pipelineContext) {
        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final int count = cache.removeAll(pipelineContext);

        if (XFormsServer.logger.isDebugEnabled())
            XFormsContainingDocument.logDebugStatic(LOG_TYPE, "removed all instances", new String[] { "count", Integer.toString(count) });
    }

    private static class SharedInstanceCacheEntry {
        public ReadonlyXFormsInstance readonlyInstance;
        public long timestamp;

        public SharedInstanceCacheEntry(ReadonlyXFormsInstance readonlyInstance, long timestamp) {
            this.readonlyInstance = readonlyInstance;
            this.timestamp = timestamp;
        }
    }
}
