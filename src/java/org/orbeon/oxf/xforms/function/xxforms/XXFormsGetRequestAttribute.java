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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

/**
 * xxforms:get-request-attribute($a as xs:string) document-node()?
 *
 * Return the value of the given request attribute.
 */
public class XXFormsGetRequestAttribute extends XXFormsGetScopeAttribute {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final XFormsContainingDocument containingDocument = getContainingDocument(xpathContext);
        if (containingDocument == null || containingDocument.isInitializing()) { // support null for use outside of XForms
            // Get attribute name
            final Expression attributeNameExpression = argument[0];
            final String attributeName = attributeNameExpression.evaluateAsString(xpathContext).toString();

            // Get optional content type
            final String contentType;
            if (argument.length >= 2) {
                final Expression contentTypeExpression = argument[1];
                contentType = contentTypeExpression.evaluateAsString(xpathContext).toString();
            } else {
                contentType = null;
            }

            // Get attribute value
            final Object attributeObject = NetUtils.getExternalContext().getRequest().getAttributesMap().get(attributeName);
            return convertAttributeValue(xpathContext, attributeObject, contentType, attributeName);
        } else {
            throw new OXFException("xxforms:get-request-attribute() can only be called during XForms initialization.");
        }
    }
}
