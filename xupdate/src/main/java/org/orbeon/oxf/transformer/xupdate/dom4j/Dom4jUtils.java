/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.transformer.xupdate.dom4j;

import org.apache.commons.io.output.StringBuilderWriter;
import org.orbeon.dom4j.*;
import org.orbeon.dom4j.io.OutputFormat;
import org.orbeon.dom4j.io.XMLWriter;
import org.orbeon.oxf.common.OXFException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Collection of utility routines for working with DOM4J. In particular offers many methods found in DocumentHelper.
 * The difference between these 'copied' methods and the originals is that our copies use our NonLazyUserData* classes.
 * (As opposed to DOM4J's defaults or whatever happens to be specified in DOM4J's system property.)
 */
public class Dom4jUtils {

    private static final String XML_PREFIX = "xml";
    private static final String XML_URI = "http://www.w3.org/XML/1998/namespace";

    /**
     * Return a Map of namespaces in scope on the given element.
     */
    public static Map<String, String> getNamespaceContext(Element element) {
        final Map<String, String> namespaces = new HashMap<String, String>();
        for (Element currentNode = element; currentNode != null; currentNode = currentNode.getParent()) {
            final List currentNamespaces = currentNode.declaredNamespaces();
            for (Iterator j = currentNamespaces.iterator(); j.hasNext();) {
                final Namespace namespace = (Namespace) j.next();
                if (!namespaces.containsKey(namespace.getPrefix())) {
                    namespaces.put(namespace.getPrefix(), namespace.getURI());

                    // TODO: Intern namespace strings to save memory; should use NamePool later
//                    namespaces.put(namespace.getPrefix().intern(), namespace.getURI().intern());
                }
            }
        }
        // It seems that by default this may not be declared. However, it should be: "The prefix xml is by definition
        // bound to the namespace name http://www.w3.org/XML/1998/namespace. It MAY, but need not, be declared, and MUST
        // NOT be bound to any other namespace name. Other prefixes MUST NOT be bound to this namespace name, and it
        // MUST NOT be declared as the default namespace."
        namespaces.put(XML_PREFIX, XML_URI);
        return namespaces;
    }

    public static XPath createXPath(final String expression) throws InvalidXPathException {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createXPath(expression);
    }

    public static Text createText(final String text) {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createText(text);
    }

    public static Attribute createAttribute(final QName qName, final String value) {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createAttribute(null, qName, value);
    }

    public static Namespace createNamespace(final String prefix, final String uri) {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createNamespace(prefix, uri);
    }

    private static String domToString(final Branch branch) {
        final OutputFormat format = new OutputFormat();
        format.setIndent(false);
        format.setNewlines(false);
        return domToString(branch, format);
    }

    private static String domToString(final Node node, final OutputFormat format) {
        try {
            final StringBuilderWriter writer = new StringBuilderWriter();
            // Ugh, XMLWriter doesn't accept null formatter _and_ default formatter is protected.
            final XMLWriter xmlWriter = format == null ? new XMLWriter(writer) : new XMLWriter(writer, format);
            xmlWriter.write(node);
            xmlWriter.close();
            return writer.toString();
        } catch (final IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Convert a dom4j node to a string.
     *
     * @param node  node to convert
     * @return      resulting string
     */
    public static String nodeToString(final Node node) {
        final String ret;
        switch (node.getNodeType()) {
            case Node.DOCUMENT_NODE: {
                ret = domToString(((Document) node).getRootElement());
                break;
            }
            case Node.ELEMENT_NODE: {
                ret = domToString((Branch) node);
                break;
            }
            case Node.TEXT_NODE: {
                ret = node.getText();
                break;
            }
            default :
                ret = domToString(node, null);
                break;
        }
        return ret;
    }

    /**
     * Removes the elements and text inside the given element, but not the attributes or namespace
     * declarations on the element.
     */
    public static void clearElementContent(final Element elt) {
        final java.util.List cntnt = elt.content();
        for (final java.util.ListIterator j = cntnt.listIterator();
             j.hasNext();) {
            final Node chld = (Node) j.next();
            if (chld.getNodeType() == Node.TEXT_NODE
                    || chld.getNodeType() == Node.ELEMENT_NODE) {
                j.remove();
            }
        }
    }
}
