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
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DebugProcessor;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xforms.event.events.XFormsLinkErrorEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.oxf.xml.dom4j.LocationDocumentSource;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.*;
import org.w3c.tidy.Tidy;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
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

    private static final int SRC_CONTENT_BUFFER_SIZE = 1024;

    // For XForms Classic only.
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
     * @param instance          instance to iterate
     * @param instanceWalker    walker to call back
     * @param allNodes          all the nodes, otherwise only leaf data nodes
     */
    public static void iterateInstanceData(XFormsInstance instance, InstanceWalker instanceWalker, boolean allNodes) {
        iterateInstanceData(instance.getInstanceRootElementInfo(), instanceWalker, allNodes);
    }

    private static void iterateInstanceData(NodeInfo elementNodeInfo, InstanceWalker instanceWalker, boolean allNodes) {

        final List childrenElements = getChildrenElements(elementNodeInfo);

        // We "walk" an element which contains elements only if allNodes == true
        if (allNodes || childrenElements.size() == 0)
            instanceWalker.walk(elementNodeInfo);

        // "walk" current element's attributes
        for (Iterator i = getAttributes(elementNodeInfo).iterator(); i.hasNext();) {
            final NodeInfo attributeNodeInfo = (NodeInfo) i.next();
            instanceWalker.walk(attributeNodeInfo);
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
            return encodeXML(pipelineContext, TransformerUtils.domToDom4jDocument(node), XFormsProperties.getXFormsPassword(), false);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    public static String encodeXML(PipelineContext pipelineContext, Document documentToEncode, boolean encodeLocationData) {
        return encodeXML(pipelineContext, documentToEncode, XFormsProperties.getXFormsPassword(), encodeLocationData);
    }

    // Use a Deflater pool as creating Deflaters is expensive
    final static SoftReferenceObjectPool deflaterPool = new SoftReferenceObjectPool(new DeflaterPoolableObjetFactory());

    public static String encodeXML(PipelineContext pipelineContext, Document documentToEncode, String encryptionPassword, boolean encodeLocationData) {
        //        XFormsServer.logger.debug("XForms - encoding XML.");

        // Get SAXStore
        // TODO: This is not optimal since we create a second in-memory representation. Should stream instead.
        final SAXStore saxStore;
        try {
            saxStore = new SAXStore();
            final SAXResult saxResult = new SAXResult(saxStore);
            final Transformer identity = TransformerUtils.getIdentityTransformer();
            final Source source = encodeLocationData ? new LocationDocumentSource(documentToEncode) : new DocumentSource(documentToEncode);
            identity.transform(source, saxResult);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }

        // Serialize SAXStore to bytes
        // TODO: This is not optimal since we create a third in-memory representation. Should stream instead.
        final byte[] bytes;
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            saxStore.writeExternal(new ObjectOutputStream(byteArrayOutputStream));
            bytes = byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new OXFException(e);
        }

        // Encode bytes
        return encodeBytes(pipelineContext, bytes, encryptionPassword);
    }

    public static String encodeBytes(PipelineContext pipelineContext, byte[] bytesToEncode, String encryptionPassword) {
        Deflater deflater = null;
        try {
            // Compress if needed
            final byte[] gzipByteArray;
            if (XFormsProperties.isGZIPState()) {
                deflater = (Deflater) deflaterPool.borrowObject();
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                final DeflaterGZIPOutputStream gzipOutputStream = new DeflaterGZIPOutputStream(deflater, byteArrayOutputStream, 1024);
                gzipOutputStream.write(bytesToEncode);
                gzipOutputStream.close();
                gzipByteArray = byteArrayOutputStream.toByteArray();
            } else {
                gzipByteArray = null;
            }

            // Encrypt if needed
            if (encryptionPassword != null) {
                // Perform encryption
                if (gzipByteArray == null) {
                    // The data was not compressed
                    return "X1" + SecureUtils.encrypt(pipelineContext, encryptionPassword, bytesToEncode).replace((char) 0xa, ' ');
                } else {
                    // The data was compressed
                    return "X2" + SecureUtils.encrypt(pipelineContext, encryptionPassword, gzipByteArray).replace((char) 0xa, ' ');
                }
            } else {
                // No encryption
                if (gzipByteArray == null) {
                    // The data was not compressed
                    return "X3" + Base64.encode(bytesToEncode).replace((char) 0xa, ' ');
                } else {
                    // The data was compressed
                    return "X4" + Base64.encode(gzipByteArray).replace((char) 0xa, ' ');
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

    private static org.w3c.dom.Document htmlStringToDocument(String value, LocationData locationData) {
        // Create and configure Tidy instance
        final Tidy tidy = new Tidy();
        tidy.setShowWarnings(false);
        tidy.setQuiet(true);
        tidy.setInputEncoding("utf-8");
        //tidy.setNumEntities(true); // CHECK: what does this do exactly?

        // Parse and output to SAXResult
        final byte[] valueBytes;
        try {
            valueBytes = value.getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e); // will not happen
        }
        try {
            final InputStream is = new ByteArrayInputStream(valueBytes);
            return tidy.parseDOM(is, null);
        } catch (Exception e) {
            throw new ValidationException("Cannot parse value as text/html for value: '" + value + "'", locationData);
        }
    }

    public static void streamHTMLFragment(final ContentHandler contentHandler, String value, LocationData locationData, final String xhtmlPrefix) {
        
        if (value != null && value.trim().length() > 0) { // don't parse blank values

//            final boolean useTagSoup = false;
//
//            if (useTagSoup) {
//                try {
//                    final XMLReader xmlReader = new org.ccil.cowan.tagsoup.Parser();
//		            final HTMLSchema theSchema = new HTMLSchema();
//
//                    xmlReader.setProperty(org.ccil.cowan.tagsoup.Parser.schemaProperty, theSchema);
//                    xmlReader.setContentHandler(new HTMLBodyContentHandler(contentHandler, xhtmlPrefix));
//
//                    final InputSource inputSource = new InputSource();
//                    inputSource.setCharacterStream(new StringReader(value));
//
//                    xmlReader.parse(inputSource);
//                } catch (SAXException e) {
//                    throw new OXFException(e);
//                } catch (IOException e) {
//                    throw new OXFException(e);
//                }
//
////			r.setFeature(Parser.CDATAElementsFeature, false);
////			r.setFeature(Parser.namespacesFeature, false);
////			r.setFeature(Parser.ignoreBogonsFeature, true);
////			r.setFeature(Parser.bogonsEmptyFeature, false);
////			r.setFeature(Parser.defaultAttributesFeature, false);
////			r.setFeature(Parser.translateColonsFeature, true);
////			r.setFeature(Parser.restartElementsFeature, false);
////			r.setFeature(Parser.ignorableWhitespaceFeature, true);
////			r.setProperty(Parser.scannerProperty, new PYXScanner());
////          r.setProperty(Parser.lexicalHandlerProperty, h);
//
//            } else {

                final org.w3c.dom.Document htmlDocument = htmlStringToDocument(value, locationData);

                // Stream fragment to the output
                try {
                    if (htmlDocument != null) {
                        final Transformer identity = TransformerUtils.getIdentityTransformer();
                        identity.transform(new DOMSource(htmlDocument), new SAXResult(new HTMLBodyContentHandler(contentHandler, xhtmlPrefix)));
                    }
                } catch (TransformerException e) {
                    throw new OXFException(e);
                }
//            }
        }
    }

    /**
     * Get the value of a child element by pushing the context of the child element on the binding stack first, then
     * calling getElementValue() and finally popping the binding context.
     *
     * @param pipelineContext       current PipelineContext
     * @param containingDocument    current XFormsContainingDocument
     * @param childElement          element to evaluate (xforms:label, etc.)
     * @param acceptHTML            whether the result may contain HTML
     * @param containsHTML          whether the result actually contains HTML (null allowed)
     * @return                      string containing the result of the evaluation, null if evaluation failed
     */
    public static String getChildElementValue(final PipelineContext pipelineContext, final XFormsContainingDocument containingDocument,
                                              final Element childElement, final boolean acceptHTML, boolean[] containsHTML) {

        // Check that there is a current child element
        if (childElement == null)
            return null;

        // Child element becomes the new binding
        final XFormsContextStack contextStack = containingDocument.getXFormsControls().getContextStack();
        contextStack.pushBinding(pipelineContext, childElement);
        final String result = getElementValue(pipelineContext, containingDocument, contextStack, childElement, acceptHTML, containsHTML);
        contextStack.popBinding();
        return result;
    }

    /**
     * Get the value of an element by trying single-node binding, value attribute, linking attribute, and inline value
     * (including nested XHTML and xforms:output elements).
     *
     * This may return an HTML string if HTML is accepted and found, or a plain string otherwise.
     *
     * @param pipelineContext       current PipelineContext
     * @param containingDocument    current XFormsContainingDocument
     * @param contextStack          context stack for XPath evaluation
     * @param childElement          element to evaluate (xforms:label, etc.)
     * @param acceptHTML            whether the result may contain HTML
     * @param containsHTML          whether the result actually contains HTML (null allowed)
     * @return                      string containing the result of the evaluation, null if evaluation failed
     */
    public static String getElementValue(final PipelineContext pipelineContext, final XFormsContainingDocument containingDocument,
                                         final XFormsContextStack contextStack,
                                         final Element childElement, final boolean acceptHTML, final boolean[] containsHTML) {

        // No HTML found by default
        if (containsHTML != null)
            containsHTML[0] = false;

        final XFormsContextStack.BindingContext currentBindingContext = contextStack.getCurrentBindingContext();

        // "the order of precedence is: single node binding attributes, linking attributes, inline text."

        // Try to get single node binding
        {
            final boolean hasSingleNodeBinding = currentBindingContext.isNewBind();
            if (hasSingleNodeBinding) {
                final NodeInfo currentNode = currentBindingContext.getSingleNode();
                if (currentNode != null) {
                    final String tempResult = XFormsInstance.getValueForNodeInfo(currentNode);
                    return (acceptHTML && containsHTML == null) ? XMLUtils.escapeXMLMinimal(tempResult) : tempResult;
                } else
                    return null;
            }
        }

        // Try to get value attribute
        // NOTE: This is an extension attribute not standard in XForms 1.0 or 1.1
        {
            final String valueAttribute = childElement.attributeValue("value");
            final boolean hasValueAttribute = valueAttribute != null;
            if (hasValueAttribute) {
                final List currentNodeset = currentBindingContext.getNodeset();
                if (currentNodeset != null && currentNodeset.size() > 0) {
                    final String tempResult = XPathCache.evaluateAsString(pipelineContext,
                            currentNodeset, currentBindingContext.getPosition(),
                            valueAttribute, containingDocument.getStaticState().getNamespaceMappings(childElement),
                            contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                            contextStack.getFunctionContext(), null,
                            (LocationData) childElement.getData());

                    return (acceptHTML && containsHTML == null) ? XMLUtils.escapeXMLMinimal(tempResult) : tempResult;
                } else {
                    return null;
                }
            }
        }

        // Try to get linking attribute
        // NOTE: This is deprecated in XForms 1.1
        {
            final String srcAttributeValue = childElement.attributeValue("src");
            final boolean hasSrcAttribute = srcAttributeValue != null;
            if (hasSrcAttribute) {
                try {
                    // NOTE: This should probably be cached, but on the other hand almost nobody uses @src
                    final String tempResult  = retrieveSrcValue(srcAttributeValue);
                    if (containsHTML != null)
                        containsHTML[0] = false; // NOTE: we could support HTML if the media type returned is text/html
                    return (acceptHTML && containsHTML == null) ? XMLUtils.escapeXMLMinimal(tempResult) : tempResult;
                } catch (IOException e) {
                    // Dispatch xforms-link-error to model
                    final XFormsModel currentModel = currentBindingContext.getModel();
                    containingDocument.dispatchEvent(pipelineContext, new XFormsLinkErrorEvent(currentModel, srcAttributeValue, childElement, e));
                    return null;
                }
            }
        }

        // Try to get inline value
        {
            final FastStringBuffer sb = new FastStringBuffer(20);

            // Visit the subtree and serialize

            // NOTE: It is a litte funny to do our own serialization here, but the alternative is to build a DOM and
            // serialize it, which is not trivial because of the possible interleaved xforms:output's. Furthermore, we
            // perform a very simple serialization of elements and text to simple (X)HTML, not full-fledged HTML or XML
            // serialization.
            Dom4jUtils.visitSubtree(childElement, new Dom4jUtils.VisitorListener() {

                public void startElement(Element element) {
                    if (element.getQName().equals(XFormsConstants.XFORMS_OUTPUT_QNAME)) {
                        // This is an xforms:output nested among other markup

                        final XFormsOutputControl outputControl = new XFormsOutputControl(containingDocument, null, element, element.getName(), null);
                        contextStack.pushBinding(pipelineContext, element);
                        {
                            outputControl.setBindingContext(contextStack.getCurrentBindingContext());
                            outputControl.evaluateIfNeeded(pipelineContext);
                        }
                        contextStack.popBinding();

                        if (acceptHTML) {
                            if ("text/html".equals(outputControl.getMediatype())) {
                                if (containsHTML != null)
                                    containsHTML[0] = true; // this indicates for sure that there is some nested HTML
                                sb.append(outputControl.getDisplayValueOrExternalValue(pipelineContext));
                            } else {
                                // Mediatype is not HTML so we don't escape
                                sb.append(XMLUtils.escapeXMLMinimal(outputControl.getDisplayValueOrExternalValue(pipelineContext)));
                            }
                        } else {
                            if ("text/html".equals(outputControl.getMediatype())) {
                                // HTML is not allowed here, better tell the user
                                throw new OXFException("HTML not allowed within element: " + childElement.getName());
                            } else {
                                // Mediatype is not HTML so we don't escape
                                sb.append(outputControl.getDisplayValueOrExternalValue(pipelineContext));
                            }
                        }
                    } else {
                        // This is a regular element, just serialize the start tag to no namespace

                        // If HTML is not allowed here, better tell the user
                        if (!acceptHTML)
                            throw new OXFException("Nested XHTML or XForms not allowed within element: " + childElement.getName());

                        if (containsHTML != null)
                            containsHTML[0] = true;// this indicates for sure that there is some nested HTML

                        sb.append('<');
                        sb.append(element.getName());
                        final List attributes = element.attributes();
                        if (attributes.size() > 0) {
                            for (Iterator i = attributes.iterator(); i.hasNext();) {
                                final Attribute currentAttribute = (Attribute) i.next();

                                final String currentName = currentAttribute.getName();
                                final String currentValue = currentAttribute.getValue();

                                // Only consider attributes in no namespace
                                if ("".equals(currentAttribute.getNamespaceURI())) {
                                    sb.append(' ');
                                    sb.append(currentName);
                                    sb.append("=\"");
                                    sb.append(XMLUtils.escapeXMLMinimal(currentValue));
                                    sb.append('"');
                                }
                            }
                        }
                        sb.append('>');
                    }
                }

                public void endElement(Element element) {
                    if (!element.getQName().equals(XFormsConstants.XFORMS_OUTPUT_QNAME)) {
                        // This is a regular element, just serialize the end tag to no namespace
                        sb.append("</");
                        sb.append(element.getName());
                        sb.append('>');
                    }
                }

                public void text(Text text) {
                    sb.append(acceptHTML ? XMLUtils.escapeXMLMinimal(text.getStringValue()) : text.getStringValue());
                }
            });
            if (acceptHTML && containsHTML != null && !containsHTML[0]) {
                // We went through the subtree and did not find any HTML
                // If the caller supports the information, return a non-escaped string so we can optimize output later
                return XMLUtils.unescapeXMLMinimal(sb.toString());
            } else {
                // We found some HTML, just return it
                return sb.toString();
            }
        }
    }

    /**
     * Compare two strings, handling null values as well.
     *
     * @param value1    first value or null
     * @param value2    second value or null
     * @return          whether the values are identical or both null
     */
    public static final boolean compareStrings(String value1, String value2) {
        return (value1 == null && value2 == null) || (value1 != null && value2 != null && value1.equals(value2));
    }

    private static class DeflaterPoolableObjetFactory implements PoolableObjectFactory {
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

    public static org.w3c.dom.Document decodeXMLAsDOM(PipelineContext pipelineContext, String encodedXML) {
        try {
            return TransformerUtils.dom4jToDomDocument(XFormsUtils.decodeXML(pipelineContext, encodedXML));
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    public static Document decodeXML(PipelineContext pipelineContext, String encodedXML) {
        return decodeXML(pipelineContext, encodedXML, XFormsProperties.getXFormsPassword());
    }

    public static Document decodeXML(PipelineContext pipelineContext, String encodedXML, String encryptionPassword) {

        final byte[] bytes = decodeBytes(pipelineContext, encodedXML, encryptionPassword);

        // Deserialize bytes to SAXStore
        // TODO: This is not optimal
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        final SAXStore saxStore;
        try {
            saxStore = new SAXStore(new ObjectInputStream(byteArrayInputStream));
        } catch (IOException e) {
            throw new OXFException(e);
        }

        // Deserialize SAXStore to dom4j document
        // TODO: This is not optimal
        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
        final LocationDocumentResult result = new LocationDocumentResult();
        identity.setResult(result);
        try {
            saxStore.replay(identity);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
        return result.getDocument();
    }

//    public static String decodeString(PipelineContext pipelineContext, String encoded) {
//        try {
//            return new String(decodeBytes(pipelineContext, encoded,  getEncryptionKey()), "utf-8");
//        } catch (UnsupportedEncodingException e) {
//            throw new OXFException(e);// won't happen
//        }
//    }

    public static byte[] decodeBytes(PipelineContext pipelineContext, String encoded, String encryptionPassword) {
        try {
            // Get raw text
            byte[] resultBytes;
            {
                final String prefix = encoded.substring(0, 2);
                final String encodedString = encoded.substring(2);

                final byte[] resultBytes1;
                final byte[] gzipByteArray;
                if (prefix.equals("X1")) {
                    // Encryption + uncompressed
                    resultBytes1 = SecureUtils.decrypt(pipelineContext, encryptionPassword, encodedString);
                    gzipByteArray = null;
                } else if (prefix.equals("X2")) {
                    // Encryption + compressed
                    resultBytes1 = null;
                    gzipByteArray = SecureUtils.decrypt(pipelineContext, encryptionPassword, encodedString);
                } else if (prefix.equals("X3")) {
                    // No encryption + uncompressed
                    resultBytes1 = Base64.decode(encodedString);
                    gzipByteArray = null;
                } else if (prefix.equals("X4")) {
                    // No encryption + compressed
                    resultBytes1 = null;
                    gzipByteArray = Base64.decode(encodedString);
                } else {
                    throw new OXFException("Invalid prefix for encoded string: " + prefix);
                }

                // Decompress if needed
                if (gzipByteArray != null) {
                    final ByteArrayInputStream compressedData = new ByteArrayInputStream(gzipByteArray);
                    final GZIPInputStream gzipInputStream = new GZIPInputStream(compressedData);
                    final ByteArrayOutputStream binaryData = new ByteArrayOutputStream(1024);
                    NetUtils.copyStream(gzipInputStream, binaryData);
                    resultBytes = binaryData.toByteArray();
                } else {
                    resultBytes = resultBytes1;
                }
            }
            return resultBytes;

        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static String retrieveSrcValue(String src) throws IOException {
        final URL url = URLFactory.createURL(src);

        // Load file into buffer
        final InputStreamReader reader = new InputStreamReader(url.openStream());
        try {
            final StringBuffer value = new StringBuffer();
            final char[] buff = new char[SRC_CONTENT_BUFFER_SIZE];
            int c = 0;
            while ((c = reader.read(buff, 0, SRC_CONTENT_BUFFER_SIZE - 1)) != -1) value.append(buff, 0, c);
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

        if (currentType.equals(XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME)) {
            // Convert from xs:base64Binary to xs:anyURI
            return NetUtils.base64BinaryToAnyURI(pipelineContext, value, NetUtils.REQUEST_SCOPE);
        } else {
            // Convert from xs:anyURI to xs:base64Binary
            return NetUtils.anyURIToBase64Binary(value);
        }
    }

    public static String resolveURLDoReplace(XFormsContainingDocument containingDocument, PipelineContext pipelineContext, Element currentElement, String url) {
        final boolean isPortletLoad = "portlet".equals(containingDocument.getContainerType());

        final URI resolvedURI = resolveXMLBase(currentElement, url);
        final String resolvedURISTring = resolvedURI.toString();
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        final String externalURL;
        // NOTE: Keep in mind that this is going to run from within a servlet, as the XForms server
        // runs in a servlet when processing these events!
        if (!isPortletLoad) {
            // XForms page was loaded from a servlet
            externalURL = externalContext.getResponse().rewriteRenderURL(resolvedURISTring);
        } else {
            // XForms page was loaded from a portlet
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
        }

        return externalURL;
    }

    public static String resolveResourceURL(PipelineContext pipelineContext, Element currentElement, String url, boolean absolute) {

        final URI resolvedURI = resolveXMLBase(currentElement, url);
        final String resolvedURISTring = resolvedURI.toString();
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        return externalContext.getResponse().rewriteResourceURL(resolvedURISTring, absolute);
    }

    /**
     * Resolve attribute value templates (AVTs).
     *
     * @param pipelineContext    current pipeline context
     * @param contextItems       context items
     * @param contextPosition    context position
     * @param variableToValueMap variables
     * @param functionLibrary    XPath function libary to use
     * @param functionContext    context object to pass to the XForms function
     * @param prefixToURIMap     namespace mappings
     * @param locationData       LocationData for error reporting
     * @param attributeValue     attribute value
     * @return                   resolved attribute value
     */
    public static String resolveAttributeValueTemplates(PipelineContext pipelineContext, List contextItems, int contextPosition, Map variableToValueMap,
                                                        FunctionLibrary functionLibrary, XPathCache.FunctionContext functionContext,
                                                        Map prefixToURIMap, LocationData locationData, String attributeValue) {

        if (attributeValue == null)
            return null;

        return XPathCache.evaluateAsAvt(pipelineContext, contextItems, contextPosition, attributeValue, prefixToURIMap,
                variableToValueMap, functionLibrary, functionContext, null, locationData);
    }

    /**
     * Resolve attribute value templates (AVTs).
     *
     * @param pipelineContext    current pipeline context
     * @param contextNode        context node for evaluation
     * @param variableToValueMap variables
     * @param functionLibrary    XPath function libary to use
     * @param functionContext    context object to pass to the XForms function
     * @param prefixToURIMap     namespace mappings
     * @param locationData       LocationData for error reporting
     * @param attributeValue     attribute value
     * @return                   resolved attribute value
     */
    public static String resolveAttributeValueTemplates(PipelineContext pipelineContext, NodeInfo contextNode, Map variableToValueMap,
                                                        FunctionLibrary functionLibrary, XPathCache.FunctionContext functionContext,
                                                        Map prefixToURIMap, LocationData locationData, String attributeValue) {

        if (attributeValue == null)
            return null;

        return XPathCache.evaluateAsAvt(pipelineContext, contextNode, attributeValue, prefixToURIMap,
                variableToValueMap, functionLibrary, functionContext, null, locationData);
    }

    /**
     * Encode a Human Readable Resource Identifier to a URI. Leading and trailing spaces are removed first.
     *
     * @param uriString    URI to encode
     * @param processSpace whether to process the space character or leave it unchanged
     * @return             encoded URI
     */
    public static String encodeHRRI(String uriString, boolean processSpace) {

        // Note that the XML Schema spec says "Spaces are, in principle, allowed in the ·lexical space· of anyURI,
        // however, their use is highly discouraged (unless they are encoded by %20).".

        // We assume that we never want leading or trailing spaces. You can use %20 if you realy want this.
        uriString = uriString.trim();

        // We try below to follow the "Human Readable Resource Identifiers" RFC, in draft as of 2007-06-06.
        // * the control characters #x0 to #x1F and #x7F to #x9F
        // * space #x20
        // * the delimiters "<" #x3C, ">" #x3E, and """ #x22
        // * the unwise characters "{" #x7B, "}" #x7D, "|" #x7C, "\" #x5C, "^" #x5E, and "`" #x60
        final FastStringBuffer sb = new FastStringBuffer(uriString.length() * 2);
        for (int i = 0; i < uriString.length(); i++) {
            final char currentChar = uriString.charAt(i);

            if (currentChar >= 0
                    && (currentChar <= 0x1f || (processSpace && currentChar == 0x20) || currentChar == 0x22
                     || currentChar == 0x3c || currentChar == 0x3e
                     || currentChar == 0x5c || currentChar == 0x5e || currentChar == 0x60
                     || (currentChar >= 0x7b && currentChar <= 0x7d)
                     || (currentChar >= 0x7f && currentChar <= 0x9f))) {
                sb.append('%');
                sb.append(NumberUtils.toHexString((byte) currentChar).toUpperCase());
            } else {
                sb.append(currentChar);
            }
        };

        return sb.toString();
    }

    public static interface InstanceWalker {
        public void walk(NodeInfo nodeInfo);
    }

    /**
     * Resolve a URI string against an element, taking into account ancestor xml:base attributes for
     * the resolution.
     *
     * @param element   element used to start resolution
     * @param uri       URI to resolve
     * @return          resolved URI
     */
    public static URI resolveXMLBase(Element element, String uri) {
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
     * Resolve f:url-norewrite attributes on this element, taking into account ancestor f:url-norewrite attributes for
     * the resolution.
     *
     * @param element   element used to start resolution
     * @return          true if rewriting is turned off, false otherwise
     */
    public static boolean resolveUrlNorewrite(Element element) {
        Element currentElement = element;
        do {
            final String urlNorewriteAttribute = currentElement.attributeValue(XMLConstants.FORMATTING_URL_NOREWRITE_QNAME);
            // Return the first ancestor value found
            if (urlNorewriteAttribute != null)
                return "true".equals(urlNorewriteAttribute);
            currentElement = currentElement.getParent();
        } while(currentElement != null);

        // Default is to rewrite
        return false;
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
     * Remove the container namespace prefix if possible. If the id is null, return null.
     *
     * @param containingDocument    current ContainingDocument
     * @param id                    id to de-prefix
     * @return                      de-prefixed id if possible or null
     */
    public static String deNamespaceId(XFormsContainingDocument containingDocument, String id) {
        if (id == null)
            return null;

        final String containerNamespace = containingDocument.getContainerNamespace();
        if (containerNamespace.length() > 0 && id.startsWith(containerNamespace))
            return id.substring(containerNamespace.length());
        else
            return id;
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
        else if (data instanceof LocationData)
            return (LocationData) data;
        else if (data instanceof InstanceData)
            return ((InstanceData) data).getLocationData();

        return null;
    }

    public static Node getNodeFromNodeInfoConvert(NodeInfo nodeInfo, String errorMessage) {
        if (nodeInfo instanceof NodeWrapper)
            return getNodeFromNodeInfo(nodeInfo, errorMessage);
        else
            return TransformerUtils.tinyTreeToDom4j2((nodeInfo.getParent() instanceof DocumentInfo) ? nodeInfo.getParent() : nodeInfo);
    }

    /**
     * Return the underlying Node from the given NodeInfo if possible. If not, throw an exception with the given error
     * message.
     *
     * @param nodeInfo      NodeInfo to process
     * @param errorMessage  error message to throw
     * @return              Node if found
     */
    public static Node getNodeFromNodeInfo(NodeInfo nodeInfo, String errorMessage) {
        if (!(nodeInfo instanceof NodeWrapper))
            throw new OXFException(errorMessage);

        return (Node) ((NodeWrapper) nodeInfo).getUnderlyingNode();
    }

    /**
     * Get an element's children elements if any.
     *
     * @param nodeInfo  element NodeInfo to look at
     * @return          elements NodeInfo or empty list
     */
    public static List getChildrenElements(NodeInfo nodeInfo) {
        final List result = new ArrayList();
        getChildrenElements(result, nodeInfo);
        return result;
    }

    /**
     * Get an element's children elements if any.
     *
     * @param result    List to which to add the elements found
     * @param nodeInfo  element NodeInfo to look at
     */
    public static void getChildrenElements(List result, NodeInfo nodeInfo) {
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
    }

    /**
     * Return whether the given node has at least one child element.
     *
     * @param nodeInfo  NodeInfo to look at
     * @return          true iff NodeInfo has at least one child element
     */
    public static boolean hasChildrenElements(NodeInfo nodeInfo) {
        final AxisIterator i = nodeInfo.iterateAxis(Axis.CHILD);
        i.next();
        while (i.current() != null) {
            final Item current = i.current();
            if (current instanceof NodeInfo) {
                final NodeInfo currentNodeInfo = (NodeInfo) current;
                if (currentNodeInfo.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE) {
                    return true;
                }
            }
            i.next();
        }
        return false;
    }

    /**
     * Get an element's attributes if any.
     *
     * @param nodeInfo  element NodeInfo to look at
     * @return          attributes or empty list
     */
    public static List getAttributes(NodeInfo nodeInfo) {
        final List result = new ArrayList();
        getAttributes(result, nodeInfo);
        return result;
    }

    /**
     * Get an element's attributes if any.
     *
     * @param result    List to which to add the attributes found
     * @param nodeInfo  element NodeInfo to look at
     */
    public static void getAttributes(List result, NodeInfo nodeInfo) {

        if (nodeInfo.getNodeKind() != org.w3c.dom.Document.ELEMENT_NODE)
            throw new OXFException("Invalid node type passed to getAttributes(): " + nodeInfo.getNodeKind());

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
    }

    /**
     * Find all attributes and nested nodes of the given nodeset.
     */
    public static void getNestedAttributesAndElements(List result, List nodeset) {
        // Iterate through all nodes
        if (nodeset.size() > 0) {
            for (Iterator i = nodeset.iterator(); i.hasNext();) {
                final NodeInfo currentNodeInfo = (NodeInfo) i.next();

                if (currentNodeInfo.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE) {
                    // Found an element

                    // Add attributes
                    getAttributes(result, currentNodeInfo);

                    // Find children elements
                    final List childrenElements = getChildrenElements(currentNodeInfo);

                    // Add all children elements
                    result.addAll(childrenElements);

                    // Recurse into children elements
                    getNestedAttributesAndElements(result, childrenElements);
                }
            }
        }
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
