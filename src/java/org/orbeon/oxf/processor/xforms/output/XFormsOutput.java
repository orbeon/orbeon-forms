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
import org.dom4j.DocumentHelper;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.processor.xforms.Model;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.processor.xforms.output.element.*;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class XFormsOutput extends ProcessorImpl {

    private static final String INPUT_MODEL = "model";
    private static final String INPUT_INSTANCE = "instance";

    public XFormsOutput() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MODEL));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INSTANCE));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, Constants.XFORMS_NAMESPACE_URI + "/controls"));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                
                // Extract info from model
                final Model model = (Model) readCacheInputAsObject(context,
                        getInputByName(INPUT_MODEL), new CacheableInputReader(){
                    public Object read(PipelineContext context, ProcessorInput input) {
                        Model model = new Model(context, readInputAsDOM4J(context, input));
                        return model;
                    }
                });

                // Obsolete: used to come from config will be removed for 2.5
                final XFormsOutputConfig xformsOutputConfig = new XFormsOutputConfig("d", "http://orbeon.org/oxf/xml/document");

                // Read instance data and annotate
                final Document instance = DocumentHelper.createDocument
                        (readCacheInputAsDOM4J(context, INPUT_INSTANCE).getRootElement().createCopy());
                XFormsUtils.setInitialDecoration(instance.getDocument());
                model.applyInputOutputBinds(instance);

                // Create evaluation context
                final XFormsElementContext elementContext =
                        new XFormsElementContext(context, contentHandler, model, instance);

                // Send SAX events of view to ViewContentHandler
                readInputAsSAX(context, INPUT_DATA, new ViewContentHandler
                        (contentHandler, elementContext, xformsOutputConfig));
            }
        };
        addOutput(name, output);
        return output;
    }
}
