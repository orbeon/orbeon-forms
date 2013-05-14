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
package org.orbeon.oxf.processor.xforms.output;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.xforms.output.element.ViewContentHandler;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsElementContext;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.InstanceData;
import org.xml.sax.ContentHandler;

/**
 *  Handle XForms output (XForms Classic engine).
 */
public class XFormsOutput extends ProcessorImpl {

    private static final String INPUT_MODEL = "model";
    private static final String INPUT_INSTANCE = "instance";

    public XFormsOutput() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MODEL));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INSTANCE));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {

                // Get XForms model
                XFormsModel model = (XFormsModel) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_MODEL), new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        return new XFormsModel(readInputAsDOM4J(context, input));
                    }
                });
                try {
                    // Clone because we set the instance, and that must not be cached
                    model = (XFormsModel) model.clone();
                } catch (CloneNotSupportedException e) {
                    throw new OXFException(e);
                }

                // Set annotated instance on model
                final Document instanceDocument = (Document) readInputAsDOM4J(pipelineContext, INPUT_INSTANCE);
                InstanceData.setInitialDecoration(instanceDocument);
                model.setInstanceDocument(instanceDocument,  model.getEffectiveId(), model.getDefaultInstanceId(), null, null, null, false, -1, null);

                // Create and initialize XForms Engine
                final XFormsContainingDocument containingDocument = new XFormsContainingDocument(pipelineContext, model);

                // Create evaluation context
                final XFormsElementContext elementContext =
                        new XFormsElementContext(pipelineContext, containingDocument, contentHandler);

                // Send SAX events of view to ViewContentHandler
                readInputAsSAX(pipelineContext, INPUT_DATA, new ViewContentHandler(contentHandler, elementContext));
            }
        };
        addOutput(name, output);
        return output;
    }
}
