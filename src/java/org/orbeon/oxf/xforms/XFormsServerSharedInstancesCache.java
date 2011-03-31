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
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.util.IndentedLogger;

/**
 * Cache for shared and immutable XForms instances.
 */
public class XFormsServerSharedInstancesCache {

    private static final String XFORMS_SHARED_INSTANCES_CACHE_NAME = "xforms.cache.shared-instances";
    private static final int XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE = 10;

    private static final Long CONSTANT_VALIDITY = 0L;
    private static final String SHARED_INSTANCE_KEY_TYPE = XFORMS_SHARED_INSTANCES_CACHE_NAME;
    
    private static final String LOG_TYPE = "instance cache";

    private static XFormsServerSharedInstancesCache instance = null;

    public interface Loader {
        public ReadonlyXFormsInstance load(String instanceStaticId, String modelEffectiveId,
                                           String instanceSourceURI, boolean handleXInclude, long timeToLive, String validation);
    }

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

    public XFormsInstance findConvertNoLoad(IndentedLogger indentedLogger, String instanceStaticId,
                                            String modelEffectiveId, String instanceSourceURI, String requestBodyHash, boolean isReadonly,
                                            boolean handleXInclude, boolean exposeXPathTypes) {

        // Try to find in cache
        final ReadonlyXFormsInstance existingInstance
                = findInCache(indentedLogger, instanceStaticId, modelEffectiveId, instanceSourceURI, requestBodyHash, handleXInclude, exposeXPathTypes);
        if (existingInstance != null) {
            // Found from the cache

            return convert(indentedLogger, isReadonly, existingInstance);
        } else {
            return null;
        }
    }

    public XFormsInstance findConvert(IndentedLogger indentedLogger, String instanceStaticId,
                                      String modelEffectiveId, String instanceSourceURI, String requestBodyHash, boolean isReadonly,
                                      boolean handleXInclude, boolean exposeXPathTypes, long timeToLive, String validation, Loader loader) {

        final ReadonlyXFormsInstance tempReadonlyInstance;
        {
            // Try to find in cache
            final ReadonlyXFormsInstance existingInstance
                    = findInCache(indentedLogger, instanceStaticId, modelEffectiveId, instanceSourceURI, requestBodyHash, handleXInclude, exposeXPathTypes);
            if (existingInstance != null) {
                // Found from the cache
                tempReadonlyInstance = existingInstance;
            } else {
                // Not found from the cache, attempt to retrieve

                // Note that this method is not synchronized. Scenario: if the method is synchronized, the resource URI may
                // may reach an XForms page which itself needs to load a shared resource. The result would be a deadlock.
                // Without synchronization, what can happen is that two concurrent requests load the same URI at the same
                // time. In the worst case scenario, the results will be different, and the two requesting XForms instances
                // will be different. The instance that is retrieved first will be stored in the cache for a very short
                // amount of time, and the one retrieved last will win and be stored in the cache for a longer time.

                // Load instance through callback
                final ReadonlyXFormsInstance newInstance = loader.load(instanceStaticId, modelEffectiveId,
                        instanceSourceURI, handleXInclude, timeToLive, validation);

                // Add result to cache
                add(indentedLogger, instanceSourceURI, requestBodyHash, newInstance, handleXInclude);

                // Return instance
                tempReadonlyInstance = newInstance;
            }
        }

        return convert(indentedLogger, isReadonly, tempReadonlyInstance);
    }

    private XFormsInstance convert(IndentedLogger indentedLogger, boolean isReadonly, ReadonlyXFormsInstance tempReadonlyInstance) {
        final XFormsInstance newInstance;
        if (isReadonly) {
            // Keep readonly instance
            newInstance = tempReadonlyInstance;

            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug(LOG_TYPE, "returning read-only cached instance", "instance", newInstance.getEffectiveId());
        } else {
            // Convert to mutable instance
            newInstance = tempReadonlyInstance.createMutableInstance();

            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug(LOG_TYPE, "returning read-write cached instance", "instance", newInstance.getEffectiveId());
        }
        return newInstance;
    }

    private void add(IndentedLogger indentedLogger, String instanceSourceURI,
                     String requestBodyHash, ReadonlyXFormsInstance readonlyXFormsInstance, boolean handleXInclude) {

        if (indentedLogger.isDebugEnabled())
            indentedLogger.logDebug(LOG_TYPE, "adding instance",
                    "id", readonlyXFormsInstance.getEffectiveId(),
                    "URI", instanceSourceURI,
                    "request hash", requestBodyHash);

        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final InternalCacheKey cacheKey = createCacheKey(instanceSourceURI, requestBodyHash, handleXInclude);

        cache.add(cacheKey, CONSTANT_VALIDITY, new SharedInstanceCacheEntry(readonlyXFormsInstance, System.currentTimeMillis()));
    }

    private synchronized ReadonlyXFormsInstance findInCache(IndentedLogger indentedLogger,
                                                            String instanceStaticId, String modelEffectiveId, String instanceSourceURI,
                                                            String requestBodyHash, boolean handleXInclude, boolean exposeXPathTypes) {

        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);

        final InternalCacheKey cacheKey = createCacheKey(instanceSourceURI, requestBodyHash, handleXInclude);
        final SharedInstanceCacheEntry sharedInstanceCacheEntry = (SharedInstanceCacheEntry) cache.findValid(cacheKey, CONSTANT_VALIDITY);

        // Whether there is an entry but it has expired
        boolean isExpired = sharedInstanceCacheEntry != null && sharedInstanceCacheEntry.readonlyInstance.getTimeToLive() >= 0
                && ((sharedInstanceCacheEntry.timestamp + sharedInstanceCacheEntry.readonlyInstance.getTimeToLive()) < System.currentTimeMillis());

        // Remove expired entry if any
        if (isExpired) {
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug(LOG_TYPE, "expiring instance",
                        "id", instanceStaticId,
                        "URI", instanceSourceURI,
                        "request hash", requestBodyHash);
            cache.remove(cacheKey);
        }

        if (sharedInstanceCacheEntry != null && !isExpired) {
            // Instance was found
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug(LOG_TYPE, "found instance",
                        "id", instanceStaticId,
                        "URI", instanceSourceURI,
                        "request hash", requestBodyHash);

            final ReadonlyXFormsInstance readonlyInstance = sharedInstanceCacheEntry.readonlyInstance;

            // Return a copy because id, etc. can be different
            return new ReadonlyXFormsInstance(modelEffectiveId, instanceStaticId, readonlyInstance.getDocumentInfo(),
                    instanceSourceURI, readonlyInstance.getRequestBodyHash(), null, null, null, readonlyInstance.isCache(),
                    readonlyInstance.getTimeToLive(), readonlyInstance.getValidation(), readonlyInstance.isHandleXInclude(), exposeXPathTypes);
        } else {
            // Not found
            return null;
        }
    }

    private InternalCacheKey createCacheKey(String instanceSourceURI, String requestBodyHash, boolean handleXInclude) {
        // Make key also depend on handleXInclude and on request body hash if present
        return new InternalCacheKey(SHARED_INSTANCE_KEY_TYPE, instanceSourceURI + "|" + Boolean.toString(handleXInclude) + (requestBodyHash != null ? "|" + requestBodyHash : ""));
    }

    public synchronized void remove(IndentedLogger indentedLogger, String instanceSourceURI, String requestBodyHash, boolean handleXInclude) {

        if (indentedLogger.isDebugEnabled())
            indentedLogger.logDebug(LOG_TYPE, "removing instance", "URI", instanceSourceURI, "request hash", requestBodyHash);

        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final InternalCacheKey cacheKey = createCacheKey(instanceSourceURI, requestBodyHash, handleXInclude);

        cache.remove(cacheKey);
    }

    public synchronized void removeAll(IndentedLogger indentedLogger) {
        final Cache cache = ObjectCache.instance(XFORMS_SHARED_INSTANCES_CACHE_NAME, XFORMS_SHARED_INSTANCES_CACHE_DEFAULT_SIZE);
        final int count = cache.removeAll();

        if (indentedLogger.isDebugEnabled())
            indentedLogger.logDebug(LOG_TYPE, "removed all instances", "count", Integer.toString(count));
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
