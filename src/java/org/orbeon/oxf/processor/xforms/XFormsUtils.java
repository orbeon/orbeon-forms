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
package org.orbeon.oxf.processor.xforms;

import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.util.*;

public class XFormsUtils {

    /**
     * Adds to <code>target</code> all the attributes in <code>source</code>
     * that are not in the XForms namespace.
     */
    public static void addNonXFormsAttributes(AttributesImpl target, Attributes source) {
        for (Iterator i = new XMLUtils.AttributesIterator(source); i.hasNext();) {
            XMLUtils.Attribute attribute = (XMLUtils.Attribute) i.next();
            if (!"".equals(attribute.getURI()) &&
                    !Constants.XXFORMS_NAMESPACE_URI.equals(attribute.getURI())) {
                target.addAttribute(attribute.getURI(), attribute.getLocalName(),
                        attribute.getQName(), ContentHandlerHelper.CDATA, attribute.getValue());
            }
        }
    }

    public static void fillNode(Node node, String value) {
        if (node instanceof Element) {
            Element elementnode = (Element) node;
            // Remove current content
            elementnode.clearContent();
            // Put text node with value
            elementnode.add(DocumentFactory.getInstance().createText(value));
        } else if (node instanceof Attribute) {
            Attribute attributenode = (Attribute) node;
            attributenode.setValue(value);
        }
    }

    /**
     * Generates a normalized XPath expression pointing to the given node
     * (element or attribute). The XPath expression is relative to the root
     * element.
     */
    public static String getNameForNode(Node node, boolean annotateElement) {

        StringBuffer name = new StringBuffer();
        if (annotateElement)
            XFormsUtils.getInstanceData(node).setGenerated(true);
        while (true) {
            if (node instanceof Element) {
                Element element = (Element) node;
                if (node.getParent() == null) {
                    // We are at the root
                    if (name.length() == 0) {
                        name.append(".");
                    }
                    break;
                } else {
                    // Insert element name
                    if (name.length() > 0) name.insert(0, "/");
                    List siblings = element.getParent().elements();
                    if (siblings.size() > 1) {
                        int position = siblings.indexOf(element);
                        name.insert(0, "[" + (position + 1) + "]");
                    }
                    name.insert(0, XPathUtils.putNamespaceInName(element.getNamespaceURI(), element.getNamespacePrefix(), element.getName(), false));
                    node = element.getParent();
                }
            } else if (node instanceof Attribute) {
                // Insert attribute name
                Attribute attribute = (Attribute) node;
                name.append("@").append(XPathUtils.putNamespaceInName(attribute.getNamespaceURI(), attribute.getNamespacePrefix(), attribute.getName(), true));
                node = attribute.getParent();
            } else {
                throw new OXFException("Only element and attributes can be referenced");
            }
        }
        return name.toString();
    }

    public static InstanceData getInstanceData(Node node) {
        return node instanceof Element
            ? (InstanceData) ((Element) node).getData()
            : node instanceof Attribute
            ? (InstanceData) ((Attribute) node).getData() : null;
    }

    /**
     * Reset all the "generated" flags to false on the instance.
     */
    public static void resetGenerated(Element element) {
        ((InstanceData) element.getData()).setGenerated(false);
        for (Iterator i = element.attributes().iterator(); i.hasNext();) {
            Attribute attribute = (Attribute) i.next();
            if (!attribute.getNamespaceURI().equals(Constants.XXFORMS_NAMESPACE_URI))
                ((InstanceData) attribute.getData()).setGenerated(false);
        }
        for (Iterator i = element.elements().iterator(); i.hasNext();) {
            Element child = (Element) i.next();
            resetGenerated(child);
        }
    }

    /**
     * Recursively decorate the element and its attribute with empty instances
     * of <code>InstanceData</code>.
     */
    public static void setInitialDecoration(Document document) {
        Element rootElement = document.getRootElement();
        Map idToNodeMap = new HashMap();
        setInitialDecorationWorker(rootElement, new int[] {-1}, idToNodeMap);
        ((InstanceData) rootElement.getData()).setIdToNodeMap(idToNodeMap);
    }

    private static void setInitialDecorationWorker(Element element, int[] currentId, Map idToNodeMap) {
        int elementId = ++currentId[0];
        idToNodeMap.put(new Integer(elementId), element);
        element.setData(new InstanceData((LocationData) element.getData(), elementId));
        for (Iterator i = element.attributes().iterator(); i.hasNext();) {
            Attribute attribute = (Attribute) i.next();
            if (!Constants.XXFORMS_NAMESPACE_URI.equals(attribute.getNamespaceURI())) {
                int attributeId = ++currentId[0];
                idToNodeMap.put(new Integer(attributeId), attribute);
                attribute.setData(new InstanceData((LocationData) attribute.getData(), attributeId));
            }
        }
        for (Iterator i = element.elements().iterator(); i.hasNext();) {
            Element child = (Element) i.next();
            setInitialDecorationWorker(child, currentId, idToNodeMap);
        }
    }

    /**
     * Canonicalize a path of the form "a/b/../../c/./d/". The string may start and end with a
     * "/". Occurrences of "..." or other similar patterns are ignored.
     */
    public static String canonicalizeRef(String path) {

        // Parse the path
        Stack elements = new Stack();
        int index = 0;
        int lastIndex = 0;
        for (;;) {
            int slashIndex = path.indexOf('/', index);
            if (slashIndex == -1)
                slashIndex = path.length();
            int braceIndex = path.indexOf('{', index);
            // Skip until after the closing brace if a brace is found
            if (braceIndex != -1 && braceIndex < slashIndex) {
                int closingBraceIndex = path.indexOf('}', braceIndex + 1);
                if (closingBraceIndex == -1)
                    throw new OXFException("Missing closing brace in ref: " + path);
                index = closingBraceIndex + 1;
                continue;
            }
            // A valid "/" was found, or this is the end of the path
            String element = path.substring(lastIndex, slashIndex);
            index = lastIndex = slashIndex + 1;
            if (element.equals("..")) {
                elements.pop();
            } else if (element.equals(".")) {
                ;// Do nothing
            } else if (!"".equals(element) || elements.size() > 0) {
                elements.push(element);
            }
            if (slashIndex == path.length())
                break;
        }

        StringBuffer sb = new StringBuffer();
        int count = 0;
        for (Iterator i = elements.iterator(); i.hasNext(); count++) {
            String s = (String) i.next();
            if (count > 0 || path.startsWith("/"))
                sb.append("/");
            sb.append(s);
        }
        if (path.endsWith("/"))
            sb.append("/");
        return sb.toString();
    }

    public static boolean isNameEncryptionEnabled() {
        return OXFProperties.instance().getPropertySet().getBoolean
            (Constants.XFORMS_ENCRYPT_NAMES, false).booleanValue();
    }

    public static boolean isHiddenEncryptionEnabled() {
        return OXFProperties.instance().getPropertySet().getBoolean
            (Constants.XFORMS_ENCRYPT_HIDDEN, false).booleanValue();
    }

    public static String encrypt(String text) {
        try {
            final Cipher cipher = SecureUtils.getEncryptingCipher
                (OXFProperties.instance().getPropertySet().getString(Constants.XFORMS_PASSWORD));
            return Base64.encode(cipher.doFinal(text.toString().getBytes())).trim();
        } catch (IllegalBlockSizeException e) {
            throw new OXFException(e);
        } catch (BadPaddingException e) {
            throw new OXFException(e);
        }
    }
}
