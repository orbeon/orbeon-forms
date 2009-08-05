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
package org.orbeon.oxf.xforms.function.xxforms;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.AtomicValue;

import java.util.Map;

/**
 * Base class for xxforms:set-*-attribute() functions.
 */
public class XXFormsSetScopeAttribute extends XFormsFunction {

    protected void storeAttribute(Map<String, Object> scope, String attributeName, Item item) throws XPathException {
        if (item == null) {
            // Clear value
            scope.put(attributeName, null);
        } else {
            // NOTE: In theory, we could store any item()* into the session. This would work fine with atomic values,
            // but we would need to copy over trees as NodeInfo can back trees that may change over time. So for now,
            // we only accept storing a single item(), and we convert trees into SAXStore.

            // Prepare value
            final Object value;
            if (item instanceof AtomicValue) {
                // Store as is
                value = item;
            } else if (item instanceof NodeInfo) {
                // Store as SAXStore
                final NodeInfo nodeInfoValue = (NodeInfo) item;
                value = TransformerUtils.tinyTreeToSAXStore(nodeInfoValue);
            } else {
                throw new OXFException("xxforms:set-*-attribute() does not support storing objects of type: "
                        + item.getClass().getName());
            }

            // Store value
            // TODO: It seems that Jetty sometimes fails down the line here by calling equals() on the value.
            scope.put(attributeName, value);
        }
    }
}
