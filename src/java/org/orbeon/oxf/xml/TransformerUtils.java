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

import orbeon.apache.xalan.processor.StylesheetHandler;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.ProcessorInput;
import org.xml.sax.ContentHandler;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TransformerUtils {

    public static final String DEFAULT_OUTPUT_ENCODING = "utf-8";

    public static final String XALAN_BUILTIN_TRANSFORMER_TYPE = "orbeon.apache.xalan.processor.TransformerFactoryImpl";
    public static final String SAXON_BUILTIN_TRANSFORMER_TYPE = "org.orbeon.saxon.TransformerFactoryImpl";
    public static final String IDENTITY_TYPE = SAXON_BUILTIN_TRANSFORMER_TYPE;
    public static final String DEFAULT_TYPE = SAXON_BUILTIN_TRANSFORMER_TYPE;

//    private static final String XALAN_INDENT_AMOUNT = "{http://xml.apache.org/xslt}indent-amount";
//    private static final String XALAN_CONTENT_HANDLER = "{http://xml.apache.org/xslt}content-handler";

    public static final String SAXON_INDENT_AMOUNT = "{http://saxon.sf.net/}indent-spaces";

    public static final String INDENT_AMOUNT = SAXON_INDENT_AMOUNT;

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
            Transformer transformer = getFactory(IDENTITY_TYPE).newTransformer();
            applyXMLOutputProperties(transformer);
            return transformer;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static void applyXMLOutputProperties(Transformer transformer) {
        transformer.setOutputProperty(OutputKeys.ENCODING, DEFAULT_OUTPUT_ENCODING);
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(INDENT_AMOUNT, "0");
//        transformer.setOutputProperty(XALAN_CONTENT_HANDLER, "orbeon.apache.xml.serializer.ToXMLStream");
    }

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
            transformer.setOutputProperty(INDENT_AMOUNT, String.valueOf(indentAmount));
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, omitXMLDeclaration ? "yes" : "no");
        if (standalone != null)
            transformer.setOutputProperty(OutputKeys.STANDALONE, standalone.booleanValue() ? "yes" : "no");
    }


    public static Transformer getIdentityTransformer() throws TransformerConfigurationException {
        Transformer transformer = getFactory(IDENTITY_TYPE).newTransformer();
        return transformer;
    }

    public static TransformerHandler getIdentityTransformerHandler() {
        try {
            return getFactory(IDENTITY_TYPE).newTransformerHandler();
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
}
