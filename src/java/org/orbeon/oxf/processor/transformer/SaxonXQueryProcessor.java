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
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.processor.transformer.xslt.StringErrorListener;
import org.orbeon.oxf.processor.transformer.xslt.XSLTTransformer;
import org.orbeon.oxf.properties.PropertyStore;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.query.DynamicQueryContext;
import org.orbeon.saxon.query.StaticQueryContext;
import org.orbeon.saxon.query.XQueryExpression;
import org.xml.sax.ContentHandler;

import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.URIResolver;
import java.util.Iterator;
import java.util.Map;

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

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                try {
                    final Document dataDocument = readInputAsDOM4J(pipelineContext, INPUT_DATA);

                    // Create XQuery configuration (depends on attributes input)
                    final URIResolver uriResolver = new TransformerURIResolver(SaxonXQueryProcessor.this, pipelineContext, INPUT_DATA, URLGenerator.DEFAULT_HANDLE_XINCLUDE);
                    // TODO: once caching is in place, make sure cached object does not contain a reference to the URIResolver
                    final Configuration config = new Configuration();
                    {
                        config.setErrorListener(new StringErrorListener(logger));

                        // 2007-07-05 MK says: "fetching of query modules is done by the ModuleURIResolver in the
                        // static context, fetching of doc() is done by the URIResolver in the dynamic context; the
                        // URIResolver in the Configuration is just a fallback."
//                        config.setURIResolver(uriResolver);

                        // Read attributes
                        Map attributes = null;
                        {
                            // Read attributes input only if connected
                            if (getConnectedInputs().get(INPUT_ATTRIBUTES) != null) {
                                // Read input as an attribute Map and cache it
                                attributes = (Map) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ATTRIBUTES), new CacheableInputReader() {
                                    public Object read(PipelineContext context, ProcessorInput input) {
                                        final Document preferencesDocument = readInputAsDOM4J(context, input);
                                        final PropertyStore propertyStore = new PropertyStore(preferencesDocument);
                                        final PropertySet propertySet = propertyStore.getGlobalPropertySet();
                                        return propertySet.getObjectMap();
                                    }
                                });
                            }
                        }
                        // Set configuration attributes if any
                        if (attributes != null) {
                            for (Iterator i = attributes.keySet().iterator(); i.hasNext();) {
                                String key = (String) i.next();
                                Object value = attributes.get(key);

                                config.setConfigurationProperty(key, value);
                            }
                        }
                    }

                    // Create static context
                    final StaticQueryContext staticContext = new StaticQueryContext(config);

                    // Create XQuery expression (depends on config input and static context)
                    // TODO: caching of query must also depend on attributes input
                    XQueryExpression xqueryExpression = (XQueryExpression) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                        public Object read(PipelineContext context, ProcessorInput input) {

                            try {

                                // Read XQuery into String
                                final Document xqueryDocument = readCacheInputAsDOM4J(pipelineContext, INPUT_CONFIG);
                                String xqueryBody;
                                if (XMLConstants.XS_STRING_QNAME.equals(Dom4jUtils.extractAttributeValueQName(xqueryDocument.getRootElement(), XMLConstants.XSI_TYPE_QNAME, false))) {
                                    // Content is text under an XML root element
                                    xqueryBody = xqueryDocument.getRootElement().getStringValue();
                                } else {
                                    // Content is XQuery embedded into XML
                                    xqueryBody = Dom4jUtils.domToString(xqueryDocument);
                                    xqueryBody = xqueryBody.substring(xqueryBody.indexOf(">") + 1);
                                    xqueryBody = xqueryBody.substring(0, xqueryBody.lastIndexOf("<"));

                                    // Add namespaces declarations
                                    // TODO: 2007-7-05 MK says that this shouldn't be necessary. In fact, I don't know why we do this.
                                    final Map namespaces = Dom4jUtils.getNamespaceContext(xqueryDocument.getRootElement());
                                    for (Iterator i = namespaces.keySet().iterator(); i.hasNext();) {
                                        String prefix = (String) i.next();
                                        String uri = (String) namespaces.get(prefix);
                                        staticContext.declarePassiveNamespace(prefix, uri, false);
                                    }
                                }

                                // 2007-07-05 MK says: "fetching of query modules is done by the ModuleURIResolver in the
                                // static context, fetching of doc() is done by the URIResolver in the dynamic context; the
                                // URIResolver in the Configuration is just a fallback."
                                // Clear URI resolver from static context as this must not end up in the cache
//                                staticContext.getConfiguration().setURIResolver(null);
                                return staticContext.compileQuery(xqueryBody);
                            } catch (Exception e) {
                                throw new OXFException(e);
                            }
                        }
                    });

                    // Create dynamic context and run query
                    DynamicQueryContext dynamicContext =  new DynamicQueryContext(config);
                    dynamicContext.setContextItem(staticContext.buildDocument(new DocumentSource(dataDocument)));
                    dynamicContext.setURIResolver(uriResolver);
                    // TODO: use xqueryExpression.getStaticContext() when Saxon is upgraded
                    xqueryExpression.run(dynamicContext, new SAXResult(contentHandler), new java.util.Properties());

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
