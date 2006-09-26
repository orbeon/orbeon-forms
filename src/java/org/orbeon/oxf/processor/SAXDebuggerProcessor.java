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

import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class SAXDebuggerProcessor extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(SAXDebuggerProcessor.class);

    public SAXDebuggerProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                readInputAsSAX(context, INPUT_DATA, new DebugContentHandler(contentHandler));
            }

            public OutputCacheKey getKeyImpl(PipelineContext context) {
                return getInputKey(context, getInputByName(INPUT_DATA));
            }

            public Object getValidityImpl(PipelineContext context) {
                return getInputValidity(context, getInputByName(INPUT_DATA));
            }
        };
        addOutput(name, output);
        return output;
    }

    public static class DebugContentHandler extends ForwardingContentHandler {

        public DebugContentHandler() {
            this(new NullSerializer.NullContentHandler());
        }

        public DebugContentHandler(ContentHandler contentHandler) {
            super(contentHandler);
        }

        public void characters(char[] chars, int start, int length) throws SAXException {
            logger.info("characters('" + new String(chars, start, length) + "', " + start + ", " + length + ")");
            super.characters(chars, start, length);
        }

        public void endDocument() throws SAXException {
            logger.info("endDocument()");
            super.endDocument();
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {
            logger.info("endElement('" + uri + "', '" + localname + "', '" + qName + "')");
            super.endElement(uri, localname, qName);
        }

        public void endPrefixMapping(String s) throws SAXException {
            logger.info("endPrefixMapping('" + s + "')");
            super.endPrefixMapping(s);
        }

        public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
            logger.info("ignorableWhitespace('" + new String(chars, start, length) + "', " + start + ", " + length + ")");
            super.ignorableWhitespace(chars, start, length);
        }

        public void processingInstruction(String s, String s1) throws SAXException {
            logger.info("processingInstruction('" + s + "', '" + s1 + "')");
            super.processingInstruction(s, s1);
        }

        public void setDocumentLocator(Locator locator) {
            logger.info("setDocumentLocator(...)");
            super.setDocumentLocator(locator);
        }

        public void skippedEntity(String s) throws SAXException {
            logger.info("skippedEntity('" + s + "')");
            super.skippedEntity(s);
        }

        public void startDocument() throws SAXException {
            logger.info("startDocument()");
            super.startDocument();
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            StringBuffer message = new StringBuffer("startElement('" + uri + "', '" + localname + "', '" + qName + "'");
            for (int i = 0; i < attributes.getLength(); i++)
                message.append(", ('" + attributes.getURI(i) + "', '" + attributes.getLocalName(i)
                        + "', '" + attributes.getQName(i) + "', '" + attributes.getValue(i) + "')");
            message.append(")");
            logger.info(message.toString());
            super.startElement(uri, localname, qName, attributes);
        }

        public void startPrefixMapping(String s, String s1) throws SAXException {
            logger.info("startPrefixMapping('" + s + "', '" + s1 + "')");
            super.startPrefixMapping(s, s1);
        }
    }
}
