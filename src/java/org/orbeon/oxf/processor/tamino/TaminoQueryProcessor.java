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
package org.orbeon.oxf.processor.tamino;

import com.softwareag.tamino.db.api.accessor.TQuery;
import com.softwareag.tamino.db.api.accessor.TXMLObjectAccessor;
import com.softwareag.tamino.db.api.accessor.TXQuery;
import com.softwareag.tamino.db.api.connection.TConnection;
import com.softwareag.tamino.db.api.objectModel.TXMLObjectModel;
import com.softwareag.tamino.db.api.objectModel.sax.TSAXElement;
import com.softwareag.tamino.db.api.objectModel.sax.TSAXElementDefaultHandler;
import com.softwareag.tamino.db.api.objectModel.sax.TSAXObjectModel;
import com.softwareag.tamino.db.api.response.TResponse;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.*;

import java.util.Iterator;

public class TaminoQueryProcessor extends TaminoProcessor {
    static private Logger logger = LoggerFactory.createLogger(TaminoQueryProcessor.class);

    public TaminoQueryProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, TAMINO_CONFIG_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, TAMINO_QUERY_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                try {
                    // Read configuration
                    final Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                        public Object read(PipelineContext context, ProcessorInput input) {
                            return readConfig(readInputAsDOM4J(context, INPUT_CONFIG));
                        }
                    });

                    // Read data
                    final Document queryDocument = readCacheInputAsDOM4J(context, INPUT_DATA);
                    final String query = XPathUtils.selectStringValueNormalize(queryDocument, "/query");
                    final String xquery = Dom4jUtils.objectToString(XPathUtils.selectObjectValue(queryDocument, "/xquery/text() | /xquery/*"));
                    final TConnection connection = getConnection(context, config);

                    final TaminoElementHandler handler = new TaminoElementHandler(contentHandler);
                    final TSAXObjectModel saxObjectModel = new TSAXObjectModel("SAXObjectModel", null, null, null, handler);
                    TXMLObjectModel.register(saxObjectModel);
                    final TXMLObjectAccessor accessor = connection.newXMLObjectAccessor(config.getCollection(), saxObjectModel);


                    final TResponse response;
                    if (query != null) {
                        if(logger.isDebugEnabled())
                            logger.debug("Executing X-Query: "+query);
                        handler.startDocument();
                        handler.startElement("", "result", "result", XMLUtils.EMPTY_ATTRIBUTES);
                        response = accessor.query(TQuery.newInstance(query));
                        handler.endElement("", "result", "result");
                        handler.endDocument();
                    } else if (xquery != null) {
                        if(logger.isDebugEnabled())
                            logger.debug("Executing XQuery: "+xquery);
                        handler.startDocument();
                        handler.startElement("", "result", "result", XMLUtils.EMPTY_ATTRIBUTES);
                        response = accessor.xquery(TXQuery.newInstance(xquery));
                        handler.endElement("", "result", "result");
                        handler.endDocument();
                    } else
                        throw new OXFException("Query or XQuery must be present");

                } catch (Exception e) {
                    throw new OXFException(e);
                } finally {
                    if(TXMLObjectModel.isRegistered("SAXObjectModel"))
                        TXMLObjectModel.deregister("SAXObjectModel");
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    private static class TaminoElementHandler extends TSAXElementDefaultHandler {

        protected ContentHandler contentHandler;
        protected boolean documentStarted = false;

        public TaminoElementHandler(ContentHandler ch) {
            this.contentHandler = ch;
        }

        public Iterator getElementIterator() {
            return null;
        }

        public TSAXElement getFirstElement() {
            return null;
        }

        public void characters(char ch[], int start, int length)
                throws SAXException {
            this.contentHandler.characters(ch, start, length);
        }

        public void endDocument()
                throws SAXException {
            this.contentHandler.endDocument();
            documentStarted = false;
        }

        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            this.contentHandler.endElement(uri, localName, qName);
        }

        public void endPrefixMapping(String prefix)
                throws SAXException {
            this.contentHandler.endPrefixMapping(prefix);
        }

        public void error(SAXParseException e)
                throws SAXException {
            throw new OXFException(e);
        }

        public void fatalError(SAXParseException e)
                throws SAXException {
            throw new OXFException(e);
        }

        public void ignorableWhitespace(char ch[], int start, int length)
                throws SAXException {
            this.contentHandler.ignorableWhitespace(ch, start, length);
        }

        public void notationDecl(String name, String publicId, String systemId)
                throws SAXException {

        }

        public void processingInstruction(String target, String data)
                throws SAXException {
            this.contentHandler.processingInstruction(target, data);
        }

        public InputSource resolveEntity(String publicId, String systemId)
                throws SAXException {
            return null;
        }

        public void setDocumentLocator(Locator locator) {
            this.contentHandler.setDocumentLocator(locator);
        }

        public void skippedEntity(String name)
                throws SAXException {
            this.contentHandler.skippedEntity(name);
        }

        public void startDocument()
                throws SAXException {
            this.contentHandler.startDocument();
            documentStarted = true;
        }

        public void startElement(String uri, String localName,
                                 String qName, Attributes attributes)
                throws SAXException {
            if(!documentStarted) {
                this.startDocument();
                documentStarted = true;
            }

            this.contentHandler.startElement(uri, localName, qName, attributes);
        }

        public void startPrefixMapping(String prefix, String uri)
                throws SAXException {
            this.contentHandler.startPrefixMapping(prefix, uri);
        }

        public void unparsedEntityDecl(String name, String publicId,
                                       String systemId, String notationName)
                throws SAXException {
        }

        public void warning(SAXParseException e)
                throws SAXException {
            throw new OXFException(e);
        }
    }
}
