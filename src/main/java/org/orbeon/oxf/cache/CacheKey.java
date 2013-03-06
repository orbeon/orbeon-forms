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

import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;

import java.io.Serializable;

public abstract class CacheKey implements Serializable {

    private Class clazz;

    public Class getClazz() { return clazz; }
    public void setClazz(Class clazz) { this.clazz = clazz; }

    @Override
    public boolean equals(Object other) {
        return other instanceof CacheKey && this.clazz.equals(((CacheKey) other).clazz);
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    public abstract void toXML(ContentHandlerHelper helper, Object validities);

    public static String toXMLString(CacheKey cacheKey, Object validities) {
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        final LocationDocumentResult result = new LocationDocumentResult();
        identity.setResult(result);

        final ContentHandlerHelper helper = new ContentHandlerHelper(identity);
        helper.startDocument();
        cacheKey.toXML(helper, validities);
        helper.endDocument();

        return Dom4jUtils.domToPrettyString(result.getDocument());
    }
}
