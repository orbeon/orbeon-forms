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

import org.orbeon.apache.xerces.impl.Constants;
import org.orbeon.apache.xerces.impl.XMLEntityManager;
import org.orbeon.apache.xerces.impl.XMLErrorReporter;
import org.orbeon.apache.xerces.xni.parser.XMLInputSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.URIProcessorOutputImpl;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.SequenceReader;
import org.orbeon.oxf.util.StringUtils;
import org.orbeon.oxf.xml.dom.XmlLocationData;
import org.orbeon.oxf.xml.xerces.XercesSAXParserFactoryImpl;
import org.w3c.dom.Document;
import org.xml.sax.*;

import javax.xml.parsers.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class XMLParsing {

    private static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(XMLParsing.class);

    public static final EntityResolver ENTITY_RESOLVER = new EntityResolver();
    public static final ErrorHandler ERROR_HANDLER = new ErrorHandler();

    private static final ContentHandler NULL_CONTENT_HANDLER = new XMLReceiverAdapter();

    private static final DocumentBuilderFactory documentBuilderFactory;
    private static Map<Thread, DocumentBuilder> documentBuilders = null;

    private static Map<String, SAXParserFactory> parserFactories = new HashMap<String, SAXParserFactory>();

    public static class ParserConfiguration {
        public final boolean validating;
        public final boolean handleXInclude;
        public final boolean externalEntities;
        public final URIProcessorOutputImpl.URIReferences uriReferences;

        public ParserConfiguration(boolean validating, boolean handleXInclude, boolean externalEntities) {
            this(validating, handleXInclude, externalEntities, null);
        }

        public ParserConfiguration(boolean validating, boolean handleXInclude, boolean externalEntities, URIProcessorOutputImpl.URIReferences uriReferences) {
            this.validating = validating;
            this.handleXInclude = handleXInclude;
            this.externalEntities = externalEntities;
            this.uriReferences = uriReferences;
        }

        public ParserConfiguration(ParserConfiguration parserConfiguration, URIProcessorOutputImpl.URIReferences uriReferences) {
            this.validating = parserConfiguration.validating;
            this.handleXInclude = parserConfiguration.handleXInclude;
            this.externalEntities = parserConfiguration.externalEntities;
            this.uriReferences = uriReferences;
        }

        public String getKey() {
            return (validating ? "1" : "0") + (handleXInclude ? "1" : "0") + (externalEntities ? "1" : "0");
        }

        public static final ParserConfiguration PLAIN         = new ParserConfiguration(false, false, false);
        public static final ParserConfiguration XINCLUDE_ONLY = new ParserConfiguration(false, true,  false);
    }

    static {
        try {
            // Create factory
            documentBuilderFactory = (DocumentBuilderFactory) Class.forName
                    ("org.orbeon.apache.xerces.jaxp.DocumentBuilderFactoryImpl").newInstance();

            // Configure factory
            documentBuilderFactory.setNamespaceAware(true);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private XMLParsing() {}

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
    public static SAXParserFactory createSAXParserFactory(ParserConfiguration parserConfiguration) {
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
    public static synchronized SAXParserFactory getSAXParserFactory(ParserConfiguration parserConfiguration) {

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
    public static synchronized SAXParser newSAXParser(ParserConfiguration parserConfiguration) {
        try {
            return getSAXParserFactory(parserConfiguration).newSAXParser();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static XMLReader newXMLReader(ParserConfiguration parserConfiguration) {
        final SAXParser saxParser = XMLParsing.newSAXParser(parserConfiguration);
        try {
            final XMLReader xmlReader = saxParser.getXMLReader();
            xmlReader.setEntityResolver(XMLParsing.ENTITY_RESOLVER);
            xmlReader.setErrorHandler(XMLParsing.ERROR_HANDLER);
            return xmlReader;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Given an input stream, return a reader. This performs encoding detection as per the XML spec. Caller must close
     * the resulting Reader when done.
     *
     * @param inputStream   InputStream to process
     * @return              Reader initialized with the proper encoding
     * @throws IOException
     */
    public static Reader getReaderFromXMLInputStream(InputStream inputStream) throws IOException {
        // Create a Xerces XMLInputSource
        final XMLInputSource inputSource = new XMLInputSource(null, null, null, inputStream, null);
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

            // Would be nice to support XML Catalogs or similar here. See:
            // http://xerces.apache.org/xerces2-j/faq-xcatalogs.html
            if (url.getProtocol().equals("http")) {
                logger.warn("XML entity resolver for public id: " + publicId + " is accessing external entity via HTTP: " + url.toExternalForm());
            }

            is.setByteStream(url.openStream());
            return is;
        }
    }

    public static class ErrorHandler implements org.xml.sax.ErrorHandler {
        public void error(SAXParseException exception) throws SAXException {
            // NOTE: We used to throw here, but we probably shouldn't.
            logger.info("Error: " + exception);
        }

        public void fatalError(SAXParseException exception) throws SAXException {
            throw new ValidationException("Fatal error: " + exception.getMessage(), XmlLocationData.apply(exception));
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
     * @param urlString             URL of the document, or null
     * @param xmlReceiver           receiver to output to
     * @param parserConfiguration   parser configuration
     * @param handleLexical         whether the XML parser must output SAX LexicalHandler events, including comments
     */
    public static void stringToSAX(String xml, String urlString, XMLReceiver xmlReceiver, ParserConfiguration parserConfiguration, boolean handleLexical) {
        if (StringUtils.trimAllToEmpty(xml).equals("")) {
            try {
                xmlReceiver.startDocument();
                xmlReceiver.endDocument();
            } catch (SAXException e) {
                throw new OXFException(e);
            }
        } else {
            readerToSAX(new StringReader(xml), urlString, xmlReceiver, parserConfiguration, handleLexical);
        }
    }

    /**
     * Read a URL into SAX events.
     *
     * @param urlString             URL of the document
     * @param xmlReceiver           receiver to output to
     * @param parserConfiguration   parser configuration
     * @param handleLexical         whether the XML parser must output SAX LexicalHandler events, including comments
     */
    public static void urlToSAX(String urlString, XMLReceiver xmlReceiver, ParserConfiguration parserConfiguration, boolean handleLexical) {
        try {
            final URL url = URLFactory.createURL(urlString);
            final InputStream is = url.openStream();
            final InputSource inputSource = new InputSource(is);
            inputSource.setSystemId(urlString);
            try {
                inputSourceToSAX(inputSource, xmlReceiver, parserConfiguration, handleLexical);
            } finally {
                is.close();
            }
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static void inputStreamToSAX(InputStream inputStream, String urlString, XMLReceiver xmlReceiver, ParserConfiguration parserConfiguration, boolean handleLexical) {
        final InputSource inputSource = new InputSource(inputStream);
        inputSource.setSystemId(urlString);
        inputSourceToSAX(inputSource, xmlReceiver, parserConfiguration, handleLexical);
    }

    public static void readerToSAX(Reader reader, String urlString, XMLReceiver xmlReceiver, ParserConfiguration parserConfiguration, boolean handleLexical) {
        final InputSource inputSource = new InputSource(reader);
        inputSource.setSystemId(urlString);
        inputSourceToSAX(inputSource, xmlReceiver, parserConfiguration, handleLexical);
    }

    private static void inputSourceToSAX(InputSource inputSource, XMLReceiver xmlReceiver, ParserConfiguration parserConfiguration, boolean handleLexical) {

        // Insert XInclude processor if needed
        final TransformerURIResolver resolver;
        if (parserConfiguration.handleXInclude) {
            parserConfiguration =  new ParserConfiguration(parserConfiguration.validating, false, parserConfiguration.externalEntities, parserConfiguration.uriReferences);
            resolver = new TransformerURIResolver(ParserConfiguration.PLAIN);
            xmlReceiver = new XIncludeReceiver(null, xmlReceiver, parserConfiguration.uriReferences, resolver);
        } else {
            resolver = null;
        }

        try {
            final XMLReader xmlReader = newSAXParser(parserConfiguration).getXMLReader();
            xmlReader.setContentHandler(xmlReceiver);
            if (handleLexical)
                xmlReader.setProperty(XMLConstants.SAX_LEXICAL_HANDLER(), xmlReceiver);

            xmlReader.setEntityResolver(ENTITY_RESOLVER);
            xmlReader.setErrorHandler(ERROR_HANDLER);
            xmlReader.parse(inputSource);
        } catch (SAXParseException e) {
            throw new ValidationException(e.getMessage(), XmlLocationData.apply(e));
        } catch (Exception e) {
            throw new OXFException(e);
        } finally {
            if (resolver != null)
                resolver.destroy();
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
        if (StringUtils.trimAllToEmpty(xmlString).length() == 0)
            return false;

        try {
            final XMLReader xmlReader = newSAXParser(ParserConfiguration.PLAIN).getXMLReader();
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

    public static void parseDocumentFragment(Reader reader, XMLReceiver xmlReceiver) throws SAXException {
        try {
            final XMLReader xmlReader = newSAXParser(ParserConfiguration.PLAIN).getXMLReader();
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
        if (fragment.contains("<") || fragment.contains("&")) {
            try {
                final XMLReader xmlReader = newSAXParser(ParserConfiguration.PLAIN).getXMLReader();
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
}