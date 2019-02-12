/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xml.dom4j;

import org.orbeon.dom.*;
import org.orbeon.dom.io.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.StringUtils;
import org.orbeon.oxf.util.StringBuilderWriter;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.*;

// TODO: move this to Scala/remove unneeded stuff
public class Dom4jUtils {

    /**
     * 03/30/2005 d : Currently DOM4J doesn't really support read only documents.  ( No real
     * checks in place.  If/when DOM4J adds real support then NULL_DOCUMENT should be made a
     * read only document.
     */
    public static final Document NULL_DOCUMENT;

    static {
        NULL_DOCUMENT = Document.apply();
        Element nullElement = DocumentFactory.createElement("null");
        nullElement.addAttribute(XMLConstants.XSI_NIL_QNAME, "true");
        NULL_DOCUMENT.setRootElement(nullElement);
    }

    private static SAXReader createSAXReader(XMLParsing.ParserConfiguration parserConfiguration) throws SAXException {
        return new SAXReader(XMLParsing.newXMLReader(parserConfiguration));
    }

    private static SAXReader createSAXReader() throws SAXException {
        return createSAXReader(XMLParsing.ParserConfiguration.XINCLUDE_ONLY);
    }

    /**
     * Convert a dom4j document to a string.
     *
     * @param document  document to convert
     * @return          resulting string
     */
    public static String domToString(final Document document) {
        final Element rootElement = document.getRootElement();
        return domToString((Branch) rootElement);
    }

    /**
     * Convert a dom4j element to a string.
     *
     * @param element   element to convert
     * @return          resulting string
     */
    public static String domToString(final Element element) {
        return domToString((Branch) element);
    }

    /**
     * Convert a dom4j node to a string.
     *
     * @param node  node to convert
     * @return      resulting string
     */
    public static String nodeToString(final Node node) {
        final String ret;

        if (node instanceof Document) {
            ret = domToString((Branch) ((Document) node).getRootElement());
        } else if (node instanceof Element) {
            ret = domToString((Branch) node);
        } else if (node instanceof Text) {
            ret = node.getText();
        } else {
            ret = domToString(node, null);
        }

        return ret;
    }

    /**
     * Convert an XML string to a prettified XML string.
     */
    public static String prettyfy(String xmlString) {
        try {
            return domToPrettyString(readDom4j(xmlString));
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Convert a dom4j document to a pretty string, for formatting/debugging purposes only.
     *
     * @param document  document to convert
     * @return          resulting string
     */
    public static String domToPrettyString(final Document document) {
        return domToPrettyString(document.getRootElement());
    }

    public static String domToPrettyString(final Element element) {
        return domToString(element, OutputFormat.apply(true, true, true));
    }

    /**
     * Convert a dom4j document to a compact string, with all text being trimmed.
     *
     * @param document  document to convert
     * @return          resulting string
     */
    public static String domToCompactString(final Document document) {
        return domToString(document.getRootElement(), OutputFormat.apply(false, false, true));
    }

    private static String domToString(final Branch branch) {
        return domToString(branch, OutputFormat.apply(false, false, false));
    }

    private static String domToString(final Node node, final OutputFormat format) {
        final StringBuilderWriter writer = new StringBuilderWriter();
        final XMLWriter xmlWriter = new XMLWriter(writer, format == null ? XMLWriter.DefaultFormat() : format);
        xmlWriter.write(node);
        writer.close();
        return writer.toString();
    }

    /**
     * Read a document from a URL.
     *
     * @param urlString             URL
     * @param parserConfiguration   parser configuration
     * @return                      document
     */
    public static Document readFromURL(String urlString, XMLParsing.ParserConfiguration parserConfiguration) {
        InputStream is = null;
        try {
            final URL url = URLFactory.createURL(urlString);
            is = url.openStream();
            return readDom4j(is, urlString, parserConfiguration);
        } catch (Exception e) {
            throw new OXFException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new OXFException("Exception while closing stream", e);
                }
            }
        }
    }

    public static Document readDom4j(Reader reader) throws SAXException, DocumentException {
        return createSAXReader().read(reader);
    }

    public static Document readDom4j(Reader reader, String uri) throws SAXException, DocumentException {
        return createSAXReader().read(reader, uri);
    }

    /*
     * Replacement for DocumentHelper.parseText. DocumentHelper.parseText is not used since it creates work for GC
     * (because it relies on JAXP).
     */
    public static Document readDom4j(String xmlString, XMLParsing.ParserConfiguration parserConfiguration) throws SAXException, DocumentException {
        final StringReader stringReader = new StringReader(xmlString);
        return createSAXReader(parserConfiguration).read(stringReader);
    }

    public static Document readDom4j(String xmlString) throws SAXException, DocumentException {
        return readDom4j(xmlString, XMLParsing.ParserConfiguration.PLAIN);
    }

    public static Document readDom4j(InputStream inputStream, String uri, XMLParsing.ParserConfiguration parserConfiguration) throws SAXException, DocumentException {
        return createSAXReader(parserConfiguration).read(inputStream, uri);
    }

    public static Document readDom4j(InputStream inputStream) throws SAXException, DocumentException {
        return createSAXReader(XMLParsing.ParserConfiguration.PLAIN).read(inputStream);
    }

    public static String makeSystemId(final Element e) {
        final LocationData ld = (LocationData) e.getData();
        final String ldSid = ld == null ? null : ld.file();
        return ldSid == null ? DOMGenerator.DefaultContext : ldSid;
    }

    /**
     * Go over the Node and its children and make sure that there are no two contiguous text nodes so as to ensure that
     * XPath expressions run correctly. As per XPath 1.0 (http://www.w3.org/TR/xpath):
     *
     * "As much character data as possible is grouped into each text node: a text node never has an immediately
     * following or preceding sibling that is a text node."
     *
     * dom4j Text and CDATA nodes are combined together.
     *
     * @param nodeToNormalize Node hierarchy to normalize
     * @return                the input node, normalized
     */
    public static Node normalizeTextNodes(Node nodeToNormalize) {
        final List<Node> nodesToDetach = new ArrayList<Node>();
        nodeToNormalize.accept(new VisitorSupport() {
            public void visit(Element element) {
                final List children = element.content();
                Node previousNode = null;
                StringBuilder sb = null;
                for (Iterator i = children.iterator(); i.hasNext();) {
                    final Node currentNode = (Node) i.next();
                    if (previousNode != null) {
                        if (previousNode instanceof Text && currentNode instanceof Text) {
                            final Text previousNodeText = (Text) previousNode;
                            if (sb == null)
                                sb = new StringBuilder(previousNodeText.getText());
                            sb.append(currentNode.getText());
                            nodesToDetach.add(currentNode);
                        } else if (previousNode instanceof Text) {
                            // Update node if needed
                            if (sb != null) {
                                previousNode.setText(sb.toString());
                            }
                            previousNode = currentNode;
                            sb = null;
                        } else {
                            previousNode = currentNode;
                            sb = null;
                        }
                    } else {
                        previousNode = currentNode;
                        sb = null;
                    }
                }
                // Update node if needed
                if (previousNode != null && sb != null) {
                    previousNode.setText(sb.toString());
                }
            }
        });
        // Detach nodes only in the end so as to not confuse the acceptor above
        for (final Node currentNode: nodesToDetach) {
            currentNode.detach();
        }

        return nodeToNormalize;
    }

    public static DocumentSource getDocumentSource(final Document d) {
        /*
         * Saxon's error handler is expensive for the service it provides so we just use our
         * singleton instead.
         *
         * Wrt expensive, delta in heap dump info below is amount of bytes allocated during the
         * handling of a single request to '/' in the examples app. i.e. The trace below was
         * responsible for creating 200k of garbage during the handing of a single request to '/'.
         *
         * delta: 213408 live: 853632 alloc: 4497984 trace: 380739 class: byte[]
         *
         * TRACE 380739:
         * java.nio.HeapByteBuffer.<init>(HeapByteBuffer.java:39)
         * java.nio.ByteBuffer.allocate(ByteBuffer.java:312)
         * sun.nio.cs.StreamEncoder$CharsetSE.<init>(StreamEncoder.java:310)
         * sun.nio.cs.StreamEncoder$CharsetSE.<init>(StreamEncoder.java:290)
         * sun.nio.cs.StreamEncoder$CharsetSE.<init>(StreamEncoder.java:274)
         * sun.nio.cs.StreamEncoder.forOutputStreamWriter(StreamEncoder.java:69)
         * java.io.OutputStreamWriter.<init>(OutputStreamWriter.java:93)
         * java.io.PrintWriter.<init>(PrintWriter.java:109)
         * java.io.PrintWriter.<init>(PrintWriter.java:92)
         * org.orbeon.saxon.StandardErrorHandler.<init>(StandardErrorHandler.java:22)
         * org.orbeon.saxon.event.Sender.sendSAXSource(Sender.java:165)
         * org.orbeon.saxon.event.Sender.send(Sender.java:94)
         * org.orbeon.saxon.IdentityTransformer.transform(IdentityTransformer.java:31)
         * org.orbeon.oxf.xml.XMLUtils.getDigest(XMLUtils.java:453)
         * org.orbeon.oxf.xml.XMLUtils.getDigest(XMLUtils.java:423)
         * org.orbeon.oxf.processor.generator.DOMGenerator.<init>(DOMGenerator.java:93)
         *
         * Before mod
         *
         * 1.4.2_06-b03 	P4 2.6 Ghz	/ 	50 th	tc 4.1.30	10510 ms ( 150 mb ), 7124 ( 512 mb ) 	2.131312472239924 ( 150 mb ), 1.7474380872589803 ( 512 mb )
         *
         * after mod
         *
         * 1.4.2_06-b03 	P4 2.6 Ghz	/ 	50 th	tc 4.1.30	9154 ms ( 150 mb ), 6949 ( 512 mb ) 	1.7316203642295738 ( 150 mb ), 1.479365288194895 ( 512 mb )
         *
         */
        final LocationDocumentSource lds = new LocationDocumentSource(d);
        final XMLReader rdr = lds.getXMLReader();
        rdr.setErrorHandler(XMLParsing.ERROR_HANDLER);
        return lds;
    }

    public static byte[] getDigest(Document document) {
        final DocumentSource ds = getDocumentSource(document);
        return DigestContentHandler.getDigest(ds);
    }

    /**
     * Clean-up namespaces. Some tools generate namespace "un-declarations" or
     * the form xmlns:abc="". While this is needed to keep the XML infoset
     * correct, it is illegal to generate such declarations in XML 1.0 (but it
     * is legal in XML 1.1). Technically, this cleanup is incorrect at the DOM
     * and SAX level, so this should be used only in rare occasions, when
     * serializing certain documents to XML 1.0.
     */
    public static Document adjustNamespaces(Document document, boolean xml11) {
        if (xml11)
            return document;
        final LocationSAXWriter writer = new LocationSAXWriter();
        final LocationSAXContentHandler ch = new LocationSAXContentHandler();
        writer.setContentHandler(new NamespaceCleanupXMLReceiver(ch, xml11));
        writer.write(document);
        return ch.getDocument();
    }

    /**
     * Return a Map of namespaces in scope on the given element.
     */
    public static Map<String, String> getNamespaceContext(Element element) {
        final Map<String, String> namespaces = new HashMap<String, String>();
        for (Element currentNode = element; currentNode != null; currentNode = currentNode.getParent()) {
            final List currentNamespaces = currentNode.declaredNamespaces();
            for (Iterator j = currentNamespaces.iterator(); j.hasNext();) {
                final Namespace namespace = (Namespace) j.next();
                if (!namespaces.containsKey(namespace.prefix())) {
                    namespaces.put(namespace.prefix(), namespace.uri());

                    // TODO: Intern namespace strings to save memory; should use NamePool later
//                    namespaces.put(namespace.getPrefix().intern(), namespace.getURI().intern());
                }
            }
        }
        // It seems that by default this may not be declared. However, it should be: "The prefix xml is by definition
        // bound to the namespace name http://www.w3.org/XML/1998/namespace. It MAY, but need not, be declared, and MUST
        // NOT be bound to any other namespace name. Other prefixes MUST NOT be bound to this namespace name, and it
        // MUST NOT be declared as the default namespace."
        namespaces.put(XMLConstants.XML_PREFIX, XMLConstants.XML_URI);
        return namespaces;
    }

    /**
     * Return a Map of namespaces in scope on the given element, without the default namespace.
     */
    public static Map<String, String> getNamespaceContextNoDefault(Element element) {
        final Map<String, String> namespaces = getNamespaceContext(element);
        namespaces.remove("");
        return namespaces;
    }

    /**
     * Extract a QName from an Element and an attribute name. The prefix of the QName must be in
     * scope. Return null if the attribute is not found.
     */
    public static QName extractAttributeValueQName(Element element, String attributeName) {
        return extractTextValueQName(element, element.attributeValue(attributeName), true);
    }

    /**
     * Extract a QName from an Element and an attribute QName. The prefix of the QName must be in
     * scope. Return null if the attribute is not found.
     */
    public static QName extractAttributeValueQName(Element element, QName attributeQName) {
        return extractTextValueQName(element, element.attributeValue(attributeQName), true);
    }

    public static QName extractAttributeValueQName(Element element, QName attributeQName, boolean unprefixedIsNoNamespace) {
        return extractTextValueQName(element, element.attributeValue(attributeQName), unprefixedIsNoNamespace);
    }

    /**
     * Extract a QName from an Element's string value. The prefix of the QName must be in scope.
     * Return null if the text is empty.
     */
    public static QName extractTextValueQName(Element element, boolean unprefixedIsNoNamespace) {
        return extractTextValueQName(element, element.getStringValue(), unprefixedIsNoNamespace);
    }

    /**
     * Extract a QName from an Element's string value. The prefix of the QName must be in scope.
     * Return null if the text is empty.
     *
     * @param element                   Element containing the attribute
     * @param qNameString               QName to analyze
     * @param unprefixedIsNoNamespace   if true, an unprefixed value is in no namespace; if false, it is in the default namespace
     * @return                          a QName object or null if not found
     */
    public static QName extractTextValueQName(Element element, String qNameString, boolean unprefixedIsNoNamespace) {
        return extractTextValueQName(getNamespaceContext(element), qNameString, unprefixedIsNoNamespace);
    }

    /**
     * Extract a QName from a string value, given namespace mappings. Return null if the text is empty.
     *
     * @param namespaces                prefix -> URI mappings
     * @param qNameString               QName to analyze
     * @param unprefixedIsNoNamespace   if true, an unprefixed value is in no namespace; if false, it is in the default namespace
     * @return                          a QName object or null if not found
     */
    public static QName extractTextValueQName(Map<String, String> namespaces, String qNameString, boolean unprefixedIsNoNamespace) {
        if (qNameString == null)
            return null;
        qNameString = StringUtils.trimAllToEmpty(qNameString);
        if (qNameString.length() == 0)
            return null;

        final int colonIndex = qNameString.indexOf(':');
        final String prefix;
        final String localName;
        final String namespaceURI;
        if (colonIndex == -1) {
            prefix = "";
            localName = qNameString;
            if (unprefixedIsNoNamespace) {
                namespaceURI = "";
            } else {
                final String nsURI = namespaces.get(prefix);
                namespaceURI = nsURI == null ? "" : nsURI;
            }
        } else if (colonIndex == 0) {
            throw new OXFException("Empty prefix for QName: " + qNameString);
        } else {
            prefix = qNameString.substring(0, colonIndex);
            localName = qNameString.substring(colonIndex + 1);
            namespaceURI = namespaces.get(prefix);
            if (namespaceURI == null) {
                throw new OXFException("No namespace declaration found for prefix: " + prefix);
            }
        }
        return QName.apply(localName, Namespace$.MODULE$.apply(prefix, namespaceURI));
    }

    /**
     * Decode a String containing an exploded QName (also known as a "Clark name") into a QName.
     */
    public static QName explodedQNameToQName(String qName) {
        int openIndex = qName.indexOf("{");

        if (openIndex == -1)
            return QName.apply(qName);

        String namespaceURI = qName.substring(openIndex + 1, qName.indexOf("}"));
        String localName = qName.substring(qName.indexOf("}") + 1);
        return QName.apply(localName, Namespace$.MODULE$.apply("p1", namespaceURI));
    }

    // TODO ORBEON: remove uses, just use DocumentFactory

    /**
     * Create a copy of a dom4j Node.
     *
     * @param source    source Node
     * @return          copy of Node
     */
    public static Node createCopy(Node source) {
        return (source instanceof Element) ? ((Element) source).createCopy() : (Node) source.deepCopy();
    }

    /**
     * Return a new document with a copy of newRoot as its root.
     */
    public static Document createDocumentCopyElement(final Element newRoot) {
        return Document.apply(newRoot.createCopy());
    }

    /**
     * Return a new document with all parent namespaces copied to the new root element, assuming they are not already
     * declared on the new root element. The element passed is deep copied.
     *
     * @param newRoot   element which must become the new root element of the document
     * @return          new document
     */
    public static Document createDocumentCopyParentNamespaces(final Element newRoot) {
        return createDocumentCopyParentNamespaces(newRoot, false);
    }

    /**
     * Return a new document with all parent namespaces copied to the new root element, assuming they are not already
     * declared on the new root element.
     *
     * @param newRoot   element which must become the new root element of the document
     * @param detach    if true the element is detached, otherwise it is deep copied
     * @return          new document
     */
    public static Document createDocumentCopyParentNamespaces(final Element newRoot, boolean detach) {

        final Element parentElement = newRoot.getParent();
        final Document document; {
            if (detach) {
                // Detach
                document = Document.apply();
                document.setRootElement((Element) newRoot.detach());
            } else {
                // Copy
                document = createDocumentCopyElement(newRoot);
            }
        }

        copyMissingNamespaces(parentElement, document.getRootElement());

        return document;
    }

    public static void copyMissingNamespaces(Element sourceElement, Element destinationElement) {
        final Map<String, String> parentNamespaceContext = Dom4jUtils.getNamespaceContext(sourceElement);
        final Map<String, String> rootElementNamespaceContext = Dom4jUtils.getNamespaceContext(destinationElement);

        for (final String prefix: parentNamespaceContext.keySet()) {
            // NOTE: Don't use rootElement.getNamespaceForPrefix() because that will return the element prefix's
            // namespace even if there are no namespace nodes
            if (rootElementNamespaceContext.get(prefix) == null) {
                final String uri = parentNamespaceContext.get(prefix);
                destinationElement.addNamespace(prefix, uri);
            }
        }
    }

    /**
     * Return a new document with a copy of newRoot as its root and all parent namespaces copied to the new root
     * element, except those with the prefixes appearing in the Map, assuming they are not already declared on the new
     * root element.
     */
    public static Document createDocumentCopyParentNamespaces(final Element newRoot, Set<String> prefixesToFilter) {

        final Document document = Dom4jUtils.createDocumentCopyElement(newRoot);
        final Element rootElement = document.getRootElement();

        final Element parentElement = newRoot.getParent();
        final Map<String, String> parentNamespaceContext = Dom4jUtils.getNamespaceContext(parentElement);
        final Map<String, String> rootElementNamespaceContext = Dom4jUtils.getNamespaceContext(rootElement);

        for (final String prefix: parentNamespaceContext.keySet()) {
            // NOTE: Don't use rootElement.getNamespaceForPrefix() because that will return the element prefix's
            // namespace even if there are no namespace nodes
            if (rootElementNamespaceContext.get(prefix) == null && ! prefixesToFilter.contains(prefix)) {
                final String uri = parentNamespaceContext.get(prefix);
                rootElement.addNamespace(prefix, uri);
            }
        }

        return document;
    }

    /**
     * Return a copy of the given element which includes all the namespaces in scope on the element.
     *
     * @param sourceElement element to copy
     * @return              copied element
     */
    public static Element copyElementCopyParentNamespaces(final Element sourceElement) {
        final Element newElement = sourceElement.createCopy();
        copyMissingNamespaces(sourceElement.getParent(), newElement);
        return newElement;
    }

    /**
     * Workaround for Java's lack of an equivalent to C's __FILE__ and __LINE__ macros.  Use
     * carefully as it is not fast.
     *
     * Perhaps in 1.5 we will find a better way.
     *
     * @return LocationData of caller.
     */
    public static LocationData getLocationData() {
        return getLocationData(1, false);
    }

    public static LocationData getLocationData(final int depth, boolean isDebug) {
        // Enable this with a property for debugging only, as it is time consuming
        if (!isDebug && !org.orbeon.oxf.properties.Properties.instance().getPropertySet()
                .getBoolean("oxf.debug.enable-java-location-data", false))
            return null;

        // Compute stack trace and extract useful information
        final Exception e = new Exception();
        final StackTraceElement[] stkTrc = e.getStackTrace();
        final int depthToUse = depth + 1;
        final String sysID = stkTrc[depthToUse].getFileName();
        final int line = stkTrc[depthToUse].getLineNumber();
        return new LocationData(sysID, line, -1);
    }

    /**
     * Visit a subtree of a dom4j document.
     *
     * @param container         element containing the elements to visit
     * @param visitorListener   listener to call back
     */
    public static void visitSubtree(Element container, VisitorListener visitorListener) {
        visitSubtree(container, visitorListener, false);
    }

    /**
     * Visit a subtree of a dom4j document.
     *
     * @param container         element containing the elements to visit
     * @param visitorListener   listener to call back
     * @param mutable           whether the source tree can mutate while being visited
     */
    public static void visitSubtree(Element container, VisitorListener visitorListener, boolean mutable) {

        // If the source tree can mutate, copy the list first, otherwise dom4j might throw exceptions
        final List<Node> content = mutable ? new ArrayList<Node>(container.content()) : container.content();

        // Iterate over the content
        for (final Node childNode : content) {
            if (childNode instanceof Element) {
                final Element childElement = (Element) childNode;
                visitorListener.startElement(childElement);
                visitSubtree(childElement, visitorListener, mutable);
                visitorListener.endElement(childElement);
            } else if (childNode instanceof Text) {
                visitorListener.text((Text) childNode);
            } else {
                // Ignore as we don't need other node types for now
            }
        }
    }

    public static String elementToDebugString(Element element) {
        // Open start tag
        final StringBuilder sb = new StringBuilder("<");
        sb.append(element.getQualifiedName());

        // Attributes if any
        for (Iterator i = element.attributeIterator(); i.hasNext();) {
            final Attribute currentAttribute = (Attribute) i.next();

            sb.append(' ');
            sb.append(currentAttribute.getQualifiedName());
            sb.append("=\"");
            sb.append(currentAttribute.getValue());
            sb.append('\"');
        }

        final boolean isEmptyElement = element.elements().isEmpty() && element.getText().length() == 0;
        if (isEmptyElement) {
            // Close empty element
            sb.append("/>");
        } else {
            // Close start tag
            sb.append('>');
            sb.append("[...]");
            // Close element with end tag
            sb.append("</");
            sb.append(element.getQualifiedName());
            sb.append('>');
        }

        return sb.toString();
    }

    public static String attributeToDebugString(Attribute attribute) {
        return attribute.getQualifiedName() + "=\"" + attribute.getValue() + '\"';
    }

    /**
     * Convert dom4j attributes to SAX attributes.
     *
     * @param element   dom4j Element
     * @return          SAX Attributes
     */
    public static AttributesImpl getSAXAttributes(Element element) {
        final AttributesImpl result = new AttributesImpl();
        for (Iterator i = element.attributeIterator(); i.hasNext();) {
            final Attribute attribute = (Attribute) i.next();

            result.addAttribute(attribute.getNamespaceURI(), attribute.getName(), attribute.getQualifiedName(),
                    XMLReceiverHelper.CDATA, attribute.getValue());
        }
        return result;
    }

    public static Document createDocument(DebugXML debugXML) {
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        final LocationDocumentResult result = new LocationDocumentResult();
        identity.setResult(result);

        final XMLReceiverHelper helper = new XMLReceiverHelper(new ForwardingXMLReceiver(identity) {
            @Override
            public void startDocument() {}
            @Override
            public void endDocument() {}
        });

        try {
            identity.startDocument();
            debugXML.toXML(helper);
            identity.endDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }

        return result.getDocument();
    }

    /**
     * Encode a QName to an exploded QName (also known as a "Clark name") String.
     */
    public static String qNameToExplodedQName(QName qName) {
        return (qName == null) ? null : XMLUtils.buildExplodedQName(qName.namespace().uri(), qName.localName());
    }

    // http://www.w3.org/TR/xpath-30/#doc-xpath30-URIQualifiedName
    public static String buildURIQualifiedName(QName qName) {
        return XMLUtils.buildURIQualifiedName(qName.namespace().uri(), qName.localName());
    }

    public interface VisitorListener {
        void startElement(Element element);
        void endElement(Element element);
        void text(Text text);
    }

    public interface DebugXML {
        void toXML(XMLReceiverHelper helper);
    }
}
