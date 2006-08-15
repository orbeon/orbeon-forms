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
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class XMLUtils {

    private static Logger logger = Logger.getLogger(XMLUtils.class);

    public static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();
    public static final EntityResolver ENTITY_RESOLVER = new EntityResolver();
    public static final ErrorHandler ERROR_HANDLER = new ErrorHandler();

    private static DocumentBuilderFactory documentBuilderFactory;
    private static Map documentBuilders = null;

    private static SAXParserFactory nonValidatingXIncludeSAXParserFactory;
    private static SAXParserFactory validatingXIncludeSAXParserFactory;
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
    public static SAXParserFactory createSAXParserFactory(boolean validating, boolean handleXInclude) {
        try {
            SAXParserFactory factory = new XercesSAXParserFactoryImpl(handleXInclude);
            factory.setValidating(validating);
            return factory;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static SAXParser newSAXParser() {
        return newSAXParser(false, true);
    }

    public static synchronized SAXParser newSAXParser(boolean validating, boolean handleXInclude) {
        try {
            if (validating) {
                if (handleXInclude) {
                    if (validatingXIncludeSAXParserFactory == null)
                        validatingXIncludeSAXParserFactory = createSAXParserFactory(validating, handleXInclude);
                    return validatingXIncludeSAXParserFactory.newSAXParser();
                } else {
                    if (validatingSAXParserFactory == null)
                        validatingSAXParserFactory = createSAXParserFactory(validating, handleXInclude);
                    return validatingSAXParserFactory.newSAXParser();
                }
            } else {
                if (handleXInclude) {
                    if (nonValidatingXIncludeSAXParserFactory == null)
                        nonValidatingXIncludeSAXParserFactory = createSAXParserFactory(validating, handleXInclude);
                    return nonValidatingXIncludeSAXParserFactory.newSAXParser();
                } else {
                    if (nonValidatingSAXParserFactory == null)
                        nonValidatingSAXParserFactory = createSAXParserFactory(validating, handleXInclude);
                    return nonValidatingSAXParserFactory.newSAXParser();
                }
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static String prefixFromQName(String qName) {
        final int colonIndex = qName.indexOf(':');
        return (colonIndex == -1) ? "" : qName.substring(0, colonIndex);
    }

    public static String localNameFromQName(String qName) {
        final int colonIndex = qName.indexOf(':');
        return (colonIndex == -1) ? qName : qName.substring(colonIndex + 1);
    }

    /**
     * Return "" if there is no prefix, null if the prefix is not mapped, a URI otherwise.
     */
    public static String uriFromQName(String qName, NamespaceSupport3 namespaceSupport) {
        final String prefix = prefixFromQName(qName);
        if ("".equals(prefix))
            return "";
        return namespaceSupport.getURI(prefix);
    }

    public static String buildQName(String prefix, String localname) {
        return (prefix.equals("")) ? localname : prefix + ":" + localname;
    }

    /**
     * Encode a URI and local name to an exploded QName (also known as a "Clark name") String.
     */
    public static String buildExplodedQName(String uri, String localname) {
        if ("".equals(uri))
            return localname;
        else
            return "{" + uri + "}" + localname;
    }

    public static class EntityResolver implements org.xml.sax.EntityResolver {
        public InputSource resolveEntity(String publicId, String systemId)
                throws SAXException, IOException {
            InputSource is = new InputSource();
            is.setSystemId(systemId);
            is.setPublicId(publicId);
            final URL url = URLFactory.createURL(systemId);
            is.setByteStream(url.openConnection().getInputStream());
            return is;
        }
    }

    public static class ErrorHandler implements org.xml.sax.ErrorHandler {
        public void error(SAXParseException exception) throws SAXException {
            // NOTE: We used to throw here, but we probably shouldn't.
            logger.info("Error: " + exception);
        }

        public void fatalError(SAXParseException exception) throws SAXException {
            throw new ValidationException("Fatal error: " + exception.getMessage(), new LocationData(exception));
        }

        public void warning(SAXParseException exception) throws SAXException {
            logger.info("Warning: " + exception);
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

    public static void stringToSAX(String xml, String systemId, ContentHandler contentHandler, boolean validating, boolean handleXInclude) {
        readerToSAX(new StringReader(xml), systemId, contentHandler, validating, handleXInclude);
    }

    public static void inputStreamToSAX(InputStream inputStream, String systemId, ContentHandler contentHandler, boolean validating, boolean handleXInclude) {
        InputSource inputSource = new InputSource(inputStream);
        inputSource.setSystemId(systemId);
        inputSourceToSAX(inputSource, contentHandler, validating, handleXInclude);
    }

    public static void readerToSAX(Reader reader, String systemId, ContentHandler contentHandler, boolean validating, boolean handleXInclude) {
        InputSource inputSource = new InputSource(reader);
        inputSource.setSystemId(systemId);
        inputSourceToSAX(inputSource, contentHandler, validating, handleXInclude);
    }

    private static void inputSourceToSAX(InputSource inputSource, ContentHandler contentHandler, boolean validating, boolean handleXInclude) {
        try {
            XMLReader xmlReader = newSAXParser(validating, handleXInclude).getXMLReader();
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
            synchronized (documentBuilderFactory) {
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

    public static void error(String message) {
        throw new OXFException(message);
    }

    public static Attributes stripNamespaceAttributes(Attributes attributes) {
        for (int i = 0; i < attributes.getLength(); i++) {
            if (XMLConstants.XMLNS_URI.equals(attributes.getURI(i)) || "xmlns".equals(attributes.getLocalName(i))) {
                // Found at least one, strip
                AttributesImpl newAttributes = new AttributesImpl();
                for (int j = 0; j < attributes.getLength(); j++) {
                    if (!XMLConstants.XMLNS_URI.equals(attributes.getURI(j)) && !"xmlns".equals(attributes.getLocalName(j)))
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

    // Necessary for Saxon
    public static byte[] getDigest(NodeList nodeList) {
        if (nodeList.getLength() == 0)
            throw new OXFException("No node supplied");
        else if (nodeList.getLength() == 1)
            return getDigest((Node) nodeList.item(0));
        else {
            Document doc = XMLUtils.createDocument();
            org.w3c.dom.Element root = doc.createElement("root");
            for (int i = 0; i < nodeList.getLength(); i++) {
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
        /**
         * 4/6/2005 d : Previously we were using String.getBytes( "UnicodeBigUnmarked" ).  ( Believe
         * the code was copied from RFC 2803 ). This first tries to get a java.nio.Charset with
         * the name if this fails it uses a sun.io.CharToByteConverter.
         * Now in the case of "UnicodeBigUnmarked" there is no such Charset so a
         * CharToByteConverter, utf-16be, is used.  Unfortunately this negative lookup is expensive.
         * ( Costing us a full second in the 50thread/512MB test. )
         * The solution, of course, is just to use get the appropriate Charset and hold on to it.
         */
        private static final Charset utf16BECharset = Charset.forName("UTF-16BE");
        /**
         * Encoder has state and therefore cannot be shared across threads.
         */
        private final CharsetEncoder charEncoder;
        private java.nio.CharBuffer charBuff;
        private java.nio.ByteBuffer byteBuff;

        private MessageDigest digest;

        public DigestContentHandler(String algorithm) {
            try {
                digest = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new OXFException(e);
            }
            charEncoder = utf16BECharset.newEncoder();
            charBuff = java.nio.CharBuffer.allocate(64);
            byteBuff = java.nio.ByteBuffer.allocate(128);
        }

        private void ensureCharBuffRemaining(final int size) {
            if (charBuff.remaining() < size) {
                final int cpcty = (charBuff.capacity() + size) * 2;
                final java.nio.CharBuffer newChBuf = java.nio.CharBuffer.allocate(cpcty);
                newChBuf.put(charBuff);
                charBuff = newChBuf;
            }
        }

        private void updateWithCharBuf() {
            final int reqSize = (int) charEncoder.maxBytesPerChar() * charBuff.position();
            if (byteBuff.capacity() < reqSize) {
                byteBuff = java.nio.ByteBuffer.allocate(2 * reqSize);
            }

            // Make ready for read
            charBuff.flip();

            final CoderResult cr = charEncoder.encode(charBuff, byteBuff, true);
            try {

                if (cr.isError()) cr.throwException();

                // Make ready for read
                byteBuff.flip();

                final byte[] byts = byteBuff.array();
                final int len = byteBuff.remaining();
                final int strt = byteBuff.arrayOffset();
                digest.update(byts, strt, len);

            } catch (final CharacterCodingException e) {
                throw new OXFException(e);
            } catch (java.nio.BufferOverflowException e) {
                throw new OXFException(e);
            } catch (java.nio.BufferUnderflowException e) {
                throw new OXFException(e);
            } finally {
                // Make ready for write
                charBuff.clear();
                byteBuff.clear();
            }
        }

        private void updateWith(final String s) {
            addToCharBuff(s);
            updateWithCharBuf();
        }

        private void updateWith(final char[] chArr, final int ofst, final int len) {
            ensureCharBuffRemaining(len);
            charBuff.put(chArr, ofst, len);
            updateWithCharBuf();
        }

        private void addToCharBuff(final char c) {
            ensureCharBuffRemaining(1);
            charBuff.put(c);
        }

        private void addToCharBuff(final String s) {
            final int size = s.length();
            ensureCharBuffRemaining(size);
            charBuff.put(s);
        }

        public byte[] getResult() {
            return digest.digest();
        }

        public void setDocumentLocator(Locator locator) {
        }

        public void startDocument() throws SAXException {
            charBuff.clear();
            byteBuff.clear();
            charEncoder.reset();
        }

        public void endDocument() throws SAXException {
        }

        public void startPrefixMapping(String prefix, String uri)
                throws SAXException {

            digest.update((byte) ((NAMESPACE_CODE >> 24) & 0xff));
            digest.update((byte) ((NAMESPACE_CODE >> 16) & 0xff));
            digest.update((byte) ((NAMESPACE_CODE >> 8) & 0xff));
            digest.update((byte) (NAMESPACE_CODE & 0xff));
            updateWith(prefix);
            digest.update((byte) 0);
            digest.update((byte) 0);
            updateWith(uri);
            digest.update((byte) 0);
            digest.update((byte) 0);
        }

        public void endPrefixMapping(String prefix)
                throws SAXException {
        }

        public void startElement(String namespaceURI, String localName,
                                 String qName, Attributes atts)
                throws SAXException {
            digest.update((byte) ((ELEMENT_CODE >> 24) & 0xff));
            digest.update((byte) ((ELEMENT_CODE >> 16) & 0xff));
            digest.update((byte) ((ELEMENT_CODE >> 8) & 0xff));
            digest.update((byte) (ELEMENT_CODE & 0xff));

            addToCharBuff('{');
            addToCharBuff(namespaceURI);
            addToCharBuff('}');
            addToCharBuff(localName);
            updateWithCharBuf();

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

                final String attURI = atts.getURI(i);
                final String attNam = atts.getLocalName(i);

                addToCharBuff('{');
                addToCharBuff(attURI);
                addToCharBuff('}');
                addToCharBuff(attNam);
                updateWithCharBuf();

                digest.update((byte) 0);
                digest.update((byte) 0);

                final String val = atts.getValue(i);
                updateWith(val);
            }
        }

        public void endElement(String namespaceURI, String localName,
                               String qName)
                throws SAXException {
        }

        public void characters(char ch[], int start, int length) throws SAXException {
            digest.update((byte) ((TEXT_CODE >> 24) & 0xff));
            digest.update((byte) ((TEXT_CODE >> 16) & 0xff));
            digest.update((byte) ((TEXT_CODE >> 8) & 0xff));
            digest.update((byte) (TEXT_CODE & 0xff));

            updateWith(ch, start, length);

            digest.update((byte) 0);
            digest.update((byte) 0);
        }

        public void ignorableWhitespace(char ch[], int start, int length)
                throws SAXException {
        }

        public void processingInstruction(String target, String data)
                throws SAXException {
            digest.update((byte) ((PROCESSING_INSTRUCTION_CODE >> 24) & 0xff));
            digest.update((byte) ((PROCESSING_INSTRUCTION_CODE >> 16) & 0xff));
            digest.update((byte) ((PROCESSING_INSTRUCTION_CODE >> 8) & 0xff));
            digest.update((byte) (PROCESSING_INSTRUCTION_CODE & 0xff));

            updateWith(target);

            digest.update((byte) 0);
            digest.update((byte) 0);

            updateWith(data);

            digest.update((byte) 0);
            digest.update((byte) 0);
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
        // Get FileItem
        final FileItem fileItem = prepareFileItem(pipelineContext);
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
     * Return a FileItem which is going to be automatically destroyed upon context destruction.
     */
    public static FileItem prepareFileItem(PipelineContext pipelineContext) {
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
        // Return FileItem object
        return fileItem;
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

    public interface Attribute {
        public String getURI();
        public String getLocalName();
        public String getQName();
        public String getValue();
    }

    public static String escapeXMLMinimal(String str) {
        str = StringUtils.replace(str, "&", "&amp;");
        str = StringUtils.replace(str, "<", "&lt;");
        return str;
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

    static public String toString(final Locator loc) {
        return loc.getSystemId() + ", line " + loc.getLineNumber() + ", column "
                + loc.getColumnNumber();
    }

    /**
     * Tell whether a string is whitespace, empty ("") or null.
     *
     * This is borrowed from Apache commons since we are not using the latest Apache commons.
     */
    public static boolean isBlank(String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if ((!Character.isWhitespace(str.charAt(i)))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param atts src attribs
     * @return new AttributesImpl containing  all attribs that were in src attribs and that were
     *         in the default name space.
     */
    public static AttributesImpl getAttribsFromDefaultNamespace(final Attributes atts) {
        final AttributesImpl ret = new AttributesImpl();
        final int size = atts.getLength();
        for (int i = 0; i < size; i++) {
            final String ns = atts.getURI(i);
            if (!"".equals(ns)) continue;
            final String lnam = atts.getLocalName(i);
            final String qnam = atts.getQName(i);
            final String typ = atts.getType(i);
            final String val = atts.getValue(i);
            ret.addAttribute(ns, lnam, qnam, typ, val);
        }
        return ret;
    }

    public static Attributes addOrReplaceAttribute(Attributes attributes, String uri, String prefix, String localname, String value) {
        final AttributesImpl newAttributes = new AttributesImpl();
        for (int i = 0; i < attributes.getLength(); i++) {
            final String attributeURI = attributes.getURI(i);
            final String attributeValue = attributes.getValue(i);
            final String attributeType = attributes.getType(i);
            final String attributeQName = attributes.getQName(i);
            final String attributeLocalname = attributes.getLocalName(i);

            if (!(uri.equals(attributeURI) && localname.equals(attributeLocalname)))
                newAttributes.addAttribute(attributeURI, attributeLocalname, attributeQName, attributeType, attributeValue);
        }
        newAttributes.addAttribute(uri, localname, XMLUtils.buildQName(prefix, localname), ContentHandlerHelper.CDATA, value);
        return newAttributes;
    }
}
