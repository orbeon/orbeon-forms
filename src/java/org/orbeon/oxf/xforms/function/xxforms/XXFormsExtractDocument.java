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
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.expr.ExpressionTool;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.trans.XPathException;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;


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
        if (item instanceof NodeWrapper) {
            final Object node = ((NodeWrapper) item).getUnderlyingNode();
            rootElement = (Element) node;
        } else {
            final NodeInfo nodeInfo = (NodeInfo) item;
            final Document document = TransformerUtils.tinyTreeToDom4j2(nodeInfo);
            rootElement = document.getRootElement();
        }

        // Convert to Document or DocumentInfo
        final Object result = extractDocument(xpathContext.getConfiguration(), rootElement, excludeResultPrefixes, readonly);

        // Return DocumentInfo
        if (result instanceof Document)
            return new DocumentWrapper((Document) result, null, getContainingDocument(xpathContext).getStaticState().xpathConfiguration());
        else
            return (DocumentInfo) result;
    }

    public static Object extractDocument(Configuration configuration, Element element, String excludeResultPrefixes, boolean readonly) {
        Object instanceDocument;
        final Document tempDocument;

        // Extract document and adjust namespaces
        // TODO: Implement exactly as per XSLT 2.0
        // TODO: Must implement namespace fixup, the code below can break serialization
        if ("#all".equals(excludeResultPrefixes)) {
            // Special #all
            tempDocument = Dom4jUtils.createDocumentCopyElement(element);
        } else if (excludeResultPrefixes != null && excludeResultPrefixes.trim().length() != 0) {
            // List of prefixes
            final StringTokenizer st = new StringTokenizer(excludeResultPrefixes);
            final Map<String, String> prefixesToExclude = new HashMap<String, String>();
            while (st.hasMoreTokens()) {
                prefixesToExclude.put(st.nextToken(), "");
            }
            tempDocument = Dom4jUtils.createDocumentCopyParentNamespaces(element, prefixesToExclude);
        } else {
            // No exclusion
            tempDocument = Dom4jUtils.createDocumentCopyParentNamespaces(element);
        }

        // Produce DOM or TinyTree
        if (!readonly) {
            instanceDocument = tempDocument;
        } else {
            instanceDocument = TransformerUtils.dom4jToTinyTree(configuration, tempDocument);
        }

        return instanceDocument;
    }
}
