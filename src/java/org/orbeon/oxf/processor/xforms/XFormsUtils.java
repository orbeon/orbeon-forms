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

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Visitor;
import org.dom4j.VisitorSupport;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class XFormsUtils {

    /**
     * Adds to <code>target</code> all the attributes in <code>source</code>
     * that are not in the XForms namespace.
     */
    public static void addNonXFormsAttributes(AttributesImpl target, Attributes source) {
        for (Iterator i = new XMLUtils.AttributesIterator(source); i.hasNext();) {
            XMLUtils.Attribute attribute = (XMLUtils.Attribute) i.next();
            if (!"".equals(attribute.getURI()) &&
                    !XFormsConstants.XXFORMS_NAMESPACE_URI.equals(attribute.getURI())) {
                target.addAttribute(attribute.getURI(), attribute.getLocalName(),
                        attribute.getQName(), ContentHandlerHelper.CDATA, attribute.getValue());
            }
        }
    }

    public static void fillNode(Node node, String value) {
        if (node instanceof Element) {
            Element elementnode = (Element) node;
            // Remove current content
            Dom4jUtils.clearElementContent(elementnode);
            // Put text node with value
            elementnode.add(Dom4jUtils.createText(value));
        } else if (node instanceof Attribute) {
            Attribute attributenode = (Attribute) node;
            attributenode.setValue(value);
        }
    }

    public static InstanceData getInstanceData(Node node) {
        return node instanceof Element
            ? (InstanceData) ((Element) node).getData()
            : node instanceof Attribute
            ? (InstanceData) ((Attribute) node).getData() : null;
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

        element.setData(newInstanceData(element.getData(), elementId));

        for (Iterator i = element.attributes().iterator(); i.hasNext();) {
            Attribute attribute = (Attribute) i.next();
            if (!XFormsConstants.XXFORMS_NAMESPACE_URI.equals(attribute.getNamespaceURI())) {
                int attributeId = ++currentId[0];
                idToNodeMap.put(new Integer(attributeId), attribute);
                attribute.setData(newInstanceData(attribute.getData(), attributeId));
            }
        }
        for (Iterator i = element.elements().iterator(); i.hasNext();) {
            Element child = (Element) i.next();
            setInitialDecorationWorker(child, currentId, idToNodeMap);
        }
    }

    private static InstanceData newInstanceData(Object existingData, int id) {
        if (existingData instanceof LocationData) {
            return new InstanceData((LocationData) existingData, id);
        } else if (existingData instanceof InstanceData) {
            return new InstanceData(((InstanceData) existingData).getLocationData(), id);
        } else {
            return new InstanceData(null, id);
        }
    }

    public static boolean isNameEncryptionEnabled() {
        return OXFProperties.instance().getPropertySet().getBoolean
            (XFormsConstants.XFORMS_ENCRYPT_NAMES_PROPERTY, false).booleanValue();
    }

    public static boolean isHiddenEncryptionEnabled() {
        return OXFProperties.instance().getPropertySet().getBoolean
            (XFormsConstants.XFORMS_ENCRYPT_HIDDEN_PROPERTY, false).booleanValue();
    }

    public static String instanceToString(PipelineContext pipelineContext, String password, Document instance) {
        removeXXFormsAttributes(instance);
        try {
            ByteArrayOutputStream gzipByteArray = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutputStream = null;
            gzipOutputStream = new GZIPOutputStream(gzipByteArray);
            gzipOutputStream.write(Dom4jUtils.domToString(instance).getBytes());
            gzipOutputStream.close();
            String compressed = Base64.encode(gzipByteArray.toByteArray());
            return XFormsUtils.isHiddenEncryptionEnabled()
                ? SecureUtils.encrypt(pipelineContext, password, compressed)
                : compressed;
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static void removeXXFormsAttributes(Document doc) {
        Visitor visitor = new VisitorSupport() {
            public void visit(Element node) {
                List newAttributes = new ArrayList();
                for(Iterator i = node.attributeIterator(); i.hasNext();) {
                    Attribute attr = (Attribute)i.next();
                    if(!XFormsConstants.XXFORMS_NAMESPACE_URI.equals(attr.getNamespaceURI()))
                        newAttributes.add(attr);

                }
                node.setAttributes(newAttributes);
            }
        };
        doc.accept(visitor);
    }

    /**
     * Iterate through all data nodes of the instance document and call the walker on each of them.
     *
     * @param instanceDocument
     * @param instanceWalker
     */
    public static void updateInstanceData(Document instanceDocument, InstanceWalker instanceWalker) {
        updateInstanceData(instanceDocument.getRootElement(), instanceWalker);
    }

    private static void updateInstanceData(Element element, InstanceWalker instanceWalker) {
        instanceWalker.walk(element, (InstanceData) element.getData());
        for (Iterator i = element.attributes().iterator(); i.hasNext();) {
            Attribute attribute = (Attribute) i.next();
            instanceWalker.walk(attribute,  (InstanceData) element.getData());
        }
        for (Iterator i = element.elements().iterator(); i.hasNext();) {
            Element child = (Element) i.next();
            updateInstanceData(child, instanceWalker);
        }
    }

    public static interface InstanceWalker {
        public void walk(Node node, InstanceData instanceData);
    }
}
