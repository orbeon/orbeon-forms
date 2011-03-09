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

import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.StringBuilderWriter;
import org.orbeon.oxf.xml.NamespaceCleanupXMLReceiver;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.*;

/**
 * Collection of utility routines for working with DOM4J. In particular offers many methods found in DocumentHelper.
 * The difference between these 'copied' methods and the originals is that our copies use our NonLazyUserData* classes.
 * (As opposed to DOM4J's defaults or whatever happens to be specified in DOM4J's system property.)
 */
public class Dom4jUtils {

    /**
     * 03/30/2005 d : Currently DOM4J doesn't really support read only documents.  ( No real
     * checks in place.  If/when DOM4J adds real support then NULL_DOCUMENT should be made a
     * read only document.
     */
    public static final Document NULL_DOCUMENT;

    static {
        NULL_DOCUMENT = new NonLazyUserDataDocument();
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        Element nullElement = factory.createElement("null");
        nullElement.addAttribute(XMLConstants.XSI_NIL_QNAME, "true");
        NULL_DOCUMENT.setRootElement(nullElement);
    }

    private static SAXReader createSAXReader(XMLUtils.ParserConfiguration parserConfiguration) throws SAXException {
        final XMLReader xmlReader = XMLUtils.newXMLReader(parserConfiguration);

        final SAXReader saxReader = new SAXReader(xmlReader);
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        saxReader.setDocumentFactory(factory);
        return saxReader;
    }

    private static SAXReader createSAXReader() throws SAXException {
        return createSAXReader(XMLUtils.ParserConfiguration.XINCLUDE_ONLY);
    }

    /**
     * Typed version of the dom4j API.
     */
    @SuppressWarnings("unchecked")
    public static List<Element> elements(Element element) {
        return (List<Element>) element.elements();
    }

    /**
     * Typed version of the dom4j API.
     */
    @SuppressWarnings("unchecked")
    public static List<Element> elements(Element element, QName name) {
        return (List<Element>) element.elements(name);
    }

    /**
     * Typed version of the dom4j API.
     */
    @SuppressWarnings("unchecked")
    public static List<Element> elements(Element element, String name) {
        return (List<Element>) element.elements(name);
    }

    /**
     * Typed version of the dom4j API.
     */
    @SuppressWarnings("unchecked")
    public static List<Node> content(Element container) {
        return (List<Node>) container.content();
    }

    /**
     * Typed version of the dom4j API.
     */
    @SuppressWarnings("unchecked")
    public static List<Attribute> attributes(Element element) {
        return (List<Attribute>) element.attributes();
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
        switch (node.getNodeType()) {
            case Node.DOCUMENT_NODE: {
                ret = domToString((Branch) ((Document) node).getRootElement());
                break;
            }
            case Node.ELEMENT_NODE: {
                ret = domToString((Branch) node);
                break;
            }
            case Node.TEXT_NODE: {
                ret = node.getText();
                break;
            }
            default :
                ret = domToString(node, null);
                break;
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
        final OutputFormat format = new OutputFormat();
        format.setIndentSize(4);
        format.setNewlines(true);
        format.setTrimText(true);
        return domToString(document.getRootElement(), format);
    }

    /**
     * Convert a dom4j document to a compact string, with all text being trimmed.
     *
     * @param document  document to convert
     * @return          resulting string
     */
    public static String domToCompactString(final Document document) {
        final OutputFormat format = new OutputFormat();
        format.setIndent(false);
        format.setNewlines(false);
        format.setTrimText(true);
        return domToString(document.getRootElement(), format);
    }

    private static String domToString(final Branch branch) {
        final OutputFormat format = new OutputFormat();
        format.setIndent(false);
        format.setNewlines(false);
        return domToString(branch, format);
    }

    private static String domToString(final Node node, final OutputFormat format) {
        try {
            final StringBuilderWriter writer = new StringBuilderWriter();
            // Ugh, XMLWriter doesn't accept null formatter _and_ default formatter is protected.
            final XMLWriter xmlWriter = format == null ? new XMLWriter(writer) : new XMLWriter(writer, format);
            xmlWriter.write(node);
            xmlWriter.close();
            return writer.toString();
        } catch (final IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Read a document from a URL.
     *
     * @param urlString             URL
     * @param parserConfiguration   parser configuration
     * @return                      document
     */
    public static Document readFromURL(String urlString, XMLUtils.ParserConfiguration parserConfiguration) {
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
        final SAXReader saxReader = createSAXReader();
        return saxReader.read(reader);
    }

    public static Document readDom4j(Reader reader, String uri) throws SAXException, DocumentException {
        final SAXReader saxReader = createSAXReader();
        return saxReader.read(reader, uri);
    }

    /*
     * Replacement for DocumentHelper.parseText. DocumentHelper.parseText is not used since it creates work for GC
     * (because it relies on JAXP).
     */
    public static Document readDom4j(String xmlString, XMLUtils.ParserConfiguration parserConfiguration) throws SAXException, DocumentException {
        final SAXReader saxReader = createSAXReader(parserConfiguration);
        final StringReader stringReader = new StringReader(xmlString);
        return saxReader.read(stringReader);
    }

    public static Document readDom4j(String xmlString) throws SAXException, DocumentException {
        return readDom4j(xmlString, XMLUtils.ParserConfiguration.PLAIN);
    }

    public static Document readDom4j(InputStream inputStream, String uri, XMLUtils.ParserConfiguration parserConfiguration) throws SAXException, DocumentException {
        final SAXReader saxReader = createSAXReader(parserConfiguration);
        return saxReader.read(inputStream, uri);
    }

    /**
     * Removes the elements and text inside the given element, but not the attributes or namespace
     * declarations on the element.
     */
    public static void clearElementContent(final Element elt) {
        final java.util.List cntnt = elt.content();
        for (final java.util.ListIterator j = cntnt.listIterator();
             j.hasNext();) {
            final Node chld = (Node) j.next();
            if (chld.getNodeType() == Node.TEXT_NODE
                    || chld.getNodeType() == Node.ELEMENT_NODE) {
                j.remove();
            }
        }
    }

    public static String makeSystemId(final Element e) {
        final LocationData ld = (LocationData) e.getData();
        final String ldSid = ld == null ? null : ld.getSystemID();
        return ldSid == null ? DOMGenerator.DefaultContext : ldSid;
    }

    /**
     *  Convert the result of XPathUtils.selectObjectValue() to a string
     */
    public static String objectToString(Object o) {
        StringBuilder builder = new StringBuilder();
        if (o instanceof List) {
            for (Iterator i = ((List) o).iterator(); i.hasNext();) {
                // this will be a node
                builder.append(objectToString(i.next()));
            }
        } else if (o instanceof Element) {
            builder.append(((Element) o).asXML());
        } else if (o instanceof Node) {
            builder.append(((Node) o).asXML());
        } else if (o instanceof String)
            builder.append((String) o);
        else if (o instanceof Number)
            builder.append(o);
        else
            throw new OXFException("Should never happen");
        return builder.toString();
    }

    /**
     * Go over the Node and its children and make sure that there are no two contiguous text nodes so as to ensure that
     * XPath expressions run correctly. As per XPath 1.0 (http://www.w3.org/TR/xpath):
     *
     * "As much character data as possible is grouped into each text node: a text node never has an immediately
     * following or preceding sibling that is a text node."
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
        rdr.setErrorHandler(XMLUtils.ERROR_HANDLER);
        return lds;
    }

    public static byte[] getDigest(Document document) {
        final DocumentSource ds = getDocumentSource(document);
        return XMLUtils.getDigest(ds);
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
        try {
            writer.write(document);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
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
                if (!namespaces.containsKey(namespace.getPrefix())) {
                    namespaces.put(namespace.getPrefix(), namespace.getURI());

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
        qNameString = qNameString.trim();
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
        return new QName(localName, new Namespace(prefix, namespaceURI));
    }

    /**
     * Decode a String containing an exploded QName (also known as a "Clark name") into a QName.
     */
    public static QName explodedQNameToQName(String qName) {
        int openIndex = qName.indexOf("{");

        if (openIndex == -1)
            return new QName(qName);

        String namespaceURI = qName.substring(openIndex + 1, qName.indexOf("}"));
        String localName = qName.substring(qName.indexOf("}") + 1);
        return new QName(localName, new Namespace("p1", namespaceURI));
    }

    /**
     * Encode a QName to an exploded QName (also known as a "Clark name") String.
     */
    public static String qNameToExplodedQName(QName qName) {
        return (qName == null) ? null : XMLUtils.buildExplodedQName(qName.getNamespaceURI(), qName.getName());
    }

    public static XPath createXPath(final String expression) throws InvalidXPathException {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createXPath(expression);
    }

    public static Text createText(final String text) {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createText(text);
    }

    public static Element createElement(final String name) {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createElement(name);
    }

    public static Element createElement(final String qualifiedName, final String namespaceURI) {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createElement(qualifiedName, namespaceURI);
    }

    public static Element createElement(final QName qName) {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createElement(qName);
    }

    public static Attribute createAttribute(final QName qName, final String value) {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createAttribute(null, qName, value);
    }

    public static Namespace createNamespace(final String prefix, final String uri) {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createNamespace(prefix, uri);
    }

    /**
     * Create a copy of a dom4j Node.
     *
     * @param source    source Node
     * @return          copy of Node
     */
    public static Node createCopy(Node source) {
        return (source instanceof Element) ? ((Element) source).createCopy() : (Node) source.clone();
    }

    /**
     * Return a new document with a copy of newRoot as its root.
     */
    public static Document createDocumentCopyElement(final Element newRoot) {
        final Element copy = newRoot.createCopy();
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createDocument(copy);
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
                document = createDocument();
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
    public static Document createDocumentCopyParentNamespaces(final Element newRoot, Map prefixesToFilter) {

        final Document document = Dom4jUtils.createDocumentCopyElement(newRoot);
        final Element rootElement = document.getRootElement();

        final Element parentElement = newRoot.getParent();
        final Map<String, String> parentNamespaceContext = Dom4jUtils.getNamespaceContext(parentElement);
        final Map<String, String> rootElementNamespaceContext = Dom4jUtils.getNamespaceContext(rootElement);

        for (final String prefix: parentNamespaceContext.keySet()) {
            // NOTE: Don't use rootElement.getNamespaceForPrefix() because that will return the element prefix's
            // namespace even if there are no namespace nodes
            if (rootElementNamespaceContext.get(prefix) == null && prefixesToFilter.get(prefix) == null) {
                final String uri = parentNamespaceContext.get(prefix);
                rootElement.addNamespace(prefix, uri);
            }
        }

        return document;
    }

    public static Document createDocument() {
        final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
        return factory.createDocument();
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

    public static Element saxToDebugElement(String qName, Attributes attributes) {
        final Element element = createElement(qName);

        for (int i = 0; i < attributes.getLength(); i++) {
            element.addAttribute(attributes.getQName(i), attributes.getValue(i));
        }

        return element;
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
        final List<Node> content = mutable ? new ArrayList<Node>(content(container)) : content(container);

        // Iterate over the content
        for (Object childObject: content) {
            final Node childNode = (Node) childObject;

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

        // Close start tag
        sb.append('>');

        if (!element.elements().isEmpty()) {
            // Mixed content
            final Object firstChild = element.content().get(0);
            if (firstChild instanceof Text) {
                sb.append(((Text) firstChild).getText());
            }
            sb.append("[...]");
        } else {
            // Not mixed content
            sb.append(element.getText());
        }

        // Close element with end tag
        sb.append("</");
        sb.append(element.getQualifiedName());
        sb.append('>');

        return sb.toString();
    }

    public static String attributeToDebugString(Attribute attribute) {
        final StringBuilder sb = new StringBuilder(attribute.getQualifiedName());
        sb.append("=\"");
        sb.append(attribute.getValue());
        sb.append('\"');
        return sb.toString();
    }

    public static interface VisitorListener {
        void startElement(Element element);
        void endElement(Element element);
        void text(Text text);
    }

    /**
     * Return a list of all elements that precede startElement (not included) up to and including the given ancestor
     * element. Elements are ordered from the starting element to the ancestor.
     *
     * @param startElement      element to start with
     * @param ancestorElement   ancestor element to stop at
     * @return                  List<Element>
     */
    public static List<Element> findPrecedingElements(Element startElement, Element ancestorElement) {
        final List<Element> result = new ArrayList<Element>();
        findPrecedingElements(result, startElement, ancestorElement);
        return result;
    }

    private static void findPrecedingElements(List<Element> finalResult, Element startElement, Element ancestorElement) {
        final Element parentElement = startElement.getParent();
        if (parentElement == null)
            return;

        final List siblingElements = parentElement.elements();
        if (siblingElements.size() > 1) {
            final List<Element> result = new ArrayList<Element>();
            for (Iterator i = siblingElements.iterator(); i.hasNext();) {
                final Element currentElement = (Element) i.next();

                if (currentElement == startElement)
                    break;

                result.add(currentElement);
            }

            Collections.reverse(result);
            finalResult.addAll(result);
        }

        // Add parent
        finalResult.add(ancestorElement);

        // Find parent's preceding elements
        if (parentElement != ancestorElement) {
            findPrecedingElements(finalResult, parentElement, ancestorElement);
        }
    }

    /**
     * Whether the content of the node is simple content.
     *
     * @param node  node to check
     * @return      true iif the content of the node is simple content
     */
    public static boolean isSimpleContent(Node node) {
        if (node instanceof Attribute)
            return true;

        if (!(node instanceof Element))
            return false;

        final Element element = (Element) node;

        final List content = element.content();
        for (Iterator i = content.iterator(); i.hasNext();) {
            final Object currentContent = i.next();
            if (currentContent instanceof Element || currentContent instanceof ProcessingInstruction) {
                return false;
            }
        }

        return true;
    }
}
