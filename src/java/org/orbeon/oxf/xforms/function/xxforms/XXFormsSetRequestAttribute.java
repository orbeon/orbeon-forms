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
package org.orbeon.oxf.xforms.function.xxforms;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

/**
 * xxforms:set-request-attribute($a as xs:string) document-node()?
 *
 * Set the value of the given session attribute.
 */
public class XXFormsSetRequestAttribute extends XXFormsSetScopeAttribute {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get attribute name
        final Expression attributeNameExpression = argument[0];
        final String attributeName = attributeNameExpression.evaluateAsString(xpathContext).toString();

        // Get value
        final Expression valueExpression = argument[1];
        final Item item = valueExpression.evaluateItem(xpathContext);

        // Store value
        final ExternalContext.Request request = NetUtils.getExternalContext().getRequest();
        storeAttribute(request.getAttributesMap(), attributeName, item);

        // Return empty sequence
        return EmptyIterator.getInstance();
    }
}
