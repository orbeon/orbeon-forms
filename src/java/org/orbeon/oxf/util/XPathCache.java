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
package org.orbeon.oxf.util;

import org.dom4j.DocumentHelper;
import org.dom4j.XPath;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.pipeline.api.PipelineContext;

/**
 * Use the object cache to cache XPath expressions. Those are costly to parse.
 */
public class XPathCache {
    public static XPath createCacheXPath(PipelineContext context, String xpathExpression) {
        final boolean doCache = true;
        if (doCache) {
            Long validity = new Long(0);
            Cache cache = ObjectCache.instance();
            InternalCacheKey cacheKey = new InternalCacheKey("XPath Expression", xpathExpression);
            XPath xpath = (XPath) cache.findValid(context, cacheKey, validity);
            if (xpath == null) {
                xpath = DocumentHelper.createXPath(xpathExpression);
                cache.add(context, cacheKey, validity, xpath);
            }
            return xpath;
        } else {
            return DocumentHelper.createXPath(xpathExpression);
        }
    }
}
