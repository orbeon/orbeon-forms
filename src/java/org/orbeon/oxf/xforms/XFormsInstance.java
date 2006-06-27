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

import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.*;
import org.xml.sax.ContentHandler;

import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.util.Map;

/**
 * Represent an XForms instance.
 */
public class XFormsInstance implements XFormsEventTarget {

    public static final String REQUEST_PORTAL_INSTANCE_DOCUMENT = "org.orbeon.oxf.request.xforms-instance-document";

    private PipelineContext pipelineContext;
    private String id;
    private String instanceSourceURI;
    private boolean hasUsername;
    private XFormsModel model;
    private DocumentInfo instanceDocumentInfo;

    public XFormsInstance(PipelineContext pipelineContext, String id, Document instanceDocument, String instanceSourceURI, boolean hasUsername, XFormsModel model) {
        // NOTE: We normalize the Document before setting it, so that text nodes follow the XPath constraints
        this(pipelineContext, id, new DocumentWrapper(Dom4jUtils.normalizeTextNodes(instanceDocument), null, new Configuration()), instanceSourceURI, hasUsername, model);
    }

    public XFormsInstance(PipelineContext pipelineContext, String id, DocumentInfo instanceDocumentInfo, String instanceSourceURI, boolean hasUsername, XFormsModel model) {
        this.pipelineContext = pipelineContext;
        this.id = id;
        this.instanceSourceURI = instanceSourceURI;
        this.hasUsername = hasUsername;
        this.model = model;
        setInstanceDocumentInfo(instanceDocumentInfo, true);
    }

    public DocumentXPathEvaluator getEvaluator() {
        return model.getEvaluator();
    }

    /**
     * Return the instance document.
     */
    public Document getInstanceDocument() {
        if (instanceDocumentInfo instanceof DocumentWrapper) {
            final DocumentWrapper documentWrapper = (DocumentWrapper) instanceDocumentInfo;
            return (Document) documentWrapper.getUnderlyingNode();
        } else {
            return null;
        }
    }

    public DocumentInfo getInstanceDocumentInfo() {
        return instanceDocumentInfo;
    }

    public boolean isReadOnly() {
        return !(instanceDocumentInfo instanceof VirtualNode);
    }

    public NodeInfo getInstanceRootElementInfo() {
        return (NodeInfo) XFormsUtils.getChildrenElements(instanceDocumentInfo).get(0);
    }

    public String getInstanceSourceURI() {
        return instanceSourceURI;
    }

    public boolean isHasUsername() {
        return hasUsername;
    }

    /**
     * Set the instance document.
     *
     * @param instanceDocument  the Document to use
     * @param initialize        true if initial decoration (MIPs) has to be reset
     */
    public void setInstanceDocument(Document instanceDocument, boolean initialize) {
        setInstanceDocumentInfo(new DocumentWrapper(instanceDocument, null, new Configuration()), initialize);
    }

    public void setInstanceDocumentInfo(DocumentInfo instanceDocumentInfo, boolean initialize) {
        if (instanceDocumentInfo instanceof DocumentWrapper) {
            // Only set annotations on Document
            final DocumentWrapper documentWrapper = (DocumentWrapper) instanceDocumentInfo;

            if (initialize)
                XFormsUtils.setInitialDecoration((Document) documentWrapper.getUnderlyingNode());
        }

        this.instanceDocumentInfo = instanceDocumentInfo;
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
        final Node node = (Node) XFormsUtils.getIdToNodeMap(instanceDocumentInfo).get(new Integer(id));
        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(node);
        setValueForNode(pipelineContext, node, value, type, instanceData);
    }

    public static void setValueForNodeInfo(PipelineContext pipelineContext, NodeInfo nodeInfo, String newValue, String type) {
        if (!(nodeInfo instanceof NodeWrapper))
            throw new OXFException("Unable to set value of read-only instance.");

        // Get local instance data as we are not using any inheritance here AND we are doing an update
        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(nodeInfo);

        final Node node = (Node) ((NodeWrapper) nodeInfo).getUnderlyingNode();

        setValueForNode(pipelineContext, node, newValue, type, instanceData);
    }

    /**
     * Set a value on the instance using a node and a value.
     *
     * @param pipelineContext   current PipelineContext
     * @param node              element or attribute node to update
     * @param newValue          value to set
     * @param type              type of the value to set (xs:anyURI or xs:base64Binary), null if none
     */
    public static void setValueForNode(PipelineContext pipelineContext, Node node, String newValue, String type, InstanceData instanceData) {

        // Convert value based on types if possible
        if (type != null) {
            final String nodeType = instanceData.getType().getAsString();

            if (nodeType != null && !nodeType.equals(type)) {
                // There is a different type already, do a conversion
                newValue = XFormsUtils.convertUploadTypes(pipelineContext, newValue, type, nodeType);
            } else if (nodeType == null) {
                // There is no type, convert to default type
                if (!XFormsConstants.DEFAULT_UPLOAD_TYPE_EXPLODED_QNAME.equals(type))
                    newValue = XFormsUtils.convertUploadTypes(pipelineContext, newValue, type, XFormsConstants.DEFAULT_UPLOAD_TYPE_EXPLODED_QNAME);
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

    public static String getValueForNode(NodeInfo currentNode) {

        if (currentNode.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE) {
            // Return the value of the first text node if any
            return XFormsUtils.getFirstTextNodeValue(currentNode);
        } else if (currentNode.getNodeKind() == org.w3c.dom.Document.ATTRIBUTE_NODE) {
            return currentNode.getStringValue();
        } else if (currentNode.getNodeKind() == org.w3c.dom.Document.TEXT_NODE) {
            return currentNode.getStringValue();
        } else {
            throw new OXFException("Invalid node type: " + currentNode.getNodeKind());
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

        final Object o = getEvaluator().evaluateSingle(pipelineContext, getInstanceDocumentInfo(), refXPath, prefixToURIMap, null, null, null);
        if (o == null || !(o instanceof NodeInfo))
            throw new OXFException("Cannot find node instance for param '" + refXPath + "'");

        setNodeInfoValue((NodeInfo) o, value, null);
    }

    private void setNodeInfoValue(NodeInfo nodeInfo, String value, String type) {
        if (!(nodeInfo instanceof NodeWrapper))
            throw new OXFException("Cannot set node value on read-only DOM.");

        final NodeWrapper nodeWrapper = (NodeWrapper) nodeInfo;
        final Object o = nodeWrapper.getUnderlyingNode();

         if (o instanceof Element) {
            setElementValue((Element) o, value, type);
        } else if (o instanceof Attribute) {
            setAttributeValue((Attribute) o, value);
        } else {
             throw new OXFException("Invalid node class: " + o.getClass().getName());
         }
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
            if (currentType != null && !currentType.equals(type)) {
                // There is a different type already, do a conversion
                value = XFormsUtils.convertUploadTypes(pipelineContext, value, type, currentType);
                Dom4jUtils.clearElementContent(element);
            } else if (currentType == null) {
                // There is no type, convert to default type
                if (!XFormsConstants.DEFAULT_UPLOAD_TYPE_EXPLODED_QNAME.equals(type))
                    value = XFormsUtils.convertUploadTypes(pipelineContext, value, type, XFormsConstants.DEFAULT_UPLOAD_TYPE_EXPLODED_QNAME);
                element.add(Dom4jUtils.createAttribute(element, XMLConstants.XSI_TYPE_QNAME, XFormsConstants.DEFAULT_UPLOAD_TYPE_QNAME.getQualifiedName()));
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
            if (instanceDocumentInfo instanceof DocumentWrapper) {
                XFormsUtils.addInstanceAttributes(getInstanceRootElementInfo());
            }

            final Transformer identity = TransformerUtils.getIdentityTransformer();
            identity.transform(instanceDocumentInfo, new SAXResult(contentHandler));

//            LocationSAXWriter saxw = new LocationSAXWriter();
//            saxw.setContentHandler(contentHandler);
//            saxw.write(instanceDocument);

            if (instanceDocumentInfo instanceof DocumentWrapper) {
                XFormsUtils.removeInstanceAttributes(getInstanceRootElementInfo());
            }

        } catch (Exception e) {
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

    /**
     * This allows dumping all the current MIPs applying to this instance.
     */
    public void logMIPs() {

        final Document result = Dom4jUtils.createDocument();

        getInstanceDocument().accept(new VisitorSupport() {

            private Element rootElement = result.addElement("mips");
            private Element currentElement;

            public final void visit(Element element) {
                currentElement = rootElement.addElement("element");
                currentElement.addAttribute("qname", element.getQualifiedName());
                currentElement.addAttribute("namespace-uri", element.getNamespaceURI());

                addMIPInfo(currentElement, element);
            }

            public final void visit(Attribute attribute) {
                final Element attributeEement = currentElement.addElement("attribute");
                attributeEement.addAttribute("qname", attribute.getQualifiedName());
                attributeEement.addAttribute("namespace-uri", attribute.getNamespaceURI());
                addMIPInfo(attributeEement, attribute);
            }

            private final void addMIPInfo(Element parentInfoElement, Node node) {
                final InstanceData instanceData = XFormsUtils.getInstanceDataUpdateInherited(node);
                parentInfoElement.addAttribute("readonly", Boolean.toString(instanceData.getInheritedReadonly().get()));
                parentInfoElement.addAttribute("relevant", Boolean.toString(instanceData.getInheritedRelevant().get()));
                parentInfoElement.addAttribute("required", Boolean.toString(instanceData.getRequired().get()));
                parentInfoElement.addAttribute("valid", Boolean.toString(instanceData.getValid().get()));
                final String typeAsString = instanceData.getType().getAsString();
                parentInfoElement.addAttribute("type", (typeAsString == null) ? "" : typeAsString);
//                parentInfoElement.addAttribute("schema-error-messages", instanceData.getSchemaErrorsMsgs());
            }
        });

        XFormsUtils.logDebugDocument("MIPs: ", result);
    }

    /**
     * Return the id of this instance.
     */
    public String getId() {
        return id;
    }

    public LocationData getLocationData() {
        if (instanceDocumentInfo instanceof DocumentWrapper) {
            final Document document = getInstanceDocument();
            return XFormsUtils.getNodeLocationData(document.getRootElement());
        } else {
            return new LocationData(instanceDocumentInfo.getSystemId(), instanceDocumentInfo.getLineNumber(), -1);
        }
    }

    public XFormsEventHandlerContainer getParentContainer() {
        return model;
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        // NOP
    }
}
