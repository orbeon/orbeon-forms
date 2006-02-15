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
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.DebugProcessor;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xforms.mip.BooleanModelItemProperty;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class XFormsUtils {

    private static final int BUFFER_SIZE = 1024;
    public static final String DEFAULT_UPLOAD_TYPE = "xs:anyURI";
    private static final int DEFAULT_SESSION_STATE_CACHE_SIZE = 1024 * 1024;

    /**
     * Return the local XForms instance data for the given node, null if not available.
     */
    public static InstanceData getLocalInstanceData(Node node) {
        return node instanceof Element
                ? (InstanceData) ((Element) node).getData()
                : node instanceof Attribute
                ? (InstanceData) ((Attribute) node).getData() : null;
    }

    /**
     * Return the inherited XForms instance data for the given node, null if not available.
     */
    public static InstanceData getInheritedInstanceData(Node node) {
        final InstanceData localInstanceData = getLocalInstanceData(node);
        if (localInstanceData == null)
            return null;

        final InstanceData resultInstanceData;
        try {
            resultInstanceData = (InstanceData) localInstanceData.clone();
        } catch (CloneNotSupportedException e) {
            // This should not happen because the classes cloned are Cloneable
            throw new OXFException(e);
        }

        for (Element currentElement = node.getParent(); currentElement != null; currentElement = currentElement.getParent()) {
            final InstanceData currentInstanceData = getLocalInstanceData(currentElement);

            // Handle readonly inheritance
            if (currentInstanceData.getReadonly().get())
                resultInstanceData.getReadonly().set(true);
            // Handle relevant inheritance
            if (!currentInstanceData.getRelevant().get())
                resultInstanceData.getRelevant().set(false);
        }

        return resultInstanceData;
    }

    /**
     * Recursively decorate all the elements and attributes with default <code>InstanceData</code>.
     */
    public static void setInitialDecoration(Document document) {
        Element rootElement = document.getRootElement();
        Map idToNodeMap = new HashMap();
        setInitialDecorationWorker(rootElement, new int[]{-1}, idToNodeMap);
        ((InstanceData) rootElement.getData()).setIdToNodeMap(idToNodeMap);
    }

    /**
     * Recursively decorate the element and its attributes with default <code>InstanceData</code>.
     */
    public static void setInitialDecoration(Element element) {
        setInitialDecorationWorker(element, null, null);
    }

    private static void setInitialDecorationWorker(Element element, int[] currentId, Map idToNodeMap) {
        // NOTE: ids are only used by the legacy XForms engine
        int elementId = (currentId != null) ? ++currentId[0] : -1;
        if (idToNodeMap != null) {
            idToNodeMap.put(new Integer(elementId), element);
        }

        element.setData(newInstanceData(element.getData(), elementId));

        for (Iterator i = element.attributes().iterator(); i.hasNext();) {
            Attribute attribute = (Attribute) i.next();
            if (!XFormsConstants.XXFORMS_NAMESPACE_URI.equals(attribute.getNamespaceURI())) {
                // NOTE: ids are only used by the legacy XForms engine
                int attributeId = (currentId != null) ? ++currentId[0] : -1;
                if (idToNodeMap != null) {
                    idToNodeMap.put(new Integer(attributeId), attribute);
                }
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

    /**
     * Mark all value nodes of an instance document as having changed.
     */
    public static void markAllValuesChanged(Document document) {
        XFormsUtils.iterateInstanceData(document, new XFormsUtils.InstanceWalker() {
            public void walk(Node node, InstanceData localInstanceData, InstanceData inheritedInstanceData) {
                if (localInstanceData != null) {
                    localInstanceData.markValueChanged();
                }
            }
        }, false);
    }

    /**
     * Return whether name encryption is enabled (legacy XForms engine only).
     */
    public static boolean isNameEncryptionEnabled() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_ENCRYPT_NAMES_PROPERTY, false).booleanValue();
    }

    /**
     * Return whether hidden fields encryption is enabled.
     */
    public static boolean isHiddenEncryptionEnabled() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_ENCRYPT_HIDDEN_PROPERTY, false).booleanValue();
    }

    /**
     * Reconcile "DOM InstanceData annotations" with "attribute annotations"
     */
    public static void addInstanceAttributes(Document instanceDocument) {
        addInstanceAttributes(instanceDocument.getRootElement());
    }

    private static void addInstanceAttributes(final Element element) {
        final Object instanceDataObject = element.getData();
        if (instanceDataObject instanceof InstanceData) {
            final InstanceData instanceData = (InstanceData) element.getData();
            final String invldBnds = instanceData.getInvalidBindIds();
            updateAttribute(element, XFormsConstants.XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_QNAME, invldBnds, null);

            // Reconcile boolean model item properties
            reconcileBoolean(instanceData.getReadonly(), element, XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_QNAME, false);
            reconcileBoolean(instanceData.getRelevant(), element, XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_QNAME, true);
            reconcileBoolean(instanceData.getRequired(), element, XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_QNAME, false);
            reconcileBoolean(instanceData.getValid(), element, XFormsConstants.XXFORMS_VALID_ATTRIBUTE_QNAME, true);
        }

        for (final Iterator i = element.elements().iterator(); i.hasNext();) {
            final Object o = i.next();
            addInstanceAttributes((Element) o);
        }
    }

    private static void reconcileBoolean(final BooleanModelItemProperty prp, final Element elt, final QName qnm, final boolean defaultValue) {
        final String currentBooleanValue;
        if (prp.hasChangedFromDefault()) {
            final boolean b = prp.get();
            currentBooleanValue = Boolean.toString(b);
        } else {
            currentBooleanValue = null;
        }
        updateAttribute(elt, qnm, currentBooleanValue, Boolean.toString(defaultValue));
    }

    private static void updateAttribute(final Element elt, final QName qnam, final String currentValue, final String defaultValue) {
        Attribute attr = elt.attribute(qnam);
        if (((currentValue == null) || (currentValue != null && currentValue.equals(defaultValue))) && attr != null) {
            elt.remove(attr);
        } else if (currentValue != null && !currentValue.equals(defaultValue)) {
            // Add a namespace declaration if necessary
            final String pfx = qnam.getNamespacePrefix();
            final String qnURI = qnam.getNamespaceURI();
            final Namespace ns = elt.getNamespaceForPrefix(pfx);
            final String nsURI = ns == null ? null : ns.getURI();
            if (ns == null) {
                elt.addNamespace(pfx, qnURI);
            } else if (!nsURI.equals(qnURI)) {
                final InstanceData instDat = XFormsUtils.getLocalInstanceData(elt);
                final LocationData locDat = instDat.getLocationData();
                throw new ValidationException("Cannot add attribute to node with 'xxforms' prefix"
                        + " as the prefix is already mapped to another URI", locDat);
            }
            // Add attribute
            if (attr == null) {
                attr = Dom4jUtils.createAttribute(elt, qnam, currentValue);
                final LocationData ld = (LocationData) attr.getData();
                final InstanceData instDat = new InstanceData(ld);
                attr.setData(instDat);
                elt.add(attr);
            } else {
                attr.setValue(currentValue);
            }
        }
    }

    public static void removeInstanceAttributes(Document instanceDocument) {
        Visitor visitor = new VisitorSupport() {
            public void visit(Element node) {
                List newAttributes = new ArrayList();
                for (Iterator i = node.attributeIterator(); i.hasNext();) {
                    Attribute attr = (Attribute) i.next();
                    if (!XFormsConstants.XXFORMS_NAMESPACE_URI.equals(attr.getNamespaceURI()))
                        newAttributes.add(attr);

                }
                node.setAttributes(newAttributes);
            }
        };
        instanceDocument.accept(visitor);
    }

    /**
     * Iterate through nodes of the instance document and call the walker on each of them.
     *
     * @param instanceDocument
     * @param instanceWalker
     * @param allNodes          all the nodes, otherwise only data nodes
     */
    public static void iterateInstanceData(Document instanceDocument, InstanceWalker instanceWalker, boolean allNodes) {
        iterateInstanceData(instanceDocument.getRootElement(), instanceWalker, allNodes);
    }

    private static void iterateInstanceData(Element element, InstanceWalker instanceWalker, boolean allNodes) {

        final List childrenElements = element.elements();

        // We "walk" an element which contains elements only if allNodes == true
        if (allNodes || childrenElements.size() == 0)
            instanceWalker.walk(element, getLocalInstanceData(element), getInheritedInstanceData(element));

        // "walk" current element's attributes
        for (Iterator i = element.attributes().iterator(); i.hasNext();) {
            final Attribute attribute = (Attribute) i.next();
            instanceWalker.walk(attribute, getLocalInstanceData(attribute), getInheritedInstanceData(attribute));
        }
        // "walk" current element's children elements
        if (childrenElements.size() != 0) {
            for (Iterator i = childrenElements.iterator(); i.hasNext();) {
                final Element child = (Element) i.next();
                iterateInstanceData(child, instanceWalker, allNodes);
            }
        }
    }

    public static String encodeXMLAsDOM(PipelineContext pipelineContext, org.w3c.dom.Node node) {

        try {
            return encodeXML(pipelineContext, TransformerUtils.domToDom4jDocument(node), getEncryptionKey());
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    public static String encodeXML(PipelineContext pipelineContext, Document instance) {
        return encodeXML(pipelineContext, instance, getEncryptionKey());
    }

    public static String encodeXML(PipelineContext pipelineContext, Document instance, String encryptionPassword) {
        try {
            final ByteArrayOutputStream gzipByteArray = new ByteArrayOutputStream();
            final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(gzipByteArray);
            gzipOutputStream.write(Dom4jUtils.domToString(instance, false, false).getBytes("utf-8"));
            gzipOutputStream.close();
            String result = Base64.encode(gzipByteArray.toByteArray());
            if (encryptionPassword != null)
                result = SecureUtils.encrypt(pipelineContext, encryptionPassword, result);
            return result;
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static String encodeXML2(PipelineContext pipelineContext, Document instance) {
        return encodeXML2(pipelineContext, instance, getEncryptionKey());
    }

    public static String encodeXML2(PipelineContext pipelineContext, Document instance, String encryptionPassword) {
        // NOTE: This is an attempt to implement an alternative way of encoding. It appears to be
        // slower. Possibly manually serializing the SAXStore could yield better performance.
        try {
            final SAXStore saxStore = new SAXStore();
            final SAXResult saxResult = new SAXResult(saxStore);
            final Transformer identity = TransformerUtils.getIdentityTransformer();
            identity.transform(new DocumentSource(instance), saxResult);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            final ObjectOutputStream objectOutputStream = new ObjectOutputStream(gzipOutputStream);
            objectOutputStream.writeObject(saxStore);
            objectOutputStream.close();

            String result = Base64.encode(byteArrayOutputStream.toByteArray());
            if (encryptionPassword != null)
                result = SecureUtils.encrypt(pipelineContext, encryptionPassword, result);
            return result;
        } catch (IOException e) {
            throw new OXFException(e);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    public static org.w3c.dom.Document decodeXMLAsDOM(PipelineContext pipelineContext, String encodedXML) {
        try {
            return TransformerUtils.dom4jToDomDocument(XFormsUtils.decodeXML(pipelineContext, encodedXML));
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    public static Document decodeXML(PipelineContext pipelineContext, String encodedXML) {
        return decodeXML(pipelineContext, encodedXML, getEncryptionKey());
    }

    public static Document decodeXML(PipelineContext pipelineContext, String encodedXML, String encryptionPassword) {
        try {
            // Get raw text
            String xmlText;
            {
                if (encryptionPassword != null)
                    encodedXML = SecureUtils.decrypt(pipelineContext, encryptionPassword, encodedXML);
                ByteArrayInputStream compressedData = new ByteArrayInputStream(Base64.decode(encodedXML));
                StringBuffer xml = new StringBuffer();
                byte[] buffer = new byte[1024];
                GZIPInputStream gzipInputStream = new GZIPInputStream(compressedData);
                int size;
                while ((size = gzipInputStream.read(buffer)) != -1) xml.append(new String(buffer, 0, size, "utf-8"));
                xmlText = xml.toString();
            }
            // Parse XML and return documents
            LocationSAXContentHandler saxContentHandler = new LocationSAXContentHandler();
            XMLUtils.stringToSAX(xmlText, null, saxContentHandler, false, false);
            return saxContentHandler.getDocument();
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static Document decodeXML2(PipelineContext pipelineContext, String encodedXML) {
        return decodeXML2(pipelineContext, encodedXML, getEncryptionKey());
    }

    public static Document decodeXML2(PipelineContext pipelineContext, String encodedXML, String encryptionPassword) {
        // NOTE: This is an attempt to implement an alternative way of decoding. It appears to be
        // slower. Possibly manually serializing the SAXStore could yield better performance.
        try {
            if (encryptionPassword != null)
                encodedXML = SecureUtils.decrypt(pipelineContext, encryptionPassword, encodedXML);
            final ByteArrayInputStream compressedData = new ByteArrayInputStream(Base64.decode(encodedXML));
            final GZIPInputStream gzipInputStream = new GZIPInputStream(compressedData);

            final ObjectInputStream objectInputStream = new ObjectInputStream(gzipInputStream);
            final SAXStore saxStore = (SAXStore) objectInputStream.readObject();

            final LocationDocumentResult documentResult = new LocationDocumentResult();
            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
            identity.setResult(documentResult);
            saxStore.replay(identity);

            return documentResult.getDocument();

        } catch (IOException e) {
            throw new OXFException(e);
        } catch (ClassNotFoundException e) {
            throw new OXFException(e);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public static String getEncryptionKey() {
        if (XFormsUtils.isHiddenEncryptionEnabled())
            return OXFProperties.instance().getPropertySet().getString(XFormsConstants.XFORMS_PASSWORD_PROPERTY);
        else
            return null;
    }

    public static String retrieveSrcValue(String src) throws IOException {
        URL url = URLFactory.createURL(src);

        // Load file into buffer
        InputStreamReader reader = new InputStreamReader(url.openStream());
        try {
            StringBuffer value = new StringBuffer();
            char[] buff = new char[BUFFER_SIZE];
            int c = 0;
            while ((c = reader.read(buff, 0, BUFFER_SIZE - 1)) != -1) value.append(buff, 0, c);
            return value.toString();
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    public static String convertUploadTypes(PipelineContext pipelineContext, String value, String currentType, String newType) {
        if (currentType.equals(newType))
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

    public static boolean isCacheDocument() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_CACHE_DOCUMENT_PROPERTY, false).booleanValue();
    }

    public static boolean isOptimizePostAllSubmission() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_OPTIMIZE_POST_ALL_PROPERTY, true).booleanValue();// default to true for backward compatibility
    }

    public static boolean isOptimizeGetAllSubmission() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_OPTIMIZE_GET_ALL_PROPERTY, true).booleanValue();
    }

    public static boolean isOptimizeLocalSubmission() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_OPTIMIZE_LOCAL_SUBMISSION_PROPERTY, true).booleanValue();
    }

    public static boolean isOptimizeLocalInstanceLoads() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_OPTIMIZE_LOCAL_INSTANCE_LOADS_PROPERTY, true).booleanValue();
    }

    public static boolean isCacheSession() {
        final String propertyValue = OXFProperties.instance().getPropertySet().getString
                (XFormsConstants.XFORMS_STATE_HANDLING_PROPERTY, XFormsConstants.XXFORMS_STATE_HANDLING_CLIENT_VALUE);

        return propertyValue.equals(XFormsConstants.XXFORMS_STATE_HANDLING_SESSION_VALUE);
    }

    public static boolean isExceptionOnInvalidClientControlId() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_EXCEPTION_INVALID_CLIENT_CONTROL_PROPERTY, false).booleanValue();
    }

    public static boolean isAjaxShowLoadingIcon() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_AJAX_SHOW_LOADING_ICON_PROPERTY, false).booleanValue();
    }

    public static boolean isAjaxShowErrors() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_AJAX_SHOW_ERRORS_PROPERTY, true).booleanValue();
    }

    public static int getSessionCacheSize() {
        return OXFProperties.instance().getPropertySet().getInteger
                (XFormsConstants.XFORMS_CACHE_SESSION_SIZE_PROPERTY, DEFAULT_SESSION_STATE_CACHE_SIZE).intValue();
    }

    public static String resolveURL(XFormsContainingDocument containingDocument, PipelineContext pipelineContext, Element currentElement, boolean doReplace, String value) {
        final boolean isPortletLoad = "portlet".equals(containingDocument.getContainerType());

        final URI resolvedURI = resolveURI(currentElement, value);
        final String resolvedURISTring = resolvedURI.toString();
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        final String externalURL;
        // NOTE: Keep in mind that this is going to run from within a servlet, as the XForms server
        // runs in a servlet when processing these events!
        if (!isPortletLoad) {
            // XForms page was loaded from a servlet
            if (doReplace) {
                externalURL = externalContext.getResponse().rewriteRenderURL(resolvedURISTring);
            } else {
                externalURL = externalContext.getResponse().rewriteResourceURL(resolvedURISTring, false);
            }
        } else {
            // XForms page was loaded from a portlet
            if (doReplace) {
                if (resolvedURI.getFragment() != null) {
                    // Remove fragment if there is one, as it doesn't serve in a portlet
                    try {
                        externalURL = new URI(resolvedURI.getScheme(), resolvedURI.getAuthority(), resolvedURI.getPath(), resolvedURI.getQuery(), null).toString();
                    } catch (URISyntaxException e) {
                        throw new OXFException(e);
                    }
                } else {
                    externalURL = resolvedURISTring;
                }
            } else {
                externalURL = externalContext.getResponse().rewriteResourceURL(resolvedURISTring, false);
            }
        }

        return externalURL;
    }

    public static interface InstanceWalker {
        public void walk(Node node, InstanceData localInstanceData, InstanceData inheritedInstanceData);
    }

    /**
     * Resolve a URI string against an element, taking into account ancestor xml:base elements for
     * the resolution.
     *
     * @param element   element used to consider xml:base scope
     * @param uri       URI to resolve
     * @return          resolved URI
     */
    public static URI resolveURI(Element element, String uri) {
        final List xmlBaseElements = new ArrayList();

        // Collect xml:base values
        Element currentElement = element;
        do {
            final String xmlBaseAttribute = currentElement.attributeValue(XMLConstants.XML_BASE_QNAME);
            if (xmlBaseAttribute != null)
                xmlBaseElements.add(xmlBaseAttribute);
            currentElement = currentElement.getParent();
        } while(currentElement != null);

        // Go from root to leaf
        Collections.reverse(xmlBaseElements);
        xmlBaseElements.add(uri);

        // Resolve paths from root to leaf
        try {
            URI result = null;
            for (Iterator i = xmlBaseElements.iterator(); i.hasNext();) {
                final String currentXMLBase = (String) i.next();
                final URI currentXMLBaseURI = new URI(currentXMLBase);
                result = (result == null) ? currentXMLBaseURI : result.resolve(currentXMLBaseURI);
            }
            return result;
        } catch (URISyntaxException e) {
            throw new ValidationException("Error while resolving URI: " + uri, e, (LocationData) element.getData());
        }
    }

    /**
     * Log a message and Document.
     *
     * @param debugMessage  the message to display
     * @param document      the Document to display
     */
    public static void logDebugDocument(String debugMessage, Document document) {
//        XFormsServer.logger.debug(debugMessage + ":\n" + Dom4jUtils.domToString(document));
        DebugProcessor.logger.info(debugMessage + ":\n" + Dom4jUtils.domToString(document));
    }
}
