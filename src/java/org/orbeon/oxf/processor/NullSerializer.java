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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class NullSerializer extends ProcessorImpl {

    public NullSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    public void start(PipelineContext pipelineContext) {
        try {
            readInputAsSAX(pipelineContext, INPUT_DATA, new NullContentHandler());
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static class NullContentHandler implements ContentHandler {
        public void setDocumentLocator(Locator locator) {
        }

        public void startDocument()
                throws SAXException {
        }

        public void endDocument()
                throws SAXException {
        }

        public void startPrefixMapping(String prefix, String uri)
                throws SAXException {
        }

        public void endPrefixMapping(String prefix)
                throws SAXException {
        }

        public void startElement(String namespaceURI, String localName,
                                 String qName, Attributes atts)
                throws SAXException {
        }

        public void endElement(String namespaceURI, String localName,
                               String qName)
                throws SAXException {
        }

        public void characters(char ch[], int start, int length)
                throws SAXException {
        }

        public void ignorableWhitespace(char ch[], int start, int length)
                throws SAXException {
        }

        public void processingInstruction(String target, String data)
                throws SAXException {
        }

        public void skippedEntity(String name)
                throws SAXException {
        }
    }
}
