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
package org.orbeon.oxf.processor.xforms.input;

import org.apache.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.validation.XFormsValidationProcessor;
import org.orbeon.oxf.processor.xforms.Model;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.processor.xforms.input.action.Action;
import org.orbeon.oxf.processor.xforms.input.action.ActionFunctionContext;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.common.OXFException;
import org.xml.sax.ContentHandler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Handle XForms decoding.
 *
 * A filter can be provided. It contains XPath references to nodes that have been filled-out by the
 * WAC based on URL filtering. The format of the filter comes directly from the native document
 * created in the WAC, for example:
 *
 * <params>
 *    <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/x"/>
 *    <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/y"/>
 *    <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/z"/>
 * </params>
 */
public class XFormsInput extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(XFormsInput.class);

    final static private String XFORMS_VALIDATION_FLAG = "validate";
    final static private String INPUT_MODEL = "model";
    final static private String INPUT_INSTANCE = "instance";
    final static private String INPUT_REQUEST = "request";
    final static private String INPUT_FILTER = "filter";
    final static private String OUTPUT_INSTANCE = "instance";

    public XFormsInput() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MODEL));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INSTANCE));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_REQUEST));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_FILTER));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_INSTANCE));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {

                // Extract information from XForms model
                Model model = (Model) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_MODEL), new CacheableInputReader(){
                    public Object read(PipelineContext context, ProcessorInput input) {
                        Model model = new Model(pipelineContext);
                        readInputAsSAX(context, input, model.getContentHandlerForModel());
                        return model;
                    }
                });

                // Extract parameters from request
                RequestParameters requestParameters = (RequestParameters) readCacheInputAsObject(pipelineContext,  getInputByName(INPUT_REQUEST), new CacheableInputReader(){
                    public Object read(PipelineContext context, ProcessorInput input) {
                        RequestParameters requestParameters = new RequestParameters();
                        readInputAsSAX(context, input, requestParameters.getContentHandlerForRequest());
                        return requestParameters;
                    }
                });

                // Try to get instance document from context
                Instance instance = Instance.createInstanceFromContext(pipelineContext);

                if (instance == null) {
                    // Get instance from input or from request
                    if (requestParameters.isSubmitted()) {
                        instance = new Instance(pipelineContext, requestParameters.getInstance());
                    } else {
                        // Get instance from input and create copy as we don't want the copy from the cache
                        Document templateInstanceFromCache = readCacheInputAsDOM4J(pipelineContext, INPUT_INSTANCE);
                        if (templateInstanceFromCache.getRootElement() == null)
                            throw new OXFException("No instance found in XForms model");
                        Document templateInstance = XMLUtils.createDOM4JDocument();
                        templateInstance.add(templateInstanceFromCache.getRootElement().createCopy());
                        instance = new Instance(pipelineContext, templateInstance);
                    }

                    // Extract filtered parameters from instance if any
                    Element filterElement = readCacheInputAsDOM4J(pipelineContext, INPUT_FILTER).getRootElement();
                    Map filteredParams = null;
                    for (Iterator i = XPathUtils.selectIterator(filterElement, "/*/*/@ref"); i.hasNext();) {
                        Attribute attribute = (Attribute) i.next();
                        String paramRef = attribute.getValue();
                        // FIXME: Argh, we don't handle namespaces correctly here!
                        // If the param element in the WAC contains namespaces, this won't work!
                        Node paramNode = XPathUtils.selectSingleNode(instance.getDocument().getRootElement(), paramRef);
                        if (paramNode != null) {
                            if (filteredParams == null)
                                filteredParams = new HashMap();

                            String nodeName = XFormsUtils.getNameForNode(paramNode, false);
                            String nodeValue = paramNode instanceof Element ? ((Element) paramNode).getStringValue() : ((Attribute) paramNode).getValue();

                            filteredParams.put(nodeName, nodeValue);
                        }
                    }

                    // Fill-out instance
                    XFormsUtils.setInitialDecoration(instance.getDocument());
                    int[] ids = requestParameters.getIds();
                    for (int i = 0; i < ids.length; i++) {
                        int id = ids[i];
                        instance.setValue(id, requestParameters.getValue(id), requestParameters.getType(id), false);
                    }

                    // FIXME: will need to modify this as we now use node id instead of node path
                    /*
                    if (requestParameters.isSubmitted() && filteredParams != null) {
                        for (Iterator i = filteredParams.keySet().iterator(); i.hasNext();) {
                            String nodeName = (String) i.next();
                            String nodeValue = (String) filteredParams.get(nodeName);
                            instance.setValue(nodeName, nodeValue, null, true);
                        }
                    }
                    */
                    if (logger.isDebugEnabled())
                        logger.debug("1) Instance recontructed from request:\n"
                                + XMLUtils.domToString(instance.getDocument()));

                    // Run actions
                    Action[] actions = requestParameters.getActions();
                    for (int i = 0; i < actions.length; i++) {
                        Action action = actions[i];
                        action.run(pipelineContext, new ActionFunctionContext(), instance.getDocument());
                    }
                    if (logger.isDebugEnabled())
                        logger.debug("2) Instance with actions applied:\n"
                                + XMLUtils.domToString(instance.getDocument()));

                    // Run model item properties
                    model.applyInputOutputBinds(instance.getDocument());
                    if (logger.isDebugEnabled())
                        logger.debug("3) Instance with model item properties applied:\n"
                                + XMLUtils.domToString(instance.getDocument()));
                }

                // Schema-validate if necessary
                Boolean enabled = getPropertySet().getBoolean(XFORMS_VALIDATION_FLAG, true);
                if (enabled != null && enabled.booleanValue() && model.getSchema() != null) {

                    Processor validator = new XFormsValidationProcessor(model.getSchema());
                    Processor resourceGenerator = PipelineUtils.createURLGenerator(model.getSchema());
                    PipelineUtils.connect(resourceGenerator, ProcessorImpl.OUTPUT_DATA, validator, XFormsValidationProcessor.INPUT_SCHEMA);
                    PipelineUtils.connect(XFormsValidationProcessor.DECORATION_CONFIG, ProcessorImpl.OUTPUT_DATA,
                            validator, ProcessorImpl.INPUT_CONFIG);
                    DOMGenerator domGenerator = new DOMGenerator(instance.getDocument());
                    PipelineUtils.connect(domGenerator, ProcessorImpl.OUTPUT_DATA, validator, ProcessorImpl.INPUT_DATA);
                    ProcessorOutput validationOutput = validator.createOutput(ProcessorImpl.OUTPUT_DATA);
                    validationOutput.read(pipelineContext, contentHandler);
                } else {
                    // Just output instance
                    instance.read(contentHandler);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
