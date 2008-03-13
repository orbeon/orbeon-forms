/**
 *  Copyright (C) 2007 Orbeon, Inc.
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

import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Attribute;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.trans.XPathException;

import java.util.Map;

/**
 * xxforms:element(xs:string) as element()
 *
 * Creates a new XML element. The argument is a string representing a QName. If a prefix is present, it is resolved
 * with the namespace mappings in scope where the expression is evaluated.
 */
public class XXFormsElement extends XFormsFunction {

    // Mmh, here we use a global wrapper for creating dom4j elements. Is this the right way of doing it?
    public static final DocumentWrapper documentWrapper
            = new DocumentWrapper(Dom4jUtils.createDocument(), null, new Configuration());

    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
    * (because the value of the expression depends on the runtime context)
    */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        final Expression qNameExpression = (argument == null || argument.length < 1) ? null : argument[0];
        final String qName = (qNameExpression == null) ? null : qNameExpression.evaluateAsString(xpathContext);

        final Expression contextExpression = (argument == null || argument.length < 2) ? null : argument[1];
        final SequenceIterator content = (contextExpression == null) ? null : contextExpression.iterate(xpathContext);

        final int colonIndex = qName.indexOf(':');
        final Element element;
        if (colonIndex == -1) {
            // NCName
            element = Dom4jUtils.createElement(qName);
        } else {
            // QName-but-not-NCName
            final String prefix = qName.substring(0, colonIndex);

            final XFormsContextStack contextStack = getContextStack(xpathContext);
            final Map namespaceMappings = getContainingDocument(xpathContext).getNamespaceMappings(contextStack.getCurrentBindingContext().getControlElement());

            final String uri = (String) namespaceMappings.get(prefix);
            if (uri == null)
                throw new OXFException("Namespace prefix not in space for QName: " + qName);

            element = Dom4jUtils.createElement(qName, uri);
        }

        // Iterate over content if passed
        if (content != null) {
            boolean hasNewText = false;
            while (true) {
                final Item currentItem = content.next();
                if (currentItem == null) {
                    break;
                }
                if (currentItem instanceof AtomicValue) {
                    // Insert as text
                    element.addText(currentItem.getStringValue());
                    hasNewText = true;
                } else if (currentItem instanceof NodeInfo) {
                    // Insert nodes

                    if (currentItem instanceof NodeWrapper) {
                        // dom4j node
                        final Node currentNode = (Node) ((NodeWrapper) currentItem).getUnderlyingNode();

                        if (currentNode instanceof Attribute) {
                            // Add attribute
                            element.add((Attribute) currentNode);
                        } else {
                            // Append node
                            element.content().add(currentNode);
                        }

                    } else {
                        // Other type of node
                        // TODO: read and convert
                    }
                }
            }

            // Make sure we are normalized if we added text 
            if (hasNewText)
                Dom4jUtils.normalizeTextNodes(element);
        }

        return documentWrapper.wrap(element);
    }
}
