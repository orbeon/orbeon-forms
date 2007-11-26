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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.VirtualNode;
import org.xml.sax.ContentHandler;
import org.apache.log4j.Logger;

import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.List;

/**
 * Represent an XForms instance.
 */
public class XFormsInstance implements XFormsEventTarget, XFormsEventHandlerContainer {

    static public Logger logger = LoggerFactory.createLogger(XFormsInstance.class);

    private DocumentInfo documentInfo;

    private String instanceId;
    private String modelId;

    private String sourceURI;

    private boolean readonly;
    private boolean applicationShared;
    private long timeToLive;
    private String username;
    private String password;
    private String validation;

    /**
     * Whether the instance was ever replaced. This is useful so that we know whether we can use an instance from the
     * static state or not: if it was ever replaced, then we can't use instance information from the static state.
     */
    private boolean replaced;

    /**
     * Create an XFormsInstance from a container element. The container contains meta-informationa about the instance,
     * such as id, username, URI, etc.
     *
     * <instance readonly="true" shared="application" id="instance-id" model-id="model-id" source-uri="http://..." username="jdoe" password="password">
     *     x7wer...
     * </instance>
     *
     * The instance document may not have been set after this is completed, in case the Element did not contained a
     * serialized document.
     *
     * @param containerElement  container element
     */
    public XFormsInstance(Element containerElement) {

        this.instanceId = containerElement.attributeValue("id");
        this.modelId = containerElement.attributeValue("model-id");

        this.sourceURI = containerElement.attributeValue("source-uri");

        this.readonly = "true".equals(containerElement.attributeValue("readonly"));
        this.applicationShared = "application".equals(containerElement.attributeValue("shared"));
        final String timeToLiveAttribute = containerElement.attributeValue("ttl");
        this.timeToLive = (timeToLiveAttribute != null) ? Long.parseLong(timeToLiveAttribute) : -1;

        this.username = containerElement.attributeValue("username");
        this.password = containerElement.attributeValue("password");
        this.validation = containerElement.attributeValue("validation");

        this.replaced = "true".equals(containerElement.attributeValue("replaced"));

        // Create and set instance document on current model
        final DocumentInfo documentInfo;
        if (containerElement.elements().size() == 0) {
            // New serialization (use serialized XML)
            try {
                final String xmlString = containerElement.getStringValue();
                if (!readonly) {
                    documentInfo = new DocumentWrapper((Document) Dom4jUtils.normalizeTextNodes(Dom4jUtils.readDom4j(xmlString)), null, new Configuration());
                } else {

                    if (xmlString.length() > 0) {
                        // Instance document is available in serialized form
                        documentInfo = TransformerUtils.readTinyTree(new StreamSource(new StringReader(xmlString)));
                    } else {
                        // Instance document is not available, defer to later initialization
                        documentInfo = null;
                    }
                }
            } catch (Exception e) {
                throw new OXFException(e);
            }
        } else {
            // Old serialization (instance is directly in the DOM)
            final Document instanceDocument = Dom4jUtils.createDocumentCopyParentNamespaces((Element) containerElement.elements().get(0));
            documentInfo = new DocumentWrapper((Document) Dom4jUtils.normalizeTextNodes(instanceDocument), null, new Configuration());
        }

        this.documentInfo = documentInfo;
    }

    public XFormsInstance(String modelId, String instanceId, Document instanceDocument, String instanceSourceURI, String username, String password, boolean applicationShared, long timeToLive, String validation) {
        // We normalize the Document before setting it, so that text nodes follow the XPath constraints
        this(modelId, instanceId, new DocumentWrapper((Document) Dom4jUtils.normalizeTextNodes(instanceDocument), null, new Configuration()), instanceSourceURI, username, password, applicationShared, timeToLive, validation);
    }

    protected XFormsInstance(String modelId, String instanceId, DocumentInfo instanceDocumentInfo, String instanceSourceURI, String username, String password, boolean applicationShared, long timeToLive, String validation) {

        if (applicationShared && instanceSourceURI == null)
            throw new OXFException("Only XForms instances externally loaded through the src attribute may have xxforms:shared=\"application\".");

        this.instanceId = instanceId;
        this.modelId = modelId;

        this.readonly = !(instanceDocumentInfo instanceof DocumentWrapper);
        this.applicationShared = applicationShared;
        this.timeToLive = timeToLive;

        this.sourceURI = instanceSourceURI;

        this.username = username;
        this.password = password;
        this.validation = validation;

        this.documentInfo = instanceDocumentInfo;
    }

    /**
     * Serialize the instance into a containing Element with meta-information.
     *
     * @param serialize     whether the instance document must be serialized
     * @return              containing Element
     */
    public Element createContainerElement(boolean serialize) {

        // DocumentInfo may wrap an actual TinyTree or a dom4j document
        final Element instanceElement = Dom4jUtils.createElement("instance");

        if (readonly)
            instanceElement.addAttribute("readonly", "true");
        if (applicationShared)
            instanceElement.addAttribute("shared", "application");
        if (timeToLive >= 0)
            instanceElement.addAttribute("ttl", Long.toString(timeToLive));

        instanceElement.addAttribute("id", instanceId);
        instanceElement.addAttribute("model-id", modelId);
        if (sourceURI != null)
            instanceElement.addAttribute("source-uri", sourceURI);
        if (username != null)
            instanceElement.addAttribute("username", username);
        if (password != null)
            instanceElement.addAttribute("password", password);
        if (validation != null)
            instanceElement.addAttribute("validation", validation);

        if (replaced)
            instanceElement.addAttribute("replaced", "true");

        if (serialize) {
            final String instanceString = TransformerUtils.tinyTreeToString(getDocumentInfo());
            instanceElement.addText(instanceString);
        }

        return instanceElement;
    }

    /**
     * Return the model that contains this instance.
     *
     * @param containingDocument    XFormsContainingDocument containing this instance
     * @return XFormsModel          XFormsModel containing this instance
     */
    public XFormsModel getModel(XFormsContainingDocument containingDocument) {
        return containingDocument.getModel(modelId);
    }

    /**
     * Return the instance DocumentInfo.
     *
     * @return  instance DocumentInfo
     */
    public DocumentInfo getDocumentInfo() {
        return documentInfo;
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
        return !(documentInfo instanceof VirtualNode);
    }


    public boolean isApplicationShared() {
        return applicationShared;
    }


    public long getTimeToLive() {
        return timeToLive;
    }

    public NodeInfo getInstanceRootElementInfo() {
        return (NodeInfo) XFormsUtils.getChildrenElements(documentInfo).get(0);
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

    public String getValidation() {
        return validation;
    }

    public boolean isReplaced() {
        return replaced;
    }

    public void setReplaced(boolean replaced) {
        this.replaced = replaced;
    }

    public void synchronizeInstanceDataEventState() {
        XFormsUtils.iterateInstanceData(this, new XFormsUtils.InstanceWalker() {
            public void walk(NodeInfo nodeInfo) {
                InstanceData.clearInstanceDataEventState(nodeInfo);
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

        // Convert value based on types if possible
        if (type != null) {
            final String nodeType = InstanceData.getType(node);

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
        final String previousValue;
        if (node instanceof Element) {
            // NOTE: Previously, there was a "first text node rule" which ended up causing problems and was removed.
            final Element elementnode = (Element) node;
            previousValue = elementnode.getStringValue();
            elementnode.setText(newValue);
        } else if (node instanceof Attribute) {
            // "Attribute nodes: The string-value of the attribute is replaced with a string corresponding to the new
            // value."
            final Attribute attributenode = (Attribute) node;
            previousValue = attributenode.getStringValue();
            attributenode.setValue(newValue);
        } else if (node instanceof Text) {
            // "Text nodes: The text node is replaced with a new one corresponding to the new value."
            final Text textNode = (Text) node;
            previousValue = textNode.getStringValue();
            textNode.setText(newValue);
        } else {
            // "Namespace, processing instruction, comment, and the XPath root node: behavior is undefined."
            throw new OXFException("Setting value on node other than element, attribute or text is not supported for node type: " + node.getNodeTypeName());
        }

        // Remember that the value has changed for this node if it has actually changed
        if (!newValue.equals(previousValue))
            InstanceData.markValueChanged(node);
    }

    public static String getValueForNodeInfo(NodeInfo nodeInfo) {

        if (nodeInfo.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE
                || nodeInfo.getNodeKind() == org.w3c.dom.Document.ATTRIBUTE_NODE
                || nodeInfo.getNodeKind() == org.w3c.dom.Document.TEXT_NODE) {

            // NOTE: In XForms 1.1, all these node types return the string value. Note that previously, there was a
            // "first text node rule" which ended up causing problems and was removed.
            return nodeInfo.getStringValue();
        } else {
            // "Namespace, processing instruction, comment, and the XPath root node: behavior is undefined."
            throw new OXFException("Setting value on node other than element, attribute or text is not supported for node type: " + nodeInfo.getNodeKind());
        }
    }

    public static String getValueForNode(Node node) {

        if (node.getNodeType() == org.w3c.dom.Document.ELEMENT_NODE
                || node.getNodeType() == org.w3c.dom.Document.ATTRIBUTE_NODE
                || node.getNodeType() == org.w3c.dom.Document.TEXT_NODE) {

            // NOTE: In XForms 1.1, all these node types return the string value. Note that previously, there was a
            // "first text node rule" which ended up causing problems and was removed.
            return node.getStringValue();
        } else {
            // "Namespace, processing instruction, comment, and the XPath root node: behavior is undefined."
            throw new OXFException("Setting value on node other than element, attribute or text is not supported for node type: " + node.getNodeTypeName());
        }
    }

    /**
     * Output the instance to the specified ContentHandler
     *
     * @param contentHandler    ContentHandler to write to
     */
    public void read(ContentHandler contentHandler) {
        try {
            if (documentInfo instanceof DocumentWrapper) {
                InstanceData.addInstanceAttributes(getInstanceRootElementInfo());
            }

            final Transformer identity = TransformerUtils.getIdentityTransformer();
            identity.transform(documentInfo, new SAXResult(contentHandler));

//            LocationSAXWriter saxw = new LocationSAXWriter();
//            saxw.setContentHandler(contentHandler);
//            saxw.write(instanceDocument);

            if (documentInfo instanceof DocumentWrapper) {
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

        getDocument().accept(new VisitorSupport() {

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
                parentInfoElement.addAttribute("readonly", Boolean.toString(InstanceData.getInheritedReadonly(node)));
                parentInfoElement.addAttribute("relevant", Boolean.toString(InstanceData.getInheritedRelevant(node)));
                parentInfoElement.addAttribute("required", Boolean.toString(InstanceData.getRequired(node)));
                parentInfoElement.addAttribute("valid", Boolean.toString(InstanceData.getValid(node)));
                final String type = InstanceData.getType(node);
                parentInfoElement.addAttribute("type", (type == null) ? "" : type);
//                parentInfoElement.addAttribute("schema-error-messages", instanceData.getSchemaErrorsMsgs());
                parentInfoElement.addAttribute("value-changed", Boolean.toString(InstanceData.isValueChanged(node)));
            }
        });

        XFormsUtils.logDebugDocument("MIPs: ", result);
    }

    public LocationData getLocationData() {
        if (documentInfo instanceof DocumentWrapper) {
            final Document document = getDocument();
            return XFormsUtils.getNodeLocationData(document.getRootElement());
        } else {
            return new LocationData(documentInfo.getSystemId(), documentInfo.getLineNumber(), -1);
        }
    }

    public XFormsEventHandlerContainer getParentContainer(XFormsContainingDocument containingDocument) {
        return getModel(containingDocument);
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        final String eventName = event.getEventName();
        if (XFormsEvents.XXFORMS_INSTANCE_INVALIDATE.equals(eventName)) {
            // Invalidate instance if it is shared read-only
            if (applicationShared) {
                XFormsServerSharedInstancesCache.instance().remove(pipelineContext, sourceURI);
            }
        }
    }

    /**
     * Return the instance document as a dom4j Document.
     *
     * NOTE: Should use getInstanceDocumentInfo() whenever possible.
     *
     * @return  instance document
     */
    public Document getDocument() {
        if (documentInfo instanceof DocumentWrapper) {
            final DocumentWrapper documentWrapper = (DocumentWrapper) documentInfo;
            return (Document) documentWrapper.getUnderlyingNode();
        } else {
            return null;
        }
    }

    /**
     * Create a shared version of this instance with the same instance document.
     *
     * @return  mutable XFormsInstance
     */
    public SharedXFormsInstance createSharedInstance() {
        return new SharedXFormsInstance(modelId, instanceId, documentInfo, sourceURI, username, password, false, timeToLive, validation);
    }

    public static String getInstanceId(Element xformsInstanceElement) {
        // NOTE: There has to be an id, but we return a non-null value just for the legacy engine
        final String idAttribute = xformsInstanceElement.attributeValue("id");
        return (idAttribute != null) ? idAttribute : "";
    }

    public static boolean isReadonlyHint(Element element) {
         return "true".equals(element.attributeValue(XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_QNAME));
    }

    public static boolean isApplicationSharedHint(Element element) {
        final String sharedAttributeValue = element.attributeValue(XFormsConstants.XXFORMS_SHARED_QNAME);
        final boolean isApplicationSharedHint = "application".equals(sharedAttributeValue);

        checkSharedHints(element, element.attributeValue(XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_QNAME),
                element.attributeValue(XFormsConstants.XXFORMS_SHARED_QNAME));

        return isApplicationSharedHint;
    }

    public static void checkSharedHints(Element element, String readonly, String shared) {
        if (shared != null) {

            // Can't have shared hints if not read-only
            if (!"true".equals(readonly))
                throw new ValidationException("xxforms:shared can be set only if xxforms:readonly is \"true\" for element: "
                        + element.attributeValue("id"), (LocationData) element.getData());

            final boolean isApplicationSharedHint = "application".equals(shared);
            final boolean isDocumentSharedHint = "document".equals(shared);

            // Check values if set
            if (!(isApplicationSharedHint || isDocumentSharedHint))
                throw new ValidationException("xxforms:shared must be either of \"application\" or \"document\" for element: "
                        + element.attributeValue("id"), (LocationData) element.getData());
        }
    }

    public static long getTimeToLive(Element element) {
        final String timeToLiveValue = element.attributeValue(XFormsConstants.XXFORMS_TIME_TO_LIVE_QNAME);
        return (timeToLiveValue != null) ? Long.parseLong(timeToLiveValue) : -1;
    }

    public List getEventHandlers(XFormsContainingDocument containingDocument) {
        return getModel(containingDocument).getEventHandlersForInstance(instanceId);
    }

    public void logIfNeeded(String message) {
        if (logger.isDebugEnabled()) {
            logger.debug("XForms - " + message + ": model id='" + getModelId() +  "', instance id= '" + getEffectiveId() + "'\n"
                    + TransformerUtils.tinyTreeToString(getInstanceRootElementInfo()));
        }
    }

}
