/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.cache;

import org.orbeon.oxf.resources.OXFProperties;

import java.util.Map;
import java.util.HashMap;

/**
 * Factory for the cache object
 */
public class ObjectCache {

    private static final String CACHE_PROPERTY_NAME_PREFIX = "oxf.cache.";
    private static final String CACHE_PROPERTY_NAME_SIZE_SUFFIX = ".size";

    private static final int DEFAULT_SIZE = 200;

    private static Cache impl = new MemoryCacheImpl(DEFAULT_SIZE);
    private static Map impls;

    private ObjectCache() {}

    public static Cache instance() {
        return impl;
    }

    public synchronized static Cache instance(String type) {
        if (impls == null)
            impls = new HashMap();
        Cache cache = (Cache) impls.get(type);
        if (cache == null) {
            final String propertyName = CACHE_PROPERTY_NAME_PREFIX + type + CACHE_PROPERTY_NAME_SIZE_SUFFIX;
            final Integer size = OXFProperties.instance().getPropertySet().getInteger(propertyName, DEFAULT_SIZE);
            cache = new MemoryCacheImpl(size.intValue());
            impls.put(type, cache);
        }
        return cache;
    }
}
