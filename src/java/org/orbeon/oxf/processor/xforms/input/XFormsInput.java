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
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.xforms.input.action.Action;
import org.orbeon.oxf.processor.xforms.input.action.ActionFunctionContext;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.event.events.XXFormsInitializeEvent;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.ContentHandler;

import java.util.Iterator;
import java.util.List;

/**
 * Handle XForms decoding (XForms Classic engine).
 *
 * A filter can be provided. It contains XPath references to nodes that have been filled-out by the
 * PFC based on URL filtering. The format of the filter comes directly from the native document
 * created in the PFC, for example:
 *
 * <params xmlns="http://www.orbeon.com/oxf/controller">
 *    <param ref="/form/x"/>
 *    <param ref="/form/y"/>
 *    <param ref="/form/z"/>
 * </params>
 */
public class XFormsInput extends ProcessorImpl {

    public static final String REQUEST_FORWARD_INSTANCE_DOCUMENT = "org.orbeon.oxf.request.forward-xforms-instance-document";

    static private Logger logger = LoggerFactory.createLogger(XFormsInput.class);

    final static private String INPUT_MODEL = "model";
    final static private String INPUT_MATCHER_RESULT = "matcher-result";
    final static private String INPUT_REQUEST = "request";
    final static private String INPUT_FILTER = "filter";
    final static private String OUTPUT_INSTANCE = "instance";

    public XFormsInput() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MODEL));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_REQUEST));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_FILTER));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MATCHER_RESULT));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_INSTANCE));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {

                // Get XForms model
                XFormsModel model = (XFormsModel) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_MODEL), new CacheableInputReader(){
                    public Object read(PipelineContext pipelineContext, ProcessorInput input) {
                        return new XFormsModel(readInputAsDOM4J(pipelineContext, input));
                    }
                });
                try {
                    // Clone because we set the instance, and that must not be cached
                    model = (XFormsModel) model.clone();
                } catch (CloneNotSupportedException e) {
                    throw new OXFException(e);
                }

//                XFormsInstance contextInstance = XFormsInstance.createInstanceFromContext(pipelineContext);// TODO: do we still need this?
                XFormsInstance contextInstance = null;
                if (contextInstance != null) {
                    // Instance comes from context in case of a forward
                    model.setInstanceDocument(pipelineContext, 0, contextInstance.getInstanceDocument(), null, null, null);
                } else {
                    // Extract parameters from request
                    final RequestParameters requestParameters = (RequestParameters) readCacheInputAsObject(pipelineContext,  getInputByName(INPUT_REQUEST), new CacheableInputReader(){
                        public Object read(PipelineContext context, ProcessorInput input) {
                            Document requestDocument = readInputAsDOM4J(context, input);
                            return new RequestParameters(pipelineContext, requestDocument);
                        }
                    });

                    // Set instance on model if provided
                    if (requestParameters.getInstance() != null)
                        model.setInstanceDocument(pipelineContext, 0, (Document) requestParameters.getInstance().clone(), null, null, null);
                    // Set initialization listener
                    model.setInstanceConstructListener(new XFormsModel.InstanceConstructListener() {
                        public void updateInstance(int position, XFormsInstance localInstance) {
                            if (position == 0) {

                                // Update instance from request
                                int[] ids = requestParameters.getIds();
                                for (int i = 0; i < ids.length; i++) {
                                    int id = ids[i];
                                    localInstance.setValueForId(id, requestParameters.getValue(id), requestParameters.getType(id));
                                }

                                // Update instance from path info
                                {
                                    final List groupElements = readCacheInputAsDOM4J
                                            (pipelineContext, INPUT_MATCHER_RESULT).getRootElement().elements("group");

                                    final Element inputFilterRootElement = readCacheInputAsDOM4J
                                            (pipelineContext, INPUT_FILTER).getRootElement();

                                    final List setValueElements;
                                    {
                                        // Handle legacy <param> element
                                        final List paramElements = inputFilterRootElement.elements("param");
                                        if (paramElements != null && paramElements.size() > 0)
                                            setValueElements = paramElements;
                                        else
                                            setValueElements = inputFilterRootElement.elements("setvalue");
                                    }

                                    if (groupElements.size() != setValueElements.size())
                                        throw new OXFException("Number of <setvalue> or <param> elements does not match number of groups in path regular expression");
                                    for (Iterator setValueIterator = setValueElements.iterator(),
                                            groupIterator = groupElements.iterator(); setValueIterator.hasNext();) {
                                        final Element paramElement = (Element) setValueIterator.next();
                                        final Element groupElement = (Element) groupIterator.next();
                                        final String value = groupElement.getStringValue();
                                        if (!"".equals(value))
                                            localInstance.setValueForParam(pipelineContext, paramElement.attributeValue("ref"),
                                                    Dom4jUtils.getNamespaceContext(paramElement), value);
                                    }
                                }

                                if (logger.isDebugEnabled())
                                    logger.debug("1) Instance recontructed from request:\n"
                                            + Dom4jUtils.domToString(localInstance.getInstanceDocument()));

                                // Run actions
                                // TODO: this has to be done in Model
                                Action[] actions = requestParameters.getActions();
                                for (int i = 0; i < actions.length; i++) {
                                    Action action = actions[i];
                                    action.run(pipelineContext, new ActionFunctionContext(),
                                            requestParameters.getEncryptionKey(), localInstance.getInstanceDocumentInfo());
                                }
                                if (logger.isDebugEnabled())
                                    logger.debug("2) Instance with actions applied:\n"
                                            + Dom4jUtils.domToString(localInstance.getInstanceDocument()));
                            }
                        }
                    });
                }

                // Create and initialize XForms Engine
                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                final XFormsContainingDocument containingDocument = new XFormsContainingDocument(model, externalContext);
                containingDocument.dispatchExternalEvent(pipelineContext, new XXFormsInitializeEvent(containingDocument));

                if (logger.isDebugEnabled())
                    logger.debug("3) Instance with model item properties applied:\n"
                            + Dom4jUtils.domToString(model.getDefaultInstance().getInstanceDocument()));

                // Get instance from XForms model
                XFormsInstance instance = model.getDefaultInstance();

                // Read out instance
                instance.read(contentHandler);

            }
        };
        addOutput(name, output);
        return output;
    }

//    private static XFormsInstance createInstanceFromContext(PipelineContext pipelineContext) {
//        ExternalContext.Request request = getRequest(pipelineContext);
//        ScopeStore instanceContextStore = (ScopeStore) request.getAttributesMap().get(REQUEST_FORWARD_INSTANCE_DOCUMENT);
//        return instanceContextStore == null || instanceContextStore.getSaxStore() == null ? null : new XFormsInstance(pipelineContext, null, instanceContextStore.getSaxStore().getDocument(), null, false, null);
//    }

//    private static ExternalContext.Request getRequest(PipelineContext context) {
//        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
//        if (externalContext == null)
//            throw new OXFException("Missing external context");
//        return externalContext.getRequest();
//    }
}
