/**
 *  Copyright (C) 2004-2005 Orbeon, Inc.
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

import orbeon.apache.xalan.processor.StylesheetHandler;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.ProcessorInput;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Utility class for XSLT and identity transformations.
 */
public class TransformerUtils {

    public static final String DEFAULT_OUTPUT_ENCODING = "utf-8";

    public static final String XALAN_BUILTIN_TRANSFORMER_TYPE = "orbeon.apache.xalan.processor.TransformerFactoryImpl";
    public static final String SAXON_BUILTIN_TRANSFORMER_TYPE = "org.orbeon.saxon.TransformerFactoryImpl";
    public static final String IDENTITY_TYPE = SAXON_BUILTIN_TRANSFORMER_TYPE;
    public static final String DEFAULT_TYPE = SAXON_BUILTIN_TRANSFORMER_TYPE;

    /**
     * Property name to use for choosing the amount of indentation.
     */
    public static final String INDENT_AMOUNT_PROPERTY = "{http://orbeon.org/oxf/}indent-spaces";

    private static final String SAXON_INDENT_AMOUNT_PROPERTY = "{http://saxon.sf.net/}indent-spaces";

    private static Map transformerFactories = new HashMap();

//    private static void createCompilerFactory() {
//        new Runnable() {
//            public void run() {
//                compilerFactory = new orbeon.apache.xalan.xsltc.trax.TransformerFactoryImpl();
//                compilerFactory.setURIResolver(uriResolver);
//            }
//        }.run();
//    }

//    static {
//        // This is needed for tools like Joost who look at this property
//        Properties properties = System.getProperties();
//        properties.put("org.xml.sax.driver", "orbeon.apache.xerces.parsers.SAXParser");
//    }

    public static SAXTransformerFactory getFactory(String type) {
        try {
            if (XALAN_BUILTIN_TRANSFORMER_TYPE.equals(type)) {
                // Special case for Xalan
                if (transformerFactories.get(XALAN_BUILTIN_TRANSFORMER_TYPE) == null) {

                    // HACK
                    //
                    // When we create a Templates (object representing
                    // the stylesheet) based on an JAXP Source, we call the
                    // TransformerFactoryImpl.newTemplates(Source source) method
                    // of Xalan.
                    //
                    // This method has a "bug": if the sytemId is not set on the
                    // Source (and it's not in the case of a SAXSource since the
                    // SAXSource is based on am XMLReader and the XMLReader will
                    // set the Locator on which is based the systemId when the
                    // parse() method is called), the
                    // TransformerFactoryImpl.newTemplates(Source) method will
                    // set the systemId on the StylesheetHandler to the
                    // home directory.
                    //
                    // We prevent this to overriding the
                    // StylesheetHandler.setSystemId below.

                    SAXTransformerFactory factory = new orbeon.apache.xalan.processor.TransformerFactoryImpl() {
                        public TemplatesHandler newTemplatesHandler()
                                throws TransformerConfigurationException {
                            return new StylesheetHandler(this) {
                                public void setSystemId(String baseID) {
                                    // Do nothing.
                                }
                            };
                        }
                    };
                    factory.setAttribute("http://xml.apache.org/xalan/features/incremental", Boolean.FALSE);
                    transformerFactories.put(XALAN_BUILTIN_TRANSFORMER_TYPE, factory);
                }
                return (SAXTransformerFactory) transformerFactories.get(XALAN_BUILTIN_TRANSFORMER_TYPE);
            } else {
                // Any other factory
                if (transformerFactories.get(type) == null) {
                    SAXTransformerFactory factory = (SAXTransformerFactory) Class.forName(type).newInstance();
                    return factory;
                }
                return (SAXTransformerFactory) transformerFactories.get(type);
            }

//                    compilerFactory.setErrorListener(new ErrorListener() {
//                        public void warning(TransformerException exception)
//                                throws TransformerException {
//                            logger.warn(exception, exception);
//                        }
//
//                        public void error(TransformerException exception)
//                                throws TransformerException {
//                            logger.error(exception, exception);
//                        }
//
//                        public void fatalError(TransformerException exception)
//                                throws TransformerException {
//                            logger.error(exception, exception);
//                        }
//                    });

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static Map listeners = Collections.synchronizedMap(new HashMap());

    public static interface URIResolverListener {
        public void startResolving(String href, String base);
        public ContentHandler getContentHandler();
        public ProcessorInput getInputByName(String name);

    }

    public static void setURIResolverListener(URIResolverListener listener) {
        listeners.put(Thread.currentThread(), listener);
    }

    public static void removeURIResolverListener() {
        listeners.remove(Thread.currentThread());
    }

    public static Transformer getXMLIdentityTransformer() {
        try {
            Transformer transformer = getIdentityTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, DEFAULT_OUTPUT_ENCODING);
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(INDENT_AMOUNT_PROPERTY, "1");
            return transformer;
        } catch (Exception e) {
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
            transformer.setOutputProperty(OutputKeys.STANDALONE, standalone.booleanValue() ? "yes" : "no");
    }

    /**
     * Return a new identity transformer object.
     *
     * @return  a new identity Transformer object
     * @throws TransformerConfigurationException
     */
    public static Transformer getIdentityTransformer() throws TransformerConfigurationException {
        Transformer transformer = getFactory(IDENTITY_TYPE).newTransformer();
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

    public static TransformerHandler getTransformerHandler(Templates templates, String type) throws TransformerConfigurationException {
        return getFactory(type).newTransformerHandler(templates);
    }

    public static TemplatesHandler getTemplatesHandler(String type) throws TransformerException {
        return getFactory(type).newTemplatesHandler();
    }

    public static Templates getTemplates(Source source, String type,
                                         ErrorListener errorListener, URIResolver uriResolver)
            throws TransformerConfigurationException {
        SAXTransformerFactory factory = getFactory(type);
        factory.setErrorListener(errorListener);
        factory.setURIResolver(uriResolver);
        return factory.newTemplates(source);
    }

    public static Transformer testCreateTransformerWrapper(Transformer transformer, String publicProperty, String privateProperty) {
        return new TransformerWrapper(transformer, publicProperty, privateProperty);
    }

    public static TransformerHandler testCreateTransformerHandlerWrapper(TransformerHandler transformerHandler, String publicProperty, String privateProperty) {
        return new TransformerHandlerWrapper(transformerHandler, publicProperty, privateProperty);
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
            transformer.setOutputProperty(name, value);;
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