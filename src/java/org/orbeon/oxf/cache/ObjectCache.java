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
package org.orbeon.oxf.cache;

import org.orbeon.oxf.properties.Properties;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for ObjectCache instances.
 */
public class ObjectCache {

    private static final String DEFAULT_CACHE_NAME = "cache.main";
    private static final int DEFAULT_SIZE = 200;

    private static final String CACHE_PROPERTY_NAME_PREFIX = "oxf.";
    private static final String CACHE_PROPERTY_NAME_SIZE_SUFFIX = ".size";

    private static final Map<String, Cache> namedObjectCaches = new HashMap<String, Cache>();

    static {
        namedObjectCaches.put(DEFAULT_CACHE_NAME, new MemoryCacheImpl(DEFAULT_CACHE_NAME, DEFAULT_SIZE));
    }

    private ObjectCache() {}

    /**
     * Get the instance of the main object cache.
     *
     * @return instance of cache
     */
    public static Cache instance() {
        return namedObjectCaches.get(DEFAULT_CACHE_NAME);
    }

    /**
     * Get the instance of the object cache specified.
     *
     * @param cacheName     name of the cache
     * @param defaultSize   default size if size is not found in properties
     * @return              instance of cache
     */
    public synchronized static Cache instance(String cacheName, int defaultSize) {
        Cache cache = namedObjectCaches.get(cacheName);
        if (cache == null) {
            final String propertyName = CACHE_PROPERTY_NAME_PREFIX + cacheName + CACHE_PROPERTY_NAME_SIZE_SUFFIX;
            final Integer size = Properties.instance().getPropertySet().getInteger(propertyName, defaultSize);
            cache = new MemoryCacheImpl(cacheName, size);
            namedObjectCaches.put(cacheName, cache);
        }
        return cache;
    }

    /**
     * Get the instance of the object cache specified if it exists.
     *
     * @param cacheName     name of the cache
     * @return              instance of cache, null if did not exist
     */
    public synchronized static Cache instanceIfExists(String cacheName) {
        return (namedObjectCaches == null) ? null : namedObjectCaches.get(cacheName);
    }
}
