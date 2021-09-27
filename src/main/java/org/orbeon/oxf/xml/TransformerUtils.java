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

import org.orbeon.dom.Document;
import org.orbeon.dom.io.DocumentSource;
import org.orbeon.dom.io.XMLWriter;
import org.orbeon.dom.saxon.DocumentWrapper;
import org.orbeon.dom.Node.NodeOps;
import org.orbeon.io.CharsetNames;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.io.StringBuilderWriter;
import org.orbeon.oxf.util.XPath;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.oxf.xml.dom4j.LocationDocumentSource;
import org.orbeon.oxf.xml.dom.LocationSAXContentHandler;
import org.orbeon.oxf.xml.dom.LocationSAXWriter;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.TransformerFactoryImpl;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.tinytree.TinyBuilder;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import scala.Tuple2;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * Utility class for XSLT and other transformations.
 */
public class TransformerUtils {

    public static final String DEFAULT_OUTPUT_ENCODING = CharsetNames.Utf8();

    /**
     * Property name to use for choosing the amount of indentation.
     */
    public static final String INDENT_AMOUNT_PROPERTY = "{http://orbeon.org/oxf/}indent-spaces";

    private static final String SAXON_INDENT_AMOUNT_PROPERTY = "{http://saxon.sf.net/}indent-spaces";

    // Class.forName is expensive, so we cache mappings
    private static Map<String, Class> classNameToHandlerClass = new HashMap<>();

    private static Class getTransformerClass(String clazz) throws ClassNotFoundException {
        Class transformerClass = classNameToHandlerClass.get(clazz);
        if (transformerClass == null) {
            transformerClass = Class.forName(clazz);
            classNameToHandlerClass.put(clazz, transformerClass);
        }
        return transformerClass;
    }

    /*
     * NOTE: Factories are not thread-safe. So we should not store them into transformerFactories
     * for now, or we should make sure there is one factory per thread, or that we synchronize.
     */
    public static SAXTransformerFactory getFactory(String className, Map attributes, Configuration configuration) {
        try {
            final SAXTransformerFactory factory; {
                if (className.equals(TransformerFactoryImpl.class.getName())) {
                    factory = new TransformerFactoryImpl(configuration);
                } else {
                    factory = (SAXTransformerFactory) getTransformerClass(className).newInstance();
                }
            }

            for (Iterator i = attributes.keySet().iterator(); i.hasNext();) {
                final String key = (String) i.next();
                factory.setAttribute(key, attributes.get(key));
            }

            return factory;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /*
     * NOTE: Factories are not thread-safe. So we should not store them into transformerFactories
     * for now, or we should make sure there is one factory per thread, or that we synchronize.
     */
    public static SAXTransformerFactory getFactory(String clazz) {
        try {
            return (SAXTransformerFactory) getTransformerClass(clazz).newInstance();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static Transformer getXMLIdentityTransformer() {
        try {
            Transformer transformer = getIdentityTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, DEFAULT_OUTPUT_ENCODING);
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(INDENT_AMOUNT_PROPERTY, "0");
            return transformer;
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Apply output properties on a Transformer.
     *
     * @param transformer           transformer to apply properties on
     * @param method                output method
     * @param version               HTML or XML version
     * @param publicDoctype         public doctype
     * @param systemDoctype         system doctype
     * @param encoding              character encoding
     * @param omitXMLDeclaration    whether XML declaration must be omitted
     * @param standalone            whether a standalone declaration must be set and to what value
     * @param indent                whether the HTML or XML must be indented
     * @param indentAmount          amount of indenting for the markup
     */
    public static void applyOutputProperties(Transformer transformer,
                                             String method,
                                             String version,
                                             String publicDoctype,
                                             String systemDoctype,
                                             String encoding,
                                             boolean omitXMLDeclaration,
                                             Boolean standalone,
                                             boolean indent,
                                             int indentAmount) {
        if (method != null && !"".equals(method))
            transformer.setOutputProperty(OutputKeys.METHOD, method);
        if (version != null && !"".equals(version))
            transformer.setOutputProperty(OutputKeys.VERSION, version);
        if (publicDoctype != null && !"".equals(publicDoctype))
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, publicDoctype);
        if (systemDoctype != null && !"".equals(systemDoctype))
            transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemDoctype);
        if (encoding != null && !"".equals(encoding))
            transformer.setOutputProperty(OutputKeys.ENCODING, encoding);
        transformer.setOutputProperty(OutputKeys.INDENT, indent ? "yes" : "no");
        if (indent)
            transformer.setOutputProperty(INDENT_AMOUNT_PROPERTY, String.valueOf(indentAmount));
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, omitXMLDeclaration ? "yes" : "no");
        if (standalone != null)
            transformer.setOutputProperty(OutputKeys.STANDALONE, standalone ? "yes" : "no");
    }

    /**
     * Return a new identity transformer object.
     *
     * @return  a new identity Transformer object
     * @throws TransformerConfigurationException
     */
    public static Transformer getIdentityTransformer() throws TransformerConfigurationException {
        return getIdentityTransformer(XPath.GlobalConfiguration());
    }

    public static Transformer getIdentityTransformer(Configuration configuration) throws TransformerConfigurationException {
        // See https://github.com/orbeon/orbeon-forms/issues/2577
        final Transformer transformer = new IdentityTransformerWithFixup(configuration);
        return new TransformerWrapper(transformer, INDENT_AMOUNT_PROPERTY, SAXON_INDENT_AMOUNT_PROPERTY);
    }

    /**
     * Return a new identity TransformerHandler object.
     *
     * @return  a new identity TransformerHandler object
     */
    public static TransformerXMLReceiver getIdentityTransformerHandler() {
        return getIdentityTransformerHandler(XPath.GlobalConfiguration());
    }

    public static TransformerXMLReceiver getIdentityTransformerHandler(Configuration configuration) {
        try {
            TransformerHandler transformerHandler = new TransformerFactoryImpl(configuration).newTransformerHandler();
            // Wrap TransformerHandler for properties
            return new TransformerHandlerWrapper(transformerHandler, INDENT_AMOUNT_PROPERTY, SAXON_INDENT_AMOUNT_PROPERTY);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    public static TransformerHandler getTransformerHandler(Templates templates, String clazz, Map attributes, Configuration configuration) throws TransformerConfigurationException {
        return ((attributes != null) ? getFactory(clazz, attributes, configuration) : getFactory(clazz)).newTransformerHandler(templates);
    }

    public static Templates getTemplates(Source source, String clazz, Map attributes, Configuration configuration, ErrorListener errorListener, URIResolver uriResolver)
            throws TransformerConfigurationException {
        final SAXTransformerFactory factory = (attributes != null) ? getFactory(clazz, attributes, configuration) : getFactory(clazz);
        factory.setErrorListener(errorListener);
        factory.setURIResolver(uriResolver);

        final Templates templates = factory.newTemplates(source);
        // These should only be used during stylesheet compilation. It is dangerous to keep them around when the
        // Templates object is cached especially the URI Resolver which may reference PipelineContext objects.
//        factory.setErrorListener(null);// This causes issues with Xalan
        factory.setURIResolver(null);

        return templates;
    }

    /**
     * Transform a W3C DOM node into a dom4j document
     *
     * @param   node    W3C DOM node
     * @return  dom4j document
     * @throws TransformerException
     */
    public static Document domToDom4jDocument(org.w3c.dom.Node node) throws TransformerException {
        final Transformer identity = getIdentityTransformer();
        final LocationDocumentResult documentResult = new LocationDocumentResult();
        identity.transform(new DOMSource(node), documentResult);
        return documentResult.getDocument();
    }

    /**
     * Transform a SAXStore into a dom4j document
     *
     * @param   saxStore input SAXStore
     * @return  dom4j document
     */
    public static Document saxStoreToDom4jDocument(SAXStore saxStore) {
        final TransformerXMLReceiver identity = getIdentityTransformerHandler();
        final LocationDocumentResult documentResult = new LocationDocumentResult();
        identity.setResult(documentResult);
        try {
            saxStore.replay(identity);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
        return documentResult.getDocument();
    }

    // Transform a SAXStore mark into a dom4j document
    public static Document saxStoreMarkToDom4jDocument(SAXStore.Mark mark) {
        final TransformerXMLReceiver identity = getIdentityTransformerHandler();
        final LocationDocumentResult documentResult = new LocationDocumentResult();
        identity.setResult(documentResult);
        try {
            identity.startDocument();
            mark.replay(identity);
            identity.endDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
        return documentResult.getDocument();
    }

    /**
     * Transform a SAXStore into a TinyTree document
     *
     * @param   saxStore input SAXStore
     * @return  DocumentInfo
     */
    public static DocumentInfo saxStoreToTinyTree(Configuration configuration, SAXStore saxStore) {
        final TinyBuilder treeBuilder = new TinyBuilder();
        try {
            final TransformerXMLReceiver identity = getIdentityTransformerHandler(configuration);
            identity.setResult(treeBuilder);
            saxStore.replay(identity);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
        return (DocumentInfo) treeBuilder.getCurrentRoot();
    }

    /**
     * Transform a SAXStore into a DOM document
     *
     * @param   saxStore input SAXStore
     * @return  DOM document
     */
    public static org.w3c.dom.Document saxStoreToDomDocument(SAXStore saxStore) {
        try {
            // Convert to dom4j and then to DOM
            return TransformerUtils.dom4jToDomDocument(saxStoreToDom4jDocument(saxStore));
        } catch (TransformerException e) {
            throw new OXFException(e);
        }

        // NOTE: The more straight and efficient implementation below doesn't seem to work
//        final TransformerHandler identity = getIdentityTransformerHandler();
//        final DOMResult domResult = new DOMResult();
//        identity.setResult(domResult);
//        try {
//            saxStore.replay(identity);
//        } catch (SAXException e) {
//            throw new OXFException(e);
//        }
//        return domResult.getNode().getOwnerDocument();
    }

    /**
     * Transform a dom4j Document into a TinyTree.
     */
    public static DocumentInfo dom4jToTinyTree(Configuration configuration, Document document, boolean location) {
        final TinyBuilder treeBuilder = new TinyBuilder();
        try {
            final Transformer identity = getIdentityTransformer(configuration);
            identity.transform(location ? new LocationDocumentSource(document) : new DocumentSource(document), treeBuilder);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
        return (DocumentInfo) treeBuilder.getCurrentRoot();
    }

    /**
     * Transform a dom4j document into a W3C DOM document
     *
     * @param   document dom4j document
     * @return  W3C DOM document
     * @throws TransformerException
     */
    public static org.w3c.dom.Document dom4jToDomDocument(Document document) throws TransformerException {
        final Transformer identity = getIdentityTransformer();
        final DOMResult domResult = new DOMResult();
        identity.transform(new DocumentSource(document), domResult);
        final Node resultNode = domResult.getNode();
        return (resultNode instanceof org.w3c.dom.Document) ? ((org.w3c.dom.Document) resultNode) : resultNode.getOwnerDocument();
    }

    public static Transformer testCreateTransformerWrapper(Transformer transformer, String publicProperty, String privateProperty) {
        return new TransformerWrapper(transformer, publicProperty, privateProperty);
    }

    /**
     * Transform an InputStream to a dom4j Document.
     */
    public static Document readDom4j(InputStream inputStream, String systemId, boolean handleXInclude, boolean handleLexical) {
        final LocationSAXContentHandler dom4jResult = new LocationSAXContentHandler();
        {
            final TransformerURIResolver resolver;
            final XMLReceiver xmlReceiver;
            if (handleXInclude) {
                // Insert XIncludeContentHandler
                resolver = new TransformerURIResolver(XMLParsing.ParserConfiguration.PLAIN);
                xmlReceiver = new XIncludeReceiver(null, dom4jResult, null, resolver);
            } else {
                resolver = null;
                xmlReceiver = dom4jResult;
            }
            try {
                XMLParsing.inputStreamToSAX(inputStream, systemId, xmlReceiver, XMLParsing.ParserConfiguration.PLAIN, handleLexical);
            } finally {
                if (resolver != null)
                    resolver.destroy();
            }
        }
        return dom4jResult.getDocument();

    }

    /**
     * Transform an InputStream to a dom4j Document.
     */
    public static Document readDom4j(Source source, boolean handleXInclude) {
        final LocationSAXContentHandler dom4jResult = new LocationSAXContentHandler();
        {
            final TransformerURIResolver resolver;
            final XMLReceiver xmlReceiver;
            if (handleXInclude) {
                // Insert XIncludeContentHandler
                resolver = new TransformerURIResolver(XMLParsing.ParserConfiguration.PLAIN);
                xmlReceiver = new XIncludeReceiver(null, dom4jResult, null, resolver);
            } else {
                resolver = null;
                xmlReceiver = dom4jResult;
            }
            try {
                sourceToSAX(source, xmlReceiver);
            } finally {
                if (resolver != null)
                    resolver.destroy();
            }
        }
        return dom4jResult.getDocument();

    }
    public static Tuple2<TinyBuilder, XMLReceiver> createTinyBuilder(Configuration configuration) {
        final TinyBuilder treeBuilder = new TinyBuilder();
        final TransformerXMLReceiver identityHandler = getIdentityTransformerHandler(configuration);
        identityHandler.setResult(treeBuilder);
        return new Tuple2<TinyBuilder, XMLReceiver>(treeBuilder, identityHandler);
    }

    /**
     * Transform an InputStream to a TinyTree.
     */
    public static DocumentInfo readTinyTree(Configuration configuration, InputStream inputStream, String systemId, boolean handleXInclude, boolean handleLexical) {
        final TinyBuilder treeBuilder = new TinyBuilder();
        {
            final TransformerXMLReceiver identityHandler = getIdentityTransformerHandler(configuration);
            identityHandler.setResult(treeBuilder);
            final TransformerURIResolver resolver;
            final XMLReceiver xmlReceiver;
            if (handleXInclude) {
                // Insert XIncludeContentHandler
                resolver = new TransformerURIResolver(XMLParsing.ParserConfiguration.PLAIN);
                xmlReceiver = new XIncludeReceiver(null, identityHandler, null, resolver);
            } else {
                resolver = null;
                xmlReceiver = identityHandler;
            }
            try {
                XMLParsing.inputStreamToSAX(inputStream, systemId, xmlReceiver, XMLParsing.ParserConfiguration.PLAIN, handleLexical);
            } finally {
                if (resolver != null)
                    resolver.destroy();
            }
        }
        return (DocumentInfo) treeBuilder.getCurrentRoot();
    }

    /**
     * Transform a SAX Source to a TinyTree.
     */
    public static DocumentInfo readTinyTree(Configuration configuration, Source source, boolean handleXInclude) {
        final TinyBuilder treeBuilder = new TinyBuilder();
        try {
            final TransformerURIResolver resolver;
            if (handleXInclude) {
                resolver = new TransformerURIResolver(XMLParsing.ParserConfiguration.PLAIN);
            } else {
                resolver = null;
            }
            try {
                if (handleXInclude) {
                    // Insert XIncludeContentHandler
                    final TransformerXMLReceiver identityHandler = getIdentityTransformerHandler(configuration);
                    identityHandler.setResult(treeBuilder);
                    final XMLReceiver receiver = new XIncludeReceiver(null, identityHandler, null, resolver);
                    TransformerUtils.sourceToSAX(source, receiver);
                } else {
                    final Transformer identity = getIdentityTransformer(configuration);
                    identity.transform(source, treeBuilder);
                }
            } finally {
                if (resolver != null)
                    resolver.destroy();
            }
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
        return (DocumentInfo) treeBuilder.getCurrentRoot();
    }

    /**
     * Transform a TinyTree to a dom4j document.
     */
    public static Document tinyTreeToDom4j(NodeInfo nodeInfo) {
        try {
            final Transformer identity = getXMLIdentityTransformer();
            final LocationDocumentResult documentResult = new LocationDocumentResult();

            identity.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            identity.transform(nodeInfo, documentResult);

            return documentResult.getDocument();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static DocumentWrapper extractAsMutableDocument(NodeInfo elementOrDocument) {
        return new DocumentWrapper(tinyTreeToDom4j(elementOrDocument), null, XPath.GlobalConfiguration());
    }

    /**
     * Transform a String to a TinyTree.
     */
    public static DocumentInfo stringToTinyTree(Configuration configuration, String string, boolean handleXInclude, boolean handleLexical) {
        try {
            return readTinyTree(configuration, new ByteArrayInputStream(string.getBytes(CharsetNames.Utf8())), null, handleXInclude, handleLexical);
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);// should not happen
        }
    }

    public static DocumentInfo urlToTinyTree(String url) {
        try {
            final InputStream inputStream = URLFactory.createURL(url).openStream();
            try {
                return readTinyTree(XPath.GlobalConfiguration(), inputStream, null, true, true);
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Transform a TinyTree to SAX events.
     */
    public static void writeTinyTree(NodeInfo nodeInfo, XMLReceiver xmlReceiver) {
        sourceToSAX(nodeInfo, xmlReceiver);
    }

    /**
     * Transform a SAX source to SAX events.
     */
    public static void sourceToSAX(Source source, XMLReceiver xmlReceiver) {
        try {
            final Transformer identity = getIdentityTransformer();
            final SAXResult saxResult = new SAXResult(xmlReceiver);
            saxResult.setLexicalHandler(xmlReceiver);
            identity.transform(source, saxResult);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Transform a SAX source to SAX events.
     */
    private static void sourceToSAX(Source source, ContentHandler contentHandler) {
        try {
            final Transformer identity = getIdentityTransformer();
            final SAXResult saxResult = new SAXResult(contentHandler);
            identity.transform(source, saxResult);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Transform a dom4j Node to SAX events.
     */
    public static void writeDom4j(org.orbeon.dom.Document document, XMLReceiver xmlReceiver) {
        // NOTE: Use dom4j directly instead of sourceToSAX as performance is better
        final LocationSAXWriter locationSAXWriter = new LocationSAXWriter();
        locationSAXWriter.setContentHandler(xmlReceiver);
        locationSAXWriter.setLexicalHandler(xmlReceiver);
        locationSAXWriter.write(document);
    }

    /**
     * Transform a dom4j Node to SAX events.
     */
    public static void writeDom4j(org.orbeon.dom.Node node, ContentHandler contentHandler) {
        sourceToSAX(new LocationDocumentSource(node), contentHandler);
    }

    /**
     * Transform a TinyTree to a String.
     */
    public static String tinyTreeToString(NodeInfo nodeInfo) {
        try {
            final Transformer identity = getXMLIdentityTransformer();
            identity.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            final StringBuilderWriter writer = new StringBuilderWriter(new StringBuilder());
            identity.transform(nodeInfo, new StreamResult(writer));
            return writer.result();
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Transform a TinyTree to a SAXStore.
     */
    public static SAXStore tinyTreeToSAXStore(NodeInfo nodeInfo) {
        final SAXStore saxStore = new SAXStore();
        sourceToSAX(nodeInfo, saxStore);
        return saxStore;
    }

    /**
     * Transform a dom4j document to a SAXStore.
     */
    public static SAXStore dom4jToSAXStore(Document document, boolean location) {
        final SAXStore saxStore = new SAXStore();
        sourceToSAX(location ? new LocationDocumentSource(document) : new DocumentSource(document), saxStore);
        return saxStore;
    }

    public static String domToString(Node node) {
        try {
            Transformer transformer = getXMLIdentityTransformer();
            DOMSource source = new DOMSource(node);

            StringBuilderWriter writer = new StringBuilderWriter(new StringBuilder());
            transformer.transform(source, new StreamResult(writer));
            return writer.result();
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }
}

class TransformerWrapper extends Transformer {

    private Transformer transformer;
    private String publicProperty;
    private String privateProperty;

    public TransformerWrapper(Transformer transformer, String publicProperty, String privateProperty) {
        this.transformer = transformer;
        this.publicProperty = publicProperty;
        this.privateProperty = privateProperty;
    }

    public void clearParameters() {
        transformer.clearParameters();
    }

    public ErrorListener getErrorListener() {
        return transformer.getErrorListener();
    }

    public Properties getOutputProperties() {
        Properties properties = transformer.getOutputProperties();

        if (properties.get(privateProperty) == null) {
            // Optimize case where we don't need to map
            return properties;
        } else {
            // Switch property
            properties.put(publicProperty, properties.get(privateProperty));
            properties.remove(privateProperty);

            return properties;
        }
    }

    public String getOutputProperty(String name) throws IllegalArgumentException {
        if (publicProperty.equals(name))
            return transformer.getOutputProperty(privateProperty);
        else
            return transformer.getOutputProperty(name);
    }

    public Object getParameter(String name) {
        return transformer.getParameter(name);
    }

    public URIResolver getURIResolver() {
        return transformer.getURIResolver();
    }

    public void setErrorListener(ErrorListener listener) throws IllegalArgumentException {
        transformer.setErrorListener(listener);
    }

    public void setOutputProperties(Properties oformat) throws IllegalArgumentException {

        if (oformat.get(publicProperty) == null) {
            // Optimize case where we don't need to map
            transformer.setOutputProperties(oformat);
        } else {
            Properties newProperties = (Properties) oformat.clone();

            newProperties.put(privateProperty, oformat.get(publicProperty));
            newProperties.remove(publicProperty);

            transformer.setOutputProperties(newProperties);
        }
    }

    public void setOutputProperty(String name, String value) throws IllegalArgumentException {
        if (publicProperty.equals(name))
            transformer.setOutputProperty(privateProperty, value);
        else
            transformer.setOutputProperty(name, value);
    }

    public void setParameter(String name, Object value) {
        transformer.setParameter(name, value);
    }

    public void setURIResolver(URIResolver resolver) {
        transformer.setURIResolver(resolver);
    }

    public void transform(Source xmlSource, Result outputTarget) throws TransformerException {
        transformer.transform(xmlSource, outputTarget);
    }
}

class TransformerHandlerWrapper extends ForwardingXMLReceiver implements TransformerXMLReceiver {

    private TransformerHandler transformerHandler;
    private String publicProperty;
    private String privateProperty;

    public TransformerHandlerWrapper(TransformerHandler transformerHandler, String publicProperty, String privateProperty) {
        super(transformerHandler, transformerHandler);
        this.transformerHandler = transformerHandler;
        this.publicProperty = publicProperty;
        this.privateProperty = privateProperty;
    }

    public String getSystemId() {
        return transformerHandler.getSystemId();
    }

    public Transformer getTransformer() {
        return new TransformerWrapper(transformerHandler.getTransformer(), publicProperty, privateProperty);
    }

    public void setResult(Result result) throws IllegalArgumentException {
        transformerHandler.setResult(result);
    }

    public void setSystemId(String systemID) {
        transformerHandler.setSystemId(systemID);
    }

    public void comment(char ch[], int start, int length) throws SAXException {
        transformerHandler.comment(ch, start, length);
    }

    public void endCDATA() throws SAXException {
        transformerHandler.endCDATA();
    }

    public void endDTD() throws SAXException {
        transformerHandler.endDTD();
    }

    public void endEntity(String name) throws SAXException {
        transformerHandler.endEntity(name);
    }

    public void startCDATA() throws SAXException {
        transformerHandler.startCDATA();
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        transformerHandler.startDTD(name, publicId, systemId);
    }

    public void startEntity(String name) throws SAXException {
        transformerHandler.startEntity(name);
    }

    public void notationDecl(String name, String publicId, String systemId) throws SAXException {
        transformerHandler.notationDecl(name, publicId, systemId);
    }

    public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) throws SAXException {
        transformerHandler.unparsedEntityDecl(name, publicId, systemId, notationName);
    }
}