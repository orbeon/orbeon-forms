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
package org.orbeon.oxf.xml.dom4j;

import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.xml.XMLUtils;

import java.util.Iterator;
import java.util.Map;

public class Dom4jUtils {

    /**
     * Clone a node, making sure that we copy all the declared namespace of
     * the source.
     */
    public static Node cloneNode(Node node) {
        if (node instanceof Element) {
            Element originalElement = (Element) node;
            Map namespaceContext = XMLUtils.getNamespaceContext(originalElement);
            Element resultElement = (Element) originalElement.clone();
            for (Iterator i = namespaceContext.keySet().iterator(); i.hasNext();) {
                String prefix = (String) i.next();
                if (resultElement.getNamespaceForPrefix(prefix) == null)
                    resultElement.addNamespace(prefix, (String) namespaceContext.get(prefix));
            }
            return resultElement;
        } else {
            return (Node) node.clone();
        }
    }


}
