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

import org.orbeon.oxf.common.OXFException;

import java.util.Iterator;

/**
 * Cache implementation based on soft references.
 *
 * NOTE: No longer used.
 */
public class SoftMemoryCacheImpl { // implements Cache
    private SoftCacheImpl impl = new SoftCacheImpl(1);// FIXME: should be configurable by property

    public synchronized void add(org.orbeon.oxf.pipeline.api.PipelineContext context, CacheKey key, Object validity, Object object) {
        Object[] existing = (Object[]) impl.get(key);
        if (existing != null) remove(context, key);
        impl.put(key, new Object[] { validity, object } );
    }

    public Iterator iterateCacheKeys(org.orbeon.oxf.pipeline.api.PipelineContext context) {
        throw new OXFException("Not implemented");
    }

    public Iterator iterateCacheObjects(org.orbeon.oxf.pipeline.api.PipelineContext context) {
        throw new OXFException("Not implemented");
    }

    public void setMaxSize(org.orbeon.oxf.pipeline.api.PipelineContext context, int maxSize) {
        throw new OXFException("Not implemented");
    }

    public CacheStatistics getStatistics(org.orbeon.oxf.pipeline.api.PipelineContext context) {
        throw new OXFException("Not implemented");
    }

    public synchronized Object findValid(org.orbeon.oxf.pipeline.api.PipelineContext context, CacheKey key, Object validity) {
        Object[] result = (Object[]) impl.get(key);
        if (result == null || !result[0].equals(validity)) return null;
        return result[1];
    }

    public synchronized void remove(org.orbeon.oxf.pipeline.api.PipelineContext context, CacheKey key) {
        impl.remove(key);
    }

    public synchronized void applyOnSoftCacheKeys(org.orbeon.oxf.pipeline.api.PipelineContext context, SoftCacheImpl.Action action) {
        impl.applyOnSoftCacheKeys(action);
    }
}
