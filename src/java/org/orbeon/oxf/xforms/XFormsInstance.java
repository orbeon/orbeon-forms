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
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.VirtualNode;
import org.xml.sax.ContentHandler;

import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

/**
 * Represent an XForms instance.
 */
public class XFormsInstance implements XFormsEventTarget {

    public static final String REQUEST_PORTAL_INSTANCE_DOCUMENT = "org.orbeon.oxf.request.xforms-instance-document";

    private DocumentInfo instanceDocumentInfo;

    private String instanceId;
    private String modelId;
    private boolean isReadonly;
    private String sourceURI;
    private String username;
    private String password;

    /**
     * Create an XFormsInstance from a container element. The container contains meta-informationa about the instance,
     * such as id, username, URI, etc.
     *
     * <instance readonly="false" id="instance-id" source-uri="http://..." username="jdoe" password="password">
     *     x7wer...
     * </instance
     *
     * @param containerElement  container element
     */
    public XFormsInstance(Element containerElement) {

        this.instanceId = containerElement.attributeValue("id");
        this.modelId = containerElement.attributeValue("model-id");
        this.isReadonly = "true".equals(containerElement.attributeValue("readonly"));
        this.sourceURI = containerElement.attributeValue("source-uri");
        this.username = containerElement.attributeValue("username");
        this.password = containerElement.attributeValue("password");

        // Create and set instance document on current model
        final DocumentInfo documentInfo;
        if (containerElement.elements().size() == 0) {
            // New serialization (use serialized XML)
            try {
                final String xmlString = containerElement.getStringValue();
                if (!isReadonly)
                    documentInfo = new DocumentWrapper(Dom4jUtils.normalizeTextNodes(Dom4jUtils.readDom4j(xmlString)), null, new Configuration());
                else
                    documentInfo = TransformerUtils.readTinyTree(new StreamSource(new StringReader(xmlString)));
            } catch (Exception e) {
                throw new OXFException(e);
            }
        } else {
            // Old serialization (instance is directly in the DOM)
            final Document instanceDocument = Dom4jUtils.createDocumentCopyParentNamespaces((Element) containerElement.elements().get(0));
            documentInfo = new DocumentWrapper(Dom4jUtils.normalizeTextNodes(instanceDocument), null, new Configuration());
        }

        setInstanceDocumentInfo(documentInfo, true);
    }

    public XFormsInstance(String modelId, String instanceId, Document instanceDocument, String instanceSourceURI, String username, String password) {
        // We normalize the Document before setting it, so that text nodes follow the XPath constraints
        this(modelId, instanceId, new DocumentWrapper(Dom4jUtils.normalizeTextNodes(instanceDocument), null, new Configuration()), instanceSourceURI, username, password);
    }

    public XFormsInstance(String modelId, String instanceId, DocumentInfo instanceDocumentInfo, String instanceSourceURI, String username, String password) {
        this.instanceId = instanceId;
        this.modelId = modelId;
        this.isReadonly = !(instanceDocumentInfo instanceof DocumentWrapper);
        this.sourceURI = instanceSourceURI;
        this.username = username;
        this.password = password;

        setInstanceDocumentInfo(instanceDocumentInfo, true);
    }

    /**
     * Serialize the instance into a containing Element with meta-information.
     *
     * @return      containing Element
     */
    public Element createContainerElement() {

        // DocumentInfo may wrap an actual TinyTree or a dom4j document
        final String instanceString = TransformerUtils.tinyTreeToString(getInstanceDocumentInfo());
        final Element instanceElement = Dom4jUtils.createElement("instance");
        if (isReadonly)
            instanceElement.addAttribute("readonly", "true");

        instanceElement.addAttribute("id", instanceId);
        instanceElement.addAttribute("model-id", modelId);
        if (sourceURI != null)
            instanceElement.addAttribute("source-uri", sourceURI);
        if (username != null)
            instanceElement.addAttribute("username", username);
        if (password != null)
            instanceElement.addAttribute("password", password);
        
        instanceElement.addText(instanceString);

        return instanceElement;
    }

    /**
     * Return the model that contains this instance.
     */
    public XFormsModel getModel(XFormsContainingDocument containingDocument) {
        return containingDocument.getModel(modelId);
    }

    /**
     * Return the instance DocumentInfo.
     *
     * @return  instance DocumentInfo
     */
    public DocumentInfo getInstanceDocumentInfo() {
        return instanceDocumentInfo;
    }

    /**
     * Return the id of this instance.
     */
    public String getEffectiveId() {
        return instanceId;
    }

    public String getModelId() {
        return modelId;
    }

    public boolean isReadOnly() {
        return !(instanceDocumentInfo instanceof VirtualNode);
    }

    public NodeInfo getInstanceRootElementInfo() {
        return (NodeInfo) XFormsUtils.getChildrenElements(instanceDocumentInfo).get(0);
    }

    public String getSourceURI() {
        return sourceURI;
    }

    public boolean isHasUsername() {
        return username != null;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Set the instance document.
     *
     * @param instanceDocument  the Document or DocumentInfo to use
     * @param initialize        true if initial decoration (MIPs) has to be reset
     */
    public void setInstanceDocument(Object instanceDocument, boolean initialize) {
        if (instanceDocument instanceof Document)
            setInstanceDocument((Document) instanceDocument, initialize);
        else
            setInstanceDocumentInfo((DocumentInfo) instanceDocument, initialize);
    }

    /**
     * Set the instance document.
     *
     * @param instanceDocument  the Document to use
     * @param initialize        true if initial decoration (MIPs) has to be reset
     */
    public void setInstanceDocument(Document instanceDocument, boolean initialize) {
        setInstanceDocumentInfo(new DocumentWrapper(Dom4jUtils.normalizeTextNodes(instanceDocument), null, new Configuration()), initialize);
    }

    /**
     * Set the instance document.
     *
     * @param instanceDocumentInfo  the DocumentInfo to use
     * @param initialize            true if initial decoration (MIPs) has to be reset
     */
    private void setInstanceDocumentInfo(DocumentInfo instanceDocumentInfo, boolean initialize) {
        this.instanceDocumentInfo = instanceDocumentInfo;

        if (initialize && instanceDocumentInfo instanceof DocumentWrapper) {
            // Only set annotations on Document
            final DocumentWrapper documentWrapper = (DocumentWrapper) instanceDocumentInfo;
            XFormsUtils.setInitialDecoration((Document) documentWrapper.getUnderlyingNode());
        }
    }

    public void synchronizeInstanceDataEventState() {
        XFormsUtils.iterateInstanceData(this, new XFormsUtils.InstanceWalker() {
            public void walk(NodeInfo nodeInfo, InstanceData updatedInstanceData) {
                if (updatedInstanceData != null) {
                    updatedInstanceData.clearInstanceDataEventState();
                }
            }
        }, true);
    }

    /**
     * Set a value on the instance using a NodeInfo and a value.
     *
     * @param pipelineContext   current PipelineContext
     * @param nodeInfo          element or attribute NodeInfo to update
     * @param newValue          value to set
     * @param type              type of the value to set (xs:anyURI or xs:base64Binary), null if none
     */
    public static void setValueForNodeInfo(PipelineContext pipelineContext, NodeInfo nodeInfo, String newValue, String type) {
        if (!(nodeInfo instanceof NodeWrapper))
            throw new OXFException("Unable to set value of read-only instance.");

        final Node node = (Node) ((NodeWrapper) nodeInfo).getUnderlyingNode();
        setValueForNode(pipelineContext, node, newValue, type);
    }

    /**
     * Set a value on the instance using a Node and a value.
     *
     * @param pipelineContext   current PipelineContext
     * @param node              element or attribute Node to update
     * @param newValue          value to set
     * @param type              type of the value to set (xs:anyURI or xs:base64Binary), null if none
     */
    public static void setValueForNode(PipelineContext pipelineContext, Node node, String newValue, String type) {

        // Get local instance data as we are not using any inheritance here AND we are doing an update
        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(node);

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

    public static String getValueForNodeInfo(NodeInfo currentNode) {

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

    public LocationData getLocationData() {
        if (instanceDocumentInfo instanceof DocumentWrapper) {
            final Document document = getInstanceDocument();
            return XFormsUtils.getNodeLocationData(document.getRootElement());
        } else {
            return new LocationData(instanceDocumentInfo.getSystemId(), instanceDocumentInfo.getLineNumber(), -1);
        }
    }

    public XFormsEventHandlerContainer getParentContainer(XFormsContainingDocument containingDocument) {
        return getModel(containingDocument);
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        // NOP
    }


    /**
     * Return the instance document.
     *
     * @deprecated should use getInstanceDocumentInfo()
     */
    public Document getInstanceDocument() {
        if (instanceDocumentInfo instanceof DocumentWrapper) {
            final DocumentWrapper documentWrapper = (DocumentWrapper) instanceDocumentInfo;
            return (Document) documentWrapper.getUnderlyingNode();
        } else {
            return null;
        }
    }
}
