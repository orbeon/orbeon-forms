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
package org.orbeon.oxf.xml;

import orbeon.apache.xerces.impl.Constants;
import orbeon.apache.xerces.impl.XMLEntityManager;
import orbeon.apache.xerces.impl.XMLErrorReporter;
import orbeon.apache.xerces.xni.parser.XMLInputSource;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorFactory;
import org.orbeon.oxf.processor.ProcessorFactoryRegistry;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.oxf.xml.xerces.XercesSAXParserFactoryImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.parsers.*;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
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

    private static final ContentHandler NULL_CONTENT_HANDLER = new XMLReceiverAdapter();

    private static final DocumentBuilderFactory documentBuilderFactory;
    private static Map<Thread, DocumentBuilder> documentBuilders = null;

    private static Map<String, SAXParserFactory> parserFactories = new HashMap<String, SAXParserFactory>();

    public static final String XML_CONTENT_TYPE1 = "text/xml";
    public static final String XML_CONTENT_TYPE2 = "application/xml";
    public static final String XML_CONTENT_TYPE3_SUFFIX = "+xml";
    public static final String XML_CONTENT_TYPE = XML_CONTENT_TYPE2;
    public static final String TEXT_CONTENT_TYPE_PREFIX = "text/";

    public static class ParserConfiguration {
        public final boolean validating;
        public final boolean handleXInclude;
        public final boolean externalEntities;

        public ParserConfiguration(boolean validating, boolean handleXInclude, boolean externalEntities) {
            this.validating = validating;
            this.handleXInclude = handleXInclude;
            this.externalEntities = externalEntities;
        }

        public String getKey() {
            return (validating ? "1" : "0") + (handleXInclude ? "1" : "0") + (externalEntities ? "1" : "0");
        }

        public static final ParserConfiguration PLAIN = new ParserConfiguration(false, false, false);
        public static final ParserConfiguration XINCLUDE_ONLY = new ParserConfiguration(false, true, false);
    }

    static {
        try {
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
    public static SAXParserFactory createSAXParserFactory(XMLUtils.ParserConfiguration parserConfiguration) {
        try {
            return new XercesSAXParserFactoryImpl(parserConfiguration);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Get a SAXParserFactory to build combinations of validating and XInclude-aware SAXParser.
     *
     * @param parserConfiguration  parser configuration
     * @return                     the SAXParserFactory
     */
    public static synchronized SAXParserFactory getSAXParserFactory(XMLUtils.ParserConfiguration parserConfiguration) {

        final String key = parserConfiguration.getKey();

        final SAXParserFactory existingFactory = parserFactories.get(key);
        if (existingFactory != null)
            return existingFactory;

        final SAXParserFactory newFactory = createSAXParserFactory(parserConfiguration);
        parserFactories.put(key, newFactory);
        return newFactory;
    }

    /**
     * Create a new SAXParser, which can be a combination of validating and/or XInclude-aware.
     *
     * @param parserConfiguration  parser configuration
     * @return                     the SAXParser
     */
    private static synchronized SAXParser newSAXParser(XMLUtils.ParserConfiguration parserConfiguration) {
        try {
            return getSAXParserFactory(parserConfiguration).newSAXParser();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static SAXParser newSAXParser() {
        return newSAXParser(XMLUtils.ParserConfiguration.XINCLUDE_ONLY);
    }

    public static XMLReader newXMLReader(XMLUtils.ParserConfiguration parserConfiguration) {
        final SAXParser saxParser = XMLUtils.newSAXParser(parserConfiguration);
        try {
            final XMLReader xmlReader = saxParser.getXMLReader();
            xmlReader.setEntityResolver(XMLUtils.ENTITY_RESOLVER);
            xmlReader.setErrorHandler(XMLUtils.ERROR_HANDLER);
            return xmlReader;
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
        else {
            final StringBuilder sb = new StringBuilder(uri.length() + localname.length() + 2);
            sb.append('{');
            sb.append(uri);
            sb.append('}');
            sb.append(localname);
            return sb.toString();
        }
    }

    /**
     * Convert dom4j attributes to SAX attributes.
     *
     * @param element   dom4j Element
     * @return          SAX Attributes
     */
    public static Attributes getSAXAttributes(Element element) {
        final AttributesImpl result = new AttributesImpl();
        for (Iterator i = element.attributeIterator(); i.hasNext();) {
            final org.dom4j.Attribute attribute = (org.dom4j.Attribute) i.next();

            result.addAttribute(attribute.getNamespaceURI(), attribute.getName(), attribute.getQualifiedName(),
                    ContentHandlerHelper.CDATA, attribute.getValue());
        }
        return result;
    }

    /**
     * Return whether the given mediatype is considered as XML.
     *
     * TODO: This does test on the mediatype only, but we need one to check the content type as well for the case
     * "text/html; charset=foobar"
     *
     * @param mediatype mediatype or null
     * @return          true if not null and XML mediatype, false otherwise
     */
    public static boolean isXMLMediatype(String mediatype) {
        return mediatype != null && (mediatype.equals(XML_CONTENT_TYPE1)
                || mediatype.equals(XML_CONTENT_TYPE2)
                || mediatype.endsWith(XML_CONTENT_TYPE3_SUFFIX));
    }

    /**
     * Return whether the given content type is considered as text.
     *
     * @param contentType   content type or null
     * @return              true if not null and a text content type, false otherwise.
     */
    public static boolean isTextContentType(String contentType) {
        return contentType != null && contentType.startsWith(TEXT_CONTENT_TYPE_PREFIX);
    }

    /**
     * Return whether the given content type is considered as text or JSON.
     *
     * NOTE: There was debate about whether JSON is text or not and whether it should have a text/* mediatype:
     *
     * http://www.alvestrand.no/pipermail/ietf-types/2006-February/001655.html
     *
     * @param contentType   content type or null
     * @return              true if not null and a text or JSON content type, false otherwise
     */
    public static boolean isTextOrJSONContentType(String contentType) {
        return contentType != null && (isTextContentType(contentType) || contentType.startsWith( "application/json"));
    }

    /**
     * Given an input stream, return a reader. This performs encoding detection as per the XML spec. Caller must close
     * the resulting Reader when done.
     *
     * @param uri           resource URI (probably unneeded)
     * @param inputStream   InputStream to process
     * @return              Reader initialized with the proper encoding
     * @throws IOException
     */
    public static Reader getReaderFromXMLInputStream(String uri, InputStream inputStream) throws IOException {
        // Create a Xerces XMLInputSource
        final XMLInputSource inputSource = new XMLInputSource(uri, null, null, inputStream, null);
        // Obtain encoding from Xerces
        final XMLEntityManager entityManager = new XMLEntityManager();
        entityManager.setProperty(Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_REPORTER_PROPERTY, new XMLErrorReporter());// prevent NPE by providing this
        entityManager.setupCurrentEntity("[xml]", inputSource, false, true);// the result is the encoding, but we don't use it directly

        return entityManager.getCurrentEntity().reader;
    }

    public static class EntityResolver implements org.xml.sax.EntityResolver {
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            final InputSource is = new InputSource();
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

    /**
     * Parse a string into SAX events. If the string is empty or only contains white space, output an empty document.
     *
     * @param xml                   XML string
     * @param systemId              system id of the document, or null
     * @param xmlReceiver           receiver to output to
     * @param parserConfiguration   parser configuration
     * @param handleLexical         whether the XML parser must output SAX LexicalHandler events, including comments
     */
    public static void stringToSAX(String xml, String systemId, XMLReceiver xmlReceiver, XMLUtils.ParserConfiguration parserConfiguration, boolean handleLexical) {
        if (xml.trim().equals("")) {
            try {
                xmlReceiver.startDocument();
                xmlReceiver.endDocument();
            } catch (SAXException e) {
                throw new OXFException(e);
            }
        } else {
            readerToSAX(new StringReader(xml), systemId, xmlReceiver, parserConfiguration, handleLexical);
        }
    }

    /**
     * Read a URL into SAX events.
     *
     * @param systemId              system id of the document
     * @param xmlReceiver           receiver to output to
     * @param parserConfiguration   parser configuration
     * @param handleLexical         whether the XML parser must output SAX LexicalHandler events, including comments
     */
    public static void urlToSAX(String systemId, XMLReceiver xmlReceiver, XMLUtils.ParserConfiguration parserConfiguration, boolean handleLexical) {
        try {
            final URL url = URLFactory.createURL(systemId);
            final InputStream is = url.openStream();
            final InputSource inputSource = new InputSource(is);
            inputSource.setSystemId(systemId);
            try {
                inputSourceToSAX(inputSource, xmlReceiver, parserConfiguration, handleLexical);
            } finally {
                is.close();
            }
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static void inputStreamToSAX(InputStream inputStream, String systemId, XMLReceiver xmlReceiver, XMLUtils.ParserConfiguration parserConfiguration, boolean handleLexical) {
        final InputSource inputSource = new InputSource(inputStream);
        inputSource.setSystemId(systemId);
        inputSourceToSAX(inputSource, xmlReceiver, parserConfiguration, handleLexical);
    }

    public static void readerToSAX(Reader reader, String systemId, XMLReceiver xmlReceiver, XMLUtils.ParserConfiguration parserConfiguration, boolean handleLexical) {
        final InputSource inputSource = new InputSource(reader);
        inputSource.setSystemId(systemId);
        inputSourceToSAX(inputSource, xmlReceiver, parserConfiguration, handleLexical);
    }

    private static void inputSourceToSAX(InputSource inputSource, XMLReceiver xmlReceiver, XMLUtils.ParserConfiguration parserConfiguration, boolean handleLexical) {
        try {
            final XMLReader xmlReader = newSAXParser(parserConfiguration).getXMLReader();
//            xmlReader.setContentHandler(new SAXLoggerProcessor.DebugContentHandler(contentHandler));
            xmlReader.setContentHandler(xmlReceiver);
            if (handleLexical)
                xmlReader.setProperty(XMLConstants.SAX_LEXICAL_HANDLER, xmlReceiver);

            xmlReader.setEntityResolver(ENTITY_RESOLVER);
            xmlReader.setErrorHandler(ERROR_HANDLER);
            xmlReader.parse(inputSource);
        } catch (SAXParseException e) {
            throw new ValidationException(e.getMessage(), new LocationData(e));
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Return whether the given string contains well-formed XML.
     *
     * @param xmlString     string to check
     * @return              true iif the given string contains well-formed XML
     */
    public static boolean isWellFormedXML(String xmlString) {

        // Empty string is never well-formed XML
        if (xmlString.trim().length() == 0)
            return false;

        try {
            final XMLReader xmlReader = newSAXParser(XMLUtils.ParserConfiguration.PLAIN).getXMLReader();
            xmlReader.setContentHandler(NULL_CONTENT_HANDLER);
            xmlReader.setEntityResolver(ENTITY_RESOLVER);
            xmlReader.setErrorHandler(new org.xml.sax.ErrorHandler() {
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                public void warning(SAXParseException exception) throws SAXException {
                }
            });
            xmlReader.parse(new InputSource(new StringReader(xmlString)));
            return true;
        } catch (Exception e) {
            // Ideally we would like the parser to not throw as this is time-consuming, but not sure how to achieve that
            return false;
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
        DocumentBuilder documentBuilder = (documentBuilders == null) ? null : documentBuilders.get(thread);
        // Try a first test outside the synchronized block
        if (documentBuilder == null) {
            synchronized (documentBuilderFactory) {
                // Redo the test within the synchronized block
                documentBuilder = (documentBuilders == null) ? null : documentBuilders.get(thread);
                if (documentBuilder == null) {
                    if (documentBuilders == null)
                        documentBuilders = new HashMap<Thread, DocumentBuilder>();
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

            StringBuilderWriter writer = new StringBuilderWriter();
            transformer.transform(source, new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    public static void error(String message) {
        throw new OXFException(message);
    }

//    public static Attributes stripNamespaceAttributes(Attributes attributes) {
//        for (int i = 0; i < attributes.getLength(); i++) {
//            if (XMLConstants.XMLNS_URI.equals(attributes.getURI(i)) || "xmlns".equals(attributes.getLocalName(i))) {
//                // Found at least one, strip
//                AttributesImpl newAttributes = new AttributesImpl();
//                for (int j = 0; j < attributes.getLength(); j++) {
//                    if (!XMLConstants.XMLNS_URI.equals(attributes.getURI(j)) && !"xmlns".equals(attributes.getLocalName(j)))
//                        newAttributes.addAttribute(attributes.getURI(j), attributes.getLocalName(j),
//                                attributes.getQName(j), attributes.getType(j), attributes.getValue(j));
//                }
//                return newAttributes;
//            }
//        }
//        return attributes;
//    }

//    public static byte[] getDigest(Node node) {
//        return getDigest(new DOMSource(node));
//    }

//    // Necessary for Saxon
//    public static byte[] getDigest(NodeList nodeList) {
//        if (nodeList.getLength() == 0)
//            throw new OXFException("No node supplied");
//        else if (nodeList.getLength() == 1)
//            return getDigest((Node) nodeList.item(0));
//        else {
//            Document doc = XMLUtils.createDocument();
//            org.w3c.dom.Element root = doc.createElement("root");
//            for (int i = 0; i < nodeList.getLength(); i++) {
//                Node n = nodeList.item(i);
//                root.appendChild(n.cloneNode(true));
//            }
//            doc.appendChild(root);
//            return getDigest(doc);
//        }
//    }

    /**
     * Compute a digest for a SAX source.
     */
    public static byte[] getDigest(Source source) {
        final DigestContentHandler digester = new DigestContentHandler("MD5");
        TransformerUtils.sourceToSAX(source, digester);
        return digester.getResult();
    }

    /**
     * This digester is based on some existing public document (not sure which). There are some
     * changes though. It is not clear anymore why we used that document as a base, as this is
     * purely internal.
     *
     * The bottom line is that the digest should change whenever the infoset of the source XML
     * document changes.
     */
    public static class DigestContentHandler implements XMLReceiver {

        private static final int ELEMENT_CODE = Node.ELEMENT_NODE;
        private static final int ATTRIBUTE_CODE = Node.ATTRIBUTE_NODE;
        private static final int TEXT_CODE = Node.TEXT_NODE;
        private static final int PROCESSING_INSTRUCTION_CODE = Node.PROCESSING_INSTRUCTION_NODE;
        private static final int NAMESPACE_CODE = 0XAA01;   // some code that is none of the above
        private static final int COMMENT_CODE = 0XAA02;     // some code that is none of the above

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

        public void startPrefixMapping(String prefix, String uri) throws SAXException {

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

        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {

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

        public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
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

        public void processingInstruction(String target, String data) throws SAXException {

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

        public void skippedEntity(String name) throws SAXException {
        }

        public void startDTD(String name, String publicId, String systemId) throws SAXException {
        }

        public void endDTD() throws SAXException {
        }

        public void startEntity(String name) throws SAXException {
        }

        public void endEntity(String name) throws SAXException {
        }

        public void startCDATA() throws SAXException {
        }

        public void endCDATA() throws SAXException {
        }

        public void comment(char[] ch, int start, int length) throws SAXException {

            // We do consider comments significant for the purpose of digesting. But should this be an option?

            digest.update((byte) ((COMMENT_CODE >> 24) & 0xff));
            digest.update((byte) ((COMMENT_CODE >> 16) & 0xff));
            digest.update((byte) ((COMMENT_CODE >> 8) & 0xff));
            digest.update((byte) (COMMENT_CODE & 0xff));

            updateWith(ch, start, length);

            digest.update((byte) 0);
            digest.update((byte) 0);
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

    public static void parseDocumentFragment(Reader reader, XMLReceiver xmlReceiver) throws SAXException {
        try {
            final XMLReader xmlReader = newSAXParser().getXMLReader();
            xmlReader.setContentHandler(new XMLFragmentReceiver(xmlReceiver));
            final ArrayList<Reader> readers = new ArrayList<Reader>(3);
            readers.add(new StringReader("<root>"));
            readers.add(reader);
            readers.add(new StringReader("</root>"));
            xmlReader.parse(new InputSource(new SequenceReader(readers.iterator())));
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static void parseDocumentFragment(String fragment, XMLReceiver xmlReceiver) throws SAXException {
        if (fragment.indexOf("<") != -1 || fragment.indexOf("&") != -1) {
            try {
                final XMLReader xmlReader = newSAXParser().getXMLReader();
                xmlReader.setContentHandler(new XMLFragmentReceiver(xmlReceiver));
                xmlReader.parse(new InputSource(new StringReader("<root>" + fragment + "</root>")));
            } catch (IOException e) {
                throw new OXFException(e);
            }
        } else {
            // Optimization when fragment looks like text
            xmlReceiver.characters(fragment.toCharArray(), 0, fragment.length());
        }
    }

    private static class XMLFragmentReceiver extends ForwardingXMLReceiver {
        private int elementCount = 0;

        public XMLFragmentReceiver(XMLReceiver xmlReceiver) {
            super(xmlReceiver);
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

        public void startDocument() throws SAXException {}
        public void endDocument() throws SAXException {}
    }

//    /**
//     * Iterator over DOM Attributes.
//     *
//     * The object returned is of type XMLUtils.Attribute.
//     */
//    public static class AttributesIterator implements Iterator {
//
//        private Attributes attributes;
//        private int size;
//        private int currentIndex;
//
//        public AttributesIterator(Attributes attributes) {
//            this.attributes = attributes;
//            size = attributes.getLength();
//            currentIndex = 0;
//        }
//
//        public boolean hasNext() {
//            return currentIndex < size;
//        }
//
//        public Object next() {
//            if (!hasNext())
//                throw new NoSuchElementException();
//            final int _currentIndex = currentIndex++;
//            return new Attribute() {
//                public String getURI() {
//                    return attributes.getURI(_currentIndex);
//                }
//
//                public String getLocalName() {
//                    return attributes.getLocalName(_currentIndex);
//                }
//
//                public String getQName() {
//                    return attributes.getQName(_currentIndex);
//                }
//
//                public String getValue() {
//                    return attributes.getValue(_currentIndex);
//                }
//            };
//        }
//
//        public void remove() {
//            throw new UnsupportedOperationException();
//        }
//    }

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
            final OutputStream os = new ContentHandlerOutputStream(contentHandler);
            NetUtils.copyStream(new BufferedInputStream(is), os);
            os.close(); // necessary with ContentHandlerOutputStream to make sure all extra characters are written
        } catch (Exception e) {
            throw new OXFException(e);
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

    public static String unescapeXMLMinimal(String str) {
        str = StringUtils.replace(str, "&amp;", "&");
        str = StringUtils.replace(str, "&lt;", "<");
        return str;
    }

    public static String escapeXML(String str) {
        str = StringUtils.replace(str, "&", "&amp;");
        str = StringUtils.replace(str, "<", "&lt;");
        str = StringUtils.replace(str, ">", "&gt;");
        str = StringUtils.replace(str, "\"", "&quot;");
        str = StringUtils.replace(str, "'", "&apos;");
        return str;
    }

    public static String unescapeXML(String str) {
        str = StringUtils.replace(str, "&amp;", "&");
        str = StringUtils.replace(str, "&lt;", "<");
        str = StringUtils.replace(str, "&gt;", ">");
        str = StringUtils.replace(str, "&quot;", "\"");
        str = StringUtils.replace(str, "&apos;", "'");
        return str;
    }

    public static String escapeHTML(String str) {
        str = StringUtils.replace(str, "&", "&amp;");
        str = StringUtils.replace(str, "<", "&lt;");
        str = StringUtils.replace(str, ">", "&gt;");
        str = StringUtils.replace(str, "\"", "&quot;");
        str = StringUtils.replace(str, "'", "&#39;");
        return str;
    }

    public static org.dom4j.Document cleanXML(org.dom4j.Document doc, String stylesheetURL) {
      try {
        final org.dom4j.Element element = doc.getRootElement();
        final String systemId = Dom4jUtils.makeSystemId(element);
        // The date to clean
        final DOMGenerator dataToClean = new DOMGenerator(doc, "clean xml", DOMGenerator.ZeroValidity, systemId);
        // The stylesheet
        URLGenerator stylesheetGenerator = new URLGenerator(stylesheetURL);
        // The transformation
        // Define the name of the processor (this is a QName)
        final QName processorName = new QName("xslt", XMLConstants.OXF_PROCESSORS_NAMESPACE);
        // Get a factory for this processor
        final ProcessorFactory processorFactory = ProcessorFactoryRegistry.lookup(processorName);
        if (processorFactory == null)
          throw new OXFException("Cannot find processor factory with name '"
                                 + processorName.getNamespacePrefix() + ":" + processorName.getName() + "'");

        // Create processor
        final Processor xsltProcessor = processorFactory.createInstance();
        // Where the result goes
        DOMSerializer transformationOutput = new DOMSerializer();

        // Connect
        PipelineUtils.connect(stylesheetGenerator, "data", xsltProcessor, "config");
        PipelineUtils.connect(dataToClean, "data", xsltProcessor, "data");
        PipelineUtils.connect(xsltProcessor, "data", transformationOutput, "data");

        // Run the pipeline
        PipelineContext pipelineContext = new PipelineContext();
        transformationOutput.start(pipelineContext);
        // Get the output
        return transformationOutput.getDocument(pipelineContext);
      }
      catch(Exception e) {
        throw new OXFException(e);
      }
    }

    public static String toString(final Locator loc) {
        return loc.getSystemId() + ", line " + loc.getLineNumber() + ", column "
                + loc.getColumnNumber();
    }

    /**
     * @param attributes    source attributes
     * @return              new AttributesImpl containing  all attributes that were in src attributes and that were
     *                      in the default name space.
     */
    public static AttributesImpl getAttributesFromDefaultNamespace(final Attributes attributes) {
        final AttributesImpl ret = new AttributesImpl();
        final int size = attributes.getLength();
        for (int i = 0; i < size; i++) {
            final String ns = attributes.getURI(i);
            if (!"".equals(ns)) continue;
            final String lnam = attributes.getLocalName(i);
            final String qnam = attributes.getQName(i);
            final String typ = attributes.getType(i);
            final String val = attributes.getValue(i);
            ret.addAttribute(ns, lnam, qnam, typ, val);
        }
        return ret;
    }

    /**
     * Append classes to existing attributes. This creates a new AttributesImpl object.
     *
     * @param attributes    existing attributes
     * @param newClasses    new classes to append
     * @return              new attributes
     */
    public static AttributesImpl appendToClassAttribute(Attributes attributes, String newClasses) {
        final String oldClassAttribute = attributes.getValue("class");
        final String newClassAttribute = oldClassAttribute == null ? newClasses : oldClassAttribute + ' ' + newClasses;
        return addOrReplaceAttribute(attributes, "", "", "class", newClassAttribute);
    }

    /**
     * Append an attribute value to existing mutable attributes.
     *
     * @param attributes        existing attributes
     * @param attributeName     attribute name
     * @param attributeValue    value to set or append
     */
    public static void addOrAppendToAttribute(AttributesImpl attributes, String attributeName, String attributeValue) {
        final int oldAttributeIndex = attributes.getIndex(attributeName);

        if (oldAttributeIndex == -1) {
            // No existing class attribute

            // Add
            attributes.addAttribute("", attributeName, attributeName, ContentHandlerHelper.CDATA, attributeValue);
        } else {
            // Existing attribute
            final String oldAttributeValue = attributes.getValue(oldAttributeIndex);
            final String newAttributeValue = oldAttributeValue + ' ' + attributeValue;

            // Replace value
            attributes.setValue(oldAttributeIndex, newAttributeValue);
        }
    }

    public static AttributesImpl addOrReplaceAttribute(Attributes attributes, String uri, String prefix, String localname, String value) {
        final AttributesImpl newAttributes = new AttributesImpl();
        boolean replaced = false;
        for (int i = 0; i < attributes.getLength(); i++) {
            final String attributeURI = attributes.getURI(i);
            final String attributeValue = attributes.getValue(i);
            final String attributeType = attributes.getType(i);
            final String attributeQName = attributes.getQName(i);
            final String attributeLocalname = attributes.getLocalName(i);

            if (uri.equals(attributeURI) && localname.equals(attributeLocalname)) {
                // Found existing attribute
                replaced = true;
                newAttributes.addAttribute(uri, localname, XMLUtils.buildQName(prefix, localname), ContentHandlerHelper.CDATA, value);
            } else {
                // Not a matched attribute
                newAttributes.addAttribute(attributeURI, attributeLocalname, attributeQName, attributeType, attributeValue);
            }
        }
        if (!replaced) {
            // Attribute did not exist already so add it
            newAttributes.addAttribute(uri, localname, XMLUtils.buildQName(prefix, localname), ContentHandlerHelper.CDATA, value);
        }
        return newAttributes;
    }
    
    public static AttributesImpl removeAttribute(Attributes attributes, String uri, String localname) {
        final AttributesImpl newAttributes = new AttributesImpl();
        for (int i = 0; i < attributes.getLength(); i++) {
            final String attributeURI = attributes.getURI(i);
            final String attributeValue = attributes.getValue(i);
            final String attributeType = attributes.getType(i);
            final String attributeQName = attributes.getQName(i);
            final String attributeLocalname = attributes.getLocalName(i);

            if (!uri.equals(attributeURI) || !localname.equals(attributeLocalname)) {
                // Not a matched attribute
                newAttributes.addAttribute(attributeURI, attributeLocalname, attributeQName, attributeType, attributeValue);
            }
        }
        return newAttributes;
    }

    public static void streamNullDocument(ContentHandler contentHandler) throws SAXException {
        contentHandler.startDocument();
        contentHandler.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
        final AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute(XMLConstants.XSI_URI, "nil", "xsi:nil", "CDATA", "true");
        contentHandler.startElement("", "null", "null", attributes);
        contentHandler.endElement("", "null", "null");
        contentHandler.endPrefixMapping(XMLConstants.XSI_PREFIX);
        contentHandler.endDocument();
    }

    public static String saxElementToDebugString(String uri, String qName, Attributes attributes) {
        // Open start tag
        final StringBuilder sb = new StringBuilder("<");
        sb.append(qName);

        final Set<String> declaredPrefixes = new HashSet<String>();
        mapPrefixIfNeeded(declaredPrefixes, uri, qName, sb);

        // Attributes if any
        for (int i = 0; i < attributes.getLength(); i++) {
            mapPrefixIfNeeded(declaredPrefixes, attributes.getURI(i), attributes.getQName(i), sb);

            sb.append(' ');
            sb.append(attributes.getQName(i));
            sb.append("=\"");
            sb.append(attributes.getValue(i));
            sb.append('\"');
        }

        // Close start tag
        sb.append('>');

        // Content
        sb.append("[...]");

        // Close element with end tag
        sb.append("</");
        sb.append(qName);
        sb.append('>');

        return sb.toString();
    }

    private static void mapPrefixIfNeeded(Set<String> declaredPrefixes, String uri, String qName, StringBuilder sb) {
        final String prefix = XMLUtils.prefixFromQName(qName);
        if (prefix.length() > 0 && !declaredPrefixes.contains(prefix)) {
            sb.append(" xmlns:");
            sb.append(prefix);
            sb.append("=\"");
            sb.append(uri);
            sb.append("\"");

            declaredPrefixes.add(prefix);
        }
    }

    public interface DebugXML {
        void toXML(PropertyContext propertyContext, ContentHandlerHelper helper);
    }

    public static org.dom4j.Document createDebugRequestDocument(final PropertyContext propertyContext, final DebugXML debugXML) {
        return createDocument(propertyContext, new DebugXML() {
            public void toXML(PropertyContext propertyContext, ContentHandlerHelper helper) {
                wrapWithRequestElement(propertyContext, helper, debugXML);
            }
        });
    }

    public static org.dom4j.Document createDocument(PropertyContext propertyContext, DebugXML debugXML) {
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        final LocationDocumentResult result = new LocationDocumentResult();
        identity.setResult(result);

        final ContentHandlerHelper helper = new ContentHandlerHelper(identity);
        debugXML.toXML(propertyContext, helper);

        return result.getDocument();
    }

    public static void wrapWithRequestElement(PropertyContext propertyContext, ContentHandlerHelper helper, DebugXML debugXML) {
        helper.startDocument();

        final ExternalContext externalContext = (ExternalContext) propertyContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        final ExternalContext.Request request = (externalContext != null) ? externalContext.getRequest() : null;
        helper.startElement("request", new String[] { "request-uri", (request != null) ? request.getRequestURI() : null,
                "query-string", (request != null) ? request.getQueryString() : null,
                "method", (request != null) ? request.getMethod() : null
        });

        debugXML.toXML(propertyContext, helper);

        helper.endElement();

        helper.endDocument();
    }
}