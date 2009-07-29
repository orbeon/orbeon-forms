/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.orbeon.oxf.util.PropertyContext;

import java.util.Iterator;

public interface Cache {

    static final int EXPIRATION_NO_CACHE = 0;
    static final int EXPIRATION_NO_EXPIRATION = -1;
    static final int EXPIRATION_LAST_MODIFIED = -2;

    String getCacheName();
    void add(PropertyContext propertyContext, CacheKey key, Object validity, Object object);
    void remove(PropertyContext propertyContext, CacheKey key);
    int removeAll(PropertyContext propertyContext);
    Object findValid(PropertyContext propertyContext, CacheKey key, Object validity);
    Iterator iterateCacheKeys(PropertyContext propertyContext);
    Iterator iterateCacheObjects(PropertyContext propertyContext);
    void setMaxSize(PropertyContext propertyContext, int maxSize);
    CacheStatistics getStatistics(PropertyContext propertyContext);
}
