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
import org.dom4j.DocumentHelper;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.xforms.Model;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

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
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {

                try {
                    // Extract info from model
                    final Model model = (Model) readCacheInputAsObject(context,  getInputByName(INPUT_MODEL), new CacheableInputReader(){
                        public Object read(PipelineContext context, ProcessorInput input) {
                            Model model = new Model();
                            readInputAsSAX(context, input, model.getContentHandlerForModel());
                            return model;
                        }
                    });

                    // Read instance data and annotate
                    final Document instance = DocumentHelper.createDocument
                            (readCacheInputAsDOM4J(context, INPUT_INSTANCE).getRootElement().createCopy());
                    XFormsUtils.setInitialDecoration(instance.getDocument());
                    model.applyInputOutputBinds(instance);

                    // Output the instance to the specified content handler
                    LocationSAXWriter saxw = new LocationSAXWriter();
                    saxw.setContentHandler(contentHandler);
                    saxw.write(instance);
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
