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
package org.orbeon.oxf.xforms;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.ContentHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Handle XForms decoding.
 * <p/>
 * A filter can be provided. It contains XPath references to nodes that have been filled-out by the
 * PFC based on URL filtering. The format of the filter comes directly from the native document
 * created in the PFC, for example:
 * <p/>
 * <params xmlns="http://www.orbeon.com/oxf/controller">
 * <param ref="/form/x"/>
 * <param ref="/form/y"/>
 * <param ref="/form/z"/>
 * </params>
 */
public class XFormsSubmission extends ProcessorImpl {

    //static private Logger logger = LoggerFactory.createLogger(XFormsSubmission.class);

    final static private String INPUT_MATCHER_RESULT = "matcher-result";
    final static private String INPUT_REQUEST = "request";
    final static private String INPUT_FILTER = "filter";
    final static private String OUTPUT_INSTANCE = "instance";

    public XFormsSubmission() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_REQUEST));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_FILTER));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MATCHER_RESULT));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_INSTANCE));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {

                final XFormsInstance instance;
                XFormsInstance contextInstance = XFormsInstance.createInstanceFromContext(pipelineContext);
                if (contextInstance != null) {
                    // Got instance from context
                    instance = contextInstance;
                } else {
                    // Check whether instance was submitted

                    Document requestDocument = readInputAsDOM4J(pipelineContext, INPUT_REQUEST);

                    // Check whether an instance was submitted
                    boolean submitted;
                    {
                        PooledXPathExpression xpathExpression =
                                XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(requestDocument, null).wrap(requestDocument),
                                        "/request/parameters/(parameter[name = '$instances'] and parameter[name = '$models' and parameter[name = '$controls']])");

                        try {
                            submitted = ((Boolean) xpathExpression.evaluateSingle()).booleanValue();
                        } catch (XPathException e) {
                            throw new OXFException(e);
                        } finally {
                            if (xpathExpression != null)
                                xpathExpression.returnToPool();
                        }
                    }

                    if (submitted) {
                        // Instance submitted, handle submission

                        final String staticStateString;
                        final String dynamicStateString;
                        {
                            // Create XPath variables
                            Map variables = new HashMap();
                            variables.put("parameter-name", "");

                            // Create XPath expression
                            PooledXPathExpression xpathExpression =
                                    XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(requestDocument, null).wrap(requestDocument),
                                            "normalize-space(/request/parameters/parameter[name = $parameter-name]/value)", XFormsServer.XFORMS_NAMESPACES, variables);

                            // Extract parameters
                            try {
                                variables.put("parameter-name", "$static-state");
                                staticStateString = (String) xpathExpression.evaluateSingle();
                                variables.put("parameter-name", "$dynamic-state");
                                dynamicStateString = (String) xpathExpression.evaluateSingle();

                            } catch (XPathException e) {
                                throw new OXFException(e);
                            } finally {
                                if (xpathExpression != null)
                                    xpathExpression.returnToPool();
                            }
                        }

                        // Create and initialize XForms engine from encoded data
                        XFormsContainingDocument containingDocument = XFormsServer.createXFormsEngine(pipelineContext,
                                staticStateString, dynamicStateString);

                        // TODO: set instance values from current controls values
//                        final RequestParameters requestParameters = (RequestParameters) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_REQUEST), new CacheableInputReader() {
//                            public Object read(PipelineContext context, ProcessorInput input) {
//                                Document requestDocument = readInputAsDOM4J(context, input);
//                                RequestParameters requestParameters = new RequestParameters(pipelineContext, requestDocument);
//                                return requestParameters;
//                            }
//                        });

                        // TODO: actions, events

                        // TODO: select submitted instance and return it
                        instance = new XFormsInstance(pipelineContext, Dom4jUtils.NULL_DOCUMENT);//TEMP FIXME XXXX

                    } else {
                        // No instance submitted, return null document
                        instance = new XFormsInstance(pipelineContext, Dom4jUtils.NULL_DOCUMENT);
                    }
                }

                // Read out instance
                instance.read(contentHandler);

            }
        };
        addOutput(name, output);
        return output;
    }
}
