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

import org.apache.commons.pool.PoolableObjectFactory;
import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DebugProcessor;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.util.SoftReferenceObjectPool;
import org.orbeon.oxf.xforms.mip.BooleanModelItemProperty;
import org.orbeon.oxf.xforms.mip.ReadonlyModelItemProperty;
import org.orbeon.oxf.xforms.mip.RelevantModelItemProperty;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.Axis;
import org.orbeon.saxon.om.AxisIterator;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import javax.xml.transform.TransformerException;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;

public class XFormsUtils {

    private static final int BUFFER_SIZE = 1024;

    /**
     * Return the local XForms instance data for the given node, null if not available.
     */
    public static InstanceData getLocalInstanceData(NodeInfo nodeInfo) {
        if (nodeInfo instanceof NodeWrapper) {
            return getLocalInstanceData(getNodeFromNodeInfo(nodeInfo, ""));
        } else {
            // TODO: check how we proceed for TinyTree: should we return something anyway?
            return null;
        }
    }

    public static InstanceData getLocalInstanceData(Node node) {
        if (node instanceof Element) {
            return (InstanceData) ((Element) node).getData();
        } else if (node instanceof Attribute) {
            return (InstanceData) ((Attribute) node).getData();
        } else if (node instanceof Document) {
            // We can't store data on the Document object. Use root element instead.
            return (InstanceData) ((Document) node).getRootElement().getData();
        } else {
            return null;
        }
        // TODO: other node types once we update to handling text nodes correctly
    }

    public static Map getIdToNodeMap(NodeInfo nodeInfo) {
        if (nodeInfo instanceof NodeWrapper) {
            final Node node = getNodeFromNodeInfo(nodeInfo, "");
            return ((InstanceData) ((Document) node.getDocument()).getRootElement().getData()).getIdToNodeMap();
        } else {
            // TODO: check how we proceed for TinyTree: should we return something anyway?
            return null;
        }
    }

    /**
     * Return the XForms instance data for the given node with updated inherited MIPs, null if not available.
     */
    public static InstanceData getInstanceDataUpdateInherited(NodeInfo nodeInfo) {
        if (nodeInfo instanceof NodeWrapper) {
            return getInstanceDataUpdateInherited(getNodeFromNodeInfo(nodeInfo, ""));
        } else {
            // TODO: check how we proceed for TinyTree: should we return something anyway?
            return null;
        }
    }

    public static InstanceData getInstanceDataUpdateInherited(Node node) {
        final InstanceData localInstanceData = getLocalInstanceData(node);
        if (localInstanceData == null)
            return null;

        // Clear current inherited state
        localInstanceData.setInheritedReadonly(null);
        localInstanceData.setInheritedRelevant(null);

        boolean handleReadonly = !localInstanceData.getReadonly().get();
        boolean handleRelevant = localInstanceData.getRelevant().get();

        if (handleReadonly || handleRelevant) {
            // There may be something to do

            for (Element currentElement = node.getParent(); currentElement != null && (handleReadonly || handleRelevant);
                 currentElement = currentElement.getParent()) {

                final InstanceData currentInstanceData = getLocalInstanceData(currentElement);

                // Handle readonly inheritance
                if (handleReadonly && currentInstanceData.getReadonly().get()) {
                    if (localInstanceData.getInheritedReadonly() == null)
                        localInstanceData.setInheritedReadonly(new ReadonlyModelItemProperty());
                    localInstanceData.getInheritedReadonly().set(true);
                    handleReadonly = false;
                }
                // Handle relevant inheritance
                if (handleRelevant && !currentInstanceData.getRelevant().get()) {
                    if (localInstanceData.getInheritedRelevant() == null)
                        localInstanceData.setInheritedRelevant(new RelevantModelItemProperty());
                    localInstanceData.getInheritedRelevant().set(false);
                    handleRelevant = false;
                }
            }
        }

        // Make sure there is a reference to a MIP
        if (localInstanceData.getInheritedReadonly() == null)
            localInstanceData.setInheritedReadonly(localInstanceData.getReadonly());
        if (localInstanceData.getInheritedRelevant() == null)
            localInstanceData.setInheritedRelevant(localInstanceData.getRelevant());

        return localInstanceData;
    }

    /**
     * Recursively decorate all the elements and attributes with default <code>InstanceData</code>.
     */
    public static void setInitialDecoration(Document document) {
        final Element rootElement = document.getRootElement();
        final Map idToNodeMap = new HashMap();
        setInitialDecorationWorker(rootElement, new int[]{-1}, idToNodeMap);
        ((InstanceData) rootElement.getData()).setIdToNodeMap(idToNodeMap);
    }

    /**
     * Recursively decorate the element and its attributes with default <code>InstanceData</code>.
     */
    public static void setInitialDecoration(Element element) {
        setInitialDecorationWorker(element, null, null);
    }

    public static void setInitialDecoration(Attribute attribute) {
        attribute.setData(newInstanceData(attribute.getData(), -1));
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
    public static void markAllValuesChanged(XFormsInstance instance) {
        XFormsUtils.iterateInstanceData(instance, new XFormsUtils.InstanceWalker() {
            public void walk(NodeInfo nodeInfo, InstanceData updatedInstanceData) {
                if (updatedInstanceData != null) {
                    updatedInstanceData.markValueChanged();
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
    public static void addInstanceAttributes(final NodeInfo elementNodeInfo) {

        // Don't do anything if we have a read-only document
        if (!(elementNodeInfo instanceof NodeWrapper))
            return;

        final InstanceData instanceData = getInstanceDataUpdateInherited(elementNodeInfo);

        if (instanceData != null) {
            final Element element = (Element) getNodeFromNodeInfo(elementNodeInfo, "");
            final String invalidBindIds = instanceData.getInvalidBindIds();
            updateAttribute(element, XFormsConstants.XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_QNAME, invalidBindIds, null);

            // Reconcile boolean model item properties
            reconcileBoolean(instanceData.getInheritedReadonly(), element, XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_QNAME, false);
            reconcileBoolean(instanceData.getInheritedRelevant(), element, XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_QNAME, true);
            reconcileBoolean(instanceData.getRequired(), element, XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_QNAME, false);
            reconcileBoolean(instanceData.getValid(), element, XFormsConstants.XXFORMS_VALID_ATTRIBUTE_QNAME, true);
        }

        final List elements = getChildrenElements(elementNodeInfo);
        for (Iterator i = elements.iterator(); i.hasNext();) {
            final NodeInfo currentElementNodeInfo = (NodeInfo) i.next();
            addInstanceAttributes(currentElementNodeInfo);
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
                final LocationData locDat = getNodeLocationData(elt);
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

    public static void removeInstanceAttributes(final NodeInfo elementNodeInfo) {
        // Don't do anything if we have a read-only document
        if (!(elementNodeInfo instanceof NodeWrapper))
            return;

        // Visit all elements in the document and remove @xxforms:*
        final Node instanceNode = (Node) getNodeFromNodeInfo(elementNodeInfo, "");
        Visitor visitor = new VisitorSupport() {
            public void visit(Element node) {
                final List newAttributes = new ArrayList();
                for (Iterator i = node.attributeIterator(); i.hasNext();) {
                    final Attribute attr = (Attribute) i.next();
                    if (!XFormsConstants.XXFORMS_NAMESPACE_URI.equals(attr.getNamespaceURI()))
                        newAttributes.add(attr);

                }
                node.setAttributes(newAttributes);
            }
        };
        instanceNode.accept(visitor);
    }

    /**
     * Iterate through nodes of the instance document and call the walker on each of them.
     *
     * @param instance
     * @param instanceWalker
     * @param allNodes          all the nodes, otherwise only leaf data nodes
     */
    public static void iterateInstanceData(XFormsInstance instance, InstanceWalker instanceWalker, boolean allNodes) {
        iterateInstanceData(instance.getInstanceRootElementInfo(), instanceWalker, allNodes);
    }

    private static void iterateInstanceData(NodeInfo elementNodeInfo, InstanceWalker instanceWalker, boolean allNodes) {

        final List childrenElements = getChildrenElements(elementNodeInfo);

        // We "walk" an element which contains elements only if allNodes == true
        if (allNodes || childrenElements.size() == 0)
            instanceWalker.walk(elementNodeInfo, getInstanceDataUpdateInherited(elementNodeInfo));

        // "walk" current element's attributes
        for (Iterator i = getAttributes(elementNodeInfo).iterator(); i.hasNext();) {
            final NodeInfo attributeNodeInfo = (NodeInfo) i.next();
            instanceWalker.walk(attributeNodeInfo, getInstanceDataUpdateInherited(attributeNodeInfo));
        }
        // "walk" current element's children elements
        if (childrenElements.size() != 0) {
            for (Iterator i = childrenElements.iterator(); i.hasNext();) {
                final NodeInfo childElement = (NodeInfo) i.next();
                iterateInstanceData(childElement, instanceWalker, allNodes);
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

    public static String encodeXML(PipelineContext pipelineContext, Document documentToEncode) {
        return encodeXML(pipelineContext, documentToEncode, getEncryptionKey());
    }

//    public static String encodeXML(PipelineContext pipelineContext, SAXStore saxStore, String encryptionPassword) {
//        try {
//            final ByteArrayOutputStream gzipByteArray = new ByteArrayOutputStream();
//            final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(gzipByteArray);
//
//            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
//            identity.setResult(new StreamResult(gzipOutputStream));
//            saxStore.replay(identity);
//
//            gzipOutputStream.close();
//            String result = Base64.encode(gzipByteArray.toByteArray());
//            if (encryptionPassword != null)
//                result = SecureUtils.encrypt(pipelineContext, encryptionPassword, result);
//            return result;
//        } catch (Exception e) {
//            throw new OXFException(e);
//        }
//    }

    // Use a Deflater pool as creating Deflaters is expensive
    final static SoftReferenceObjectPool deflaterPool = new SoftReferenceObjectPool(new CachedPoolableObjetFactory());

    public static String encodeXML(PipelineContext pipelineContext, Document documentToEncode, String encryptionPassword) {
//        XFormsServer.logger.debug("XForms - encoding XML.");
        Deflater deflater = null;
        try {
            // The XML document as a string
            final String xmlString = Dom4jUtils.domToString(documentToEncode, false, false);

            // Compress if needed
            final byte[] gzipByteArray;
            if (isGZIPState()) {
                deflater = (Deflater) deflaterPool.borrowObject();
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                final DeflaterGZIPOutputStream gzipOutputStream = new DeflaterGZIPOutputStream(deflater, byteArrayOutputStream, 1024);
                gzipOutputStream.write(xmlString.getBytes("utf-8"));
                gzipOutputStream.close();
                gzipByteArray = byteArrayOutputStream.toByteArray();
            } else {
                gzipByteArray = null;
            }

            // Encrypt if needed
            if (encryptionPassword != null) {
                // Perform encryption
                final String encryptedString;
                if (gzipByteArray == null) {
                    // The data was not compressed
                    encryptedString = "X1" + SecureUtils.encrypt(pipelineContext, encryptionPassword, xmlString);
                } else {
                    // The data was compressed
                    encryptedString = "X2" + SecureUtils.encrypt(pipelineContext, encryptionPassword, gzipByteArray);
                }
                return encryptedString;
            } else {
                // No encryption
                if (gzipByteArray == null) {
                    // The data was not compressed
                    // NOTE: In this scenario, we take a shortcut and assume we don't even need to base64 the string
                    // as it is going to stay in memory
                    return "X3" + xmlString;
                } else {
                    // The data was compressed
                    return "X4" + Base64.encode(gzipByteArray);
                }
            }
        } catch (Throwable e) {
            try {
                if (deflater != null)
                    deflaterPool.invalidateObject(deflater);
            } catch (Exception e1) {
                throw new OXFException(e1);
            }
            throw new OXFException(e);
        } finally {
            try {
                if (deflater != null) {
                    deflater.reset();
                    deflaterPool.returnObject(deflater);
                }
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }
    }

    private static class CachedPoolableObjetFactory implements PoolableObjectFactory {
        public Object makeObject() throws Exception {
            XFormsServer.logger.debug("XForms - creating new Deflater.");
            return new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        }

        public void destroyObject(Object object) throws Exception {
        }

        public boolean validateObject(Object object) {
            return true;
        }

        public void activateObject(Object object) throws Exception {
        }

        public void passivateObject(Object object) throws Exception {
        }
    }

    private static class DeflaterGZIPOutputStream extends DeflaterOutputStream {
        public DeflaterGZIPOutputStream(Deflater deflater, OutputStream out, int size) throws IOException {
            super(out, deflater, size);
            writeHeader();
            crc.reset();
        }

        private boolean closed = false;
        protected CRC32 crc = new CRC32();
        private final static int GZIP_MAGIC = 0x8b1f;
        private final static int TRAILER_SIZE = 8;

        public synchronized void write(byte[] buf, int off, int len) throws IOException {
            super.write(buf, off, len);
            crc.update(buf, off, len);
        }

        public void finish() throws IOException {
            if (!def.finished()) {
                def.finish();
                while (!def.finished()) {
                    int len = def.deflate(buf, 0, buf.length);
                    if (def.finished() && len <= buf.length - TRAILER_SIZE) {
                        writeTrailer(buf, len);
                        len = len + TRAILER_SIZE;
                        out.write(buf, 0, len);
                        return;
                    }
                    if (len > 0)
                        out.write(buf, 0, len);
                }
                byte[] trailer = new byte[TRAILER_SIZE];
                writeTrailer(trailer, 0);
                out.write(trailer);
            }
        }

        public void close() throws IOException {
            if (!closed) {
                finish();
                out.close();
                closed = true;
            }
        }

        private final static byte[] header = {
                (byte) GZIP_MAGIC,
                (byte) (GZIP_MAGIC >> 8),
                Deflater.DEFLATED,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        };

        private void writeHeader() throws IOException {
            out.write(header);
        }

        private void writeTrailer(byte[] buf, int offset) {
            writeInt((int) crc.getValue(), buf, offset);
            writeInt(def.getTotalIn(), buf, offset + 4);
        }

        private void writeInt(int i, byte[] buf, int offset) {
            writeShort(i & 0xffff, buf, offset);
            writeShort((i >> 16) & 0xffff, buf, offset + 2);
        }

        private void writeShort(int s, byte[] buf, int offset) {
            buf[offset] = (byte) (s & 0xff);
            buf[offset + 1] = (byte) ((s >> 8) & 0xff);
        }
    }

//    public static String encodeXML2(PipelineContext pipelineContext, Document instance) {
//        return encodeXML2(pipelineContext, instance, getEncryptionKey());
//    }
//
//    public static String encodeXML2(PipelineContext pipelineContext, Document instance, String encryptionPassword) {
//        // NOTE: This is an attempt to implement an alternative way of encoding. It appears to be
//        // slower. Possibly manually serializing the SAXStore could yield better performance.
//        try {
//            final SAXStore saxStore = new SAXStore();
//            final SAXResult saxResult = new SAXResult(saxStore);
//            final Transformer identity = TransformerUtils.getIdentityTransformer();
//            identity.transform(new DocumentSource(instance), saxResult);
//
//            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//            final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
//            final ObjectOutputStream objectOutputStream = new ObjectOutputStream(gzipOutputStream);
//            objectOutputStream.writeObject(saxStore);
//            objectOutputStream.close();
//
//            String result = Base64.encode(byteArrayOutputStream.toByteArray());
//            if (encryptionPassword != null)
//                result = SecureUtils.encrypt(pipelineContext, encryptionPassword, result);
//            return result;
//        } catch (IOException e) {
//            throw new OXFException(e);
//        } catch (TransformerException e) {
//            throw new OXFException(e);
//        }
//    }

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
            String xmlString;
            {
                final String prefix = encodedXML.substring(0, 2);
                final String encodedString = encodedXML.substring(2);

                final String xmlString1;
                final byte[] gzipByteArray;
                if (prefix.equals("X1")) {
                    // Encryption + uncompressed
                    xmlString1 = SecureUtils.decryptAsString(pipelineContext, encryptionPassword, encodedString);
                    gzipByteArray = null;
                } else if (prefix.equals("X2")) {
                    // Encryption + compressed
                    xmlString1 = null;
                    gzipByteArray = SecureUtils.decrypt(pipelineContext, encryptionPassword, encodedString);
                } else if (prefix.equals("X3")) {
                    // No encryption + uncompressed
                    xmlString1 = encodedString;
                    gzipByteArray = null;
                } else if (prefix.equals("X4")) {
                    // No encryption + compressed
                    xmlString1 = null;
                    gzipByteArray = Base64.decode(encodedString);
                } else {
                    throw new OXFException("Invalid prefix for encoded XML string: " + prefix);
                }

                // Decompress if needed
                if (gzipByteArray != null) {
                    final ByteArrayInputStream compressedData = new ByteArrayInputStream(gzipByteArray);
                    final GZIPInputStream gzipInputStream = new GZIPInputStream(compressedData);
                    final ByteArrayOutputStream binaryData = new ByteArrayOutputStream(1024);
                    NetUtils.copyStream(gzipInputStream, binaryData);
                    xmlString = new String(binaryData.toByteArray(), "utf-8");
                } else {
                    xmlString = xmlString1;
                }
            }
            // Parse XML and return document
            final LocationSAXContentHandler saxContentHandler = new LocationSAXContentHandler();
            XMLUtils.stringToSAX(xmlString, null, saxContentHandler, false, false);
            return saxContentHandler.getDocument();
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

//    public static Document decodeXML2(PipelineContext pipelineContext, String encodedXML) {
//        return decodeXML2(pipelineContext, encodedXML, getEncryptionKey());
//    }
//
//    public static Document decodeXML2(PipelineContext pipelineContext, String encodedXML, String encryptionPassword) {
//        // NOTE: This is an attempt to implement an alternative way of decoding. It appears to be
//        // slower. Possibly manually serializing the SAXStore could yield better performance.
//        try {
//            if (encryptionPassword != null)
//                encodedXML = SecureUtils.decrypt(pipelineContext, encryptionPassword, encodedXML);
//            final ByteArrayInputStream compressedData = new ByteArrayInputStream(Base64.decode(encodedXML));
//            final GZIPInputStream gzipInputStream = new GZIPInputStream(compressedData);
//
//            final ObjectInputStream objectInputStream = new ObjectInputStream(gzipInputStream);
//            final SAXStore saxStore = (SAXStore) objectInputStream.readObject();
//
//            final LocationDocumentResult documentResult = new LocationDocumentResult();
//            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
//            identity.setResult(documentResult);
//            saxStore.replay(identity);
//
//            return documentResult.getDocument();
//
//        } catch (IOException e) {
//            throw new OXFException(e);
//        } catch (ClassNotFoundException e) {
//            throw new OXFException(e);
//        } catch (SAXException e) {
//            throw new OXFException(e);
//        }
//    }

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
        if (ProcessorUtils.SUPPORTED_BINARY_TYPES.get(currentType) == null)
            throw new UnsupportedOperationException("Unsupported type: " + currentType);
        if (ProcessorUtils.SUPPORTED_BINARY_TYPES.get(newType) == null)
            throw new UnsupportedOperationException("Unsupported type: " + newType);

        if (currentType.equals(ProcessorUtils.XS_BASE64BINARY_EXPLODED_QNAME)) {
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

    // This is not used currently
//    public static boolean isOptimizeLocalInstanceLoads() {
//        return OXFProperties.instance().getPropertySet().getBoolean
//                (XFormsConstants.XFORMS_OPTIMIZE_LOCAL_INSTANCE_LOADS_PROPERTY, true).booleanValue();
//    }

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
                (XFormsConstants.XFORMS_CACHE_SESSION_SIZE_PROPERTY, XFormsConstants.DEFAULT_SESSION_STATE_CACHE_SIZE).intValue();
    }

    public static int getApplicationCacheSize() {
        return OXFProperties.instance().getPropertySet().getInteger
                (XFormsConstants.XFORMS_CACHE_APPLICATION_SIZE_PROPERTY, XFormsConstants.DEFAULT_APPLICATION_STATE_CACHE_SIZE).intValue();
    }

    public static boolean isGZIPState() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_GZIP_STATE_PROPERTY, XFormsConstants.DEFAULT_GZIP_STATE).booleanValue();
    }

    public static boolean isHostLanguageAVTs() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (XFormsConstants.XFORMS_HOST_LANGUAGE_AVTS_PROPERTY, XFormsConstants.DEFAULT_HOST_LANGUAGE_AVTS).booleanValue();
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

    /**
     * Resolve attribute value templates (AVTs).
     *
     * @param pipelineContext   current pipeline context
     * @param xformsControls    XFormsControls object (to obtain context information)
     * @param element           element on which the AVT attribute is present
     * @param attributeValue    attribute value
     * @return                  resolved attribute value
     */
    public static String resolveAttributeValueTemplates(PipelineContext pipelineContext, XFormsControls xformsControls, Element element, String attributeValue) {

        if (attributeValue == null)
            return null;

        int startIndex = 0;
        int openingIndex;
        final StringBuffer sb = new StringBuffer();
        while ((openingIndex = attributeValue.indexOf('{', startIndex)) != -1) {
            sb.append(attributeValue.substring(startIndex, openingIndex));
            final int closingIndex = attributeValue.indexOf('}', openingIndex + 1);
            if (closingIndex == -1)
                throw new OXFException("Missing closing '}' in attribute value: " + attributeValue);
            final String xpathExpression = attributeValue.substring(openingIndex + 1, closingIndex);

            final String result = xformsControls.getContainingDocument().getEvaluator().evaluateAsString(pipelineContext, xformsControls.getCurrentSingleNode(),
                    xpathExpression, Dom4jUtils.getNamespaceContextNoDefault(element), null, xformsControls.getFunctionLibrary(), null);

            sb.append(result);
            startIndex = closingIndex + 1;
        }
        sb.append(attributeValue.substring(startIndex));
        return sb.toString();
    }

    public static interface InstanceWalker {
        public void walk(NodeInfo nodeInfo, InstanceData updatedInstanceData);
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

    /**
     * Prefix an id with the container namespace if needed. If the id is null, return null.
     *
     * @param containingDocument    current ContainingDocument
     * @param id                    id to prefix
     * @return                      prefixed id or null
     */
    public static String namespaceId(XFormsContainingDocument containingDocument, String id) {
        if (id == null)
            return null;
        else
            return containingDocument.getContainerNamespace() + id;
    }

    /**
     * Return LocationData for a given node, null if not found.
     *
     * @param node  node containing the LocationData
     * @return      LocationData or null
     */
    public static LocationData getNodeLocationData(Node node) {
        final Object data;
        {
            if (node instanceof Element)
                data = ((Element) node).getData();
            else if (node instanceof Attribute)
                data = ((Attribute) node).getData();
            else
                data = null;
            // TODO: other node types
        }
        if (data == null)
            return null;
        if (data instanceof LocationData)
            return (LocationData) node;
        if (data instanceof InstanceData)
            return ((InstanceData) data).getLocationData();

        return null;
    }

    public static Node getNodeFromNodeInfoConvert(NodeInfo nodeInfo, String errorMessage) {
        if (nodeInfo instanceof NodeWrapper)
            return getNodeFromNodeInfo(nodeInfo, errorMessage);

//        if (!(nodeInfo instanceof DocumentInfo))
//            throw new OXFException(errorMessage);// TODO: cannot convert for now, but should!

        return TransformerUtils.tinyTreeToDom4j(nodeInfo);
    }

    /**
     * Return the underlying Node from the given NodeInfo if possible. If not, throw an exception with the given error
     * message.
     */
    public static Node getNodeFromNodeInfo(NodeInfo nodeInfo, String errorMessage) {
        if (!(nodeInfo instanceof NodeWrapper))
            throw new OXFException(errorMessage);

        return (Node) ((NodeWrapper) nodeInfo).getUnderlyingNode();
    }

    public static List getChildrenElements(NodeInfo nodeInfo) {
        final List result = new ArrayList();
        final AxisIterator i = nodeInfo.iterateAxis(Axis.CHILD);
        i.next();
        while (i.current() != null) {
            final Item current = i.current();
            if (current instanceof NodeInfo) {
                final NodeInfo currentNodeInfo = (NodeInfo) current;
                if (currentNodeInfo.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE) {
                    result.add(currentNodeInfo);
                }
            }
            i.next();
        }
        return result;
    }

    public static List getAttributes(NodeInfo nodeInfo) {

        if (nodeInfo.getNodeKind() != org.w3c.dom.Document.ELEMENT_NODE)
            throw new OXFException("Invalid node type passed to getAttributes(): " + nodeInfo.getNodeKind());

        final List result = new ArrayList();
        final AxisIterator i = nodeInfo.iterateAxis(Axis.ATTRIBUTE);
        i.next();
        while (i.current() != null) {
            final Item current = i.current();
            if (current instanceof NodeInfo) {
                final NodeInfo currentNodeInfo = (NodeInfo) current;
                if (currentNodeInfo.getNodeKind() == org.w3c.dom.Document.ATTRIBUTE_NODE) {
                    result.add(currentNodeInfo);
                }
            }
            i.next();
        }
        return result;
    }

    public static String getFirstTextNodeValue(NodeInfo nodeInfo) {
        // NOTE: We could probably optimize this for dom4j
        if (nodeInfo.hasChildNodes()) {
            final AxisIterator i = nodeInfo.iterateAxis(Axis.CHILD);
            i.next();
            while (i.current() != null) {
                final Item current = i.current();
                if (current instanceof NodeInfo) {
                    final NodeInfo currentNodeInfo = (NodeInfo) current;
                    if (currentNodeInfo.getNodeKind() == org.w3c.dom.Document.TEXT_NODE) {
                        return currentNodeInfo.getStringValue();
                    }
                }
                i.next();
            }
        }
        // TODO: Check: if we bind to an element that doesn't have a "first text node", do we return ""?
        return "";
    }

    /**
     * Create a JavaScript function name based on a script id.
     *
     * @param scriptId  id of the script
     * @return          JavaScript function name
     */
    public static String scriptIdToScriptName(String scriptId) {
        return scriptId.replace('-', '_') + "_xforms_function";
    }
}
