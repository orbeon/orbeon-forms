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
package org.orbeon.oxf.processor.sql;

import org.apache.log4j.Logger;
import org.dom4j.*;
import org.jaxen.Function;
import org.jaxen.UnresolvableException;
import org.jaxen.VariableContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.xpath.*;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.NamespaceSupport;

import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
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
 * <p/>
 * o esql:error-results//esql:get-message
 * o esql:error-results//esql:to-string
 * o esql:error-results//esql:get-stacktrace
 * <p/>
 * o multiple results
 * o caching options
 */
public class SQLProcessor extends ProcessorImpl {

    static Logger logger = LoggerFactory.createLogger(SQLProcessor.class);
    public static final String SQL_NAMESPACE_URI = "http://orbeon.org/oxf/xml/sql";

    private static final String INPUT_DATASOURCE = "datasource";
    public static final String SQL_DATASOURCE_URI = "http://www.orbeon.org/oxf/sql-datasource";

//    private static final String SQL_TYPE_VARCHAR = "varchar";
    private static final String SQL_TYPE_CLOB = "clob";
    private static final String SQL_TYPE_BLOB = "blob";
    private static final String SQL_TYPE_XMLTYPE = "xmltype";

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

    public ProcessorOutput createOutput(String name) {
        // This will be called only if there is an output
        ProcessorOutput output = new ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                execute(context, contentHandler);
            }
        };
        addOutput(name, output);
        return output;
    }

    public void start(PipelineContext context) {
        // This will be called only if no output is connected
        execute(context, new NullSerializer.NullContentHandler());
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

    protected void execute(final PipelineContext context, ContentHandler contentHandler) {
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
                        SAXStore store = new SAXStore();
                        LocationSAXWriter saxw = new LocationSAXWriter();
                        saxw.setContentHandler(store);
                        saxw.write(configDocument);
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
                data = XMLUtils.NULL_DOCUMENT;
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
            config.configInput.replay(new RootInterpreter(context, getPropertySet(), data, datasource, xpathContentHandler, contentHandler));
        } catch (OXFException e) {
            throw e;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static class ExecuteInterpreter extends InterpreterContentHandler {

        public ExecuteInterpreter(SQLProcessorInterpreterContext interpreterContext) {
            super(interpreterContext, true);
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
            interpreterContext.pushResultSet();
            addElementHandler(new QueryInterpreter(interpreterContext, QueryInterpreter.QUERY), SQL_NAMESPACE_URI, "query");
            addElementHandler(new QueryInterpreter(interpreterContext, QueryInterpreter.UPDATE), SQL_NAMESPACE_URI, "update");
            addElementHandler(new ResultsInterpreter(interpreterContext), SQL_NAMESPACE_URI, "results");
            addElementHandler(new NoResultsInterpreter(interpreterContext), SQL_NAMESPACE_URI, "no-results");
            ValueOfCopyOfInterpreter valueOfCopyOfInterpreter = new ValueOfCopyOfInterpreter(interpreterContext);
            addElementHandler(valueOfCopyOfInterpreter, SQL_NAMESPACE_URI, "value-of");
            addElementHandler(valueOfCopyOfInterpreter, SQL_NAMESPACE_URI, "copy-of");
            addElementHandler(new TextInterpreter(interpreterContext), SQL_NAMESPACE_URI, "text");
            addElementHandler(new ForEachInterpreter(getInterpreterContext(), getElementHandlers()), SQL_NAMESPACE_URI, "for-each");
        }

        public void end(String uri, String localname, String qName) throws SAXException {
            SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
            PreparedStatement stmt = interpreterContext.getStatement(0);
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    throw new ValidationException(e, new LocationData(getDocumentLocator()));
                }
            }
            interpreterContext.popResultSet();
        }
    }

    private static class ResultsInterpreter extends InterpreterContentHandler {

        public ResultsInterpreter(SQLProcessorInterpreterContext interpreterContext) {
            super(interpreterContext, false);
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            if (!getInterpreterContext().isEmptyResultSet()) {
                setForward(true);
                addElementHandler(new RowResultsInterpreter(getInterpreterContext()), SQL_NAMESPACE_URI, "row-results");
                ValueOfCopyOfInterpreter valueOfCopyOfInterpreter = new ValueOfCopyOfInterpreter(getInterpreterContext());
                addElementHandler(valueOfCopyOfInterpreter, SQL_NAMESPACE_URI, "value-of");
                addElementHandler(valueOfCopyOfInterpreter, SQL_NAMESPACE_URI, "copy-of");
                addElementHandler(new TextInterpreter(getInterpreterContext()), SQL_NAMESPACE_URI, "text");
                // We must not be able to have a RowResultsInterpreter within the for-each
                // This must be checked in the schema
                addElementHandler(new ForEachInterpreter(getInterpreterContext(), getElementHandlers()), SQL_NAMESPACE_URI, "for-each");
            }
        }

        public void end(String uri, String localname, String qName) throws SAXException {
            SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
            PreparedStatement stmt = interpreterContext.getStatement(0);
            if (stmt != null) {
                try {
                    stmt.close();
                    interpreterContext.setStatement(null);
                } catch (SQLException e) {
                    throw new ValidationException(e, new LocationData(getDocumentLocator()));
                }
            }
        }
    }

    private static class GetterInterpreter extends InterpreterContentHandler {
        private ResultSetMetaData metadata;

        public GetterInterpreter(SQLProcessorInterpreterContext interpreterContext) {
            super(interpreterContext, false);
        }

        /**
         * Return a Clob or Clob object or a String
         */
        public static Object interpretSimpleGetter(SQLProcessorInterpreterContext interpreterContext, Locator locator,
                                                   String getterName, String columnName, int level) {
            try {
                ResultSet resultSet = interpreterContext.getResultSet(level);
                int columnType = resultSet.getMetaData().getColumnType(resultSet.findColumn(columnName));

                if (columnType == Types.CLOB) {
                    // The actual column is a CLOB
                    if ("get-string".equals(getterName)) {
                        return resultSet.getClob(columnName);
                    } else {
                        throw new ValidationException("Illegal getter type for CLOB: " + getterName, new LocationData(locator));
                    }
                } else if (columnType == Types.BLOB) {
                    // The actual column is a BLOB
                    if ("get-base64binary".equals(getterName)) {
                        return resultSet.getBlob(columnName);
                    } else {
                        throw new ValidationException("Illegal getter type for BLOB: " + getterName, new LocationData(locator));
                    }
                } else if (columnType == Types.BINARY || columnType == Types.VARBINARY || columnType == Types.LONGVARBINARY) {
                    // The actual column is binary
                    if ("get-base64binary".equals(getterName)) {
                        return resultSet.getBinaryStream(columnName);
                    } else {
                        throw new ValidationException("Illegal getter type for BINARY, VARBINARY or LONGVARBINARY column: " + getterName, new LocationData(locator));
                    }
                } else {
                    // The actual column is not a CLOB or BLOB, in which case we use regular ResultSet getters
                    if ("get-string".equals(getterName)) {
                        return resultSet.getString(columnName);
                    } else if ("get-int".equals(getterName)) {
                        int value = resultSet.getInt(columnName);
                        if (resultSet.wasNull()) return null;
                        return Integer.toString(value);
                    } else if ("get-double".equals(getterName)) {
                        double value = resultSet.getDouble(columnName);
                        // For XPath 1.0, we have to get rid of the scientific notation
                        return resultSet.wasNull() ? null : XMLUtils.removeScientificNotation(value);
                    } else if ("get-float".equals(getterName)) {
                        float value = resultSet.getFloat(columnName);
                        // For XPath 1.0, we have to get rid of the scientific notation
                        return resultSet.wasNull() ? null : XMLUtils.removeScientificNotation(value);
                    } else if ("get-decimal".equals(getterName)) {
                        BigDecimal value = resultSet.getBigDecimal(columnName);
                        return (resultSet.wasNull()) ? null : value.toString();
                    } else if ("get-boolean".equals(getterName)) {
                        boolean value = resultSet.getBoolean(columnName);
                        if (resultSet.wasNull()) return null;
                        return value ? "true" : "false";
                    } else if ("get-date".equals(getterName)) {
                        Date value = resultSet.getDate(columnName);
                        if (value == null) return null;
                        return ISODateUtils.formatDate(value, ISODateUtils.XS_DATE);
                    } else if ("get-timestamp".equals(getterName)) {
                        Timestamp value = resultSet.getTimestamp(columnName);
                        if (value == null) return null;
                        return ISODateUtils.formatDate(value, ISODateUtils.XS_DATE_TIME_LONG);
                    } else {
                        throw new ValidationException("Illegal getter name: " + getterName, new LocationData(locator));
                    }
                }
            } catch (SQLException e) {
                throw new ValidationException("Exception while getting column: " + columnName + " at " + e.toString() , new LocationData(locator));
            }
        }

        private static final Map typesToGetterNames = new HashMap();

        static {
            typesToGetterNames.put("xs:string", "get-string");
            typesToGetterNames.put("xs:int", "get-int");
            typesToGetterNames.put("xs:boolean", "get-boolean");
            typesToGetterNames.put("xs:decimal", "get-decimal");
            typesToGetterNames.put("xs:float", "get-float");
            typesToGetterNames.put("xs:double", "get-double");
            typesToGetterNames.put("xs:dateTime", "get-timestamp");
            typesToGetterNames.put("xs:date", "get-date");
            typesToGetterNames.put("xs:base64Binary", "get-base64binary");
        }

        /**
         * Return a Clob or Blob object or a String
         */
        public static Object interpretGenericGetter(SQLProcessorInterpreterContext interpreterContext, Locator locator, String type, String columnName, int level) {
            String getterName = (String) typesToGetterNames.get(type);
            if (getterName == null)
                throw new ValidationException("Incorrect or missing type attribute for sql:get-column: " + type, new LocationData(locator));
            return interpretSimpleGetter(interpreterContext, locator, getterName, columnName, level);
        }

        private int getColumnsLevel;
        private String getColumnsFormat;
        private String getColumnsPrefix;
        private boolean getColumnsAllElements;
        private boolean inExclude;
        private StringBuffer getColumnsCurrentExclude;
        private Map getColumnsExcludes;

        public void characters(char[] chars, int start, int length) throws SAXException {
            if (inExclude)
                getColumnsCurrentExclude.append(chars, start, length);
            else
                super.characters(chars, start, length);
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
            interpreterContext.getNamespaceSupport().pushContext();
            try {
                String levelString = attributes.getValue("ancestor");
                int level = (levelString == null) ? 0 : Integer.parseInt(levelString);
                ResultSet rs = interpreterContext.getResultSet(level);
                if ("get-columns".equals(localname)) {
                    // Remember attributes
                    getColumnsLevel = level;
                    getColumnsFormat = attributes.getValue("format");
                    getColumnsPrefix = attributes.getValue("prefix");
                    getColumnsAllElements = "true".equals(attributes.getValue("all-elements"));
                } else if ("get-column".equals(localname)) {
                    String type = attributes.getValue("type");
                    String columnName = attributes.getValue("column");

                    if ("oxf:xmlFragment".equals(type)) {
                        // XML fragment
                        if (metadata == null)
                            metadata = rs.getMetaData();
                        int columnIndex = rs.findColumn(columnName);
                        int columnType = metadata.getColumnType(columnIndex);
                        String columnTypeName = metadata.getColumnTypeName(columnIndex);
                        if (columnType == Types.CLOB) {
                            // The fragment is stored as a Clob
                            Clob clob = rs.getClob(columnName);
                            if (clob != null) {
                                Reader reader = clob.getCharacterStream();
                                try {
                                    XMLUtils.parseDocumentFragment(reader, interpreterContext.getOutput());
                                } finally {
                                    reader.close();
                                }
                            }
                        } else if (interpreterContext.getDelegate().isXMLType(columnType, columnTypeName)) {
                            // The fragment is stored as a native XMLType
                            org.w3c.dom.Node node = interpreterContext.getDelegate().getDOM(rs, columnName);
                            if (node != null) {
                                TransformerUtils.getIdentityTransformer().transform(new DOMSource(node),
                                        new SAXResult(new ForwardingContentHandler() {
                                            protected ContentHandler getContentHandler() {
                                                return interpreterContext.getOutput();
                                            }

                                            public void endDocument() {
                                            }

                                            public void startDocument() {
                                            }
                                        }));
                            }
                        } else {
                            // The fragment is stored as a String
                            String value = rs.getString(columnName);
                            if (value != null)
                                XMLUtils.parseDocumentFragment(value, interpreterContext.getOutput());
                        }
                    } else {
                        // xs:*
                        Object o = interpretGenericGetter(interpreterContext, getDocumentLocator(), type, columnName, level);
                        if (o != null) {
                            if (o instanceof Clob) {
                                Reader reader = ((Clob) o).getCharacterStream();
                                try {
                                    XMLUtils.readerToCharacters(reader, interpreterContext.getOutput());
                                } finally {
                                    reader.close();
                                }
                            } else if (o instanceof Blob) {
                                InputStream is = ((Blob) o).getBinaryStream();
                                try {
                                    XMLUtils.inputStreamToBase64Characters(is, interpreterContext.getOutput());
                                } finally {
                                    is.close();
                                }
                            } else if (o instanceof InputStream) {
                                InputStream is = (InputStream) o;
                                try {
                                    XMLUtils.inputStreamToBase64Characters(is, interpreterContext.getOutput());
                                } finally {
                                    is.close();
                                }
                            } else {
                                XMLUtils.objectToCharacters(o, interpreterContext.getOutput());
                            }
                        }
                    }
                } else {
                    // Simple getter (deprecated)
                    Object o = interpretSimpleGetter(interpreterContext, getDocumentLocator(), localname, attributes.getValue("column"), level);
                    if (o != null) {
                        if (o instanceof Clob) {
                            Reader reader = ((Clob) o).getCharacterStream();
                            try {
                                XMLUtils.readerToCharacters(reader, interpreterContext.getOutput());
                            } finally {
                                reader.close();
                            }
                        } else if (o instanceof Blob) {
                            InputStream is = ((Blob) o).getBinaryStream();
                            try {
                                XMLUtils.inputStreamToBase64Characters(is, interpreterContext.getOutput());
                            } finally {
                                is.close();
                            }
                        } else if (o instanceof InputStream) {
                            InputStream is = (InputStream) o;
                            try {
                                XMLUtils.inputStreamToBase64Characters(is, interpreterContext.getOutput());
                            } finally {
                                is.close();
                            }
                        } else {
                            XMLUtils.objectToCharacters(o, interpreterContext.getOutput());
                        }
                    }
                }
            } catch (Exception e) {
                throw new ValidationException(e, new LocationData(getDocumentLocator()));
            }
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            if ("exclude".equals(localname)) {
                // Collect excludes
                if (getColumnsExcludes == null)
                    getColumnsExcludes = new HashMap();
                getColumnsCurrentExclude = new StringBuffer();
                inExclude = true;
            }
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {
            if ("exclude".equals(localname)) {
                // Add current exclude
                String value = getColumnsCurrentExclude.toString().toLowerCase();
                getColumnsExcludes.put(value, value);
                inExclude = false;
            }
        }

        public void end(String uri, String localname, String qName) throws SAXException {
            SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
            try {
                if ("get-columns".equals(localname)) {
                    // Do nothing except collect sql:exclude
                    ResultSet resultSet = interpreterContext.getResultSet(getColumnsLevel);
                    if (metadata == null)
                        metadata = resultSet.getMetaData();

                    NamespaceSupport namespaceSupport = interpreterContext.getNamespaceSupport();
//                                        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
//                                            String p = (String) e.nextElement();
//                                            String u = namespaceSupport.getURI(p);
//                                            System.out.println("Prefix: " + p + " -> " + u);
//                                        }

                    // Get format once for all columns

                    // Get URI once for all columns
                    String outputElementURI = (getColumnsPrefix == null) ? "" : namespaceSupport.getURI(getColumnsPrefix);
                    if (outputElementURI == null)
                        throw new ValidationException("Invalid namespace prefix: " + getColumnsPrefix, new LocationData(getDocumentLocator()));

                    // Iterate through all columns
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        // Get column name
                        String columnName = metadata.getColumnName(i);
                        // Make sure it is not excluded
                        if (getColumnsExcludes != null && getColumnsExcludes.get(columnName.toLowerCase()) != null)
                            continue;
                        // Process column
                        int columnType = metadata.getColumnType(i);
                        String stringValue = null;
                        Clob clobValue = null;
                        Blob blobValue = null;
                        if (columnType == Types.DATE) {
                            Date value = resultSet.getDate(i);
                            if (value != null)
                                stringValue = ISODateUtils.formatDate(value, ISODateUtils.XS_DATE);
                        } else if (columnType == Types.TIMESTAMP) {
                            Timestamp value = resultSet.getTimestamp(i);
                            if (value != null)
                                stringValue = ISODateUtils.formatDate(value, ISODateUtils.XS_DATE_TIME_LONG);
                        } else if (columnType == Types.DECIMAL
                                || columnType == Types.NUMERIC) {
                            BigDecimal value = resultSet.getBigDecimal(i);
                            stringValue = (resultSet.wasNull()) ? null : value.toString();
                        } else if (columnType == 16) {// Types.BOOLEAN is present from JDK 1.4 only
                            boolean value = resultSet.getBoolean(i);
                            stringValue = (resultSet.wasNull()) ? null : (value ? "true" : "false");
                        } else if (columnType == Types.INTEGER
                                || columnType == Types.SMALLINT
                                || columnType == Types.TINYINT
                                || columnType == Types.BIGINT) {
                            long value = resultSet.getLong(i);
                            stringValue = (resultSet.wasNull()) ? null : Long.toString(value);
                        } else if (columnType == Types.DOUBLE
                                || columnType == Types.FLOAT
                                || columnType == Types.REAL) {
                            double value = resultSet.getDouble(i);
                            // For XPath 1.0, we have to get rid of the scientific notation
                            stringValue = resultSet.wasNull() ? null : XMLUtils.removeScientificNotation(value);
                        } else if (columnType == Types.CLOB) {
                            clobValue = resultSet.getClob(i);
                        } else if (columnType == Types.BLOB) {
                            blobValue = resultSet.getBlob(i);
                        } else {
                            // Assume the type is compatible with getString()
                            stringValue = resultSet.getString(i);
                        }
                        final boolean nonNullValue = stringValue != null || clobValue != null || blobValue != null;
                        if (nonNullValue || getColumnsAllElements) {
                            // Format element name
                            String elementName = columnName;
                            if ("xml".equals(getColumnsFormat)) {
                                elementName = elementName.toLowerCase();
                                elementName = elementName.replace('_', '-');
                            } else if (getColumnsFormat != null)
                                throw new ValidationException("Invalid get-columns format: " + getColumnsFormat, new LocationData(getDocumentLocator()));
                            String elementQName = (outputElementURI.equals("")) ? elementName : getColumnsPrefix + ":" + elementName;
                            ContentHandler output = interpreterContext.getOutput();
                            output.startElement(outputElementURI, elementName, elementQName, XMLUtils.EMPTY_ATTRIBUTES);
                            // Output value if non-null
                            if (nonNullValue) {
                                if (clobValue == null && blobValue == null) {
                                    // Just output the String value as characters
                                    char[] localCharValue = stringValue.toCharArray();
                                    output.characters(localCharValue, 0, localCharValue.length);
                                } else if (clobValue != null) {
                                    // Clob: convert the Reader into characters
                                    Reader reader = clobValue.getCharacterStream();
                                    try {
                                        XMLUtils.readerToCharacters(reader, output);
                                    } finally {
                                        reader.close();
                                    }
                                } else {
                                    // Blob: convert the InputStream into characters in Base64
                                    InputStream is = blobValue.getBinaryStream();
                                    try {
                                        XMLUtils.inputStreamToBase64Characters(is, output);
                                    } finally {
                                        is.close();
                                    }
                                }
                            }
                            output.endElement(outputElementURI, elementName, elementQName);
                        }
                    }
                }
            } catch (Exception e) {
                throw new ValidationException(e, new LocationData(getDocumentLocator()));
            }
            interpreterContext.getNamespaceSupport().popContext();
        }
    }

    private static class ValueOfCopyOfInterpreter extends InterpreterContentHandler {
        DocumentWrapper wrapper;

        public ValueOfCopyOfInterpreter(SQLProcessorInterpreterContext interpreterContext) {
            super(interpreterContext, false);
            this.wrapper = new DocumentWrapper(interpreterContext.getCurrentNode().getDocument(), null);
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
            NamespaceSupport namespaceSupport = interpreterContext.getNamespaceSupport();
            ContentHandler output = interpreterContext.getOutput();

            namespaceSupport.pushContext();
            try {
                String selectString = attributes.getValue("select");

                // Variable context (obsolete)
                VariableContext variableContext = new VariableContext() {
                    public Object getVariableValue(String namespaceURI, String prefix, String localName) throws UnresolvableException {
                        if (!SQL_NAMESPACE_URI.equals(namespaceURI))
                            throw new UnresolvableException("Unbound variable: {" + namespaceURI + "}" + localName);
                        if ("row-position".equals(localName)) {
                            return new Integer(interpreterContext.getRowPosition());
                        } else
                            throw new UnresolvableException("Unbound variable: {" + namespaceURI + "}" + localName);
                    }
                };

                // Interpret expression
                Object result = XPathUtils.selectObjectValue(interpreterContext.getCurrentNode(), selectString,
                        interpreterContext.getPrefixesMap(), variableContext, interpreterContext.getFunctionContext());

                if ("value-of".equals(localname) || "copy-of".equals(localname)) {
                    // Case of Number and String
                    if (result instanceof Number) {
                        String stringValue;
                        if (result instanceof Float || result instanceof Double) {
                            stringValue = XMLUtils.removeScientificNotation(((Number) result).doubleValue());
                        } else {
                            stringValue = Long.toString(((Number) result).longValue());
                        } // FIXME: what about BigDecimal and BigInteger: can they be returned?
                        output.characters(stringValue.toCharArray(), 0, stringValue.length());
                    } else if (result instanceof String) {
                        String stringValue = (String) result;
                        output.characters(stringValue.toCharArray(), 0, stringValue.length());
                    } else if (result instanceof List) {
                        if ("value-of".equals(localname)) {
                            // Get string value
//                            String stringValue = interpreterContext.getInput().createXPath(".").valueOf(result);
//                            String stringValue = XPathCache.createCacheXPath(null, ".").valueOf(result);
                            PooledXPathExpression expr = XPathCache.getXPathExpression(interpreterContext.getPipelineContext(),
                                    wrapper.wrap(result), "string(.)");
                            String stringValue;
                            try {
                                stringValue = (String)expr.evaluateSingle();
                            } catch (XPathException e) {
                                throw new OXFException(e);
                            } finally {
                                if(expr != null)
                                    expr.returnToPool();
                            }
                            output.characters(stringValue.toCharArray(), 0, stringValue.length());
                        } else {
                            LocationSAXWriter saxw = new LocationSAXWriter();
                            saxw.setContentHandler(output);
                            for (Iterator i = ((List) result).iterator(); i.hasNext();) {
                                Node node = (Node) i.next();
                                saxw.write(node);
                            }
                        }
                    } else if (result instanceof Node) {
                        if ("value-of".equals(localname)) {
                            // Get string value
//                            String stringValue = interpreterContext.getInput().createXPath(".").valueOf(result);
//                            String stringValue = XPathCache.createCacheXPath(null, ".").valueOf(result);
                              PooledXPathExpression expr = XPathCache.getXPathExpression(interpreterContext.getPipelineContext(),
                                      wrapper.wrap(result), "string(.)");
                            String stringValue;
                            try {
                                stringValue = (String)expr.evaluateSingle();
                            } catch (XPathException e) {
                                throw new OXFException(e);
                            } finally {
                                if(expr != null)
                                    expr.returnToPool();
                            }
                            output.characters(stringValue.toCharArray(), 0, stringValue.length());
                        } else {
                            LocationSAXWriter saxw = new LocationSAXWriter();
                            saxw.setContentHandler(output);
                            saxw.write((Node) result);
                        }
                    } else
                        throw new OXFException("Unexpected XPath result type: " + result.getClass());
                } else {
                    throw new OXFException("Invalid element: " + qName);
                }

            } catch (Exception e) {
                throw new ValidationException(e, new LocationData(getDocumentLocator()));
            }
        }

        public void end(String uri, String localname, String qName) throws SAXException {
            getInterpreterContext().getNamespaceSupport().popContext();
        }
    }

    private static class RowResultsInterpreter extends InterpreterContentHandler {

        private SAXStore saxStore;
        private ContentHandler savedOutput;

        private class Group {
            private String columnName;
            private String columnValue;
            private SAXStore footer = new SAXStore();
            private boolean showHeader;

            public Group(String columnName, ResultSet resultSet) throws SQLException {
                this.columnName = columnName;
                this.columnValue = resultSet.getString(columnName);
            }

            public boolean columnChanged(ResultSet resultSet) throws SQLException {
                String newValue = resultSet.getString(columnName);
                return (columnValue != null && !columnValue.equals(newValue)) || (newValue != null && !newValue.equals(columnValue));
            }

            public void setColumnValue(ResultSet resultSet) throws SQLException {
                this.columnValue = resultSet.getString(columnName);
            }

            public boolean isShowHeader() {
                return showHeader;
            }

            public void setShowHeader(boolean showHeader) {
                this.showHeader = showHeader;
            }

            public SAXStore getFooter() {
                return footer;
            }
        }

        public RowResultsInterpreter(SQLProcessorInterpreterContext interpreterContext) {
            super(interpreterContext, false);
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            // Only forward if the result set is not empty
            if (!getInterpreterContext().isEmptyResultSet()) {
                saxStore = new SAXStore();
                saxStore.setDocumentLocator(getDocumentLocator());
                savedOutput = getInterpreterContext().getOutput();
                getInterpreterContext().setOutput(saxStore);
                setForward(true);
            }
        }

        public void end(String uri, String localname, String qName) throws SAXException {
            if (saxStore != null) {
                final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
                interpreterContext.setOutput(savedOutput);

                final ResultSet resultSet = interpreterContext.getResultSet(0);
                try {
                    boolean hasNext = true;
                    final int[] rowNum = {1};
                    final int[] groupCount = {0};
                    final List groups = new ArrayList();

                    // Interpret row-results for each result-set row
                    InterpreterContentHandler contentHandler = new InterpreterContentHandler(interpreterContext, true) {
                        public void startPrefixMapping(String prefix, String uri) throws SAXException {
                            super.startPrefixMapping(prefix, uri);
                            interpreterContext.declarePrefix(prefix, uri);
                        }

                        private boolean hiding;

                        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                            if (!isInElementHandler() && SQL_NAMESPACE_URI.equals(uri)) {
                                if (localname.equals("group")) {
                                    try {
                                        ResultSet resultSet = interpreterContext.getResultSet(0);
                                        // Save group information if first row
                                        if (rowNum[0] == 1) {
                                            groups.add(new Group(attributes.getValue("column"), resultSet));
                                        }

                                        // Get current group information
                                        Group currentGroup = (Group) groups.get(groupCount[0]);

                                        if (rowNum[0] == 1 || columnChanged(resultSet, groups, groupCount[0])) {
                                            // Need to display group's header and footer
                                            currentGroup.setShowHeader(true);
                                            hiding = false;
                                            currentGroup.setColumnValue(resultSet);
                                        } else {
                                            // Hide group's header
                                            currentGroup.setShowHeader(false);
                                            hiding = true;
                                        }

                                        groupCount[0]++;

                                    } catch (SQLException e) {
                                        throw new ValidationException(e, new LocationData(getDocumentLocator()));
                                    }

                                } else if (localname.equals("member")) {
                                    hiding = false;
                                } else if (!hiding) {
                                    super.startElement(uri, localname, qName, attributes);
                                }
                            } else if (!hiding) {
                                super.startElement(uri, localname, qName, attributes);
                            }
                        }

                        public void endElement(String uri, String localname, String qName) throws SAXException {
                            if (!isInElementHandler() && SQL_NAMESPACE_URI.equals(uri)) {
                                if (localname.equals("group")) {
                                    groupCount[0]--;
                                    // Restore sending to the regular output
                                    Group currentGroup = (Group) groups.get(groupCount[0]);
                                    if (currentGroup.isShowHeader())
                                        interpreterContext.setOutput(savedOutput);
                                } else if (localname.equals("member")) {
                                    Group currentGroup = (Group) groups.get(groupCount[0] - 1);
                                    // The first time, everything is sent to the footer SAXStore
                                    if (currentGroup.isShowHeader()) {
                                        savedOutput = interpreterContext.getOutput();
                                        interpreterContext.setOutput(currentGroup.getFooter());
                                        hiding = false;
                                    } else
                                        hiding = true;
                                } else if (!hiding) {
                                    super.endElement(uri, localname, qName);
                                }
                            } else if (!hiding) {
                                super.endElement(uri, localname, qName);
                            }
                        }

                        public void characters(char[] chars, int start, int length) throws SAXException {
                            if (!hiding) {
                                // Output only if the string is non-blank [FIXME: Incorrect white space handling!]
//                                String s = new String(chars, start, length);
//                                if (!s.trim().equals(""))
                                super.characters(chars, start, length);
                            }
                        }
                    };

                    // Initialize the content handler
                    contentHandler.addElementHandler(new ExecuteInterpreter(interpreterContext), SQL_NAMESPACE_URI, "execute");
                    GetterInterpreter getterInterpreter = new GetterInterpreter(interpreterContext);
                    contentHandler.addElementHandler(getterInterpreter, SQL_NAMESPACE_URI, "get-string");
                    contentHandler.addElementHandler(getterInterpreter, SQL_NAMESPACE_URI, "get-int");
                    contentHandler.addElementHandler(getterInterpreter, SQL_NAMESPACE_URI, "get-double");
                    contentHandler.addElementHandler(getterInterpreter, SQL_NAMESPACE_URI, "get-decimal");
                    contentHandler.addElementHandler(getterInterpreter, SQL_NAMESPACE_URI, "get-date");
                    contentHandler.addElementHandler(getterInterpreter, SQL_NAMESPACE_URI, "get-timestamp");
                    contentHandler.addElementHandler(getterInterpreter, SQL_NAMESPACE_URI, "get-column");
                    contentHandler.addElementHandler(getterInterpreter, SQL_NAMESPACE_URI, "get-columns");
                    ValueOfCopyOfInterpreter valueOfCopyOfInterpreter = new ValueOfCopyOfInterpreter(interpreterContext);
                    contentHandler.addElementHandler(valueOfCopyOfInterpreter, SQL_NAMESPACE_URI, "value-of");
                    contentHandler.addElementHandler(valueOfCopyOfInterpreter, SQL_NAMESPACE_URI, "copy-of");
                    contentHandler.addElementHandler(new TextInterpreter(interpreterContext), SQL_NAMESPACE_URI, "text");
                    contentHandler.addElementHandler(new ForEachInterpreter(getInterpreterContext(), contentHandler.getElementHandlers()), SQL_NAMESPACE_URI, "for-each");

                    // Functions in this context
                    Map functions = new HashMap();
                    functions.put("{" + SQL_NAMESPACE_URI + "}" + "row-position", new Function() {
                        public Object call(org.jaxen.Context context, List args) {
                            return new Integer(interpreterContext.getRowPosition());
                        }
                    });

                    interpreterContext.pushFunctions(functions);
                    try {

                        // Iterate through the result set
                        while (hasNext) {
                            // Output footers that need it
                            if (groups != null) {
                                for (int i = groups.size() - 1; i >= 0; i--) {
                                    Group g1 = (Group) groups.get(i);
                                    if (columnChanged(resultSet, groups, i)) {
                                        g1.getFooter().replay(interpreterContext.getOutput());
                                        g1.getFooter().clear();
                                    }
                                }
                                groupCount[0] = 0;
                            }
                            // Set variables
                            interpreterContext.setRowPosition(rowNum[0]);
                            // Interpret row
                            saxStore.replay(contentHandler);
                            // Go to following row
                            hasNext = resultSet.next();
                            rowNum[0]++;
                        }
                        // Output last footers
                        for (int i = groups.size() - 1; i >= 0; i--) {
                            Group group = (Group) groups.get(i);
                            group.getFooter().replay(interpreterContext.getOutput());
                            group.getFooter().clear();
                        }
                    } finally {
                        interpreterContext.popFunctions();
                    }
                } catch (Exception e) {
                    throw new ValidationException(e, new LocationData(getDocumentLocator()));
                }
            }
        }

        private boolean columnChanged(ResultSet resultSet, List groups, int level) throws SQLException {
            for (int i = level; i >= 0; i--) {
                Group group = (Group) groups.get(i);
                if (group.columnChanged(resultSet))
                    return true;
            }
            return false;
        }
    }

    private static class ForEachInterpreter extends InterpreterContentHandler {

        private Map elementHandlers;
        private SAXStore saxStore;
        private ContentHandler savedOutput;
        private String select;

        public ForEachInterpreter(SQLProcessorInterpreterContext interpreterContext, Map elementHandlers) {
            super(interpreterContext, false);
            this.elementHandlers = elementHandlers;
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            // Everything will be stored in a SAXStore
            saxStore = new SAXStore();
            saxStore.setDocumentLocator(getDocumentLocator());
            savedOutput = getInterpreterContext().getOutput();
            getInterpreterContext().setOutput(saxStore);
            setForward(true);
            // Get attributes
            select = attributes.getValue("select");
        }

        public void end(String uri, String localname, String qName) throws SAXException {
            if (saxStore != null) {
                final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
                // Restore the regular output
                interpreterContext.setOutput(savedOutput);

                try {
                    // Create a new InterpreterContentHandler with the same handlers as our parent
                    InterpreterContentHandler contentHandler = new InterpreterContentHandler(interpreterContext, true);
                    contentHandler.setElementHandlers(elementHandlers);

                    // Scope functions
                    final Node[] currentNode = new Node[1];
                    final int[] currentPosition = new int[1];
                    Map functions = new HashMap();
                    functions.put("current", new Function() {
                        public Object call(org.jaxen.Context context, List args) {
                            return currentNode[0];
                        }
                    });

                    // FIXME: position() won't work because it will override
                    // the default XPath position() function
                    // We probably need to create a Jaxen Context directly to fix this

//                        functions.put("position", new Function() {
//                            public Object call(org.jaxen.Context context, List args) throws FunctionCallException {
//                                return new Integer(currentPosition[0]);
//                            }
//                        });
                    interpreterContext.pushFunctions(functions);
                    try {
                        // Iterate through the result set
                        int nodeCount = 1;

                        for (Iterator i = XPathUtils.selectIterator(interpreterContext.getCurrentNode(), select, interpreterContext.getPrefixesMap(), null, interpreterContext.getFunctionContext()); i.hasNext(); nodeCount++) {
                            currentNode[0] = (Node) i.next();
                            currentPosition[0] = nodeCount;

                            // Interpret iteration
                            interpreterContext.pushCurrentNode(currentNode[0]);
                            saxStore.replay(contentHandler);
                            interpreterContext.popCurrentNode();
                        }
                    } finally {
                        interpreterContext.popFunctions();
                    }

                } catch (Exception e) {
                    throw new ValidationException(e, new LocationData(getDocumentLocator()));
                }
            }
        }
    }

    private static class NoResultsInterpreter extends InterpreterContentHandler {

        public NoResultsInterpreter(SQLProcessorInterpreterContext interpreterContext) {
            super(interpreterContext, false);
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            // Only forward if the result set is empty
            if (getInterpreterContext().isEmptyResultSet()) {
                setForward(true);
                addElementHandler(new ExecuteInterpreter(getInterpreterContext()), SQL_NAMESPACE_URI, "execute");
                ValueOfCopyOfInterpreter valueOfCopyOfInterpreter = new ValueOfCopyOfInterpreter(getInterpreterContext());
                addElementHandler(valueOfCopyOfInterpreter, SQL_NAMESPACE_URI, "value-of");
                addElementHandler(valueOfCopyOfInterpreter, SQL_NAMESPACE_URI, "copy-of");
                addElementHandler(new TextInterpreter(getInterpreterContext()), SQL_NAMESPACE_URI, "text");
                addElementHandler(new ForEachInterpreter(getInterpreterContext(), getElementHandlers()), SQL_NAMESPACE_URI, "for-each");
            }
        }
    }

    private static class QueryInterpreter extends InterpreterContentHandler {

        public static final int QUERY = 0;
        public static final int UPDATE = 1;

        private int type;

        private StringBuffer query;
        private List queryParameters;
        private boolean hasReplaceOrSeparator;
        private Iterator nodeIterator;
        private String debugString;

        public QueryInterpreter(SQLProcessorInterpreterContext interpreterContext, int type) {
            super(interpreterContext, false);
            this.type = type;
        }

        public void characters(char[] chars, int start, int length) throws SAXException {
            if (query == null)
                query = new StringBuffer();
            query.append(chars, start, length);
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localname, qName, attributes);
            if (SQL_NAMESPACE_URI.equals(uri)) {
                if (localname.equals("param") || localname.equals("parameter")) {
                    if (query == null)
                        query = new StringBuffer();
                    // Add parameter information
                    String direction = attributes.getValue("direction");
                    String type = attributes.getValue("type");
                    String sqlType = attributes.getValue("sql-type");
                    String select = attributes.getValue("select");
                    String separator = attributes.getValue("separator");
                    boolean replace = new Boolean(attributes.getValue("replace")).booleanValue();
                    String nullIf = attributes.getValue("null-if");
                    if (replace || separator != null) {
                        // Remember that we have to replace at least once
                        hasReplaceOrSeparator = true;
                    } else {
                        // Add question mark for prepared statement
                        query.append(" ? ");
                    }
                    // Remember parameter
                    if (queryParameters == null)
                        queryParameters = new ArrayList();
                    queryParameters.add(new QueryParameter(direction, type, sqlType, select, separator, replace, nullIf, query.length(), new LocationData(getDocumentLocator())));
                } else {
                    // This must be either a get-column or a simple getter (deprecated)
                    boolean isGetColumn = localname.equals("get-column");

                    String levelString = attributes.getValue("ancestor");
                    String columnName = attributes.getValue("column");

                    // Level defaults to 1 in query
                    int level = (levelString == null) ? 1 : Integer.parseInt(levelString);
                    if (level < 1)
                        throw new ValidationException("Attribute level must be 1 or greater in query", new LocationData(getDocumentLocator()));
                    // Set value
                    try {
                        Object value;
                        if (isGetColumn) {
                            String type = attributes.getValue("type");
                            ResultSet rs = getInterpreterContext().getResultSet(level);
                            if ("oxf:xmlFragment".equals(type)) {
                                ResultSetMetaData metadata = rs.getMetaData();
                                int columnType = metadata.getColumnType(rs.findColumn(columnName));
                                if (columnType == Types.CLOB) {
                                    value = rs.getClob(columnName);
                                } else if (columnType == Types.BLOB) {
                                    throw new ValidationException("Cannot read a Blob as an oxf:xmlFragment", new LocationData(getDocumentLocator()));
                                } else {
                                    value = rs.getString(columnName);
                                }
                            } else
                                value = GetterInterpreter.interpretGenericGetter(getInterpreterContext(), getDocumentLocator(), type, columnName, level);
                        } else {
                            value = GetterInterpreter.interpretSimpleGetter(getInterpreterContext(), getDocumentLocator(), localname, columnName, level);
                        }
                        ((QueryParameter) queryParameters.get(queryParameters.size() - 1)).setValue(value);
                    } catch (Exception e) {
                        throw new ValidationException(e, new LocationData(getDocumentLocator()));
                    }
                }
            }
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            // Get select attribute
            String selectString = attributes.getValue("select");
            if (selectString != null) {
                if (type != UPDATE)
                    throw new ValidationException("select attribute is valid only on update element", new LocationData(getDocumentLocator()));
                nodeIterator = XPathUtils.selectIterator(getInterpreterContext().getCurrentNode(), selectString, getInterpreterContext().getPrefixesMap(), null, getInterpreterContext().getFunctionContext());
            }
            // Get debug attribute
            debugString = attributes.getValue("debug");
        }

        public void end(String uri, String localname, String qName) throws SAXException {
            // Validate query
            if (query == null)
                throw new ValidationException("Missing query", new LocationData(getDocumentLocator()));
            // Execute query
            try {
                // Create a single PreparedStatement if the query is not modified at each iteration
                PreparedStatement stmt = null;
                if (!hasReplaceOrSeparator) {
                    String queryString = query.toString();
                    stmt = getInterpreterContext().getConnection().prepareStatement(queryString);
                    getInterpreterContext().setStatementString(queryString);
                }
                getInterpreterContext().setStatement(stmt);
                int nodeCount = 1;
                // Iterate through all source nodes (only one if "select" attribute is missing)
                for (Iterator j = (nodeIterator != null) ? nodeIterator : Collections.singletonList(getInterpreterContext().getCurrentNode()).iterator(); j.hasNext(); nodeCount++) {
                    final Node currentNode = (Node) j.next();
                    final int _nodeCount = nodeCount;
//                    LocationData locationData = (currentNode instanceof Element)
//                            ? (LocationData) ((Element) currentNode).getData() : null;

                    // Scope sql:position variable (deprecated)
                    Map prefixesMap = getInterpreterContext().getPrefixesMap();
                    VariableContext variableContext = new VariableContext() {
                        public Object getVariableValue(String namespaceURI, String prefix, String localName) throws UnresolvableException {
                            if (!SQL_NAMESPACE_URI.equals(namespaceURI))
                                throw new UnresolvableException("Unbound variable: {" + namespaceURI + "}" + localName);
                            if ("position".equals(localName)) {
                                return new Integer(_nodeCount);
                            } else
                                throw new UnresolvableException("Unbound variable: {" + namespaceURI + "}" + localName);
                        }
                    };

                    // Scope sql:current(), sql:position() and sql:get-column functions
                    Map functions = new HashMap();
                    functions.put("{" + SQL_NAMESPACE_URI + "}" + "current", new Function() {
                        public Object call(org.jaxen.Context context, List args) {
                            return currentNode;
                        }
                    });

                    functions.put("{" + SQL_NAMESPACE_URI + "}" + "position", new Function() {
                        public Object call(org.jaxen.Context context, List args) {
                            return new Integer(_nodeCount);
                        }
                    });

                    functions.put("{" + SQL_NAMESPACE_URI + "}" + "get-column", new Function() {
                        public Object call(org.jaxen.Context context, List args) {
                            int argc = args.size();
                            if (argc < 1 || argc > 2)
                                throw new OXFException("sql:get-column expects one or two parameters");
                            String colname = (String) args.get(0);
                            String levelString = (argc == 2) ? (String) args.get(1) : null;
                            int level = (levelString == null) ? 1 : Integer.parseInt(levelString);
                            if (level < 1)
                                throw new OXFException("Attribute level must be 1 or greater in query");
                            ResultSet rs = getInterpreterContext().getResultSet(level);
                            try {
                                return rs.getString(colname);
                            } catch (SQLException e) {
                                throw new OXFException(e);
                            }
                        }
                    });

                    try {
                        getInterpreterContext().pushFunctions(functions);

                        // Replace inline parameters
                        StringBuffer replacedQuery = query;
                        if (hasReplaceOrSeparator) {
                            replacedQuery = new StringBuffer();
                            String queryString = query.toString();
                            int firstIndex = 0;
                            for (Iterator i = queryParameters.iterator(); i.hasNext();) {
                                QueryParameter parameter = (QueryParameter) i.next();
                                try {
                                    String select = parameter.getSelect();
                                    String separator = parameter.getSeparator();

                                    if (parameter.isReplace() || separator != null) {
                                        // Handle query modification for this parameter
                                        int secondIndex = parameter.getReplaceIndex();
                                        replacedQuery.append(queryString.substring(firstIndex, secondIndex));

                                        // Create List of either strings or nodes
                                        List values;
                                        if (separator == null) {
                                            // Read the expression as a string if there is a select, otherwise get parameter value as string
                                            Object objectValue;
                                            if (select != null) {
                                                objectValue = XPathUtils.selectStringValue(currentNode, parameter.getSelect(), prefixesMap, variableContext, getInterpreterContext().getFunctionContext());
                                            } else {
                                                objectValue = (parameter.getValue() == null) ? null : parameter.getValue().toString();
                                            }
                                            values = Collections.singletonList(objectValue);
                                        } else {
                                            // Accept only a node or node-set if there is a separator, in which case a select is mandatory
                                            Object objectValue = XPathUtils.selectObjectValue(currentNode, parameter.getSelect(), prefixesMap, variableContext, getInterpreterContext().getFunctionContext());
                                            if (objectValue instanceof List) {
                                                values = (List) objectValue;
                                            } else if (objectValue instanceof Node) {
                                                values = Collections.singletonList(objectValue);
                                            } else {
                                                throw new OXFException("sql:parameter with separator requires an expression returning a node-set");
                                            }
                                            // Set values on the parameter if they are not replaced immediately
                                            if (!parameter.isReplace())
                                                parameter.setValues(values);
                                        }

                                        if (parameter.isReplace()) {
                                            // Replace in the query
                                            for (Iterator k = values.iterator(); k.hasNext();) {
                                                Object objectValue = k.next();
                                                // Get value as a string
                                                String stringValue = (objectValue instanceof Node) ?
                                                        XPathUtils.selectStringValue((Node) objectValue, ".") :
                                                        (String) objectValue;

                                                // null values are prohibited
                                                if (stringValue == null)
                                                    throw new OXFException("Cannot replace value with null result");

                                                if ("int".equals(parameter.getType()) || "xs:int".equals(parameter.getType())) {
                                                    replacedQuery.append(Integer.parseInt(stringValue));
                                                } else if ("literal-string".equals(parameter.getType()) || "oxf:literalString".equals(parameter.getType())) {
                                                    replacedQuery.append(stringValue);
                                                } else
                                                    throw new ValidationException("Unsupported parameter type: " + parameter.getType(), parameter.getLocationData());

                                                // Append separator if needed
                                                if (k.hasNext())
                                                    replacedQuery.append(separator);
                                            }
                                        } else {
                                            // Update prepared statement
                                            for (int k = 0; k < values.size(); k++) {
                                                if (k > 0)
                                                    replacedQuery.append(separator);
                                                replacedQuery.append(" ? ");
                                            }
                                        }
                                        firstIndex = secondIndex;
                                    }
                                } catch (ValidationException e) {
                                    throw e;
                                } catch (Exception e) {
                                    throw new ValidationException(e, parameter.getLocationData());
                                }
                            }
                            if (firstIndex < queryString.length()) {
                                replacedQuery.append(queryString.substring(firstIndex));
                            }
                            // We create a new PreparedStatement for each iteration
                            String replacedQueryString = replacedQuery.toString();
                            if (stmt != null) {
                                stmt.close();
                            }
                            stmt = getInterpreterContext().getConnection().prepareStatement(replacedQueryString);
                            getInterpreterContext().setStatement(stmt);
                            getInterpreterContext().setStatementString(replacedQueryString);
                        }
                        // Output debug if needed
                        if (debugString != null)
                            logger.info("PreparedStatement (debug=\"" + debugString + "\"):\n" + getInterpreterContext().getStatementString());
                        // Set prepared statement parameters
                        if (queryParameters != null) {
                            int index = 1;
                            for (Iterator i = queryParameters.iterator(); i.hasNext();) {
                                QueryParameter parameter = (QueryParameter) i.next();
                                try {
                                    if (!parameter.isReplace()) {
                                        String select = parameter.getSelect();
                                        String type = parameter.getType();

                                        boolean doSetNull = parameter.getNullIf() != null
                                                && XPathUtils.selectBooleanValue(currentNode, parameter.getNullIf(), prefixesMap, variableContext, getInterpreterContext().getFunctionContext()).booleanValue();

                                        if ("string".equals(type) || "xs:string".equals(type) || "oxf:xmlFragment".equals(type)) {
                                            // Set a string or XML Fragment

                                            // List of Clobs, strings or nodes
                                            List values;
                                            if (parameter.getValues() != null)
                                                values = parameter.getValues();
                                            else if (select != null)
                                                values = Collections.singletonList(XPathUtils.selectObjectValue(currentNode, parameter.getSelect(), prefixesMap, variableContext, getInterpreterContext().getFunctionContext()));
                                            else
                                                values = Collections.singletonList(parameter.getValue());

                                            // Iterate through all values
                                            for (Iterator k = values.iterator(); k.hasNext(); index++) {
                                                Object objectValue = k.next();

                                                // Get Clob, String or Element
                                                Object value = null;
                                                if (!doSetNull) {
                                                    if (objectValue instanceof Clob || objectValue instanceof Blob || objectValue instanceof String) {
                                                        // Leave unchanged
                                                        value = objectValue;
                                                    } else if ("oxf:xmlFragment".equals(type)) {
                                                        // Case of XML Fragment
                                                        // Get an Element or a String
                                                        if (objectValue instanceof Element)
                                                            value = objectValue;
                                                        else if (objectValue instanceof List) {
                                                            List list = ((List) objectValue);
                                                            if (list.size() == 0)
                                                                value = null;
                                                            else if (list.get(0) instanceof Element)
                                                                value = list.get(0);
                                                            else
                                                                throw new OXFException("oxf:xmlFragment type expects a node-set an element node in first position");
                                                        } else if (objectValue != null)
                                                            throw new OXFException("oxf:xmlFragment type expects a node, a node-set or a string");

                                                    } else {
                                                        // Case of String
                                                        if (objectValue instanceof Node)
                                                            value = XPathUtils.selectStringValue((Node) objectValue, ".");
                                                        else if (objectValue instanceof List) {
                                                            List list = ((List) objectValue);
                                                            if (list.size() == 0)
                                                                value = null;
                                                            else if (list.get(0) instanceof Node)
                                                                value = XPathUtils.selectStringValue((Node) list.get(0), ".");
                                                            else
                                                                throw new OXFException("Invalid type: " + objectValue.getClass());
                                                        } else if (objectValue != null)
                                                            throw new OXFException("Invalid type: " + objectValue.getClass());
                                                    }
                                                }

                                                String sqlType = parameter.getSqlType();
                                                if (value == null) {
                                                    if (SQL_TYPE_CLOB.equals(sqlType))
                                                        stmt.setNull(index, Types.CLOB);
                                                    else if (SQL_TYPE_BLOB.equals(sqlType))
                                                        stmt.setNull(index, Types.BLOB);
                                                    else
                                                        stmt.setNull(index, Types.VARCHAR);
                                                } else if (value instanceof Clob) {
                                                    Clob clob = (Clob) value;
                                                    if (SQL_TYPE_CLOB.equals(sqlType)) {
                                                        // Set Clob as Clob
                                                        stmt.setClob(index, clob);
                                                    } else {
                                                        // Set Clob as String
                                                        long clobLength = clob.length();
                                                        if (clobLength > (long) Integer.MAX_VALUE)
                                                            throw new OXFException("CLOB length can't be larger than 2GB");
                                                        stmt.setString(index, clob.getSubString(1, (int) clob.length()));
                                                    }
                                                    // TODO: Check BLOB: should we be able to set a String as a Blob?
                                                } else if (value instanceof String || value instanceof Element) {
                                                    // Make sure we create a Document from the Element if we have one
                                                    Document xmlFragmentDocument = (value instanceof Element) ? DocumentHelper.createDocument(((Element) value).createCopy()) : null;

                                                    // Convert document into an XML String if necessary
                                                    if (value instanceof Element && !SQL_TYPE_XMLTYPE.equals(sqlType)) {
                                                        // Convert Document into a String
                                                        boolean serializeXML11 = getInterpreterContext().getPropertySet().getBoolean("serialize-xml-11", false).booleanValue();
                                                        value = XMLUtils.domToString(XMLUtils.adjustNamespaces(xmlFragmentDocument, serializeXML11), false, false);
                                                    }
                                                    if (SQL_TYPE_XMLTYPE.equals(sqlType)) {
                                                        // Set DOM using native XML type
                                                        if (value instanceof Element) {
                                                            // We have a Document - convert it to DOM

                                                            // TEMP HACK: We can't seem to be able to convert directly from dom4j to regular DOM (NAMESPACE_ERR from Xerces)

//                                                            DOMResult domResult = new DOMResult();
//                                                            TransformerUtils.getIdentityTransformer().transform(new DocumentSource(xmlFragmentDocument), domResult);xxx
//                                                            org.w3c.dom.Node node = domResult.getNode();

                                                            boolean serializeXML11 = getInterpreterContext().getPropertySet().getBoolean("serialize-xml-11", false).booleanValue();
                                                            String stringValue = XMLUtils.domToString(XMLUtils.adjustNamespaces(xmlFragmentDocument, serializeXML11), false, false);

                                                            // TEMP HACK: Oracle seems to have a problem with XMLType instanciated from a DOM, so we pass a String
//                                                            org.w3c.dom.Node node = XMLUtils.stringToDOM(stringValue);
//                                                            if (!(node instanceof org.w3c.dom.Document)) {
//                                                                // FIXME: Is this necessary? Why wouldn't we always get a Document from the transformation?
//                                                                org.w3c.dom.Document document = XMLUtils.createDocument();
//                                                                document.appendChild(node);
//                                                                node = document;
//                                                            }
//                                                            getInterpreterContext().getDelegate().setDOM(stmt, index, (org.w3c.dom.Document) node);
                                                            getInterpreterContext().getDelegate().setDOM(stmt, index, stringValue);
                                                        } else {
                                                            // We have a String - create a DOM from it
                                                            // FIXME: Do we need this?
                                                            throw new UnsupportedOperationException("Setting native XML type from a String is not yet supported. Please report this usage.");
                                                        }
                                                    } else if (SQL_TYPE_CLOB.equals(sqlType)) {
                                                        // Set String as Clob
                                                        String stringValue = (String) value;
                                                        getInterpreterContext().getDelegate().setClob(stmt, index, stringValue);
                                                        // TODO: Check BLOB: should we be able to set a String as a Blob?
                                                    } else {
                                                        // Set String as String
                                                        stmt.setString(index, (String) value);
                                                    }
                                                } else
                                                    throw new OXFException("Invalid parameter type: " + parameter.getType());
                                            }
                                        } else if ("xs:base64Binary".equals(type)) {
                                            // We are writing binary data encoded in Base 64. The only target supported
                                            // is Blob
                                            // For now, only support passing a string from the input document

                                            String sqlType = parameter.getSqlType();
                                            if (sqlType != null && !SQL_TYPE_CLOB.equals(sqlType))
                                                throw new OXFException("Invalid sql-type attribute: " + sqlType);

                                            if (select == null)
                                                throw new UnsupportedOperationException("Setting BLOB requires a select attribute.");

                                            // Base64
                                            XPathContentHandler xpathContentHandler = getInterpreterContext().getXPathContentHandler();
                                            if (xpathContentHandler != null && xpathContentHandler.containsExpression(parameter.getSelect())) {
                                                // Handle streaming if possible
                                                OutputStream blobOutputStream = getInterpreterContext().getDelegate().getBlobOutputStream(stmt, index);
                                                xpathContentHandler.selectContentHandler(parameter.getSelect(), new Base64ContentHandler(blobOutputStream));
                                                blobOutputStream.close();
                                            } else {
                                                String base64Value = XPathUtils.selectStringValue(currentNode, parameter.getSelect(), prefixesMap, variableContext, getInterpreterContext().getFunctionContext());
                                                getInterpreterContext().getDelegate().setBlob(stmt, index, XMLUtils.base64StringToByteArray(base64Value));
                                            }
                                        } else {
                                            // Simple cases

                                            // List of strings or nodes
                                            List values;
                                            if (parameter.getValues() != null)
                                                values = parameter.getValues();
                                            else if (select != null)
                                                values = Collections.singletonList(XPathUtils.selectStringValue(currentNode, parameter.getSelect(), prefixesMap, variableContext, getInterpreterContext().getFunctionContext()));
                                            else
                                                values = Collections.singletonList(parameter.getValue());

                                            // Iterate through all values
                                            for (Iterator k = values.iterator(); k.hasNext(); index++) {
                                                Object objectValue = k.next();
                                                // Get String value
                                                String stringValue = null;
                                                if (!doSetNull) {
                                                    if (objectValue instanceof String)
                                                        stringValue = (String) objectValue;
                                                    else if (objectValue != null)
                                                        stringValue = XPathUtils.selectStringValue((Node) objectValue, ".");
                                                }
                                                // For the specific type, set to null or convert String value
                                                if ("int".equals(type) || "xs:int".equals(type)) {
                                                    if (stringValue == null)
                                                        stmt.setNull(index, Types.INTEGER);
                                                    else
                                                        stmt.setInt(index, new Integer(stringValue).intValue());
                                                } else if ("date".equals(type) || "xs:date".equals(type)) {
                                                    if (stringValue == null) {
                                                        stmt.setNull(index, Types.DATE);
                                                    } else {
                                                        java.sql.Date date = new java.sql.Date(ISODateUtils.parseDate(stringValue).getTime());
                                                        stmt.setDate(index, date);
                                                    }
                                                } else if ("xs:dateTime".equals(type)) {
                                                    if (stringValue == null) {
                                                        stmt.setNull(index, Types.TIMESTAMP);
                                                    } else {
                                                        java.sql.Timestamp timestamp = new java.sql.Timestamp(ISODateUtils.parseDate(stringValue).getTime());
                                                        stmt.setTimestamp(index, timestamp);
                                                    }
                                                } else if ("xs:boolean".equals(type)) {
                                                    if (stringValue == null)
                                                        stmt.setNull(index, Types.INTEGER); // Types.BOOLEAN is present from JDK 1.4 only
                                                    else
                                                        stmt.setBoolean(index, "true".equals(stringValue));
                                                } else if ("xs:decimal".equals(type)) {
                                                    if (stringValue == null)
                                                        stmt.setNull(index, Types.DECIMAL);
                                                    else
                                                        stmt.setBigDecimal(index, new BigDecimal(stringValue));
                                                } else if ("xs:float".equals(type)) {
                                                    if (stringValue == null)
                                                        stmt.setNull(index, Types.FLOAT);
                                                    else
                                                        stmt.setFloat(index, Float.parseFloat(stringValue));
                                                } else if ("xs:double".equals(type)) {
                                                    if (stringValue == null)
                                                        stmt.setNull(index, Types.DOUBLE);
                                                    else
                                                        stmt.setDouble(index, Double.parseDouble(stringValue));
                                                } else if ("xs:anyURI".equals(type)) {
                                                    String sqlType = parameter.getSqlType();
                                                    if (sqlType != null && !SQL_TYPE_CLOB.equals(sqlType))
                                                        throw new OXFException("Invalid sql-type attribute: " + sqlType);
                                                    if (stringValue == null) {
                                                        stmt.setNull(index, Types.BLOB);
                                                    } else {
                                                        // Dereference the URI and write to the BLOB
                                                        OutputStream blobOutputStream = getInterpreterContext().getDelegate().getBlobOutputStream(stmt, index);
                                                        XMLUtils.anyURIToOutputStream(stringValue, blobOutputStream);
                                                        blobOutputStream.close();
                                                    }
                                                } else
                                                    throw new ValidationException("Unsupported parameter type: " + type, parameter.getLocationData());
                                            }
                                        }
                                    }
                                } catch (ValidationException e) {
                                    throw e;
                                } catch (Exception e) {
                                    throw new ValidationException(e, parameter.getLocationData());
                                }
                            }
                        }
                    } finally {
                        getInterpreterContext().popFunctions();
                    }
                    if (type == QUERY) {
                        if (nodeCount > 1)
                            throw new ValidationException("More than one iteration on query element", new LocationData(getDocumentLocator()));
                        ResultSet resultSet = stmt.executeQuery();
                        boolean hasNext = resultSet.next();
                        getInterpreterContext().setEmptyResultSet(!hasNext);
                        // Close result set and statement immediately if possible
                        if (getInterpreterContext().isEmptyResultSet()) {
                            stmt.close();
                            resultSet = null;
                            stmt = null;
                            getInterpreterContext().setStatement(null);
                        }
                        // Remember result set and statement
                        getInterpreterContext().setResultSet(resultSet);
                    } else if (type == UPDATE) {
                        int updateCount = stmt.executeUpdate();
                        getInterpreterContext().setUpdateCount(updateCount);//FIXME: should add?
                    }
                }
            } catch (Exception e) {
                // FIXME: should store exception so that it can be retrieved
                // Actually, we'll need a global exception mechanism for pipelines, so this may end up being done
                // in XPL or BPEL.
                // Log closest query related to the exception if we can find it
                String statementString = getInterpreterContext().getStatementString();
                logger.error("PreparedStatement:\n" + statementString);
                // And throw
                throw new ValidationException(e, new LocationData(getDocumentLocator()));
            }
        }
    }

    private static class ConfigInterpreter extends InterpreterContentHandler {

        public ConfigInterpreter(SQLProcessorInterpreterContext interpreterContext) {
            super(interpreterContext, true);
            final SQLProcessorInterpreterContext _interpreterContext = interpreterContext;

            addElementHandler(new InterpreterContentHandler(interpreterContext, true) {
                public void start(String uri, String localname, String qName, Attributes attributes) {
                    addElementHandler(new InterpreterContentHandler() {
                        StringBuffer datasourceName;

                        public void characters(char[] chars, int start, int length) {
                            if (datasourceName == null)
                                datasourceName = new StringBuffer();
                            datasourceName.append(chars, start, length);
                        }

                        public void end(String uri, String localname, String qName) {
                            // Validate datasource element
                            if (datasourceName == null)
                                throw new ValidationException("Missing datasource name in datasource element", new LocationData(getDocumentLocator()));
                            // Get the connection from the datasource and set in context
                            try {
                                _interpreterContext.setConnection(getDocumentLocator(), datasourceName.toString());
                            } catch (Exception e) {
                                throw new ValidationException(e, new LocationData(getDocumentLocator()));
                            }
                        }
                    }, SQL_NAMESPACE_URI, "datasource");
                    addElementHandler(new ExecuteInterpreter(getInterpreterContext()), SQL_NAMESPACE_URI, "execute");
                    ValueOfCopyOfInterpreter valueOfCopyOfInterpreter = new ValueOfCopyOfInterpreter(getInterpreterContext());
                    addElementHandler(valueOfCopyOfInterpreter, SQL_NAMESPACE_URI, "value-of");
                    addElementHandler(valueOfCopyOfInterpreter, SQL_NAMESPACE_URI, "copy-of");
                    addElementHandler(new TextInterpreter(getInterpreterContext()), SQL_NAMESPACE_URI, "text");
                    addElementHandler(new ForEachInterpreter(getInterpreterContext(), getElementHandlers()), SQL_NAMESPACE_URI, "for-each");
                }

                public void end(String uri, String localname, String qName) {
                    // Close connection
                    // NOTE: Don't do this anymore: the connection will be closed when the context is destroyed
                }
            }, SQL_NAMESPACE_URI, "connection");
            addElementHandler(new TextInterpreter(interpreterContext), SQL_NAMESPACE_URI, "text");
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            getInterpreterContext().getOutput().startDocument();
        }

        public void end(String uri, String localname, String qName) throws SAXException {
            getInterpreterContext().getOutput().endDocument();
        }
    }

    private static class TextInterpreter extends InterpreterContentHandler {
        private StringBuffer text;

        public TextInterpreter(SQLProcessorInterpreterContext interpreterContext) {
            super(interpreterContext, false);
        }

        public void characters(char[] chars, int start, int length) throws SAXException {
            if (text == null)
                text = new StringBuffer();
            text.append(chars, start, length);
        }

        public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            text = null;
        }

        public void end(String uri, String localname, String qName) throws SAXException {
            char[] localText = text.toString().toCharArray();
            getInterpreterContext().getOutput().characters(localText, 0, localText.length);
        }
    }

    private static class RootInterpreter extends InterpreterContentHandler {
        private SQLProcessorInterpreterContext interpreterContext;
        private NamespaceSupport namespaceSupport = new NamespaceSupport();

        public RootInterpreter(PipelineContext context, OXFProperties.PropertySet propertySet, Node input, Datasource datasource, XPathContentHandler xpathContentHandler, ContentHandler output) {
            super(null, false);
            interpreterContext = new SQLProcessorInterpreterContext(propertySet);
            interpreterContext.setPipelineContext(context);
            interpreterContext.setInput(input);
            interpreterContext.setDatasource(datasource);
            interpreterContext.setXPathContentHandler(xpathContentHandler);
            interpreterContext.setOutput(output);
            interpreterContext.setNamespaceSupport(namespaceSupport);
            addElementHandler(new ConfigInterpreter(interpreterContext), SQL_NAMESPACE_URI, "config");
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            try {
                namespaceSupport.pushContext();
                super.startElement(uri, localname, qName, attributes);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {
            try {
                super.endElement(uri, localname, qName);
                namespaceSupport.popContext();
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            try {
                super.startPrefixMapping(prefix, uri);
                interpreterContext.declarePrefix(prefix, uri);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        public void characters(char[] chars, int start, int length) throws SAXException {
            try {
                super.characters(chars, start, length);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        public void endDocument() throws SAXException {
            try {
                super.endDocument();
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
            try {
                super.ignorableWhitespace(chars, start, length);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        public void processingInstruction(String s, String s1) throws SAXException {
            try {
                super.processingInstruction(s, s1);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        public void skippedEntity(String s) throws SAXException {
            try {
                super.skippedEntity(s);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        public void startDocument() throws SAXException {
            try {
                super.startDocument();
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        public void endPrefixMapping(String s) throws SAXException {
            try {
                super.endPrefixMapping(s);
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

        public Locator getDocumentLocator() {
            try {
                return super.getDocumentLocator();
            } catch (Exception t) {
                dispose();
                throw new OXFException(t);
            }
        }

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

    private static class InterpreterContentHandler extends ForwardingContentHandler {

        private SQLProcessorInterpreterContext interpreterContext;
        private boolean forward;
        private Locator documentLocator;

        private Map elementHandlers = new HashMap();
        private int forwardingLevel = -1;
        private InterpreterContentHandler currentHandler;
        private int level = 0;
        private String currentKey;

        public InterpreterContentHandler() {
            this(null, false);
        }

        public InterpreterContentHandler(SQLProcessorInterpreterContext interpreterContext, boolean forward) {
            this.interpreterContext = interpreterContext;
            this.forward = forward;
        }

        public void addElementHandler(InterpreterContentHandler handler, String uri, String localname) {
            elementHandlers.put("{" + uri + "}" + localname, handler);
        }

        public Map getElementHandlers() {
            return elementHandlers;
        }

        public void setElementHandlers(Map elementHandlers) {
            this.elementHandlers = elementHandlers;
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            if (forwardingLevel == -1 && elementHandlers.size() > 0) {
                String key = "{" + uri + "}" + localname;
                InterpreterContentHandler elementHandler = (InterpreterContentHandler) elementHandlers.get(key);
                if (elementHandler != null) {
                    forwardingLevel = level;
                    currentKey = key;
                    currentHandler = elementHandler;
                    elementHandler.setDocumentLocator(documentLocator);
                    elementHandler.start(uri, localname, qName, attributes);
                } else
                    super.startElement(uri, localname, qName, attributes);
            } else
                super.startElement(uri, localname, qName, attributes);
            level++;
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {
            level--;
            if (forwardingLevel == level) {
                String key = "{" + uri + "}" + localname;
                if (!currentKey.equals(key))
                    throw new ValidationException("Illegal document: expecting " + key + ", got " + currentKey, new LocationData(getDocumentLocator()));
                InterpreterContentHandler elementHandler = (InterpreterContentHandler) elementHandlers.get(key);

                forwardingLevel = -1;
                currentKey = null;
                currentHandler = null;
                elementHandler.end(uri, localname, qName);
            } else
                super.endElement(uri, localname, qName);
        }

        public void setDocumentLocator(Locator locator) {
            this.documentLocator = locator;
            super.setDocumentLocator(locator);
        }

        public Locator getDocumentLocator() {
            return documentLocator;
        }

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
            if (currentHandler != null)
                return currentHandler;
            else if (forward)
                return interpreterContext.getOutput();
            else
                return null;
        }

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

        public boolean isForward() {
            return forward;
        }

        public void setForward(boolean forward) {
            this.forward = forward;
        }
    }

    private static class QueryParameter {
        private String direction;
        private String type;
        private String sqlType;
        private String select;
        private String separator;
        private boolean replace;
        private String nullIf;
        private int replaceIndex;
        private Object value;
        private List values;
        private LocationData locationData;

        public QueryParameter(String direction, String type, String sqlType, String select, String separator,
                              boolean replace, String nullIf, int replaceIndex, LocationData locationData) {
            this.direction = direction;
            this.type = type;
            this.sqlType = sqlType;
            this.select = select;
            this.separator = separator;
            this.replace = replace;
            this.nullIf = nullIf;
            this.replaceIndex = replaceIndex;
            this.locationData = locationData;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getDirection() {
            return direction;
        }

        public String getType() {
            return type;
        }

        public String getSqlType() {
            return sqlType;
        }

        public String getSelect() {
            return select;
        }

        public String getSeparator() {
            return separator;
        }

        public boolean isReplace() {
            return replace;
        }

        public String getNullIf() {
            return nullIf;
        }

        public int getReplaceIndex() {
            return replaceIndex;
        }

        public Object getValue() {
            return value;
        }

        public LocationData getLocationData() {
            return locationData;
        }

        public List getValues() {
            return values;
        }

        public void setValues(List values) {
            this.values = values;
        }
    }

    private static abstract class ForwardingContentHandler implements ContentHandler {

        public ForwardingContentHandler() {
        }

        protected abstract ContentHandler getContentHandler();

        public void characters(char[] chars, int start, int length) throws SAXException {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.characters(chars, start, length);
        }

        public void endDocument() throws SAXException {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.endDocument();
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.endElement(uri, localname, qName);
        }

        public void endPrefixMapping(String s) throws SAXException {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.endPrefixMapping(s);
        }

        public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.ignorableWhitespace(chars, start, length);
        }

        public void processingInstruction(String s, String s1) throws SAXException {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.processingInstruction(s, s1);
        }

        public void setDocumentLocator(Locator locator) {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.setDocumentLocator(locator);
        }

        public void skippedEntity(String s) throws SAXException {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.skippedEntity(s);
        }

        public void startDocument() throws SAXException {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.startDocument();
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.startElement(uri, localname, qName, attributes);
        }

        public void startPrefixMapping(String s, String s1) throws SAXException {
            ContentHandler contentHandler = getContentHandler();
            if (contentHandler != null)
                contentHandler.startPrefixMapping(s, s1);
        }
    }

}
