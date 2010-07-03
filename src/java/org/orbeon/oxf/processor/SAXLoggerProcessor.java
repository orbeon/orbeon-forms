/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor;

import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.ForwardingXMLReceiver;
import org.orbeon.oxf.xml.XMLReceiverAdapter;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class SAXLoggerProcessor extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(SAXLoggerProcessor.class);

    public SAXLoggerProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorOutputImpl(SAXLoggerProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                readInputAsSAX(context, INPUT_DATA, new DebugXMLReceiver(xmlReceiver));
            }

            @Override
            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
                return getInputKey(pipelineContext, getInputByName(INPUT_DATA));
            }

            @Override
            public Object getValidityImpl(PipelineContext pipelineContext) {
                return getInputValidity(pipelineContext, getInputByName(INPUT_DATA));
            }
        };
        addOutput(name, output);
        return output;
    }

    public static class DebugXMLReceiver extends ForwardingXMLReceiver {

        private Locator locator;
        private int level = 0;

        public DebugXMLReceiver() {
            this(new XMLReceiverAdapter());
        }

        public DebugXMLReceiver(XMLReceiver xmlReceiver) {
            super(xmlReceiver);
        }

        @Override
        public void characters(char[] chars, int start, int length) throws SAXException {
            log("characters('" + new String(chars, start, length) + "', " + start + ", " + length + ")");
            super.characters(chars, start, length);
        }

        @Override
        public void endDocument() throws SAXException {
            log("endDocument()");
            super.endDocument();
        }

        @Override
        public void endElement(String uri, String localname, String qName) throws SAXException {
            level--;
            log("endElement('" + uri + "', '" + localname + "', '" + qName + "')");
            super.endElement(uri, localname, qName);
        }

        @Override
        public void endPrefixMapping(String s) throws SAXException {
            log("endPrefixMapping('" + s + "')");
            super.endPrefixMapping(s);
        }

        @Override
        public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
            log("ignorableWhitespace('" + new String(chars, start, length) + "', " + start + ", " + length + ")");
            super.ignorableWhitespace(chars, start, length);
        }

        @Override
        public void processingInstruction(String s, String s1) throws SAXException {
            log("processingInstruction('" + s + "', '" + s1 + "')");
            super.processingInstruction(s, s1);
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            logger.info("setDocumentLocator(...)");
            this.locator = locator;
            super.setDocumentLocator(locator);
        }

        @Override
        public void skippedEntity(String s) throws SAXException {
            log("skippedEntity('" + s + "')");
            super.skippedEntity(s);
        }

        @Override
        public void startDocument() throws SAXException {
            log("startDocument()");
            super.startDocument();
        }

        @Override
        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

            final StringBuilder message = new StringBuilder("startElement('" + uri + "', '" + localname + "', '" + qName + "'");
            for (int i = 0; i < attributes.getLength(); i++)
                message.append(", ('" + attributes.getURI(i) + "', '" + attributes.getLocalName(i)
                        + "', '" + attributes.getQName(i) + "', '" + attributes.getValue(i) + "')");
            message.append(")");

            log(message.toString());

            super.startElement(uri, localname, qName, attributes);
            level++;
        }

        @Override
        public void startPrefixMapping(String s, String s1) throws SAXException {
            log("startPrefixMapping('" + s + "', '" + s1 + "')");
            super.startPrefixMapping(s, s1);
        }

        @Override
        public void comment(char[] chars, int start, int length) throws SAXException {
            log("comment('" + new String(chars, start, length) + "')");
            super.comment(chars, start, length);
        }

        @Override
        public void startDTD(String name, String publicId, String systemId) throws SAXException {
            log("startDTD('" + name + ", " + publicId + ", " + systemId + "')");
            super.startDTD(name, publicId, systemId);
        }

        @Override
        public void endDTD() throws SAXException {
            log("endDTD()");
            super.endDTD();
        }

        @Override
        public void startEntity(String name) throws SAXException {
            log("startEntity('" + name + "')");
            super.startEntity(name);
        }

        @Override
        public void endEntity(String name) throws SAXException {
            log("endEntity('" + name + "')");
            super.endEntity(name);
        }

        @Override
        public void startCDATA() throws SAXException {
            log("startCDATA()");
            super.startCDATA();
        }

        @Override
        public void endCDATA() throws SAXException {
            log("endCDATA()");
            super.endCDATA();
        }

        private void log(String message) {
            final StringBuilder builder = new StringBuilder(getLogSpaces());
            builder.append(message);
            addLocatorInfo(builder);
            logger.info(builder.toString());
        }

        private void addLocatorInfo(StringBuilder message) {
            if (locator != null) {
                message.append(" [");
                if (locator.getSystemId() != null)
                    message.append(locator.getSystemId());
                message.append(", ");
                message.append(Integer.toString(locator.getLineNumber()));
                message.append(", ");
                message.append(Integer.toString(locator.getColumnNumber()));
                message.append("]");
            }
        }

        private String getLogSpaces() {
            final StringBuffer sb = new StringBuffer();
            for (int i = 0; i < level; i++)
                sb.append(" ");
            return sb.toString();
        }
    }
}
