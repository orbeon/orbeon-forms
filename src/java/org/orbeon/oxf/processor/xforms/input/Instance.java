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
package org.orbeon.oxf.processor.xforms.input;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.util.UserDataDocumentFactory;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.XMLConstants;
import org.orbeon.oxf.processor.scope.ScopeStore;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Map;

public class Instance {

    public static final String REQUEST_INSTANCE_DOCUMENT = "org.orbeon.oxf.request.xforms-instance-document";
    public static final String DEFAULT_UPLOAD_TYPE = "xs:anyURI";

    private PipelineContext pipelineContext;
    private Document instance;
    private NodeInfo instanceNodeInfo;

    public Instance(PipelineContext pipelineContext, Document template) {
        this.pipelineContext = pipelineContext;
        instance = XMLUtils.createDOM4JDocument();
        instance.add(template.getRootElement().createCopy());
        instanceNodeInfo = new DocumentWrapper(instance, null).wrap(instance);

    }

    public Document getDocument() {
        return instance;
    }

    public void setDocument(Document instance) {
        this.instance = instance;
    }

    /**
     * Set a value on the instance.
     *
     * If a type is specified, it means that the Request generator set it, which, for now, means
     * that it was a file upload.
     */
    public void setValueForId(int id, String value, String type) {
        InstanceData rootInstanceData = XFormsUtils.getInstanceData(instance.getRootElement());
        Node node = (Node) rootInstanceData.getIdToNodeMap().get(new Integer(id));
        if (node instanceof Element) {
            setElementValue((Element) node, value, type);
        } else {
            setAttributeValue((Attribute) node, value);
        }
    }

    public void setValueForParam(PipelineContext pipelineContext, String xpath, Map prefixToURIMap, String value) {
        PooledXPathExpression xpathExpression =
                XPathCache.getXPathExpression(pipelineContext, instanceNodeInfo, xpath, prefixToURIMap);
        try {
            Node node = (Node) xpathExpression.evaluateSingle();
            if (node == null)
                throw new OXFException("Cannot find node instance for param '" + xpath + "'");
            if (node instanceof Element) {
                setElementValue((Element) node, value, null);
            } else {
                setAttributeValue((Attribute) node, value);
            }
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
        }
    }

    private void setAttributeValue(Attribute attribute, String value) {
        // Handle xsi:type if needed
        if (XMLConstants.XSI_TYPE_QNAME.getNamespaceURI().equals(attribute.getNamespaceURI()) && !"".equals(attribute.getParent().getText())) {
            // This is a type attribute and we already have content
            String currentType = attribute.getParent().attributeValue(XMLConstants.XSI_TYPE_QNAME);
            if (currentType != null && !currentType.equals(value)) { // FIXME: prefixes of type name could be different!
                // Convert element value
                String newValue = convertUploadTypes(attribute.getParent().getText(), currentType, value);
                attribute.getParent().clearContent();
                attribute.getParent().addText(newValue);
            }
        }

        attribute.setValue(value);
    }

    private void setElementValue(Element element, String value, String type) {
        // Don't do anything if value exists and dontSetIfexisting is true
        if (type != null) {
            // Handle value type
            String currentType = element.attributeValue(XMLConstants.XSI_TYPE_QNAME);
            if (currentType != null && !currentType.equals(type)) { // FIXME: prefixes of type name could be different!
                // There is a different type already, do a conversion
                value = convertUploadTypes(value, type, currentType);
                Dom4jUtils.clearElementContent(element);
            } else if (currentType == null) {
                // There is no type, convert to default type
                if (!DEFAULT_UPLOAD_TYPE.equals(type)) // FIXME: prefixes of type name could be different!
                    value = convertUploadTypes(value, type, DEFAULT_UPLOAD_TYPE);
                element.add(UserDataDocumentFactory.getInstance().createAttribute(element, XMLConstants.XSI_TYPE_QNAME, DEFAULT_UPLOAD_TYPE));
            }
            element.setText(value);
        } else {
            // No type, just set the value
            element.setText(value);
        }
    }

    /**
     * Output the instance to the specified content handler
     */
    public void read(ContentHandler contentHandler) {
        try {
            LocationSAXWriter saxw = new LocationSAXWriter();
            saxw.setContentHandler(contentHandler);
            saxw.write(instance);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    private String convertUploadTypes(String value, String currentType, String newType) {
        if (currentType.equals("newType"))
            return value;
        if (ProcessorUtils.supportedBinaryTypes.get(currentType) == null)
            throw new UnsupportedOperationException("Unsupported type: " + currentType);
        if (ProcessorUtils.supportedBinaryTypes.get(newType) == null)
            throw new UnsupportedOperationException("Unsupported type: " + newType);

        if (currentType.equals(XMLConstants.XS_BASE64BINARY_QNAME.getQualifiedName())) {
            // Convert from xs:base64Binary to xs:anyURI
            return XMLUtils.base64BinaryToAnyURI(pipelineContext, value); 
        } else {
            // Convert from xs:anyURI to xs:base64Binary
            return XMLUtils.anyURIToBase64Binary(value);
        }
    }

    public static Instance createInstanceFromContext(PipelineContext pipelineContext) {
        ExternalContext.Request request = getRequest(pipelineContext);
        ScopeStore instanceContextStore = (ScopeStore) request.getAttributesMap().get(REQUEST_INSTANCE_DOCUMENT);
        return instanceContextStore == null || instanceContextStore.getSaxStore() == null ? null : new Instance(pipelineContext, instanceContextStore.getSaxStore().getDocument());
    }

    private static ExternalContext.Request getRequest(PipelineContext context) {
        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (externalContext == null)
            throw new OXFException("Missing external context");
        ExternalContext.Request request = externalContext.getRequest();
        return request;
    }
}
