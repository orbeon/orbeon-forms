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

import org.dom4j.Document;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.processor.xinclude.XIncludeProcessor;
import org.orbeon.oxf.util.StringBuilderWriter;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.oxf.xml.dom4j.LocationDocumentSource;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.tinytree.TinyBuilder;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
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

    public static final String DEFAULT_OUTPUT_ENCODING = "utf-8";

    public static final String SAXON_BUILTIN_TRANSFORMER_TYPE = "org.orbeon.saxon.TransformerFactoryImpl";
    public static final String IDENTITY_TYPE = SAXON_BUILTIN_TRANSFORMER_TYPE;
    public static final String DEFAULT_TYPE = SAXON_BUILTIN_TRANSFORMER_TYPE;

    /**
     * Property name to use for choosing the amount of indentation.
     */
    public static final String INDENT_AMOUNT_PROPERTY = "{http://orbeon.org/oxf/}indent-spaces";

    private static final String SAXON_INDENT_AMOUNT_PROPERTY = "{http://saxon.sf.net/}indent-spaces";

    // Class.forName is expensive, so we cache mappings
    private static Map<String, Class> classNameToHandlerClass = new HashMap<String, Class>();

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
    public static SAXTransformerFactory getFactory(String clazz, Map attributes) {
        try {
            final SAXTransformerFactory factory = (SAXTransformerFactory) getTransformerClass(clazz).newInstance();

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
     * @param standalone            wether a standalone declartion must be set and to what value
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
        final Transformer transformer = getFactory(IDENTITY_TYPE).newTransformer();
        // Wrap Transformer for properties
        return new TransformerWrapper(transformer, INDENT_AMOUNT_PROPERTY, SAXON_INDENT_AMOUNT_PROPERTY);
    }

    /**
     * Return a new identity TransformerHandler object.
     *
     * @return  a new identity TransformerHandler object
     */
    public static TransformerHandler getIdentityTransformerHandler() {
        try {
            TransformerHandler transformerHandler = getFactory(IDENTITY_TYPE).newTransformerHandler();
            // Wrap TransformerHandler for properties
            return new TransformerHandlerWrapper(transformerHandler, INDENT_AMOUNT_PROPERTY, SAXON_INDENT_AMOUNT_PROPERTY);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    public static TransformerHandler getTransformerHandler(Templates templates, String clazz, Map attributes) throws TransformerConfigurationException {
        return ((attributes != null) ? getFactory(clazz, attributes) : getFactory(clazz)).newTransformerHandler(templates);
    }

    public static TemplatesHandler getTemplatesHandler(String clazz) throws TransformerException {
        return getFactory(clazz).newTemplatesHandler();
    }

    public static Templates getTemplates(Source source, String clazz, Map attributes, ErrorListener errorListener, URIResolver uriResolver)
            throws TransformerConfigurationException {
        final SAXTransformerFactory factory = (attributes != null) ? getFactory(clazz, attributes) : getFactory(clazz);
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
        Transformer identity = getIdentityTransformer();
        LocationDocumentResult documentResult = new LocationDocumentResult();
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
        final TransformerHandler identity = getIdentityTransformerHandler();
        final LocationDocumentResult documentResult = new LocationDocumentResult();
        identity.setResult(documentResult);
        try {
            saxStore.replay(identity);
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
    public static DocumentInfo saxStoreToTinyTree(SAXStore saxStore) {
        final TinyBuilder treeBuilder = new TinyBuilder();
        try {
            final TransformerHandler identity = getIdentityTransformerHandler();
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
    public static DocumentInfo dom4jToTinyTree(Document document) {
        final TinyBuilder treeBuilder = new TinyBuilder();
        try {
            final Transformer identity = getIdentityTransformer();
            identity.transform(new LocationDocumentSource(document), treeBuilder);
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

    public static TransformerHandler testCreateTransformerHandlerWrapper(TransformerHandler transformerHandler, String publicProperty, String privateProperty) {
        return new TransformerHandlerWrapper(transformerHandler, publicProperty, privateProperty);
    }

    /**
     * Transform an InputStream to a dom4j Document.
     */
    public static Document readDom4j(InputStream inputStream, String systemId, boolean handleXInclude) {
        final LocationSAXContentHandler dom4jResult = new LocationSAXContentHandler();
        {
            final ContentHandler ch;
            if (handleXInclude) {
                // Insert XIncludeContentHandler
                ch = new XIncludeProcessor.XIncludeContentHandler(null, dom4jResult, null, new TransformerURIResolver(false));
            } else {
                ch = dom4jResult;
            }
            XMLUtils.inputStreamToSAX(inputStream, systemId, ch, false, false);
        }
        return dom4jResult.getDocument();

    }

    /**
     * Transform a SAX Source to a dom4j Document.
     */
//    private static Document readDom4j(Source source, boolean handleXInclude) {
//        final LocationSAXContentHandler dom4jResult = new LocationSAXContentHandler();
//        try {
//            final Transformer identity = getIdentityTransformer();
//            if (handleXInclude) {
//                // Insert XIncludeContentHandler
//                identity.transform(source, new SAXResult(new XIncludeProcessor.XIncludeContentHandler(null, dom4jResult, null, new TransformerURIResolver(false))));
//            } else {
//                identity.transform(source, new SAXResult(dom4jResult));
//            }
//        } catch (TransformerException e) {
//            throw new OXFException(e);
//        }
//        return dom4jResult.getDocument();
//    }

    /**
     * Transform an InputStream to a TinyTree.
     */
    public static DocumentInfo readTinyTree(InputStream inputStream, String systemId, boolean handleXInclude) {
        final TinyBuilder treeBuilder = new TinyBuilder();
        {
            final TransformerHandler identityHandler = getIdentityTransformerHandler();
            identityHandler.setResult(treeBuilder);
            final ContentHandler ch;
            if (handleXInclude) {
                // Insert XIncludeContentHandler
                ch = new XIncludeProcessor.XIncludeContentHandler(null, identityHandler, null, new TransformerURIResolver(false));
            } else {
                ch = identityHandler;
            }
            XMLUtils.inputStreamToSAX(inputStream, systemId, ch, false, false);
        }
        return (DocumentInfo) treeBuilder.getCurrentRoot();
    }

    /**
     * Transform a SAX Source to a TinyTree.
     */
    public static DocumentInfo readTinyTree(Source source, boolean handleXInclude) {
        final TinyBuilder treeBuilder = new TinyBuilder();
        try {
            final Transformer identity = getIdentityTransformer();
            if (handleXInclude) {
                // Insert XIncludeContentHandler
                final TransformerHandler identityHandler = getIdentityTransformerHandler();
                identityHandler.setResult(treeBuilder);
                identity.transform(source, new SAXResult(new XIncludeProcessor.XIncludeContentHandler(null, identityHandler, null, new TransformerURIResolver(false))));
            } else {
                identity.transform(source, treeBuilder);
            }

        } catch (TransformerException e) {
            throw new OXFException(e);
        }
        return (DocumentInfo) treeBuilder.getCurrentRoot();
    }

    /**
     * Transform a TinyTree to a dom4j document.
     */
//    public static Document tinyTreeToDom4j(NodeInfo nodeInfo) {
//        try {
//            final Transformer identity = getIdentityTransformer();
//            final LocationDocumentResult documentResult = new LocationDocumentResult();
//            identity.transform(nodeInfo, documentResult);
//            return documentResult.getDocument();
//        } catch (TransformerException e) {
//            throw new OXFException(e);
//        }
//    }

    /**
     * Transform a TinyTree to a dom4j document.
     *
     * This version uses a temporary string as we are having issues with converting directly from TinyTree to dom4j.
     */
    public static Document tinyTreeToDom4j2(NodeInfo nodeInfo) {
        try {
            final String xmlString = tinyTreeToString(nodeInfo);
            return Dom4jUtils.readDom4j(xmlString, false, false);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Transforms a W3C DOM into a new W3C DOM. Useful when the initial DOM is immutable.
     *
     */
    public static org.w3c.dom.Document domDocument2DomDocument(org.w3c.dom.Document document) throws TransformerException {
        final Transformer identity = getIdentityTransformer();
        final DOMResult domResult = new DOMResult();
        identity.transform(new DOMSource(document), domResult);
        final Node resultNode = domResult.getNode();
        return (resultNode instanceof org.w3c.dom.Document) ? ((org.w3c.dom.Document) resultNode) : resultNode.getOwnerDocument();
    }

    /**
     * Transform a String to a TinyTree.
     */
    public static DocumentInfo stringToTinyTree(String string, boolean handleXInclude) {
        try {
            return readTinyTree(new ByteArrayInputStream(string.getBytes("utf-8")), null, handleXInclude);
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);// should not happen
        }
    }

    /**
     * Transform a TinyTree to SAX events.
     */
    public static void writeTinyTree(NodeInfo nodeInfo, ContentHandler contentHandler) {
        try {
            final Transformer identity = getIdentityTransformer();
            identity.transform(nodeInfo, new SAXResult(contentHandler));
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Transform a dom4j Node to SAX events.
     */
    public static void writeDom4j(org.dom4j.Node node, ContentHandler contentHandler) {
        try {
            final Transformer identity = getIdentityTransformer();
            identity.transform(new LocationDocumentSource(node), new SAXResult(contentHandler));
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Transform a dom4j document to a String.
     */
    public static String dom4jToString(Document document) {
        try {
            final Transformer identity = getXMLIdentityTransformer();
            final StringBuilderWriter writer = new StringBuilderWriter();
            identity.transform(new LocationDocumentSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Transform a TinyTree to a String.
     */
    public static String tinyTreeToString(NodeInfo nodeInfo) {
        try {
            final Transformer identity = getXMLIdentityTransformer();
            final StringBuilderWriter writer = new StringBuilderWriter();
            identity.transform(nodeInfo, new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Transform a TinyTree to a SAXStore.
     */
    public static SAXStore tinyTreeToSAXStore(NodeInfo nodeInfo) {
        try {
            final Transformer identity = getXMLIdentityTransformer();
            final SAXStore result = new SAXStore();
            identity.transform(nodeInfo, new SAXResult(result));
            return result;
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Transform a dom4j document to a SAXStore.
     */
    public static SAXStore dom4jToSAXStore(Document document) {
        try {
            final Transformer identity = getXMLIdentityTransformer();
            final SAXStore result = new SAXStore();
            identity.transform(new LocationDocumentSource(document), new SAXResult(result));
            return result;
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

class TransformerHandlerWrapper extends ForwardingContentHandler implements TransformerHandler {

    private TransformerHandler transformerHandler;
    private String publicProperty;
    private String privateProperty;

    public TransformerHandlerWrapper(TransformerHandler transformerHandler, String publicProperty, String privateProperty) {
        super(transformerHandler);
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