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
package org.orbeon.oxf.xml;

import org.apache.commons.fileupload.DefaultFileItem;
import org.apache.commons.fileupload.DefaultFileItemFactory;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.lang.StringUtils;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.dom4j.util.UserDataDocumentFactory;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.parsers.*;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class XMLUtils {

    public static final String XSI_NAMESPACE_URI = "http://www.w3.org/2001/XMLSchema-instance";
    public static final String XSI_NAMESPACE_PREFIX = "xsi";
    public static final Namespace XSI_NAMESPACE = new Namespace(XSI_NAMESPACE_PREFIX, XSI_NAMESPACE_URI);
    public static final String XSI_PREFIX = "xsi";
    public static final String XSI_NIL_ATTRIBUTE = "nil";

    public static final String XSLT_NAMESPACE = "http://www.w3.org/1999/XSL/Transform";
    public static final String XSLT_PREFIX = "xsl";

    public static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();
    public static final EntityResolver ENTITY_RESOLVER = new EntityResolver();
    public static final ErrorHandler ERROR_HANDLER = new ErrorHandler();

    public static final org.dom4j.Document NULL_DOCUMENT;
    static {
        NULL_DOCUMENT = DocumentHelper.createDocument();
        Element nullElement = DocumentHelper.createElement("null");
        nullElement.addAttribute(new QName(XMLUtils.XSI_NIL_ATTRIBUTE,
                new Namespace(XMLUtils.XSI_PREFIX, XMLUtils.XSI_NAMESPACE_URI)), "true");
        NULL_DOCUMENT.setRootElement(nullElement);
    }

    private static DocumentBuilderFactory documentBuilderFactory;
    private static Map documentBuilders = null;
    private static DocumentFactory dom4jFactory = UserDataDocumentFactory.getInstance();

    private static SAXParserFactory nonValidatingSAXParserFactory;
    private static SAXParserFactory validatingSAXParserFactory;

    private static FileItemFactory fileItemFactory;

    static {
        try {
            // Enable XInclude
            // 
//            System.setProperty("orbeon.apache.xerces.xni.parser.XMLParserConfiguration",
//                    "org.orbeon.oxf.xml.XIncludeParserConfiguration");

            // Create factory
            documentBuilderFactory = (DocumentBuilderFactory) Class.forName
                    ("orbeon.apache.xerces.jaxp.DocumentBuilderFactoryImpl").newInstance();

            // Configure factory
            documentBuilderFactory.setNamespaceAware(true);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private XMLUtils() {
    }

    /**
     * Create a new DocumentBuilder.
     *
     * WARNING: Check how this is used in this file first before calling!
     */
    private static DocumentBuilder newDocumentBuilder() {
        synchronized (documentBuilderFactory) {
            try {
                return documentBuilderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new OXFException(e);
            }
        }
    }

    /**
     * Create a new SAX parser factory.
     *
     * WARNING: Use this only in special cases. In general, use newSAXParser().
     */
    public static SAXParserFactory createSAXParserFactory(boolean validating) {
        try {
            SAXParserFactory factory = (SAXParserFactory) Class.forName
                    ("orbeon.apache.xerces.jaxp.SAXParserFactoryImpl").newInstance();
            factory.setFeature("http://xml.org/sax/features/namespaces", true);
            factory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
            factory.setNamespaceAware(true); // this is needed by some tools in addition to the feature
            factory.setValidating(validating);
            return factory;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static SAXParser newSAXParser() {
        return newSAXParser(false);
    }

    public static synchronized SAXParser newSAXParser(boolean validating) {
        try {
            if (validating) {
                if (validatingSAXParserFactory == null)
                    validatingSAXParserFactory = createSAXParserFactory(true);
                return validatingSAXParserFactory.newSAXParser();
            } else {
                if (nonValidatingSAXParserFactory == null)
                    nonValidatingSAXParserFactory = createSAXParserFactory(false);
                return nonValidatingSAXParserFactory.newSAXParser();
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static class EntityResolver implements org.xml.sax.EntityResolver {
        public InputSource resolveEntity(String publicId,
                                         String systemId)
                throws SAXException, IOException {
            InputSource is = new InputSource();
            is.setSystemId(systemId);
            is.setPublicId(publicId);
            is.setByteStream(URLFactory.createURL(systemId).openConnection().getInputStream());
            return is;
        }
    }

    public static class ErrorHandler implements org.xml.sax.ErrorHandler {
        public void error(SAXParseException exception)
                throws SAXException {
            throw new ValidationException("Error: " + exception.getMessage(), new LocationData(exception));
        }

        public void fatalError(SAXParseException exception)
                throws SAXException {
            throw new ValidationException("Fatal Error: " + exception.getMessage(), new LocationData(exception));
        }

        public void warning(SAXParseException exception)
                throws SAXException {
            throw new ValidationException("Warning: " + exception.getMessage(), new LocationData(exception));
        }
    }

    public static Document createDocument() {
        return getThreadDocumentBuilder().newDocument();
    }

    public static Document stringToDOM(String xml) {
        try {
            return getThreadDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (SAXException e) {
            throw new OXFException(e);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static void stringToSAX(String xml, String systemId, ContentHandler contentHandler, boolean validating) {
        readerToSAX(new StringReader(xml), systemId, contentHandler, validating);
    }

    public static void inputStreamToSAX(InputStream inputStream, String systemId, ContentHandler contentHandler, boolean validating) {
        InputSource inputSource = new InputSource(inputStream);
        inputSource.setSystemId(systemId);
        inputSourceToSAX(inputSource, contentHandler, validating);
    }

    public static void readerToSAX(Reader reader, String systemId, ContentHandler contentHandler, boolean validating) {
        InputSource inputSource = new InputSource(reader);
        inputSource.setSystemId(systemId);
        inputSourceToSAX(inputSource, contentHandler, validating);
    }

    private static void inputSourceToSAX(InputSource inputSource, ContentHandler contentHandler, boolean validating) {
        try {
            XMLReader xmlReader = newSAXParser(validating).getXMLReader();
            xmlReader.setContentHandler(contentHandler);
            xmlReader.setEntityResolver(ENTITY_RESOLVER);
            xmlReader.setErrorHandler(ERROR_HANDLER);
            xmlReader.parse(inputSource);
        } catch (SAXParseException e) {
            throw new ValidationException(e.getMessage(), new LocationData(e));
        } catch (SAXException e) {
            throw new OXFException(e);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static Document fileToDOM(File xmlFile) {
        try {
            return getThreadDocumentBuilder().parse(new InputSource(new FileReader(xmlFile)));
        } catch (SAXException e) {
            throw new OXFException(e);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static Document base64ToDOM(String base64) {
        try {
            return getThreadDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(Base64.decode(base64))));
        } catch (SAXException e) {
            throw new OXFException(e);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Associated one DocumentBuilder per thread. This is so we avoid synchronizing (parse() for
     * example may take a lot of time on a DocumentBuilder) or creating DocumentBuilder instances
     * all the time. Since typically in an app server we work with a thread pool, not too many
     * instances of DocumentBuilder should be created.
     */
    private static DocumentBuilder getThreadDocumentBuilder() {
        Thread thread = Thread.currentThread();
        DocumentBuilder documentBuilder = (documentBuilders == null) ? null : (DocumentBuilder) documentBuilders.get(thread);
        // Try a first test outside the synchronized block
        if (documentBuilder == null) {
            synchronized(documentBuilderFactory) {
                // Redo the test within the synchronized block
                documentBuilder = (documentBuilders == null) ? null : (DocumentBuilder) documentBuilders.get(thread);
                if (documentBuilder == null) {
                    if (documentBuilders == null)
                        documentBuilders = new HashMap();
                    documentBuilder = newDocumentBuilder();
                    documentBuilders.put(thread, documentBuilder);
                }
            }
        }
        return documentBuilder;
    }

    public static org.dom4j.Document createDOM4JDocument() {
        return dom4jFactory.createDocument();
    }

    public static String domToString(Node node) {
        try {
            Transformer transformer = TransformerUtils.getXMLIdentityTransformer();
            DOMSource source = new DOMSource(node);

            StringWriter writer = new StringWriter();
            transformer.transform(source, new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /*
    *  Convert the result of XPathUtils.selectObjectValue() to a string
    */
    public static String objectToString(Object o) {
        StringBuffer buff = new StringBuffer();
        if (o instanceof List) {
            for (Iterator i = ((List) o).iterator(); i.hasNext();) {
                buff.append(objectToString(i.next())); // this will be a node
            }
        } else if(o instanceof org.dom4j.Element){
//            org.dom4j.Element element = cleanDOM4JNamespaces((org.dom4j.Element) o);
//            buff.append(domToString(element, false, false));
            buff.append(((org.dom4j.Element) o).asXML());
        } else if(o instanceof org.dom4j.Node){
            buff.append(((org.dom4j.Node)o).asXML());
        } else if (o instanceof String)
            buff.append((String) o);
        else if (o instanceof Number)
            buff.append((Number) o);
        else
            throw new OXFException("Should never happen");
        return buff.toString();
    }

//    public static org.dom4j.Element cleanDOM4JNamespaces(org.dom4j.Element element) {
//        element = element.createCopy();
//        element.accept(new VisitorSupport() {
//            public void visit(Element element) {
//                List content = new ArrayList(element.content());
//                for (Iterator i = content.iterator(); i.hasNext();) {
//                    Object o = i.next();
//                    if (o instanceof org.dom4j.Namespace) {
//                        org.dom4j.Namespace namespace = (org.dom4j.Namespace) o;
//                        if (element.getParent() != null && element.getParent().getNamespaceForPrefix(namespace.getPrefix()) == null && (namespace.getURI() == null || "".equals(namespace.getURI())))
//                            element.remove(namespace);
//                    }
//                }
//            }
//        });
//        return element;
//    }

    public static String domToString(org.dom4j.Node node, boolean trim, boolean compact) {
        try {
            if (node instanceof org.dom4j.Text) {
                return ((org.dom4j.Text) node).getText();
            } else {
                Element rootElement = node instanceof Element ? ((Element) node).createCopy()
                        : node instanceof org.dom4j.Document ? ((org.dom4j.Document) node).getRootElement().createCopy()
                        : null;

                // HACK: in some cases it looks like the pretty printer is not displaying the
                // content of some element. Normalizing the document fixes the issue.
//                rootElement.normalize();

                OutputFormat format = new OutputFormat();
                if (compact) {
                    format.setIndent(false);
                    format.setNewlines(false);
                    format.setTrimText(true);
                } else {
                    format.setIndentSize(4);
                    format.setNewlines(trim);
                    format.setTrimText(trim);
                }

                StringWriter writer = new StringWriter();
                XMLWriter xmlWriter = new XMLWriter(writer, format);
                xmlWriter.write(rootElement);
                xmlWriter.close();
                return writer.toString();
            }
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static String domToString(org.dom4j.Node node) {
        return domToString(node, true, false);
    }

    public static String domToCompactString(org.dom4j.Node node) {
        return domToString(node, true, true);
    }

    public static void error(String message) {
        throw new OXFException(message);
    }

    public static Map getNamespaceContext(Element element) {
        Map namespaces = new HashMap();
        for (Element currentNode = element; currentNode != null; currentNode = currentNode.getParent()) {
            List currentNamespaces = currentNode.declaredNamespaces();
            for (Iterator j = currentNamespaces.iterator(); j.hasNext();) {
                Namespace namespace = (Namespace) j.next();
                if (!namespaces.containsKey(namespace.getPrefix()))
                    namespaces.put(namespace.getPrefix(), namespace.getURI());
            }
        }
        return namespaces;
    }

    public static Attributes stripNamespaceAttributes(Attributes attributes) {
        final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";
        for (int i = 0; i < attributes.getLength(); i++) {
            if (XMLNS_URI.equals(attributes.getURI(i)) || "xmlns".equals(attributes.getLocalName(i))) {
                // Found at least one, strip
                AttributesImpl newAttributes = new AttributesImpl();
                for (int j = 0; j < attributes.getLength(); j++) {
                    if (!XMLNS_URI.equals(attributes.getURI(j)) && !"xmlns".equals(attributes.getLocalName(j)))
                        newAttributes.addAttribute(attributes.getURI(j), attributes.getLocalName(j),
                                attributes.getQName(j), attributes.getType(j), attributes.getValue(j));
                }
                return newAttributes;
            }
        }
        return attributes;
    }

    public static byte[] getDigest(Node node) {
        return getDigest(new DOMSource(node));
    }

    public static byte[] getDigest(org.dom4j.Document document) {
        return getDigest(new DocumentSource(document));
    }

    // Necessary for Saxon
    public static byte[] getDigest(NodeList nodeList) {
        if(nodeList.getLength() == 0)
            throw new OXFException("No node supplied");
        else if(nodeList.getLength() == 1)
            return getDigest((Node)nodeList.item(0));
        else {
            Document doc = XMLUtils.createDocument();
            org.w3c.dom.Element root = doc.createElement("root");
            for(int i = 0; i<nodeList.getLength(); i++) {
                Node n = nodeList.item(i);
                root.appendChild(n.cloneNode(true));
            }
            doc.appendChild(root);
            return getDigest(doc);
        }
    }



    /**
     * Compute a digest.
     */
    public static byte[] getDigest(Source source) {
        try {
            Transformer transformer = TransformerUtils.getIdentityTransformer();
            DigestContentHandler dch = new DigestContentHandler("MD5");
            transformer.transform(source, new SAXResult(dch));
            return dch.getResult();
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    public static DigestContentHandler getDigestContentHandler(String algorithm) {
        return new DigestContentHandler(algorithm);
    }

    /**
     * This digester is based on some existing public document (not sure which). There are some
     * changes though. It is not clear anymore why we used that document as a base, as this is
     * purely internal.
     *
     * The bottom line is that the digest should change whenever the infoset of the source XML
     * document changes.
     */
    public static class DigestContentHandler implements ContentHandler {

        private static final int ELEMENT_CODE = Node.ELEMENT_NODE;
        private static final int ATTRIBUTE_CODE = Node.ATTRIBUTE_NODE;
        private static final int TEXT_CODE = Node.TEXT_NODE;
        private static final int PROCESSING_INSTRUCTION_CODE = Node.PROCESSING_INSTRUCTION_NODE;
        private static final int NAMESPACE_CODE = 0XAA01; // some code that is none of the above

        private MessageDigest digest;

        public DigestContentHandler(String algorithm) {
            try {
                digest = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new OXFException(e);
            }
        }

        public byte[] getResult() {
            return digest.digest();
        }

        public void setDocumentLocator(Locator locator) {
        }

        public void startDocument()
                throws SAXException {
        }

        public void endDocument()
                throws SAXException {
        }

        public void startPrefixMapping(String prefix, String uri)
                throws SAXException {
            try {
                digest.update((byte) ((NAMESPACE_CODE >> 24) & 0xff));
                digest.update((byte) ((NAMESPACE_CODE >> 16) & 0xff));
                digest.update((byte) ((NAMESPACE_CODE >> 8) & 0xff));
                digest.update((byte) (NAMESPACE_CODE & 0xff));
                digest.update(prefix.getBytes("UnicodeBigUnmarked"));
                digest.update((byte) 0);
                digest.update((byte) 0);
                digest.update(uri.getBytes("UnicodeBigUnmarked"));
                digest.update((byte) 0);
                digest.update((byte) 0);
            } catch (UnsupportedEncodingException e) {
                throw new OXFException(e);
            }
        }

        public void endPrefixMapping(String prefix)
                throws SAXException {
        }

        public void startElement(String namespaceURI, String localName,
                                 String qName, Attributes atts)
                throws SAXException {
            try {
                digest.update((byte) ((ELEMENT_CODE >> 24) & 0xff));
                digest.update((byte) ((ELEMENT_CODE >> 16) & 0xff));
                digest.update((byte) ((ELEMENT_CODE >> 8) & 0xff));
                digest.update((byte) (ELEMENT_CODE & 0xff));
                digest.update(new String("{" + namespaceURI + "}" + localName).getBytes("UnicodeBigUnmarked"));
                digest.update((byte) 0);
                digest.update((byte) 0);
                int attCount = atts.getLength();
                digest.update((byte) ((attCount >> 24) & 0xff));
                digest.update((byte) ((attCount >> 16) & 0xff));
                digest.update((byte) ((attCount >> 8) & 0xff));
                digest.update((byte) (attCount & 0xff));
                for (int i = 0; i < attCount; i++) {
                    digest.update((byte) ((ATTRIBUTE_CODE >> 24) & 0xff));
                    digest.update((byte) ((ATTRIBUTE_CODE >> 16) & 0xff));
                    digest.update((byte) ((ATTRIBUTE_CODE >> 8) & 0xff));
                    digest.update((byte) (ATTRIBUTE_CODE & 0xff));
                    digest.update(new String("{" + atts.getURI(i) + "}" + atts.getLocalName(i)).getBytes("UnicodeBigUnmarked"));
                    digest.update((byte) 0);
                    digest.update((byte) 0);
                    digest.update(atts.getValue(i).getBytes("UnicodeBigUnmarked"));
                }
            } catch (UnsupportedEncodingException e) {
                throw new OXFException(e);
            }
        }

        public void endElement(String namespaceURI, String localName,
                               String qName)
                throws SAXException {
        }

        public void characters(char ch[], int start, int length)
                throws SAXException {
            try {
                digest.update((byte) ((TEXT_CODE >> 24) & 0xff));
                digest.update((byte) ((TEXT_CODE >> 16) & 0xff));
                digest.update((byte) ((TEXT_CODE >> 8) & 0xff));
                digest.update((byte) (TEXT_CODE & 0xff));
                String s = new String(ch, start, length);
                digest.update(s.getBytes("UnicodeBigUnmarked"));
                digest.update((byte) 0);
                digest.update((byte) 0);
            } catch (UnsupportedEncodingException e) {
                throw new OXFException(e);
            }
        }

        public void ignorableWhitespace(char ch[], int start, int length)
                throws SAXException {
        }

        public void processingInstruction(String target, String data)
                throws SAXException {
            try {
                digest.update((byte) ((PROCESSING_INSTRUCTION_CODE >> 24) & 0xff));
                digest.update((byte) ((PROCESSING_INSTRUCTION_CODE >> 16) & 0xff));
                digest.update((byte) ((PROCESSING_INSTRUCTION_CODE >> 8) & 0xff));
                digest.update((byte) (PROCESSING_INSTRUCTION_CODE & 0xff));
                digest.update(target.getBytes("UnicodeBigUnmarked"));
                digest.update((byte) 0);
                digest.update((byte) 0);
                digest.update(data.getBytes("UnicodeBigUnmarked"));
                digest.update((byte) 0);
                digest.update((byte) 0);
            } catch (UnsupportedEncodingException e) {
                throw new OXFException(e);
            }
        }

        public void skippedEntity(String name)
                throws SAXException {
        }
    }

    /**
     * Convert a double into a String without scientific notation.
     *
     * This is useful for XPath 1.0, which does not understand the scientific notation.
     */
    public static String removeScientificNotation(double value) {

        String result = Double.toString(value);
        int eIndex = result.indexOf('E');

        if (eIndex == -1) {
            // No scientific notation, return value as is
            return stripZeros(result);
        } else {
            // Scientific notation, convert value

            // Parse string representation
            String mantissa = result.substring(0, eIndex);
            boolean negative = mantissa.charAt(0) == '-';
            String sign = negative ? "-" : "";
            String mantissa1 = mantissa.substring(negative ? 1 : 0, negative ? 2 : 1);
            String mantissa2 = mantissa.substring(negative ? 3 : 2);
            int exponent = Integer.parseInt(result.substring(eIndex + 1));

            // Calculate result
            if (exponent > 0) {
                // Positive exponent, shift decimal point to the right
                int mantissa2Length = mantissa2.length();
                if (exponent > mantissa2Length) {
                    result = sign + mantissa1 + mantissa2 + nZeros(exponent - mantissa2Length);
                } else if (exponent == mantissa2Length) {
                    result = sign + mantissa1 + mantissa2;
                } else {
                    result = sign + mantissa1 + mantissa2.substring(0, exponent) + '.' + mantissa2.substring(exponent);
                }
            } else if (exponent == 0) {
                // Not sure if this can happen
                result = mantissa;
            } else {
                // Negative exponent, shift decimal point to the left
                result = sign + '0' + '.' + nZeros(-exponent - 1) + mantissa1 + mantissa2;
            }
            return stripZeros(result);
        }
    }

    /**
     * Remove unnecessary zeros after the decimal point, e.g. "12.000" becomes "12".
     */
    private static String stripZeros(String s) {
        int index = s.lastIndexOf('.');
        if (index == -1) return s;
        for (int i = index + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '0')
                return s;
        }
        return s.substring(0, index);
    }

    private static String nZeros(int n) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < n; i++)
            sb.append('0');
        return sb.toString();
    }

    public static void parseDocumentFragment(Reader reader, ContentHandler contentHandler) throws SAXException {
        try {
            XMLReader xmlReader = newSAXParser().getXMLReader();
            xmlReader.setContentHandler(new XMLFragmentContentHandler(contentHandler));
            ArrayList readers = new ArrayList(3);
            readers.add(new StringReader("<root>"));
            readers.add(reader);
            readers.add(new StringReader("</root>"));
            xmlReader.parse(new InputSource(new SequenceReader(readers.iterator())));
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static void parseDocumentFragment(String fragment, ContentHandler contentHandler) throws SAXException {
        if (fragment.indexOf("<") != -1 || fragment.indexOf("&") != -1) {
            try {
                XMLReader xmlReader = newSAXParser().getXMLReader();
                xmlReader.setContentHandler(new XMLFragmentContentHandler(contentHandler));
                xmlReader.parse(new InputSource(new StringReader("<root>" + fragment + "</root>")));
            } catch (IOException e) {
                throw new OXFException(e);
            }
        } else {
            // Optimization when fragment looks like text
            contentHandler.characters(fragment.toCharArray(), 0, fragment.length());
        }
    }

    private static class XMLFragmentContentHandler extends ForwardingContentHandler {
        private int elementCount = 0;

        public XMLFragmentContentHandler(ContentHandler contentHandler) {
            super(contentHandler);
        }

        public void startDocument() throws SAXException {
        }

        public void endDocument() throws SAXException {
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            elementCount++;
            if (elementCount > 1)
                super.startElement(uri, localname, qName, attributes);
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {
            elementCount--;
            if (elementCount > 0)
                super.endElement(uri, localname, qName);
        }
    }

    /**
     * Iterator over DOM Attributes.
     *
     * The object returned is of type XMLUtils.Attribute.
     */
    public static class AttributesIterator implements Iterator {

        private Attributes attributes;
        private int size;
        private int currentIndex;

        public AttributesIterator(Attributes attributes) {
            this.attributes = attributes;
            size = attributes.getLength();
            currentIndex = 0;
        }

        public boolean hasNext() {
            return currentIndex < size;
        }

        public Object next() {
            if (!hasNext())
                throw new NoSuchElementException();
            final int _currentIndex = currentIndex++;
            return new Attribute() {
                public String getURI() {
                    return attributes.getURI(_currentIndex);
                }

                public String getLocalName() {
                    return attributes.getLocalName(_currentIndex);
                }

                public String getQName() {
                    return attributes.getQName(_currentIndex);
                }

                public String getValue() {
                    return attributes.getValue(_currentIndex);
                }
            };
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Convert an Object to a String and generate SAX characters events.
     */
    public static void objectToCharacters(Object o, ContentHandler contentHandler) {
        try {
            char[] charValue = (o == null) ? null : o.toString().toCharArray();
            if (charValue != null)
                contentHandler.characters(charValue, 0, charValue.length);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Read characters from a Reader and generate SAX characters events.
     *
     * The caller has to close the Reader if needed.
     */
    public static void readerToCharacters(Reader reader, ContentHandler contentHandler) {
        try {
            // Work with buffered Reader
            reader = new BufferedReader(reader);
            // Read and write in chunks
            char[] buf = new char[1024];
            int count;
            while ((count = reader.read(buf)) != -1)
                contentHandler.characters(buf, 0, count);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Read bytes from an InputStream and generate SAX characters events in Base64 encoding. The
     * InputStream is closed when done.
     *
     * The caller has to close the stream if needed.
     */
    public static void inputStreamToBase64Characters(InputStream is, ContentHandler contentHandler) {

        try {
            // NOTE: New implementation based on ContentHandlerOutputStream
            OutputStream os = new ContentHandlerOutputStream(contentHandler);
            NetUtils.copyStream(new BufferedInputStream(is), os);
            os.close(); // necessary with ContentHandlerOutputStream to make sure all extra characters are written


//            // Work with buffered stream
//            is = new BufferedInputStream(is);
//            // Read and write in chunks
//            // Check http://www.ietf.org/rfc/rfc2045.txt for Base64
//            byte[] buf = new byte[76 * 3 / 4]; // maximum bytes that, once decoded, can fit in a line of 76 characters
//            char[] result = new char[76 + 1];
//            int count;
//            while ((count = is.read(buf, 0, buf.length)) != -1) {
//                String encoded;
//                if (count == buf.length) {
//                    encoded = Base64.encode(buf);
//                } else {
//                    // This can only be the last chunk
//                    byte[] tempBuf = new byte[count];
//                    System.arraycopy(buf, 0, tempBuf, 0, count);
//                    encoded = Base64.encode(tempBuf);
//                }
//                // The terminating LF is already added by encode()
//                encoded.getChars(0, encoded.length(), result, 0);
//                // Output characters
//                contentHandler.characters(result, 0, encoded.length());
//            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static byte[] base64StringToByteArray(String base64String) {
        return Base64.decode(base64String);
    }

    /**
     * Convert a String in xs:base64Binary to an xs:anyURI.
     *
     * NOTE: The implementation creates a temporary file. The Pipeline Context is required so
     * that the file can be deleted when no longer used.
     */
    public static String base64BinaryToAnyURI(PipelineContext pipelineContext, String value) {
        // Convert Base64 to binary first
        byte[] bytes = XMLUtils.base64StringToByteArray(value);

        return inputStreamToAnyURI(pipelineContext, new ByteArrayInputStream(bytes));
    }

    /**
     * Convert an InputStream to an xs:anyURI.
     *
     * NOTE: The implementation creates a temporary file. The Pipeline Context is required so
     * that the file can be deleted when no longer used.
     */
    public static String inputStreamToAnyURI(PipelineContext pipelineContext, InputStream inputStream) {
        // We use the commons fileupload utilities to save a file
        if (fileItemFactory == null)
            fileItemFactory = new DefaultFileItemFactory(0, SystemUtils.getTemporaryDirectory());
        final FileItem fileItem = fileItemFactory.createItem("dummy", "dummy", false, null);
        // Make sure the file is deleted when the context is destroyed
        pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter() {
            public void contextDestroyed(boolean success) {
                fileItem.delete();
            }
        });
        // Write to file
        OutputStream os = null;
        try {
            os = fileItem.getOutputStream();
            NetUtils.copyStream(inputStream, os);
        } catch (IOException e) {
            throw new OXFException(e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        }
        try {
            // Return a file URL
            return ((DefaultFileItem) fileItem).getStoreLocation().toURL().toExternalForm();
        } catch (MalformedURLException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Convert a String in xs:anyURI to an xs:base64Binary.
     *
     * The URI has to be a URL. It is read entirely
     */
    public static String anyURIToBase64Binary(String value) {
        InputStream is = null;
        try {
            // Read from URL and convert to Base64
            is = new URL(value).openStream();
            final StringBuffer sb = new StringBuffer();
            XMLUtils.inputStreamToBase64Characters(is, new ContentHandlerAdapter() {
                public void characters(char ch[], int start, int length) {
                    sb.append(ch, start, length);
                }
            });
            // Return Base64 String
            return sb.toString();
        } catch (IOException e) {
            throw new OXFException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        }
    }

    public static void anyURIToOutputStream(String value, OutputStream outputStream) {
        InputStream is = null;
        try {
            is = new URL(value).openStream();
            NetUtils.copyStream(is, outputStream);
        } catch (IOException e) {
            throw new OXFException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        }
    }

    public static org.dom4j.Document createNullDocument() {
        org.dom4j.Document result = DocumentHelper.createDocument();
        Element nullElement = DocumentHelper.createElement("null");
        nullElement.addAttribute(new QName(XMLUtils.XSI_NIL_ATTRIBUTE,
                new Namespace(XMLUtils.XSI_PREFIX, XMLUtils.XSI_NAMESPACE_URI)), "true");
        result.setRootElement(nullElement);
        return result;
    }

    /**
     * Extract a QName from an Element and an attribute name. The prefix of the QName must be in
     * scope. Return null if the attribute is not found.
     */
    public static QName extractAttributeValueQName(Element element, String attributeName) {
        return extractTextValueQName(element, element.attributeValue(attributeName));
    }

    /**
     * Extract a QName from an Element's string value. The prefix of the QName must be in scope.
     * Return null if the text is empty.
     */
    public static QName extractTextValueQName(Element element) {
        return extractTextValueQName(element, element.getStringValue());
    }

    private static QName extractTextValueQName(Element element, String qNameString) {
        if (qNameString == null)
            return null;
        qNameString = qNameString.trim();
        if (qNameString.length() == 0)
            return null;
        Map namespaces = XMLUtils.getNamespaceContext(element);
        int colonIndex = qNameString.indexOf(':');
        String prefix = qNameString.substring(0, colonIndex);
        String localName = qNameString.substring(colonIndex + 1);
        String namespaceURI = (String) namespaces.get(prefix);
        if (namespaceURI == null)
            throw new OXFException("No namespace declaration found for prefix: " + prefix);

        return new QName(localName, new Namespace(prefix, namespaceURI));
    }

    /**
     * Decode a String containing an exploded QName into a QName.
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
     * Encode a QName to an exploded QName String.
     */
    public static String qNameToexplodedQName(QName qName) {
        if ("".equals(qName.getNamespaceURI()))
            return qName.getName();
        else
            return "{" + qName.getNamespaceURI() + "}" + qName.getName();
    }

    /**
     * Clean-up namespaces. Some tools generate namespace "un-declarations" or the form
     * xmlns:abc="". While this is needed to keep the XML infoset correct, it is illegal to generate
     * such declarations in XML 1.0 (but it is legal in XML 1.1). Technically, this cleanup is
     * incorrect at the DOM and SAX level, so this should be used only in rare occasions, when
     * serializing certain documents to XML 1.0.
     */
    public static org.dom4j.Document adjustNamespaces(org.dom4j.Document document, boolean xml11) {
        if (xml11)
            return document;
        LocationSAXWriter writer = new LocationSAXWriter();
        LocationSAXContentHandler ch = new LocationSAXContentHandler();
        writer.setContentHandler(new NamespaceCleanupContentHandler(ch, xml11));
        try {
            writer.write(document);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
        return ch.getDocument();
    }

    public interface Attribute {
        public String getURI();

        public String getLocalName();

        public String getQName();

        public String getValue();
    }

    static public String escapeXML(String str) {
        str = StringUtils.replace(str, "&", "&amp;");
        str = StringUtils.replace(str, "<", "&lt;");
        str = StringUtils.replace(str, ">", "&gt;");
        str = StringUtils.replace(str, "\"", "&quot;");
        str = StringUtils.replace(str, "'", "&apos;");
        return str;
    }

    static public String unescapeXML(String str) {
        str = StringUtils.replace(str, "&amp;", "&");
        str = StringUtils.replace(str, "&lt;", "<");
        str = StringUtils.replace(str, "&gt;", ">");
        str = StringUtils.replace(str, "&quot;", "\"");
        str = StringUtils.replace(str, "&apos;", "'");
        return str;
    }

    static public String escapeHTML(String str) {
        str = StringUtils.replace(str, "&", "&amp;");
        str = StringUtils.replace(str, "<", "&lt;");
        str = StringUtils.replace(str, ">", "&gt;");
        str = StringUtils.replace(str, "\"", "&quot;");
        str = StringUtils.replace(str, "'", "&#39;");
        return str;
    }
}
