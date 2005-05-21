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
package org.orbeon.oxf.xforms;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.scope.ScopeStore;
import org.orbeon.oxf.xforms.event.EventTarget;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.List;
import java.util.Map;

/**
 * Represent an XForms instance.
 */
public class XFormsInstance implements EventTarget {

    public static final String REQUEST_INSTANCE_DOCUMENT = "org.orbeon.oxf.request.xforms-instance-document";
    public static final String DEFAULT_UPLOAD_TYPE = "xs:anyURI";

    private PipelineContext pipelineContext;
    private XFormsModel model;
    private Document instanceDocument;

    private DocumentXPathEvaluator documentXPathEvaluator;

    public XFormsInstance(PipelineContext pipelineContext, Document instanceDocument, XFormsModel model) {
        this.pipelineContext = pipelineContext;
        this.model = model;
        setInstanceDocument(instanceDocument);
    }

    /**
     * Return the instance document.
     */
    public Document getDocument() {
        return instanceDocument;
    }

    /**
     * Set the instance document.
     */
    public void setInstanceDocument(Document instanceDocument) {
        this.instanceDocument = instanceDocument;
        this.documentXPathEvaluator = new DocumentXPathEvaluator(instanceDocument);
    }

    /**
     * Set a value on the instance using an element or attribute id.
     *
     * If a type is specified, it means that the Request generator set it, which, for now, means
     * that it was a file upload.
     */
    public void setValueForId(int id, String value, String type) {
        InstanceData rootInstanceData = XFormsUtils.getLocalInstanceData(instanceDocument.getRootElement());
        Node node = (Node) rootInstanceData.getIdToNodeMap().get(new Integer(id));
        if (node instanceof Element) {
            setElementValue((Element) node, value, type);
        } else {
            setAttributeValue((Attribute) node, value);
        }
    }

    /**
     * Set a value on the instance using a node and a value.
     *
     * @param currentNode
     * @param valueToSet
     */
    public static void setValueForNode(Node currentNode, String valueToSet) {
        XFormsUtils.fillNode(currentNode, valueToSet);
    }

    public static String getValueForNode(Node currentNode) {
        if (currentNode instanceof Element) {
            Element elementnode = (Element) currentNode;
            return elementnode.getStringValue();
        } else if (currentNode instanceof Attribute) {
            Attribute attributenode = (Attribute) currentNode;
            return attributenode.getStringValue();
        } else {
            throw new OXFException("Invalid node type: " + currentNode.getNodeTypeName());
        }
    }

    /**
     * Set a value on the instance using an XPath expression pointing to an element or attribute.
     *
     * @param pipelineContext
     * @param refXPath
     * @param prefixToURIMap
     * @param value
     */
    public void setValueForParam(PipelineContext pipelineContext, String refXPath, Map prefixToURIMap, String value) {

        Node node = (Node) evaluateXPathSingle(pipelineContext, instanceDocument, refXPath, prefixToURIMap, null, null, null);
        if (node == null)
            throw new OXFException("Cannot find node instance for param '" + refXPath + "'");

        if (node instanceof Element) {
            setElementValue((Element) node, value, null);
        } else {
            setAttributeValue((Attribute) node, value);
        }
    }

    /**
     * Evaluate an XPath expression on the instance and return its string value.
     */
    public String evaluateXPathAsString(PipelineContext pipelineContext, String xpathExpression, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        return documentXPathEvaluator.evaluateAsString(pipelineContext, xpathExpression, prefixToURIMap,
                variableToValueMap, functionLibrary, baseURI);
    }

    /**
     * Evaluate an XPath expression on the instance.
     */
    public List evaluateXPath(PipelineContext pipelineContext, Node contextNode, String xpathExpression,
                              Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        return documentXPathEvaluator.evaluate(pipelineContext, contextNode, xpathExpression, prefixToURIMap,
                variableToValueMap, functionLibrary, baseURI);
    }

    /**
     * Evaluate an XPath expression on the instance.
     */
    public Object evaluateXPathSingle(PipelineContext pipelineContext, Node contextNode, String xpathExpression,
                              Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        return documentXPathEvaluator.evaluateSingle(pipelineContext, contextNode, xpathExpression, prefixToURIMap,
                variableToValueMap, functionLibrary, baseURI);
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
                element.add(Dom4jUtils.createAttribute(element, XMLConstants.XSI_TYPE_QNAME, DEFAULT_UPLOAD_TYPE));
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
            XFormsUtils.addInstanceAttributes(getDocument());
            LocationSAXWriter saxw = new LocationSAXWriter();
            saxw.setContentHandler(contentHandler);
            saxw.write(instanceDocument);
            XFormsUtils.removeInstanceAttributes(getDocument());

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

    public static XFormsInstance createInstanceFromContext(PipelineContext pipelineContext) {
        ExternalContext.Request request = getRequest(pipelineContext);
        ScopeStore instanceContextStore = (ScopeStore) request.getAttributesMap().get(REQUEST_INSTANCE_DOCUMENT);
        return instanceContextStore == null || instanceContextStore.getSaxStore() == null ? null : new XFormsInstance(pipelineContext, instanceContextStore.getSaxStore().getDocument(), null);
    }

    private static ExternalContext.Request getRequest(PipelineContext context) {
        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (externalContext == null)
            throw new OXFException("Missing external context");
        ExternalContext.Request request = externalContext.getRequest();
        return request;
    }

    public void dispatchEvent(final PipelineContext pipelineContext, XFormsEvent xformsEvent) {
        dispatchEvent(pipelineContext, xformsEvent, xformsEvent.getEventName());
    }

    public void dispatchEvent(PipelineContext pipelineContext, XFormsGenericEvent xformsEvent, String eventName) {
        if (XFormsEvents.XFORMS_INSERT.equals(eventName)) {
            // 4.4.5 The xforms-insert and xforms-delete Events
            // Bubbles: Yes / Cancelable: No / Context Info: Path expression used for insert/delete (xsd:string).
            // The default action for this event results in the following: None; notification event only.

        } else {
            throw new OXFException("Invalid action requested: " + eventName);
        }
    }
}
