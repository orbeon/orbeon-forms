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
package org.orbeon.oxf.processor.sql;

import org.apache.log4j.Logger;
import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.sql.interpreters.*;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.NamespaceSupport;

import java.util.*;

/**
 * This is the SQL processor implementation.
 * <p/>
 * TODO:
 * <p/>
 * o batch for mass updates when supported
 * o esql:use-limit-clause, esql:skip-rows, esql:max-rows
 * <p/>
 * o The position() and last() functions are not implemented within
 * sql:for-each it does not appear to be trivial to implement them, because
 * they are already defined by default. Probably that playing with the Jaxen
 * Context object will allow a correct implementation.
 * <p/>
 * o debugging facilities, i.e. output full query with replaced parameters (even on PreparedStatement)
 * o support more types in replace mode
 * <p/>
 * o sql:choose, sql:if
 * o sql:variable
 * o define variables such as:
 * o $sql:results (?)
 * o $sql:column-count
 * o $sql:update-count
 * o caching options
 */
public class SQLProcessor extends ProcessorImpl {

    public static Logger logger = LoggerFactory.createLogger(SQLProcessor.class);
    public static final String SQL_NAMESPACE_URI = "http://orbeon.org/oxf/xml/sql";

    private static final String INPUT_DATASOURCE = "datasource";
    public static final String SQL_DATASOURCE_URI = "http://www.orbeon.org/oxf/sql-datasource";

    public SQLProcessor() {
        // Mandatory config input
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, SQL_NAMESPACE_URI));

        // Optional datasource input
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATASOURCE, SQL_DATASOURCE_URI));

        // Optional data input and output
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        // For now don't declare it, because it causes problems
//        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        // This will be called only if there is an output
        ProcessorOutput output = new ProcessorOutputImpl(SQLProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                execute(context, xmlReceiver);
            }
        };
        addOutput(name, output);
        return output;
    }

    @Override
    public void start(PipelineContext context) {
        // This will be called only if no output is connected
        execute(context, new XMLReceiverAdapter());
    }

    private static class Config {
        public Config(SAXStore configInput, boolean useXPathExpressions, List xpathExpressions) {
            this.configInput = configInput;
            this.useXPathExpressions = useXPathExpressions;
            this.xpathExpressions = xpathExpressions;
        }

        public SAXStore configInput;
        public boolean useXPathExpressions;
        public List xpathExpressions;
    }

    protected void execute(final PipelineContext context, XMLReceiver xmlReceiver) {
        try {
            // Cache, read and interpret the config input
            Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                public Object read(PipelineContext context, ProcessorInput input) {
                    // Read the config input document
                    Node config = readInputAsDOM4J(context, input);
                    Document configDocument = config.getDocument();

                    // Extract XPath expressions and also check whether any XPath expression is used at all
                    // NOTE: This could be done through streaming below as well
                    // NOTE: For now, just match <sql:param select="/*" type="xs:base64Binary"/>
                    List xpathExpressions = new ArrayList();
                    boolean useXPathExpressions = false;
                    for (Iterator i = XPathUtils.selectIterator(configDocument, "//*[namespace-uri() = '" + SQL_NAMESPACE_URI + "' and @select]"); i.hasNext();) {
                        Element element = (Element) i.next();
                        useXPathExpressions = true;
                        String typeAttribute = element.attributeValue("type");
                        if ("xs:base64Binary".equals(typeAttribute)) {
                            String selectAttribute = element.attributeValue("select");
                            xpathExpressions.add(selectAttribute);
                        }
                    }

                    // Normalize spaces. What this does is to coalesce adjacent text nodes, and to remove
                    // resulting empty text, unless the text is contained within a sql:text element.
                    configDocument.accept(new VisitorSupport() {
                        private boolean endTextSequence(Element element, Text previousText) {
                            if (previousText != null) {
                                String value = previousText.getText();
                                if (value == null || value.trim().equals("")) {
                                    element.remove(previousText);
                                    return true;
                                }
                            }
                            return false;
                        }
                        @Override
                        public void visit(Element element) {
                            // Don't touch text within sql:text elements
                            if (!SQL_NAMESPACE_URI.equals(element.getNamespaceURI()) || !"text".equals(element.getName())) {
                                Text previousText = null;
                                for (int i = 0, size = element.nodeCount(); i < size;) {
                                    Node node = element.node(i);
                                    if (node instanceof Text) {
                                        Text text = (Text) node;
                                        if (previousText != null) {
                                            previousText.appendText(text.getText());
                                            element.remove(text);
                                        } else {
                                            String value = text.getText();
                                            // Remove empty text nodes
                                            if (value == null || value.length() < 1) {
                                                element.remove(text);
                                            } else {
                                                previousText = text;
                                                i++;
                                            }
                                        }
                                    } else {
                                        if (!endTextSequence(element, previousText))
                                            i++;
                                        previousText = null;
                                    }
                                }
                                endTextSequence(element, previousText);
                            }
                        }
                    });
                    // Create SAXStore
                    try {
                        final SAXStore store = new SAXStore();
                        final LocationSAXWriter locationSAXWriter = new LocationSAXWriter();
                        locationSAXWriter.setContentHandler(store);
                        locationSAXWriter.write(configDocument);
                        // Return the normalized document
                        return new Config(store, useXPathExpressions, xpathExpressions);
                    } catch (SAXException e) {
                        throw new OXFException(e);
                    }
                }
            });

            // Either read the whole input as a DOM, or try to serialize
            Node data = null;
            XPathContentHandler xpathContentHandler = null;

            // Check if the data input is connected
            boolean hasDataInput = getConnectedInputs().get(INPUT_DATA) != null;
            if (!hasDataInput && config.useXPathExpressions)
                throw new OXFException("The data input must be connected when the configuration uses XPath expressions.");
            if (!hasDataInput || !config.useXPathExpressions) {
                // Just use an empty document
                data = Dom4jUtils.NULL_DOCUMENT;
            } else {
                // There is a data input connected and there are some XPath epxressions operating on it
                boolean useXPathContentHandler = false;
                if (config.xpathExpressions.size() > 0) {
                    // Create XPath content handler
                    final XPathContentHandler _xpathContentHandler = new XPathContentHandler();
                    // Add expressions and check whether we can try to stream
                    useXPathContentHandler = true;
                    for (Iterator i = config.xpathExpressions.iterator(); i.hasNext();) {
                        String expression = (String) i.next();
                        boolean canStream = _xpathContentHandler.addExpresssion(expression, false);// FIXME: boolean nodeSet
                        if (!canStream) {
                            useXPathContentHandler = false;
                            break;
                        }
                    }
                    // Finish setting up the XPathContentHandler
                    if (useXPathContentHandler) {
                        _xpathContentHandler.setReadInputCallback(new Runnable() {
                            public void run() {
                                readInputAsSAX(context, INPUT_DATA, _xpathContentHandler);
                            }
                        });
                        xpathContentHandler = _xpathContentHandler;
                    }
                }
                // If we can't stream, read everything in
                if (!useXPathContentHandler)
                    data = readInputAsDOM4J(context, INPUT_DATA);
            }

            // Try to read datasource input if any
            Datasource datasource = null; {
                List datasourceInputs = (List) getConnectedInputs().get(INPUT_DATASOURCE);
                if (datasourceInputs != null) {
                    if (datasourceInputs.size() > 1)
                        throw new OXFException("At most one one datasource input can be connected.");
                    ProcessorInput datasourceInput = (ProcessorInput) datasourceInputs.get(0);
                    datasource = Datasource.getDatasource(context, this, datasourceInput);
                }
            }

            // Replay the config SAX store through the interpreter
            config.configInput.replay(new RootInterpreter(context, getPropertySet(), data, datasource, xpathContentHandler, xmlReceiver));
        } catch (OXFException e) {
            throw e;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static class RootInterpreter extends InterpreterContentHandler {

        private SQLProcessorInterpreterContext interpreterContext;
        private NamespaceSupport namespaceSupport = new NamespaceSupport();

        public RootInterpreter(PipelineContext context, PropertySet propertySet, Node input, Datasource datasource, XPathContentHandler xpathContentHandler, XMLReceiver output) {
            super(null, false);
            interpreterContext = new SQLProcessorInterpreterContext(propertySet);
            interpreterContext.setPipelineContext(context);
            interpreterContext.setInput(input);
            interpreterContext.setDatasource(datasource);
            interpreterContext.setXPathContentHandler(xpathContentHandler);
            interpreterContext.setOutput(new DeferredXMLReceiverImpl(output));
            interpreterContext.setNamespaceSupport(namespaceSupport);
            addElementHandler(new ConfigInterpreter(interpreterContext), SQL_NAMESPACE_URI, "config");
        }

        @Override
        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            try {
                namespaceSupport.pushContext();
                super.startElement(uri, localname, qName, attributes);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public void endElement(String uri, String localname, String qName) throws SAXException {
            try {
                super.endElement(uri, localname, qName);
                namespaceSupport.popContext();
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            try {
                super.startPrefixMapping(prefix, uri);
                interpreterContext.declarePrefix(prefix, uri);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public void characters(char[] chars, int start, int length) throws SAXException {
            try {
                super.characters(chars, start, length);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public void endDocument() throws SAXException {
            try {
                super.endDocument();
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
            try {
                super.ignorableWhitespace(chars, start, length);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public void processingInstruction(String s, String s1) throws SAXException {
            try {
                super.processingInstruction(s, s1);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public void skippedEntity(String s) throws SAXException {
            try {
                super.skippedEntity(s);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public void startDocument() throws SAXException {
            try {
                super.startDocument();
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public void endPrefixMapping(String s) throws SAXException {
            try {
                super.endPrefixMapping(s);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public Locator getDocumentLocator() {
            try {
                return super.getDocumentLocator();
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            try {
                super.setDocumentLocator(locator);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        private void dispose() {
            // NOTE: Don't do this anymore: the connection will be closed when the context is destroyed
        }
    }

    public static class InterpreterContentHandler extends ForwardingContentHandler {

        private SQLProcessorInterpreterContext interpreterContext;
        private Locator documentLocator;

        private boolean forward;
        private boolean repeating;
        private SAXStore saxStore;
        private DeferredXMLReceiver savedOutput;
        private Attributes savedAttributes;

        private Map elementHandlers = new HashMap();
        private int forwardingLevel = -1;
        private InterpreterContentHandler currentHandler;
        private int level = 0;
        private String currentKey;

        /**
         *
         *
         * @param interpreterContext    current SQLProcessorInterpreterContext
         * @param repeating             set this to true if the body of this handler will be repeated
         */
        public InterpreterContentHandler(SQLProcessorInterpreterContext interpreterContext, boolean repeating) {
            this.interpreterContext = interpreterContext;
            this.repeating = repeating;
        }

        public void addElementHandler(InterpreterContentHandler handler, String uri, String localname) {
            elementHandlers.put("{" + uri + "}" + localname, handler);
        }

        public void addAllDefaultElementHandlers() {

            addElementHandler(new ConnectionInterpreter(interpreterContext), SQLProcessor.SQL_NAMESPACE_URI, "connection");
            addElementHandler(new DatasourceInterpreter(interpreterContext), SQLProcessor.SQL_NAMESPACE_URI, "datasource");

            addElementHandler(new ExecuteInterpreter(interpreterContext), SQLProcessor.SQL_NAMESPACE_URI, "execute");
            final GetterInterpreter getterInterpreter = new GetterInterpreter(interpreterContext);
            // Legacy getters
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-string");
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-int");
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-double");
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-decimal");
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-date");
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-timestamp");

            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-column-value");
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-column-type");
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-column-name");
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-column-index");
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-column");
            addElementHandler(getterInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "get-columns");

            addElementHandler(new TextInterpreter(interpreterContext), SQLProcessor.SQL_NAMESPACE_URI, "text");
            addElementHandler(new ColumnIteratorInterpreter(getInterpreterContext()), SQLProcessor.SQL_NAMESPACE_URI, "column-iterator");
            addElementHandler(new ForEachInterpreter(getInterpreterContext()), SQLProcessor.SQL_NAMESPACE_URI, "for-each");

            addElementHandler(new ExecuteInterpreter(getInterpreterContext()), SQLProcessor.SQL_NAMESPACE_URI, "execute");
            addElementHandler(new QueryInterpreter(interpreterContext, QueryInterpreter.QUERY), SQLProcessor.SQL_NAMESPACE_URI, "query");
            addElementHandler(new QueryInterpreter(interpreterContext, QueryInterpreter.UPDATE), SQLProcessor.SQL_NAMESPACE_URI, "update");
            addElementHandler(new QueryInterpreter(interpreterContext, QueryInterpreter.CALL), SQLProcessor.SQL_NAMESPACE_URI, "call");

            final ResultSetInterpreter resultSetInterpreter = new ResultSetInterpreter(interpreterContext);
            addElementHandler(resultSetInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "results");
            addElementHandler(resultSetInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "result-set");
            addElementHandler(new NoResultsInterpreter(interpreterContext), SQLProcessor.SQL_NAMESPACE_URI, "no-results");

            final RowIteratorInterpreter rowIteratorInterpreter = new RowIteratorInterpreter(getInterpreterContext());
            addElementHandler(rowIteratorInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "row-results");
            addElementHandler(rowIteratorInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "row-iterator");

            final ValueOfCopyOfInterpreter valueOfCopyOfInterpreter = new ValueOfCopyOfInterpreter(interpreterContext);
            addElementHandler(valueOfCopyOfInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "value-of");
            addElementHandler(valueOfCopyOfInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "copy-of");

            addElementHandler(new AttributeInterpreter(interpreterContext), SQLProcessor.SQL_NAMESPACE_URI, "attribute");
        }

        @Override
        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            if (forwardingLevel == -1 && elementHandlers.size() > 0) {
                final String key = "{" + uri + "}" + localname;
                final InterpreterContentHandler elementHandler = (InterpreterContentHandler) elementHandlers.get(key);
                if (elementHandler != null) {
                    // Found element handler
                    forwardingLevel = level;
                    currentKey = key;
                    if (elementHandler.isRepeating()) {
                        // Remember SAX content of the element body
                        savedOutput = getInterpreterContext().getOutput();
                        savedAttributes = new AttributesImpl(attributes);
                        elementHandler.saxStore = new SAXStore();
                        elementHandler.saxStore.setDocumentLocator(documentLocator);
                        getInterpreterContext().setOutput(new DeferredXMLReceiverImpl(elementHandler.saxStore));
                    } else {
                        // Notify start of element
                        currentHandler = elementHandler;
                        elementHandler.setDocumentLocator(documentLocator);
                        elementHandler.start(uri, localname, qName, attributes);
                    }
                } else
                    super.startElement(uri, localname, qName, attributes);
            } else
                super.startElement(uri, localname, qName, attributes);
            level++;
        }

        @Override
        public void endElement(String uri, String localname, String qName) throws SAXException {
            level--;
            if (forwardingLevel == level) {
                final String key = "{" + uri + "}" + localname;
                if (!currentKey.equals(key))
                    throw new ValidationException("Illegal document: expecting " + key + ", got " + currentKey, new LocationData(getDocumentLocator()));

                final InterpreterContentHandler elementHandler = (InterpreterContentHandler) elementHandlers.get(key);
                if (elementHandler.isRepeating()) {
                    // Restore output
                    interpreterContext.setOutput(savedOutput);
                    savedOutput = null;

                    // Notify start of element
                    currentHandler = elementHandler;
                    elementHandler.setDocumentLocator(documentLocator);
                    elementHandler.start(uri, localname, qName, savedAttributes);

                    // Restore state
                    savedAttributes = null;
                    elementHandler.saxStore = null;
                }

                // Notify end of element
                forwardingLevel = -1;
                currentKey = null;
                currentHandler = null;
                elementHandler.end(uri, localname, qName);
            } else
                super.endElement(uri, localname, qName);
        }

        protected void repeatBody() throws SAXException {
            if (!repeating)
                throw new IllegalStateException("repeatBody() can only be called when repeating is true.");

            saxStore.replay(this);
        }

        public boolean isRepeating() {
            return repeating;
        }

        protected void setForward(boolean forward) {
            this.forward = forward;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.documentLocator = locator;
            super.setDocumentLocator(locator);
        }

        public Locator getDocumentLocator() {
            return documentLocator;
        }

        @Override
        public void startPrefixMapping(String s, String s1) throws SAXException {
            super.startPrefixMapping(s, s1);
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        }

        public void end(String uri, String localname, String qName) throws SAXException {
        }

        protected boolean isInElementHandler() {
            return forwardingLevel > -1;
        }

        protected ContentHandler getContentHandler() {
            // The purpose of the code below is to determine whether SAX events are sent to an
            // element handler or directly to the output
            if (currentHandler != null)
                return currentHandler;
            else if (forward)
                return interpreterContext.getOutput();
            else
                return null;
        }

        @Override
        public void characters(char[] chars, int start, int length) throws SAXException {
            if (currentHandler == null) {
                // Output only if the string is non-blank [FIXME: Incorrect white space handling!]
//                String s = new String(chars, start, length);
//                if (!s.trim().equals(""))
                super.characters(chars, start, length);
            } else {
                super.characters(chars, start, length);
            }
        }

        public SQLProcessorInterpreterContext getInterpreterContext() {
            return interpreterContext;
        }
    }

    public static abstract class ForwardingContentHandler implements XMLReceiver {

        public ForwardingContentHandler() {
        }

        protected abstract ContentHandler getContentHandler();

        public void characters(char[] chars, int start, int length) throws SAXException {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.characters(chars, start, length);
        }

        public void endDocument() throws SAXException {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.endDocument();
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.endElement(uri, localname, qName);
        }

        public void endPrefixMapping(String s) throws SAXException {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.endPrefixMapping(s);
        }

        public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.ignorableWhitespace(chars, start, length);
        }

        public void processingInstruction(String s, String s1) throws SAXException {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.processingInstruction(s, s1);
        }

        public void setDocumentLocator(Locator locator) {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.setDocumentLocator(locator);
        }

        public void skippedEntity(String s) throws SAXException {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.skippedEntity(s);
        }

        public void startDocument() throws SAXException {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.startDocument();
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.startElement(uri, localname, qName, attributes);
        }

        public void startPrefixMapping(String s, String s1) throws SAXException {
            final ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.startPrefixMapping(s, s1);
        }

        // Ignore LexicalHandler methods as we don't plan to do anything useful with them

        public void startDTD(String name, String publicId, String systemId) throws SAXException {
        }

        public void endDTD() throws SAXException {
        }

        public void startEntity(String name) throws SAXException {
        }

        public void endEntity(String name) throws SAXException {
        }

        public void startCDATA() throws SAXException {
        }

        public void endCDATA() throws SAXException {
        }

        public void comment(char[] ch, int start, int length) throws SAXException {
        }
    }
}
