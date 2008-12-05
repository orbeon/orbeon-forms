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
package org.orbeon.oxf.xml;

import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.StaticError;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.QNameValue;
import org.orbeon.saxon.value.SequenceType;
import org.xml.sax.InputSource;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import java.io.IOException;
import java.net.URL;

public class XPathCacheStaticContext extends IndependentContext {

    private static final URIResolver URI_RESOLVER = new XPathCacheURIResolver();

    // NOTE: It would be nice to use TransformerURIResolver, but currently doing so will NPE because there is no
    // ExternalContext available.
//    private static final URIResolver URI_RESOLVER = new TransformerURIResolver(false);

    // Whether whis context
    private final boolean allowAllVariables;

    public XPathCacheStaticContext(boolean allowAllVariables) {
        super();
        this.allowAllVariables = allowAllVariables;
        getConfiguration().setURIResolver(URI_RESOLVER);
    }

    private static class XPathCacheURIResolver implements URIResolver {
        public Source resolve(String href, String base) throws TransformerException {
            try {
                // Saxon Document.makeDoc() changes the base to "" if it is null
                if ("".equals(base))
                    base = null;
                final URL url = URLFactory.createURL(base, href);
                return new SAXSource(XMLUtils.newXMLReader(false, false), new InputSource(url.openStream()));
            } catch (IOException e) {
                throw new TransformerException(e);
            }
        }
    }

    public VariableReference bindVariable(int fingerprint) throws StaticError {
        if (allowAllVariables) {
            final QNameValue qname = new QNameValue(getNamePool(), fingerprint);
            return new VariableReference(new DeferredVariable(qname));
        } else {
            try {
                return super.bindVariable(fingerprint);
            } catch (StaticError e) {
                // Be a little more friendly in the error message
                final QNameValue qname = new QNameValue(getNamePool(), fingerprint);
                throw new StaticError("Undeclared variable in a standalone expression: $" + qname.getStringValue());
            }
        }
    }
}


/**
 * This class is inspired from Saxon's JAXPVariable, but it just removes any dependency on org.orbeon.saxon.xpath and
 * JAXP and only work at compile-time.
 */
final class DeferredVariable implements VariableDeclaration, Binding {

    private QNameValue name;

    public DeferredVariable(QNameValue name) {
        this.name = name;
    };

    public boolean isGlobal() {
        return true;
    }

    public final boolean isAssignable() {
        return false;
    }

    public int getLocalSlotNumber() {
        return -1;
    }

    public String getVariableName() {
        return name.getStringValue();
    }
    public int getNameCode() {
        return -1;
    }

    public void registerReference(BindingReference ref) {
        ref.setStaticType(SequenceType.ANY_SEQUENCE, null, 0);
        ref.fixup(this);
    }

    public ValueRepresentation evaluateVariable(XPathContext context) throws XPathException {
        // Don't handle any runtime operation
        return null;
    }
}
