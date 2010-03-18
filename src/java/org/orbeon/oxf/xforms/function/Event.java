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
package org.orbeon.oxf.xforms.function;

import org.dom4j.QName;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.ExpressionVisitor;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.NamespaceResolver;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 2.4 Accessing Context Information for Events
 *
 * This is the event() function which returns "context specific information" for an event.
 */
public class Event extends XFormsFunction {

    private Map<String, String> namespaceMappings;

    @Override
    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {
        // Get parameter name
        final Expression instanceIdExpression = argument[0];
        final String attributeName = instanceIdExpression.evaluateAsString(xpathContext).toString();

        // Get the current event
        final XFormsEvent event = getContainingDocument(xpathContext).getCurrentEvent();

        return getEventAttribute(event, attributeName);
    }

    protected SequenceIterator getEventAttribute(XFormsEvent event, String attributeName) {
        // TODO: Currently the spec doesn't specify what happens when we call event() outside of an event handler
        if (event == null)
            return EmptyIterator.getInstance();

        // As an extension, we allow a QName

        // NOTE: Here the idea is to find the namespaces in scope. We assume that the expression occurs on an XForms
        // element. There are other ways of obtaining the namespaces, for example we could extract them from the static
        // state.
//        final Element element = getContextStack(xpathContext).getCurrentBindingContext().getControlElement();
//        final Map namespaceMappings = getContainingDocument(xpathContext).getStaticState().getNamespaceMappings(element);

        final QName attributeQName = Dom4jUtils.extractTextValueQName(namespaceMappings, attributeName, true);

        // Simply ask the event for the attribute
        return event.getAttribute(Dom4jUtils.qNameToExplodedQName(attributeQName));
    }

    // The following copies StaticContext namespace information
    @Override
    public void checkArguments(ExpressionVisitor visitor) throws XPathException {
        // See also Saxon Evaluate.java
        if (namespaceMappings == null) { // only do this once
            final StaticContext env = visitor.getStaticContext();
            super.checkArguments(visitor);

            namespaceMappings = new HashMap<String, String>();

            final NamespaceResolver namespaceResolver = env.getNamespaceResolver();
            for (Iterator iterator = namespaceResolver.iteratePrefixes(); iterator.hasNext();) {
                final String prefix = (String) iterator.next();
                if (!"".equals(prefix)) {
                    final String uri = namespaceResolver.getURIForPrefix(prefix, true);
                    namespaceMappings.put(prefix, uri);
                }
            }
        }
    }
}
