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
package org.orbeon.oxf.xforms.processor;

import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.xml.sax.ContentHandler;

/**
 * This processor transforms an XML request into an encoded XML request for the XForms Server.
 */
public class XFormsRequestEncoder extends ProcessorImpl {

    public XFormsRequestEncoder() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {

                final ContentHandlerHelper contentHandlerHelper = new ContentHandlerHelper(contentHandler);
                contentHandlerHelper.startDocument();
                contentHandlerHelper.startElement("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-request");
                contentHandlerHelper.startElement("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI, "static-state");

                final Document document = readCacheInputAsDOM4J(pipelineContext, INPUT_DATA);
                final String encoded = XFormsUtils.encodeXML(pipelineContext, document, XFormsUtils.getEncryptionKey());
                contentHandlerHelper.text(encoded);

                contentHandlerHelper.endElement();
                contentHandlerHelper.startElement("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI, "dynamic-state");
                contentHandlerHelper.endElement();
                contentHandlerHelper.startElement("xxforms", XFormsConstants.XXFORMS_NAMESPACE_URI, "action");
                contentHandlerHelper.endElement();
                contentHandlerHelper.endElement();
                contentHandlerHelper.endDocument();
            }
        };
        addOutput(name, output);
        return output;
    }
}