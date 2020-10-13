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
package org.orbeon.oxf.processor.transformer;

import org.orbeon.dom.Document;
import org.orbeon.dom.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.transformer.xslt.StringErrorListener;
import org.orbeon.oxf.processor.transformer.xslt.XSLTTransformer;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.properties.PropertyStore;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.XPath;
import org.orbeon.oxf.xml.ParserConfiguration;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.xml.dom.Extensions;
import org.orbeon.oxf.xml.dom.IOSupport;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.query.DynamicQueryContext;
import org.orbeon.saxon.query.StaticQueryContext;
import org.orbeon.saxon.query.XQueryExpression;

import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXResult;
import java.util.Collections;
import java.util.Map;

/**
 * XQuery processor based on the Saxon engine.
 *
 * TODO: should work like the XSLT processor, and handle:
 *
 * - caching [BE CAREFUL WITH NOT CACHING TransformerURIResolver!]
 * - errors
 * - additional inputs
 * - etc.
 *
 * To get there, should maybe abstract what's in XSLT processor and derive from it here.
 */
public class SaxonXQueryProcessor extends ProcessorImpl {

    private static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(SaxonXQueryProcessor.class);

    // This input determines attributes to set on the Configuration
    private static final String INPUT_ATTRIBUTES = "attributes";

    public SaxonXQueryProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ATTRIBUTES, XSLTTransformer.XSLT_PREFERENCES_CONFIG_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(SaxonXQueryProcessor.this, name) {
            public void readImpl(final PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                try {
                    final Document dataDocument = readInputAsOrbeonDom(pipelineContext, INPUT_DATA);

                    // Create XQuery configuration (depends on attributes input)
                    final URIResolver uriResolver = new TransformerURIResolver(SaxonXQueryProcessor.this, pipelineContext, INPUT_DATA, ParserConfiguration.Plain());
                    // TODO: once caching is in place, make sure cached object does not contain a reference to the URIResolver
                    final Configuration configuration = XPath.newConfiguration();
                    {
                        configuration.setErrorListener(new StringErrorListener(logger));

                        // 2007-07-05 MK says: "fetching of query modules is done by the ModuleURIResolver in the
                        // static context, fetching of doc() is done by the URIResolver in the dynamic context; the
                        // URIResolver in the Configuration is just a fallback."
//                        config.setURIResolver(uriResolver);

                        // Read attributes
                        final Map<String, Boolean> attributesFromProperties;
                        {
                            // Read attributes input only if connected
                            if (getConnectedInputs().get(INPUT_ATTRIBUTES) != null) {
                                // Read input as an attribute Map and cache it
                                attributesFromProperties = readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ATTRIBUTES), new CacheableInputReader<Map<String, Boolean>>() {
                                    public Map<String, Boolean> read(PipelineContext context, ProcessorInput input) {
                                        final Document preferencesDocument = readInputAsOrbeonDom(context, input);
                                        final PropertyStore propertyStore = PropertyStore.parse(preferencesDocument);
                                        final PropertySet propertySet = propertyStore.getGlobalPropertySet();
                                        return propertySet.getBooleanProperties();
                                    }
                                });
                            } else
                                attributesFromProperties = Collections.emptyMap();
                        }
                        // Set configuration attributes if any
                        for (Map.Entry<String, Boolean> entry: attributesFromProperties.entrySet())
                            configuration.setConfigurationProperty(entry.getKey(), entry.getValue());
                    }

                    // Create static context
                    final StaticQueryContext staticContext = new StaticQueryContext(configuration);

                    // Create XQuery expression (depends on config input and static context)
                    // TODO: caching of query must also depend on attributes input
                    XQueryExpression xqueryExpression = readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader<XQueryExpression>() {
                        public XQueryExpression read(PipelineContext context, ProcessorInput input) {

                            try {

                                // Read XQuery into String
                                final Document xqueryDocument = readCacheInputAsDOM4J(pipelineContext, INPUT_CONFIG);
                                String xqueryBody;
                                if (XMLConstants.XS_STRING_QNAME().equals(Extensions.resolveAttValueQNameJava(xqueryDocument.getRootElement(), XMLConstants.XSI_TYPE_QNAME(), false))) {
                                    // Content is text under an XML root element
                                    xqueryBody = xqueryDocument.getRootElement().getStringValue();
                                } else {
                                    // Content is XQuery embedded into XML
                                    xqueryBody = IOSupport.domToStringJava(xqueryDocument);
                                    xqueryBody = xqueryBody.substring(xqueryBody.indexOf(">") + 1);
                                    xqueryBody = xqueryBody.substring(0, xqueryBody.lastIndexOf("<"));
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
                    final DynamicQueryContext dynamicContext =  new DynamicQueryContext(configuration);
                    dynamicContext.setContextItem(staticContext.buildDocument(new DocumentSource(dataDocument)));
                    dynamicContext.setURIResolver(uriResolver);
                    // TODO: use xqueryExpression.getStaticContext() when Saxon is upgraded
                    final SAXResult saxResult = new SAXResult(xmlReceiver);
                    saxResult.setLexicalHandler(xmlReceiver);

                    xqueryExpression.run(dynamicContext, saxResult, new java.util.Properties());

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
