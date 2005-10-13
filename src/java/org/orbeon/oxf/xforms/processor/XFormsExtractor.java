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
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * This processor extracts XForms controls and creates a static state document for the request
 * encoder.
 */
public class XFormsExtractor extends ProcessorImpl {

    public XFormsExtractor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {

                readInputAsSAX(pipelineContext, INPUT_DATA, new ForwardingContentHandler(contentHandler) {

                    public void startDocument() throws SAXException {
                        super.startDocument();
                        super.startElement("", "static-state", "static-state", XMLUtils.EMPTY_ATTRIBUTES);
                    }

                    public void endDocument() throws SAXException {
                        super.endElement("", "static-state", "static-state");
                        super.endDocument();
                    }

                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

                        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
                            // This is an XForms element

                            //TODO

                            super.startElement(uri, localname, qName, attributes);
                        }
                    }

                    public void endElement(String uri, String localname, String qName) throws SAXException {

                        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
                            // This is an XForms element

                            //TODO

                            super.endElement(uri, localname, qName);
                        }
                    }
                });
            }
        };
        addOutput(name, output);
        return output;
    }
}