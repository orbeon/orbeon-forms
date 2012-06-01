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

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.saxon.expr.ExpressionTool;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.VirtualNode;
import org.orbeon.saxon.trans.XPathException;


/**
 * xxforms:extract-document() takes an element as parameter and extracts a document.
 */
public class XXFormsExtractDocument extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // Get parameters
        final Item item = argument[0].evaluateItem(xpathContext);
        final String excludeResultPrefixes = argument.length >= 2 ? argument[1].evaluateAsString(xpathContext).toString() : null;
        final boolean readonly = argument.length >= 3 && ExpressionTool.effectiveBooleanValue(argument[2].iterate(xpathContext));

        // Make sure it is a NodeInfo
        if (!(item instanceof NodeInfo)) {
            return null;
        }

        // Get Element
        final Element rootElement;
        if (item instanceof VirtualNode) {
            final Object node = ((VirtualNode) item).getUnderlyingNode();
            rootElement = (Element) node;
        } else {
            final NodeInfo nodeInfo = (NodeInfo) item;
            final Document document = TransformerUtils.tinyTreeToDom4j2(nodeInfo);
            rootElement = document.getRootElement();
        }

        // Extract the document starting at the given root element
        return XFormsInstance.extractDocument(rootElement, excludeResultPrefixes, readonly, false);
    }
}
