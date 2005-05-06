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
package org.orbeon.oxf.processor.xforms.validation;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsEvents;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Collections;

public class XFormsAnnotate extends ProcessorImpl {

    private static final String INPUT_MODEL = "model";
    private static final String INPUT_INSTANCE = "instance";
    private static final String OUTPUT_INSTANCE = "instance";

    public XFormsAnnotate() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MODEL));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INSTANCE));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_INSTANCE));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {

                try {
                    // Get XForms model
                    XFormsModel model = (org.orbeon.oxf.xforms.XFormsModel) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_MODEL), new CacheableInputReader(){
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
                    Document instanceDocument = (Document) readCacheInputAsDOM4J(pipelineContext, INPUT_INSTANCE).clone();
                    model.setInstanceDocument(pipelineContext, instanceDocument);

                    // Create and initialize XForms Engine
                    XFormsContainingDocument containingDocument = new XFormsContainingDocument(Collections.singletonList(model), null);
                    containingDocument.initialize(pipelineContext);
                    containingDocument.dispatchEvent(pipelineContext, XFormsEvents.XXFORMS_INITIALIZE);

                    // Run remaining model item properties
                    // TODO: this has to be done in a different way (events?)
                    model.applyOtherBinds(pipelineContext);

                    // Output the instance to the specified content handler
                    LocationSAXWriter saxw = new LocationSAXWriter();
                    saxw.setContentHandler(contentHandler);
                    saxw.write(instanceDocument);
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
