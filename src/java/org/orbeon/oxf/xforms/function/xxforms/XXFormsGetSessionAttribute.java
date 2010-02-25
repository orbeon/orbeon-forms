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

import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

/**
 * xxforms:get-session-attribute($a as xs:string) document-node()?
 *
 * Return the value of the given session attribute.
 */
public class XXFormsGetSessionAttribute extends XXFormsGetScopeAttribute {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get attribute name
        final Expression attributeNameExpression = argument[0];
        final String attributeName = attributeNameExpression.evaluateAsString(xpathContext);

        // Get optional content type
        final String contentType;
        if (argument.length >= 2) {
            final Expression contentTypeExpression = argument[1];
            contentType = contentTypeExpression.evaluateAsString(xpathContext);
        } else {
            contentType = null;
        }

        // Get attribute value

        // This function is always called from controls so ExternalContext should be present
        final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
        final ExternalContext externalContext = staticContext.getExternalContext();

        final ExternalContext.Session session = externalContext.getSession(false);// do not force session creation
        if (session != null) {
            // Found session
            final Object attributeObject = session.getAttributesMap().get(attributeName);
            return convertAttributeValue(attributeObject, contentType, attributeName);
        } else {
            // No session, return empty result
            return EmptyIterator.getInstance();
        }
    }
}
