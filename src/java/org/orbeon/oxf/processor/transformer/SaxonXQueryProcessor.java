/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.processor.transformer;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.processor.transformer.xslt.StringErrorListener;
import org.orbeon.oxf.processor.transformer.xslt.XSLTTransformer;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.TransformerFactoryImpl;
import org.orbeon.saxon.query.DynamicQueryContext;
import org.orbeon.saxon.query.StaticQueryContext;
import org.orbeon.saxon.query.XQueryExpression;
import org.xml.sax.ContentHandler;

import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.URIResolver;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * XQuery processor based on the Saxon engine.
 *
 * TODO: should work like the XSLT processor, and handle:
 *
 *   o caching [BE CAREFUL WITH NOT CACHING TransformerURIResolver!]
 *   o errors
 *   o additional inputs
 *   o etc.
 *
 * To get there, should maybe abstract what's in XSLT processor and derive from it here.
 */
public class SaxonXQueryProcessor extends ProcessorImpl {

    private static Logger logger = Logger.getLogger(SaxonXQueryProcessor.class);

    // This input determines attributes to set on the Configuration
    private static final String INPUT_ATTRIBUTES = "attributes";

    public SaxonXQueryProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ATTRIBUTES, XSLTTransformer.XSLT_PREFERENCES_CONFIG_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    // This class just to make a method public. I believe it is already public in the newer version
    // of Saxon. When that is integrated, we can remove the class below.
    class LocalStaticQueryContext extends StaticQueryContext {

        public LocalStaticQueryContext(Configuration config) {
            super(config);
        }

        public void declareActiveNamespace(String prefix, String uri) {
            super.declareActiveNamespace(prefix, uri);
        }
    };

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                try {
                    final Document dataDocument = readInputAsDOM4J(pipelineContext, INPUT_DATA);

                    // Create XQuery configuration (depends on attributes input)
                    final URIResolver uriResolver = new TransformerURIResolver(SaxonXQueryProcessor.this, pipelineContext, INPUT_DATA, URLGenerator.DEFAULT_HANDLE_XINCLUDE);
                    final Configuration config = new Configuration();
                    {
                        config.setErrorListener(new StringErrorListener(logger));
                        config.setURIResolver(uriResolver);

                        // Read attributes
                        Map attributes = null;
                        {
                            // Read attributes input only if connected
                            if (getConnectedInputs().get(INPUT_ATTRIBUTES) != null) {
                                // Read input as an attribute Map and cache it
                                attributes = (Map) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ATTRIBUTES), new CacheableInputReader() {
                                    public Object read(PipelineContext context, ProcessorInput input) {
                                        Document preferencesDocument = readInputAsDOM4J(context, input);
                                        OXFPropertiesSerializer.PropertyStore propertyStore = OXFPropertiesSerializer.createPropertyStore(preferencesDocument);
                                        OXFProperties.PropertySet propertySet = propertyStore.getGlobalPropertySet();
                                        return propertySet.getObjectMap();
                                    }
                                });
                            }
                        }
                        if (attributes != null)
                            setConfigurationAttributes(config, attributes);
                    }

                    // Create static context
                    final LocalStaticQueryContext staticContext = new LocalStaticQueryContext(config);

                    // Create XQuery expression (depends on config input and static context)
                    // TODO: caching of query must also depend on attributes input
                    XQueryExpression xqueryExpression = (XQueryExpression) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                        public Object read(PipelineContext context, ProcessorInput input) {

                            // Read XQuery into String
                            final Document xqueryDocument = readCacheInputAsDOM4J(pipelineContext, INPUT_CONFIG);
                            String xqueryBody;
                            if (XMLConstants.XS_STRING_QNAME.equals(Dom4jUtils.extractAttributeValueQName(xqueryDocument.getRootElement(), XMLConstants.XSI_TYPE_QNAME))) {
                                // Content is text under an XML root element
                                xqueryBody = xqueryDocument.getRootElement().getStringValue();
                            } else {
                                // Content is XQuery embedded into XML
                                xqueryBody = Dom4jUtils.domToString(xqueryDocument);
                                xqueryBody = xqueryBody.substring(xqueryBody.indexOf(">") + 1);
                                xqueryBody = xqueryBody.substring(0, xqueryBody.lastIndexOf("<"));
                            }

                            // Add namespaces declarations
                            final Map namespaces = new HashMap();
                            getDeclaredNamespaces(namespaces, xqueryDocument.getRootElement());
                            for (Iterator i = namespaces.keySet().iterator(); i.hasNext();) {
                                String prefix = (String) i.next();
                                String uri = (String) namespaces.get(prefix);
                                staticContext.declareActiveNamespace(prefix, uri);
                            }

                            try {
                                final XQueryExpression result = staticContext.compileQuery(xqueryBody);
                                // Clear URI resolver from static context as this must not end up in the cache
                                staticContext.getConfiguration().setURIResolver(null);
                                return result;
                            } catch (Exception e) {
                                throw new OXFException(e);
                            }
                        }
                    });

                    // Create dynamic context and run query
                    DynamicQueryContext dynamicContext =  new DynamicQueryContext(config);
                    dynamicContext.setContextNode(staticContext.buildDocument(new DocumentSource(dataDocument)));
                    dynamicContext.setURIResolver(uriResolver);
                    // TODO: use xqueryExpression.getStaticContext() when Saxon is upgraded
                    xqueryExpression.run(dynamicContext, new SAXResult(contentHandler), new Properties());

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    private void setConfigurationAttributes(Configuration config, Map attributes) {

        // NOTE: The code below directly uses the Saxon TransformerFactoryImpl, which may change
        // when we upgrade Saxon, but it's the easiest way to set attributes on a Configuration
        // object from name / value pairs.
        TransformerFactoryImpl transformerFactory = new TransformerFactoryImpl();
        transformerFactory.setConfiguration(config);

        for (Iterator i = attributes.keySet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            Object value = attributes.get(key);

            transformerFactory.setAttribute(key, value);
        }
    }

    /**
     * Returns all the namespaces declared in the given document. Throws an exception if a prefix
     * is mapped to two different URI in the document (we might want to support this in the future).
     */
    private void getDeclaredNamespaces(Map namespaces, Element element) {

        // Handle namespace in this element
        for (Iterator i = element.declaredNamespaces().iterator(); i.hasNext();) {
            Namespace namespace = (Namespace) i.next();
            String prefix = namespace.getPrefix();
            String uri = namespace.getURI();
            String existingMapping = (String) namespaces.get(prefix);
            if (existingMapping != null) {
                if (!uri.equals(existingMapping))
                    throw new OXFException("Namespace prefix '" + prefix + "' is mapped to more than one URI: '"
                            + existingMapping + "' and '" + uri + "'");
            } else {
                namespaces.put(prefix, uri);
            }
        }

        // Go through children elements
        for (Iterator i = element.elements().iterator(); i.hasNext();) {
            Element child = (Element) i.next();
            getDeclaredNamespaces(namespaces, child);
        }
    }
}
