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

import org.exolab.castor.mapping.Mapping;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.scope.ScopeGenerator;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.SingletonIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.AtomicValue;
import org.xml.sax.InputSource;

import java.io.StringReader;

/**
 * Base class for xxforms:get-*-attribute() functions.
 */
public abstract class XXFormsGetScopeAttribute extends XFormsFunction {

    protected SequenceIterator convertAttributeValue(XPathContext xpathContext, Object attributeObject, String contentType, String key) throws XPathException {
        if (attributeObject instanceof AtomicValue) {
            // Found atomic value
            return SingletonIterator.makeIterator((AtomicValue) attributeObject);
        } else if (attributeObject != null) {
            // Found something else, hopefully convertible to SAXStore
            final SAXStore saxStore;
            try {
                // We don't have any particular mappings to pass to serialize objects
                final Mapping mapping = new Mapping();
                mapping.loadMapping(new InputSource(new StringReader("<mapping/>")));

                saxStore = ScopeGenerator.getSAXStore(attributeObject, mapping, contentType, key);
            } catch (Exception e) {
                throw new OXFException(e);
            }
            // Convert to DocumentInfo
            final DocumentInfo documentInfo = TransformerUtils.saxStoreToTinyTree(xpathContext.getConfiguration(), saxStore);
            return SingletonIterator.makeIterator(documentInfo);
        } else {
            // Empty result
            return EmptyIterator.getInstance();
        }
    }
}
