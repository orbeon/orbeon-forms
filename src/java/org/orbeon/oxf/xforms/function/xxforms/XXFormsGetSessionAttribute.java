/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms;

import org.exolab.castor.mapping.Mapping;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.scope.ScopeGenerator;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.Collections;

/**
 * xxforms:get-session-attribute($a as xs:string) document-node()?
 *
 * Return the value of the given session attribute.
 *
 * TODO: Later we could return other types, including strings, numeric types, booleans, etc.
 */
public class XXFormsGetSessionAttribute extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get attribute name
        final Expression attributeNameExpression = argument[0];
        final String attributeName = attributeNameExpression.evaluateAsString(xpathContext);

        // Get attribute value

        // This function is always called from controls so ExternalContext should be present
        final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
        final ExternalContext externalContext = staticContext.getExternalContext();

        final ExternalContext.Session session = externalContext.getSession(false);// do not force session creation
        if (session != null) {
            // Found session
            final Object attributeObject = session.getAttributesMap().get(attributeName);
            if (attributeObject != null) {
                // Found session attribute
                final SAXStore saxStore;
                try {
                    // We don't have any particular mappings to pass to serialize objects
                    final Mapping mapping = new Mapping();
                    mapping.loadMapping(new InputSource(new StringReader("<mapping/>")));

                    saxStore = ScopeGenerator.getSAXStore(attributeObject, mapping);
                } catch (Exception e) {
                    throw new OXFException(e);
                }
                final DocumentInfo documentInfo = TransformerUtils.saxStoreToTinyTree(saxStore);

                return new ListIterator(Collections.singletonList(documentInfo));
            }
        }

        return EmptyIterator.getInstance();
    }
}
