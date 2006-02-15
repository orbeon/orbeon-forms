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
import org.orbeon.oxf.processor.scope.ScopeStore;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.util.List;
import java.util.Map;

/**
 * Represent an XForms instance.
 */
public class XFormsInstance implements XFormsEventTarget {

    public static final String REQUEST_FORWARD_INSTANCE_DOCUMENT = "org.orbeon.oxf.request.forward-xforms-instance-document";
    public static final String REQUEST_PORTAL_INSTANCE_DOCUMENT = "org.orbeon.oxf.request.xforms-instance-document";

    private PipelineContext pipelineContext;
    private String id;
    private XFormsModel model;
    private Document instanceDocument;

    private DocumentXPathEvaluator documentXPathEvaluator;

    public XFormsInstance(PipelineContext pipelineContext, String id, Document instanceDocument, XFormsModel model) {
        this.pipelineContext = pipelineContext;
        this.id = id;
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
        XFormsUtils.setInitialDecoration(instanceDocument);
        this.instanceDocument = instanceDocument;
        this.documentXPathEvaluator = new DocumentXPathEvaluator(instanceDocument);
    }

    /**
     * Set a value on the instance using an element or attribute id.
     *
     * If a type is specified, it means that the Request generator set it, which, for now, means
     * that it was a file upload.
     *
     * @deprecated legacy XForms engine
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
     * @param pipelineContext   current PipelineContext
     * @param node              element or attribute node to update
     * @param newValue          value to set
     * @param type              type of the value to set (xs:anyURI or xs:base64Binary), null if none
     */
    public static void setValueForNode(PipelineContext pipelineContext, Node node, String newValue, String type) {
        // Get local instance data as we are not using any inheritance here AND we are doing an update
        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(node);

        // Convert value based on types if possible
        if (type != null) {
            final String nodeType = instanceData.getType().getAsString();

            if (nodeType != null && !nodeType.equals(type)) { // FIXME: prefixes of type name could be different!
                // There is a different type already, do a conversion
                newValue = XFormsUtils.convertUploadTypes(pipelineContext, newValue, type, nodeType);
            } else if (nodeType == null) {
                // There is no type, convert to default type
                if (!XFormsUtils.DEFAULT_UPLOAD_TYPE.equals(type)) // FIXME: prefixes of type name could be different!
                    newValue = XFormsUtils.convertUploadTypes(pipelineContext, newValue, type, XFormsUtils.DEFAULT_UPLOAD_TYPE);
            }
        }

        // Set value
        final String currentValue;
        if (node instanceof Element) {
            final Element elementnode = (Element) node;
            currentValue = elementnode.getText();
            // Remove current content
            Dom4jUtils.clearElementContent(elementnode);
            // Put text node with value
            elementnode.add(Dom4jUtils.createText(newValue));
        } else if (node instanceof Attribute) {
            final Attribute attributenode = (Attribute) node;
            currentValue = attributenode.getValue();
            attributenode.setValue(newValue);
        } else {
            throw new OXFException("Node is not an element or attribute.");
        }

        // Remember that the value has changed for this node if it has actually changed
        if (!newValue.equals(currentValue))
            instanceData.markValueChanged();
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
     *
     * @deprecated legacy XForms engine
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
    public String evaluateXPathAsString(PipelineContext pipelineContext, List contextNodeSet, int contextPosition, String xpathExpression, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        return documentXPathEvaluator.evaluateAsString(pipelineContext, contextNodeSet, contextPosition, xpathExpression, prefixToURIMap,
                variableToValueMap, functionLibrary, baseURI);
    }

    /**
     * Evaluate an XPath expression on the instance and return its string value.
     */
    public String evaluateXPathAsString(PipelineContext pipelineContext, Node contextNode, String xpathExpression, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        return documentXPathEvaluator.evaluateAsString(pipelineContext, contextNode, xpathExpression, prefixToURIMap,
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
                String newValue = XFormsUtils.convertUploadTypes(pipelineContext, attribute.getParent().getText(), currentType, value);
                attribute.getParent().clearContent();
                attribute.getParent().addText(newValue);
            }
        }

        attribute.setValue(value);
    }

    /**
     * @deprecated legacy XForms engine
     */
    private void setElementValue(Element element, String value, String type) {
        // Don't do anything if value exists and dontSetIfexisting is true
        if (type != null) {
            // Handle value type
            final String currentType
                    = Dom4jUtils.qNameToexplodedQName(Dom4jUtils.extractAttributeValueQName(element, XMLConstants.XSI_TYPE_QNAME));
            if (currentType != null && !currentType.equals(type)) { // FIXME: prefixes of type name could be different!
                // There is a different type already, do a conversion
                value = XFormsUtils.convertUploadTypes(pipelineContext, value, type, currentType);
                Dom4jUtils.clearElementContent(element);
            } else if (currentType == null) {
                // There is no type, convert to default type
                if (!XFormsUtils.DEFAULT_UPLOAD_TYPE.equals(type)) // FIXME: prefixes of type name could be different!
                    value = XFormsUtils.convertUploadTypes(pipelineContext, value, type, XFormsUtils.DEFAULT_UPLOAD_TYPE);
                element.add(Dom4jUtils.createAttribute(element, XMLConstants.XSI_TYPE_QNAME, XFormsUtils.DEFAULT_UPLOAD_TYPE));
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

    /**
     * This prints the instance with extra annotation attributes to System.out. For debug only.
     */
    public void readOut() {
        final TransformerHandler  th = TransformerUtils.getIdentityTransformerHandler();
        th.setResult(new StreamResult(System.out));
        read(th);
    }

    public static XFormsInstance createInstanceFromContext(PipelineContext pipelineContext) {
        ExternalContext.Request request = getRequest(pipelineContext);
        ScopeStore instanceContextStore = (ScopeStore) request.getAttributesMap().get(REQUEST_FORWARD_INSTANCE_DOCUMENT);
        return instanceContextStore == null || instanceContextStore.getSaxStore() == null ? null : new XFormsInstance(pipelineContext, null, instanceContextStore.getSaxStore().getDocument(), null);
    }

    private static ExternalContext.Request getRequest(PipelineContext context) {
        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (externalContext == null)
            throw new OXFException("Missing external context");
        return externalContext.getRequest();
    }

    /**
     * Return the id of this instance.
     */
    public String getId() {
        return id;
    }

    public LocationData getLocationData() {
        return (LocationData) instanceDocument.getRootElement().getData();
    }

    public XFormsEventHandlerContainer getParentContainer() {
        return model;
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        // NOP
    }

    /**
     * Return a dom4j node wrapped into a NodeInfo. The node must belong to this particular
     * instance.
     */
    public NodeInfo wrapNode(Node node) {
        return documentXPathEvaluator.wrapNode(node);
    }
}
