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
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.trans.XPathException;


/**
 * xxforms:mutable-document() takes a document as input and produces a mutable document as output, i.g. a document on
 * which you can use xforms:setvalue, for example.
 */
public class XXFormsMutableDocument extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // Get item
        final Expression itemExpression = argument[0];
        final Item item = itemExpression.evaluateItem(xpathContext);

        // Make sure it is a NodeInfo
        if (!(item instanceof NodeInfo)) {
            return null;
        }

        // Convert and return
        final NodeInfo nodeInfo = (NodeInfo) item;
        final Document document = TransformerUtils.tinyTreeToDom4j2(nodeInfo);

        return new DocumentWrapper(document, null, xpathContext.getConfiguration());
    }
}
