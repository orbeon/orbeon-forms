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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.pool.PoolableObjectFactory;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DebugProcessor;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
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
import org.orbeon.saxon.value.*;
import org.w3c.tidy.Tidy;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.ccil.cowan.tagsoup.HTMLSchema;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.dom.DOMResult;
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

    // Binary types supported for upload, images, etc.
    private static final Map SUPPORTED_BINARY_TYPES = new HashMap();
    static {
        SUPPORTED_BINARY_TYPES.put(XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME, "");
        SUPPORTED_BINARY_TYPES.put(XMLConstants.XS_ANYURI_EXPLODED_QNAME, "");
        SUPPORTED_BINARY_TYPES.put(XFormsConstants.XFORMS_BASE64BINARY_EXPLODED_QNAME, "");
        SUPPORTED_BINARY_TYPES.put(XFormsConstants.XFORMS_ANYURI_EXPLODED_QNAME, "");
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
                    // The data was not compressed above
                    return "X1" + SecureUtils.encrypt(pipelineContext, encryptionPassword, bytesToEncode);
                } else {
                    // The data was compressed above
                    return "X2" + SecureUtils.encrypt(pipelineContext, encryptionPassword, gzipByteArray);
                }
            } else {
                // No encryption
                if (gzipByteArray == null) {
                    // The data was not compressed above
                    return "X3" + Base64.encode(bytesToEncode, false);
                } else {
                    // The data was compressed above
                    return "X4" + Base64.encode(gzipByteArray, false);
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

    public static String ensureEncrypted(PipelineContext pipelineContext, String encoded) {
        if (encoded.startsWith("X3") || encoded.startsWith("X4")) {
            // Data is currently not encrypted, so encrypt it
            final byte[] decodedValue = XFormsUtils.decodeBytes(pipelineContext, encoded, XFormsProperties.getXFormsPassword());
            return XFormsUtils.encodeBytes(pipelineContext, decodedValue, XFormsProperties.getXFormsPassword());
        } else {
            // Data is already encrypted
            return encoded;
        }

//        if (encoded.startsWith("X1") || encoded.startsWith("X2")) {
//            // Case where data is already encrypted
//            return encoded;
//        } else if (encoded.startsWith("X3")) {
//            // Uncompressed data to encrypt
//            final byte[] decoded = Base64.decode(encoded.substring(2));
//            return "X1" + SecureUtils.encrypt(pipelineContext, encryptionPassword, decoded).replace((char) 0xa, ' ');
//        } else if (encoded.startsWith("X4")) {
//            // Compressed data to encrypt
//            final byte[] decoded = Base64.decode(encoded.substring(2));
//            return "X2" + SecureUtils.encrypt(pipelineContext, encryptionPassword, decoded).replace((char) 0xa, ' ');
//        } else {
//            throw new OXFException("Invalid prefix for encoded data: " + encoded.substring(0, 2));
//        }
    }

    public static org.w3c.dom.Document htmlStringToDocument(String value, LocationData locationData) {
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

    public static org.w3c.dom.Document htmlStringToDocumentTagSoup(String value, LocationData locationData) {

        try {
            final XMLReader xmlReader = new org.ccil.cowan.tagsoup.Parser();
            final HTMLSchema theSchema = new HTMLSchema();

            xmlReader.setProperty(org.ccil.cowan.tagsoup.Parser.schemaProperty, theSchema);
            
            xmlReader.setFeature(org.ccil.cowan.tagsoup.Parser.ignoreBogonsFeature, true);

            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();

            final org.w3c.dom.Document document = XMLUtils.createDocument();
            final DOMResult domResult = new DOMResult(document);
            identity.setResult(domResult);

            xmlReader.setContentHandler(identity);

            final InputSource inputSource = new InputSource();
            inputSource.setCharacterStream(new StringReader(value));

            xmlReader.parse(inputSource);

            return document;
        } catch (Exception e) {
            throw new ValidationException("Cannot parse value as text/html for value: '" + value + "'", locationData);
        }
        
//			r.setFeature(Parser.CDATAElementsFeature, false);
//			r.setFeature(Parser.namespacesFeature, false);
//			r.setFeature(Parser.ignoreBogonsFeature, true);
//			r.setFeature(Parser.bogonsEmptyFeature, false);
//			r.setFeature(Parser.defaultAttributesFeature, false);
//			r.setFeature(Parser.translateColonsFeature, true);
//			r.setFeature(Parser.restartElementsFeature, false);
//			r.setFeature(Parser.ignorableWhitespaceFeature, true);
//			r.setProperty(Parser.scannerProperty, new PYXScanner());
//          r.setProperty(Parser.lexicalHandlerProperty, h);
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
     * Get the value of a child element known to have only static content.
     *
     * @param childElement          element to evaluate (xforms:label, etc.)
     * @param acceptHTML            whether the result may contain HTML
     * @param containsHTML          whether the result actually contains HTML (null allowed)
     * @return                      string containing the result of the evaluation, null if evaluation failed
     */
    public static String getStaticChildElementValue(final Element childElement, final boolean acceptHTML, final boolean[] containsHTML) {
        // Check that there is a current child element
        if (childElement == null)
            return null;

        // No HTML found by default
        if (containsHTML != null)
            containsHTML[0] = false;

        // Try to get inline value
        {
            final FastStringBuffer sb = new FastStringBuffer(20);

            // Visit the subtree and serialize

            // NOTE: It is a litte funny to do our own serialization here, but the alternative is to build a DOM and
            // serialize it, which is not trivial because of the possible interleaved xforms:output's. Furthermore, we
            // perform a very simple serialization of elements and text to simple (X)HTML, not full-fledged HTML or XML
            // serialization.

            Dom4jUtils.visitSubtree(childElement, new LHHAElementVisitorListener(acceptHTML, containsHTML, sb, childElement));
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
     * Get the value of a label, help, hint or alert related to a particular control.
     *
     * @param pipelineContext       current PipelineContext
     * @param container             current XFormsContainer
     * @param control               control
     * @param lhhaElement           element associated to the control (either as child or using @for)
     * @param acceptHTML            whether the result may contain HTML
     * @param containsHTML          whether the result actually contains HTML (null allowed)
     * @return                      string containing the result of the evaluation, null if evaluation failed
     */
    public static String getLabelHelpHintAlertValue(PipelineContext pipelineContext, XFormsContainer container,
                                                    XFormsControl control, Element lhhaElement, boolean acceptHTML, boolean[] containsHTML) {

        final XFormsContainingDocument containingDocument = container.getContainingDocument();
        final XFormsContextStack contextStack = containingDocument.getControls().getContextStack();
        final String value;
        if (lhhaElement == null) {
            // No LHHA at all
            value = null;
        } else if (lhhaElement.getParent() == control.getControlElement()) {
            // LHHA is direct child of control, evaluate within context
            contextStack.setBinding(control);
            contextStack.pushBinding(pipelineContext, lhhaElement);
            value = XFormsUtils.getElementValue(pipelineContext, container, contextStack, lhhaElement, acceptHTML, containsHTML);
            contextStack.popBinding();
        } else {
            // LHHA is somewhere else, assumed as a child of xforms:* or xxforms:*

            // Find context object for XPath evaluation
            final Element parentElement = lhhaElement.getParent();

            final String parentStaticId = parentElement.attributeValue("id");
            if (parentStaticId == null) {
                // Assume we are at the top-level
                contextStack.resetBindingContext(pipelineContext);
            } else {
                // Not at top-level, find containing object
                final Object contextObject = containingDocument.resolveObjectById(control.getEffectiveId(), parentStaticId);
                if (contextObject instanceof XFormsControl) {
                    // Found context, evaluate relative to that
                    contextStack.setBinding((XFormsControl) contextObject);
                } else {
                    // No context, don't evaluate (not sure why this should happen!)
                    contextStack.resetBindingContext(pipelineContext);
                }
            }

            // Push binding relative to context established above and evaluate
            contextStack.pushBinding(pipelineContext, lhhaElement);
            value = XFormsUtils.getElementValue(pipelineContext, container, contextStack, lhhaElement, acceptHTML, containsHTML);
            contextStack.popBinding();
        }
        return value;
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
        final XFormsContextStack contextStack = containingDocument.getControls().getContextStack();
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
     * @param container             current XFormsContainer
     * @param contextStack          context stack for XPath evaluation
     * @param childElement          element to evaluate (xforms:label, etc.)
     * @param acceptHTML            whether the result may contain HTML
     * @param containsHTML          whether the result actually contains HTML (null allowed)
     * @return                      string containing the result of the evaluation, null if evaluation failed
     */
    public static String getElementValue(final PipelineContext pipelineContext, final XFormsContainer container,
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
                            valueAttribute, container.getContainingDocument().getStaticState().getNamespaceMappings(childElement),
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
                    // NOTE: xforms-link-error is no longer in XForms 1.1 starting 2009-03-10
                    currentModel.getContainer(null).dispatchEvent(pipelineContext, new XFormsLinkErrorEvent(currentModel, srcAttributeValue, childElement, e));
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
            Dom4jUtils.visitSubtree(childElement, new LHHAElementVisitorListener(pipelineContext, container, contextStack, acceptHTML, containsHTML, sb, childElement));
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

    public static ValueRepresentation convertJavaObjectToSaxonObject(Object object) {
        final ValueRepresentation valueRepresentation;
        if (object instanceof ValueRepresentation) {
            // Native Saxon variable value
            valueRepresentation = (ValueRepresentation) object;
        } else if (object instanceof String) {
            valueRepresentation = new StringValue((String) object);
        } else if (object instanceof Boolean) {
            valueRepresentation = BooleanValue.get(((Boolean) object).booleanValue());
        } else if (object instanceof Integer) {
            valueRepresentation = new IntegerValue(((Integer) object).intValue());
        } else if (object instanceof Float) {
            valueRepresentation = new FloatValue(((Float) object).floatValue());
        } else if (object instanceof Double) {
            valueRepresentation = new DoubleValue(((Double) object).doubleValue());
        } else if (object instanceof URI) {
            valueRepresentation = new AnyURIValue(((URI) object).toString());
        } else {
            throw new OXFException("Invalid variable type: " + object.getClass());
        }
        return valueRepresentation;
    }

    /**
     * Returns whether there are relevant upload controls bound to any node of the given instance.
     *
     * @param containingDocument    current XFormsContainingDocument
     * @param currentInstance       instance to check
     * @return                      true iif there are relevant upload controls bound
     */
    public static boolean hasBoundRelevantUploadControls(XFormsContainingDocument containingDocument, XFormsInstance currentInstance) {
        final XFormsControls xformsControls = containingDocument.getControls();
        final Map uploadControls = xformsControls.getCurrentControlTree().getUploadControls();
        if (uploadControls != null) {
            for (Iterator i = uploadControls.values().iterator(); i.hasNext();) {
                final XFormsUploadControl currentControl = (XFormsUploadControl) i.next();
                if (currentControl.isRelevant()) {
                    final NodeInfo controlBoundNodeInfo = currentControl.getBoundNode();
                    if (currentInstance == currentInstance.getModel(containingDocument).getInstanceForNode(controlBoundNodeInfo)) {
                        // Found one relevant upload control bound to the instance we are submitting
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Annotate the DOM with information about file name and mediatype provided by uploads if available.
     *
     * @param pipelineContext       current PipelineContext
     * @param containingDocument    current XFormsContainingDocument
     * @param currentInstance       instance containing the nodes to check
     */
    public static void annotateBoundRelevantUploadControls(final PipelineContext pipelineContext, XFormsContainingDocument containingDocument, XFormsInstance currentInstance) {
        final XFormsControls xformsControls = containingDocument.getControls();
        final Map uploadControls = xformsControls.getCurrentControlTree().getUploadControls();
        if (uploadControls != null) {
            for (Iterator i = uploadControls.values().iterator(); i.hasNext();) {
                final XFormsUploadControl currentControl = (XFormsUploadControl) i.next();
                if (currentControl.isRelevant()) {
                    final NodeInfo controlBoundNodeInfo = currentControl.getBoundNode();
                    if (currentInstance == currentInstance.getModel(containingDocument).getInstanceForNode(controlBoundNodeInfo)) {
                        // Found one relevant upload control bound to the instance we are submitting
                        // NOTE: special MIP-like annotations were added just before re-rooting/pruning element. Those
                        // will be removed during the next recalculate.
                        final String fileName = currentControl.getFileName(pipelineContext);
                        if (fileName != null) {
                            InstanceData.setCustom(controlBoundNodeInfo, "xxforms-filename", fileName);
                        }
                        final String mediatype = currentControl.getFileMediatype(pipelineContext);
                        if (mediatype != null) {
                            InstanceData.setCustom(controlBoundNodeInfo, "xxforms-mediatype", mediatype);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the first relevant upload controls bound the given node if any.
     *
     * @param containingDocument    current XFormsContainingDocument
     * @param node                  node to check
     * @return                      first bound relevant XFormsUploadControl, null if not found
     */
    public static XFormsUploadControl getFirstBoundRelevantUploadControl(XFormsContainingDocument containingDocument, Node node) {
        final XFormsControls xformsControls = containingDocument.getControls();
        final Map uploadControls = xformsControls.getCurrentControlTree().getUploadControls();
        if (uploadControls != null) {
            for (Iterator i = uploadControls.values().iterator(); i.hasNext();) {
                final XFormsUploadControl currentControl = (XFormsUploadControl) i.next();
                if (currentControl.isRelevant()) {
                    final NodeInfo controlBoundNodeInfo = currentControl.getBoundNode();
                    if (controlBoundNodeInfo instanceof NodeWrapper) {
                        final Node controlBoundNode = getNodeFromNodeInfo(controlBoundNodeInfo, "");
                        if (node == controlBoundNode) {
                            // Found one relevant upload control bound to the given node
                            return currentControl;
                        }
                    }
                }
            }
        }
        return null;
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

        // Handle HHRI
        src = encodeHRRI(src, true);

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
        if (SUPPORTED_BINARY_TYPES.get(currentType) == null)
            throw new UnsupportedOperationException("Unsupported type: " + currentType);
        if (SUPPORTED_BINARY_TYPES.get(newType) == null)
            throw new UnsupportedOperationException("Unsupported type: " + newType);

        if (currentType.equals(XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME)) {
            // Convert from xs:base64Binary to xs:anyURI
            return NetUtils.base64BinaryToAnyURI(pipelineContext, value, NetUtils.REQUEST_SCOPE);
        } else {
            // Convert from xs:anyURI to xs:base64Binary
            return NetUtils.anyURIToBase64Binary(value);
        }
    }

    /**
     * Resolve a render or action URL including xml:base resolution.
     *
     * @param isPortletLoad         whether this is called within a portlet
     * @param pipelineContext       current PipelineContext
     * @param currentElement        element used for xml:base resolution
     * @param url                   URL to resolve
     * @param generateAbsoluteURL   whether the result must be an absolute URL (if isPortletLoad == false)
     * @return                      resolved URL
     */
    public static String resolveRenderOrActionURL(boolean isPortletLoad, PipelineContext pipelineContext, Element currentElement, String url, boolean generateAbsoluteURL) {
        final URI resolvedURI = resolveXMLBase(currentElement, url);
        final String resolvedURIString = resolvedURI.toString();
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        final String externalURL;
        // NOTE: Keep in mind that this is going to run from within a servlet, as the XForms server
        // runs in a servlet when processing these events!
        if (!isPortletLoad) {
            // XForms page was loaded from a servlet
            // TODO: check this: must probably use response rewriting methods
//            externalURL = externalContext.getResponse().rewriteRenderURL(resolvedURIString);
            externalURL = URLRewriterUtils.rewriteURL(externalContext.getRequest(), resolvedURIString,
                generateAbsoluteURL ? ExternalContext.Response.REWRITE_MODE_ABSOLUTE : ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
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
                externalURL = resolvedURIString;
            }
        }

        return externalURL;
    }

    /**
     * Resolve a resource URL including xml:base resolution.
     *
     * @param pipelineContext       current PipelineContext
     * @param element               element used to start resolution (if null, no resolution takes place)
     * @param url                   URL to resolve
     * @param rewriteMode           rewrite mode (see ExternalContext.Response)
     * @return                      resolved URL
     */
    public static String resolveResourceURL(PipelineContext pipelineContext, Element element, String url, int rewriteMode) {

        final URI resolvedURI = resolveXMLBase(element, url);

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        return externalContext.getResponse().rewriteResourceURL(resolvedURI.toString(), rewriteMode);
    }

    /**
     * Resolve a resource URL including xml:base resolution.
     *
     * @param pipelineContext       current PipelineContext
     * @param element               element used to start resolution (if null, no resolution takes place)
     * @param url                   URL to resolve
     * @param rewriteMode           rewrite mode (see ExternalContext.Response)
     * @return                      resolved URL
     */
    public static String resolveServiceURL(PipelineContext pipelineContext, Element element, String url, int rewriteMode) {

        final URI resolvedURI = resolveXMLBase(element, url);

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        return externalContext.rewriteServiceURL(resolvedURI.toString(), rewriteMode == ExternalContext.Response.REWRITE_MODE_ABSOLUTE);
    }

    /**
     * Rewrite an attribute if that attribute contains a URI, e.g. @href or @src.
     *
     * @param pipelineContext       current PipelineContext
     * @param element               element used to start resolution (if null, no resolution takes place)
     * @param attributeName         attribute name
     * @param attributeValue        attribute value
     * @return                      rewritten URL
     */
    public static String getEscapedURLAttributeIfNeeded(PipelineContext pipelineContext, Element element, String attributeName, String attributeValue) {
        final String rewrittenValue;
        if ("src".equals(attributeName) || "href".equals(attributeName)) {
            rewrittenValue = resolveResourceURL(pipelineContext, element, attributeValue, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        } else {
            rewrittenValue = attributeValue;
        }
        return rewrittenValue;
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
     * @return             encoded URI, or null if uriString was null
     */
    public static String encodeHRRI(String uriString, boolean processSpace) {

        if (uriString == null)
            return null;

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
     * @param element   element used to start resolution (if null, no resolution takes place)
     * @param uri       URI to resolve
     * @return          resolved URI
     */
    public static URI resolveXMLBase(Element element, String uri) {
        try {
            // Allow for null Element
            if (element == null)
                return new URI(uri);

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
     * Return an element's xml:base value, checking ancestors as well.
     *
     * @param element   element to check
     * @return          xml:base value or null if not found
     */
    public static String resolveXMLang(Element element) {
        // Allow for null Element
        if (element == null)
            return null;

        // Collect xml:base values
        Element currentElement = element;
        do {
            final String xmlBaseAttribute = currentElement.attributeValue(XMLConstants.XML_LANG_QNAME);
            if (xmlBaseAttribute != null)
                return xmlBaseAttribute;
            currentElement = currentElement.getParent();
        } while(currentElement != null);

        // Not found
        return null;
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
     * Return whether the given string contains a well-formed XPath 2.0 expression.
     *
     * @param xpathString   string to check
     * @return              true iif the given string contains well-formed XPath 2.0
     */
    public static boolean isXPath2Expression(String xpathString, Map namespaceMap) {
        // Empty string is never well-formed XPath
        if (xpathString.trim().length() == 0)
            return false;

        try {
            XPathCache.checkXPathExpression(xpathString, namespaceMap, XFormsContainingDocument.getFunctionLibrary());
        } catch (Exception e) {
            // Ideally we would like the parser to not throw as this is time-consuming, but not sure ho.w to achieve that
            return false;
        }

        return true;
    }

    /**
     * Create a JavaScript function name based on a script id.
     *
     * @param scriptId  id of the script
     * @return          JavaScript function name
     */
    public static String scriptIdToScriptName(String scriptId) {
        return scriptId.replace('-', '_').replace('$', '_') + "_xforms_function";
    }

    private static class LHHAElementVisitorListener implements Dom4jUtils.VisitorListener {
        private final PipelineContext pipelineContext;
        private final XFormsContainer container;
        private final XFormsContextStack contextStack;
        private final boolean acceptHTML;
        private final boolean[] containsHTML;
        private final FastStringBuffer sb;
        private final Element childElement;
        private final boolean hostLanguageAVTs;

        // Constructor for "static" case, i.e. when we know the child element cannot have dynamic content
        public LHHAElementVisitorListener(boolean acceptHTML, boolean[] containsHTML, FastStringBuffer sb, Element childElement) {
            this.pipelineContext = null;
            this.container = null;
            this.contextStack = null;
            this.acceptHTML = acceptHTML;
            this.containsHTML = containsHTML;
            this.sb = sb;
            this.childElement = childElement;
            this.hostLanguageAVTs = false;
        }

        // Constructor for "dynamic" case, i.e. when we know the child element can have dynamic content
        public LHHAElementVisitorListener(PipelineContext pipelineContext, XFormsContainer container, XFormsContextStack contextStack, boolean acceptHTML, boolean[] containsHTML, FastStringBuffer sb, Element childElement) {
            this.pipelineContext = pipelineContext;
            this.container = container;
            this.contextStack = contextStack;
            this.acceptHTML = acceptHTML;
            this.containsHTML = containsHTML;
            this.sb = sb;
            this.childElement = childElement;
            this.hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs();
        }

        public void startElement(Element element) {
            if (element.getQName().equals(XFormsConstants.XFORMS_OUTPUT_QNAME)) {
                // This is an xforms:output nested among other markup

                // This can be null in "static" mode
                if (pipelineContext == null)
                    throw new OXFException("xforms:output must not show-up in static itemset: " + childElement.getName());

                final XFormsOutputControl outputControl = new XFormsOutputControl(container, null, element, element.getName(), null) {
                    // Override this as super.getContextStack() gets the containingDocument's stack, and here we need whatever is the current stack
                    // Probably need to modify super.getContextStack() at some point to NOT use the containingDocument's stack
                    protected XFormsContextStack getContextStack() {
                        return LHHAElementVisitorListener.this.contextStack;
                    }
                };
                contextStack.pushBinding(pipelineContext, element);
                {
                    outputControl.setBindingContext(pipelineContext, contextStack.getCurrentBindingContext());
                    outputControl.evaluateIfNeeded(pipelineContext);
                }
                contextStack.popBinding();

                if (acceptHTML) {
                    if ("text/html".equals(outputControl.getMediatype())) {
                        if (containsHTML != null)
                            containsHTML[0] = true; // this indicates for sure that there is some nested HTML
                        sb.append(outputControl.getExternalValue(pipelineContext));
                    } else {
                        // Mediatype is not HTML so we don't escape
                        sb.append(XMLUtils.escapeXMLMinimal(outputControl.getExternalValue(pipelineContext)));
                    }
                } else {
                    if ("text/html".equals(outputControl.getMediatype())) {
                        // HTML is not allowed here, better tell the user
                        throw new OXFException("HTML not allowed within element: " + childElement.getName());
                    } else {
                        // Mediatype is not HTML so we don't escape
                        sb.append(outputControl.getExternalValue(pipelineContext));
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

                        final String currentAttributeName = currentAttribute.getName();
                        final String currentAttributeValue = currentAttribute.getValue();

                        final String resolvedValue;
                        if (hostLanguageAVTs && currentAttributeValue.indexOf('{') != -1) {
                            // This is an AVT, use attribute control to produce the output
                            final XXFormsAttributeControl attributeControl
                                    = new XXFormsAttributeControl(container, element, currentAttributeValue);

                            contextStack.pushBinding(pipelineContext, element);
                            {
                                attributeControl.setBindingContext(pipelineContext, contextStack.getCurrentBindingContext());
                                attributeControl.evaluateIfNeeded(pipelineContext);
                            }
                            contextStack.popBinding();

                            resolvedValue = attributeControl.getExternalValue(pipelineContext);
                        } else {
                            // Simply use control value
                            resolvedValue = currentAttributeValue;
                        }

                        // Only consider attributes in no namespace
                        if ("".equals(currentAttribute.getNamespaceURI())) {
                            sb.append(' ');
                            sb.append(currentAttributeName);
                            sb.append("=\"");
                            if (resolvedValue != null)
                                sb.append(XMLUtils.escapeXMLMinimal(resolvedValue));
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
    }

    public static String escapeJavaScript(String value) {
        return StringUtils.replace(StringUtils.replace(StringUtils.replace(value, "\\", "\\\\"), "\"", "\\\""), "\n", "\\n");
    }

    /**
     * Return the prefix of an effective id, e.g. "" or "foo$bar$". The prefix returned does end with a separator.
     *
     * @param effectiveId   effective id to check
     * @return              prefix if any, "" if none, null if effectiveId was null
     */
    public static String getEffectiveIdPrefix(String effectiveId) {
        if (effectiveId == null)
            return null;

        final int prefixIndex = effectiveId.lastIndexOf(XFormsConstants.COMPONENT_SEPARATOR);
        if (prefixIndex != -1) {
            return effectiveId.substring(0, prefixIndex + 1);
        } else {
            return "";
        }
    }

    /**
     * Return the suffix of an effective id, e.g. "" or "2-5-1". The suffix returned does not start with a separator.
     *
     * @param effectiveId   effective id to check
     * @return              suffix if any, "" if none, null if effectiveId was null
     */
    public static String getEffectiveIdSuffix(String effectiveId) {
        if (effectiveId == null)
            return null;

        final int suffixIndex = effectiveId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        if (suffixIndex != -1) {
            return effectiveId.substring(suffixIndex + 1);
        } else {
            return "";
        }
    }

    /**
     * Return the suffix of an effective id, e.g. "" or "2-5-1". The suffix returned starts with a separator.
     *
     * @param effectiveId   effective id to check
     * @return              suffix if any, "" if none, null if effectiveId was null
     */
    public static String getEffectiveIdSuffixWithSeparator(String effectiveId) {
        if (effectiveId == null)
            return null;

        final int suffixIndex = effectiveId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        if (suffixIndex != -1) {
            return effectiveId.substring(suffixIndex);
        } else {
            return "";
        }
    }

    /**
     * Return an effective id without its suffix, e.g.:
     *
     * o foo$bar$my-input.1-2 => foo$bar$my-input
     *
     * @param effectiveId   effective id to check
     * @return              effective id without its suffix, null if effectiveId was null
     */
    public static String getEffectiveIdNoSuffix(String effectiveId) {
        if (effectiveId == null)
            return null;

        final int suffixIndex = effectiveId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        if (suffixIndex != -1) {
            return effectiveId.substring(0, suffixIndex);
        } else {
            return effectiveId;
        }
    }

    /**
     * Return an effective id without its prefix, e.g.:
     *
     * o foo$bar$my-input => my-input
     * o foo$bar$my-input.1-2 => my-input.1-2
     *
     * @param effectiveId   effective id to check
     * @return              effective id without its prefix, null if effectiveId was null
     */
    public static String getEffectiveIdNoPrefix(String effectiveId) {
        if (effectiveId == null)
            return null;

        final int prefixIndex = effectiveId.lastIndexOf(XFormsConstants.COMPONENT_SEPARATOR);
        if (prefixIndex != -1) {
            return effectiveId.substring(prefixIndex + 1);
        } else {
            return effectiveId;
        }
    }

    /**
     * Return the parts of an effective id prefix, e.g. for foo$bar$my-input return new String[] { "foo", "bar" }
     *
     * @param effectiveId   effective id to check
     * @return              array of parts, empty array if no parts, null if effectiveId was null
     */
    public static String[] getEffectiveIdPrefixParts(String effectiveId) {
        if (effectiveId == null)
            return null;

        final int prefixIndex = effectiveId.lastIndexOf(XFormsConstants.COMPONENT_SEPARATOR);
        if (prefixIndex != -1) {
            return StringUtils.split(effectiveId.substring(0, prefixIndex), XFormsConstants.COMPONENT_SEPARATOR);
        } else {
            return new String[0];
        }
    }

    /**
     * Given a repeat control's effective id, compute the effective id of an iteration.
     *
     * @param repeatEffectiveId     repeat control effective id
     * @param iterationIndex        repeat iteration
     * @return                      repeat iteration effective id
     */
    public static String getIterationEffectiveId(String repeatEffectiveId, int iterationIndex) {
        final String parentSuffix = XFormsUtils.getEffectiveIdSuffix(repeatEffectiveId);
        if (parentSuffix.equals("")) {
            // E.g. foobar => foobar.3
            return repeatEffectiveId + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + iterationIndex;
        } else {
            // E.g. foobar.3-7 => foobar.3-7-2
            return repeatEffectiveId + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2 + iterationIndex;
        }
    }

    /**
     * Return the parts of an effective id suffix, e.g. for $foo$bar.3-1-5 return new Integer[] { 3, 1, 5 }
     *
     * @param effectiveId   effective id to check
     * @return              array of parts, empty array if no parts, null if effectiveId was null
     */
    public static Integer[] getEffectiveIdSuffixParts(String effectiveId) {
        if (effectiveId == null)
            return null;

        final int suffixIndex = effectiveId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        if (suffixIndex != -1) {
            final String[] stringResult = StringUtils.split(effectiveId.substring(suffixIndex + 1), XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2);
            final Integer[] result = new Integer[stringResult.length];
            for (int i = 0; i < stringResult.length; i++) {
                final String currentString = stringResult[i];
                result[i] = new Integer(currentString);
            }
            return result;
        } else {
            return new Integer[0];
        }
    }

    /**
     * Compute an effective id based on an existing effective id and a static id. E.g.:
     *
     *  foo$bar.1-2 and myStaticId => foo$myStaticId.1-2
     *
     * @param baseEffectiveId   base effective id
     * @param staticId          static id
     * @return                  effective id
     */
    public static String getRelatedEffectiveId(String baseEffectiveId, String staticId) {
        final String prefix = getEffectiveIdPrefix(baseEffectiveId);
        final String suffix; {
            final int suffixIndex = baseEffectiveId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
            suffix = (suffixIndex == -1) ? "" : baseEffectiveId.substring(suffixIndex);
        }
        return prefix + staticId + suffix;
    }

    /**
     * Return the static id associated with the given id, removing suffix and prefix if present.
     *
     *  foo$bar.1-2 => bar
     *
     * @param anyId id to check
     * @return      static id, or null if anyId was null
     */
    public static String getStaticIdFromId(String anyId) {
        return getEffectiveIdNoSuffix(getEffectiveIdNoPrefix(anyId));
    }
}
