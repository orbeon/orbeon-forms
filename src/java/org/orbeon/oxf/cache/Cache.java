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

import org.orbeon.oxf.pipeline.api.PipelineContext;

import java.util.Iterator;

public interface Cache {

    public static final int EXPIRATION_NO_CACHE = 0;
    public static final int EXPIRATION_NO_EXPIRATION = -1;
    public static final int EXPIRATION_LAST_MODIFIED = -2;

    public void add(PipelineContext context, CacheKey key, Object validity, Object object);
    public void remove(PipelineContext context, CacheKey key);
    public Object findValid(PipelineContext context, CacheKey key, Object validity);
    public Object findValidWithExpiration(PipelineContext context, CacheKey key, long expiration);
    public Iterator iterateCacheKeys(PipelineContext context);
    public Iterator iterateCacheObjects(PipelineContext context);
    public void setMaxSize(PipelineContext context, int maxSize);
    public CacheStatistics getStatistics(PipelineContext context);
}
