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

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.QName;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.AtomicValue;

/**
 * xxforms:element(xs:string) as element()
 *
 * Creates a new XML element. The argument is a string representing a QName. If a prefix is present, it is resolved
 * with the namespace mappings in scope where the expression is evaluated.
 */
public class XXFormsElement extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // Element QName
        final Expression qNameExpression = (argument.length < 1) ? null : argument[0];
        final QName qName = getQNameFromExpression(xpathContext, qNameExpression);

//        final String qNameString;
//        final String qNameURI;
//        {
//            final SequenceIterator qNameIterator = (qNameExpression == null) ? null : qNameExpression.evaluateItem(xpathContext);
//            qNameString = qNameIterator.next().getStringValue();
//            final Item secondItem = qNameIterator.next();
//            qNameURI = (secondItem != null) ? secondItem.getStringValue() : null;
//        }

        // Content sequence
        final Expression contextExpression = (argument.length < 2) ? null : argument[1];
        final SequenceIterator content = (contextExpression == null) ? null : contextExpression.iterate(xpathContext);

        final String qNameURI = qName.getNamespaceURI();
        final Element element = Dom4jUtils.createElement(qName.getQualifiedName(), (qNameURI != null) ? qNameURI : "");// createElement() doesn't like a null namespace

        // Iterate over content if passed
        if (content != null) {
            boolean hasNewText = false;
            while (true) {
                final Item currentItem = content.next();
                if (currentItem == null)
                    break;
                hasNewText |= addItem(element, currentItem);
            }

            // Make sure we are normalized if we added text 
            if (hasNewText)
                Dom4jUtils.normalizeTextNodes(element);
        }

        return getContainingDocument(xpathContext).getStaticState().documentWrapper().wrap(element);
    }

    public static boolean addItem(Element element, Item item) {
        boolean hasNewText = false;
        if (item instanceof AtomicValue) {
            // Insert as text
            element.addText(item.getStringValue());
            hasNewText = true;
        } else if (item instanceof NodeInfo) {
            // Insert nodes

            // Copy node before using it
            final Node currentNode = XFormsUtils.getNodeFromNodeInfoConvert((NodeInfo) item);
            // TODO: check namespace handling might be incorrect. Should use copyElementCopyParentNamespaces() instead?
            final Node newNode = Dom4jUtils.createCopy(currentNode);

            if (newNode instanceof Attribute) {
                // Add attribute
                element.add((Attribute) newNode);
            } else if (newNode instanceof Document) {
                // use the document root instead
                element.content().add(newNode.getDocument().getRootElement());
            } else {
                // Append node
                element.content().add(newNode);
            }
        }
        return hasNewText;
    }
}
