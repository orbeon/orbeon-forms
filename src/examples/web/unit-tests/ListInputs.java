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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.SimpleProcessor;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Iterator;
import java.util.List;

public class ListInputs extends SimpleProcessor {

    private static org.apache.log4j.Logger logger =
            org.orbeon.oxf.util.LoggerFactory.createLogger(ListInputs.class);

    public ListInputs() {
        addInputInfo(new ProcessorInputOutputInfo("required-input-1"));
        addInputInfo(new ProcessorInputOutputInfo("required-input-2"));
        addOutputInfo(new ProcessorInputOutputInfo("list"));
    }

    public void generateList(PipelineContext pipelineContext, ContentHandler contentHandler) {
        try {
            List inputList = getConnectedInputs();

            contentHandler.startDocument();
            contentHandler.startElement("", "inputs", "inputs", new AttributesImpl());
            for (Iterator i = inputList.iterator(); i.hasNext();) {
                ProcessorInput input = (ProcessorInput) i.next();
                addStringElement(contentHandler, "input", input.getName());
                readInputAsSAX(pipelineContext, input, new ForwardingContentHandler(contentHandler) {
                    public void startDocument() {
                    }
                    public void endDocument() {
                    }
                });
            }
            contentHandler.endElement("", "inputs", "inputs");
            contentHandler.endDocument();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    protected static void addStringElement(ContentHandler contentHandler, String name, String value)
            throws SAXException {
        contentHandler.startElement("", name, name, new AttributesImpl());
        contentHandler.characters(value.toCharArray(), 0, value.length());
        contentHandler.endElement("", name, name);
    }
}
