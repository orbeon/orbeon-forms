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
package org.orbeon.oxf.processor.sql.interpreters;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jaxen.Function;
import org.jaxen.UnresolvableException;
import org.jaxen.VariableContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.util.Base64XMLReceiver;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XPathContentHandler;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 *
 */
public class QueryInterpreter extends SQLProcessor.InterpreterContentHandler {

    //    private static final String SQL_TYPE_VARCHAR = "varchar";
    private static final String SQL_TYPE_CLOB = "clob";
    private static final String SQL_TYPE_BLOB = "blob";
    private static final String SQL_TYPE_XMLTYPE = "xmltype";

    public static final int QUERY = 0;
    public static final int UPDATE = 1;
    public static final int CALL = 2;

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
        if (SQLProcessor.SQL_NAMESPACE_URI.equals(uri)) {
            if (localname.equals("param") || localname.equals("parameter")) {
                if (query == null)
                    query = new StringBuffer();
                // Add parameter information
                String direction = attributes.getValue("direction");
                String type = attributes.getValue("type");
                String sqlType = attributes.getValue("sql-type");
                String select = attributes.getValue("select");
                String separator = attributes.getValue("separator");
                boolean replace = Boolean.valueOf(attributes.getValue("replace")).booleanValue();
                String nullIf; {
                    nullIf = attributes.getValue("null");
                    if (nullIf == null)
                        nullIf = attributes.getValue("null-if");// legacy attribute name
                }
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
                // This must be either a get-column or a (deprecated) simple getter
                final boolean isGetColumn = "get-column".equals(localname) || "get-column-value".equals(localname);

                final String levelString = attributes.getValue("ancestor");
                final String columnName = (attributes.getValue("column-name") != null) ? attributes.getValue("column-name") : attributes.getValue("column");

                // Level defaults to 1 in query
                int level = (levelString == null) ? 1 : Integer.parseInt(levelString);
                if (level < 1)
                    throw new ValidationException("Attribute level must be 1 or greater in query", new LocationData(getDocumentLocator()));
                // Set value
                try {
                    final ResultSet rs = getInterpreterContext().getResultSet(level);
                    final int columnIndex = rs.findColumn(columnName);
                    final ResultSetMetaData metadata = rs.getMetaData();
                    final int columnType = metadata.getColumnType(rs.findColumn(columnName));

                    final Object value;
                    if (isGetColumn) {
                        // Generic getter
                        final String xmlType = GetterInterpreter.getXMLTypeFromAttributeStringHandleDefault(getDocumentLocator(), getInterpreterContext().getPropertySet(), attributes.getValue("type"), getInterpreterContext().getPrefixesMap(), columnType);
                        if (Dom4jUtils.qNameToExplodedQName(XMLConstants.OPS_XMLFRAGMENT_QNAME).equals(xmlType)) {
                            if (columnType == Types.CLOB) {
                                value = rs.getClob(columnName);
                            } else if (columnType == Types.BLOB) {
                                throw new ValidationException("Cannot read a Blob as an xmlFragment type", new LocationData(getDocumentLocator()));
                            } else {
                                value = rs.getString(columnName);
                            }
                        } else {
                            value = GetterInterpreter.getColumnValue(rs, getDocumentLocator(), columnIndex, xmlType);
                        }
                    } else {
                        // Deprecated: simple getter
                        value = GetterInterpreter.getColumnValue(rs, getDocumentLocator(), columnIndex, GetterInterpreter.getXMLTypeFromLegacyGetterName(localname));
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
                final String queryString = query.toString();
                if (type != CALL) {
                    // TODO: see how we can support this: Statement.RETURN_GENERATED_KEYS (won't work with hsqldb)
                    stmt = getInterpreterContext().getConnection().prepareStatement(queryString);
                } else
                    stmt = getInterpreterContext().getConnection().prepareCall(queryString);
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
                        if (!SQLProcessor.SQL_NAMESPACE_URI.equals(namespaceURI))
                            throw new UnresolvableException("Unbound variable: {" + namespaceURI + "}" + localName);
                        if ("position".equals(localName)) {
                            return new Integer(_nodeCount);
                        } else
                            throw new UnresolvableException("Unbound variable: {" + namespaceURI + "}" + localName);
                    }
                };

                // Scope sql:current(), sql:position() and sql:get-column functions
                Map functions = new HashMap();
                functions.put("{" + SQLProcessor.SQL_NAMESPACE_URI + "}" + "current", new Function() {
                    public Object call(org.jaxen.Context context, List args) {
                        return currentNode;
                    }
                });

                functions.put("{" + SQLProcessor.SQL_NAMESPACE_URI + "}" + "position", new Function() {
                    public Object call(org.jaxen.Context context, List args) {
                        return new Integer(_nodeCount);
                    }
                });

                functions.put("{" + SQLProcessor.SQL_NAMESPACE_URI + "}" + "get-column", new Function() {
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

                                            final String type = parameter.getType();
                                            if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_INT_QNAME).equals(type)) {
                                                replacedQuery.append(Integer.parseInt(stringValue));
                                            } else if ("literal-string".equals(type) || "oxf:literalString".equals(type)) {
                                                replacedQuery.append(stringValue);
                                            } else
                                                throw new ValidationException("Unsupported parameter type: " + type, parameter.getLocationData());

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
                        SQLProcessor.logger.info("PreparedStatement (debug=\"" + debugString + "\"):\n" + getInterpreterContext().getStatementString());
                    // Set prepared statement parameters
                    if (queryParameters != null) {
                        int index = 1;
                        for (Iterator i = queryParameters.iterator(); i.hasNext();) {
                            QueryParameter parameter = (QueryParameter) i.next();
                            try {
                                if (!parameter.isReplace()) {
                                    final String select = parameter.getSelect();
                                    final String xmlType; {
                                        final String type = parameter.getType();
                                        xmlType = GetterInterpreter.getXMLTypeFromAttributeString(getDocumentLocator(), getInterpreterContext().getPropertySet(), type, getInterpreterContext().getPrefixesMap());
                                    }

                                    boolean doSetNull = parameter.getNullIf() != null
                                            && XPathUtils.selectBooleanValue(currentNode, parameter.getNullIf(), prefixesMap, variableContext, getInterpreterContext().getFunctionContext()).booleanValue();

                                    if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_STRING_QNAME).equals(xmlType) || Dom4jUtils.qNameToExplodedQName(XMLConstants.OPS_XMLFRAGMENT_QNAME).equals(xmlType)) {
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
                                                } else if (Dom4jUtils.qNameToExplodedQName(XMLConstants.OPS_XMLFRAGMENT_QNAME).equals(xmlType)) {
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
                                                            throw new OXFException("xmlFragment type expects a node-set an element node in first position");
                                                    } else if (objectValue != null)
                                                        throw new OXFException("xmlFragment type expects a node, a node-set or a string");

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

                                            final String sqlType = parameter.getSqlType();
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
                                                Document xmlFragmentDocument = (value instanceof Element) ? Dom4jUtils.createDocumentCopyParentNamespaces((Element) value) : null;

                                                // Convert document into an XML String if necessary
                                                if (value instanceof Element && !SQL_TYPE_XMLTYPE.equals(sqlType)) {
                                                    // Convert Document into a String
                                                    boolean serializeXML11 = getInterpreterContext().getPropertySet().getBoolean("serialize-xml-11", false).booleanValue();
                                                    value = Dom4jUtils.domToString(Dom4jUtils.adjustNamespaces(xmlFragmentDocument, serializeXML11));
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
                                                        String stringValue = Dom4jUtils.domToString(Dom4jUtils.adjustNamespaces(xmlFragmentDocument, serializeXML11));

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
                                                    //stmt.setCharacterStream(index, new StringReader(stringValue), stringValue.length());
                                                    getInterpreterContext().getDelegate().setClob(stmt, index, stringValue);


                                                    // TODO: Check BLOB: should we be able to set a String as a Blob?
                                                } else {
                                                    // Set String as String
                                                    stmt.setString(index, (String) value);
                                                }
                                            } else
                                                throw new OXFException("Invalid parameter type: " + parameter.getType());
                                        }
                                    } else if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_BASE64BINARY_QNAME).equals(xmlType)) {
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
                                            xpathContentHandler.selectContentHandler(parameter.getSelect(), new Base64XMLReceiver(blobOutputStream));
                                            blobOutputStream.close();
                                        } else {
                                            String base64Value = XPathUtils.selectStringValue(currentNode, parameter.getSelect(), prefixesMap, variableContext, getInterpreterContext().getFunctionContext());
                                            getInterpreterContext().getDelegate().setBlob(stmt, index, NetUtils.base64StringToByteArray(base64Value));
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
                                            if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_INT_QNAME).equals(xmlType)) {
                                                if (stringValue == null)
                                                    stmt.setNull(index, Types.INTEGER);
                                                else
                                                    stmt.setInt(index, Integer.parseInt(stringValue));
                                            } else if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_DATE_QNAME).equals(xmlType)) {
                                                if (stringValue == null) {
                                                    stmt.setNull(index, Types.DATE);
                                                } else {
                                                    java.sql.Date date = new java.sql.Date(ISODateUtils.parseDate(stringValue).getTime());
                                                    stmt.setDate(index, date);
                                                }
                                            } else if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_DATETIME_QNAME).equals(xmlType)) {
                                                if (stringValue == null) {
                                                    stmt.setNull(index, Types.TIMESTAMP);
                                                } else {
                                                    java.sql.Timestamp timestamp = new java.sql.Timestamp(ISODateUtils.parseDate(stringValue).getTime());
                                                    stmt.setTimestamp(index, timestamp);
                                                }
                                            } else if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_BOOLEAN_QNAME).equals(xmlType)) {
                                                if (stringValue == null)
                                                    stmt.setNull(index, Types.BOOLEAN);
                                                else
                                                    stmt.setBoolean(index, "true".equals(stringValue));
                                            } else if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_DECIMAL_QNAME).equals(xmlType)) {
                                                if (stringValue == null)
                                                    stmt.setNull(index, Types.DECIMAL);
                                                else
                                                    stmt.setBigDecimal(index, new BigDecimal(stringValue));
                                            } else if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_FLOAT_QNAME).equals(xmlType)) {
                                                if (stringValue == null)
                                                    stmt.setNull(index, Types.FLOAT);
                                                else
                                                    stmt.setFloat(index, Float.parseFloat(stringValue));
                                            } else if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_DOUBLE_QNAME).equals(xmlType)) {
                                                if (stringValue == null)
                                                    stmt.setNull(index, Types.DOUBLE);
                                                else
                                                    stmt.setDouble(index, Double.parseDouble(stringValue));
                                            } else if (Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_ANYURI_QNAME).equals(xmlType)) {
                                                String sqlType = parameter.getSqlType();
                                                if (sqlType != null && !(SQL_TYPE_BLOB.equals(sqlType) || SQL_TYPE_CLOB.equals(sqlType)))
                                                    throw new OXFException("Invalid sql-type attribute: " + sqlType);
                                                if (stringValue == null) {
                                                    stmt.setNull(index, Types.BLOB);
                                                } else {
                                                    // Dereference the URI and write to the BLOB
                                                    OutputStream blobOutputStream = getInterpreterContext().getDelegate().getBlobOutputStream(stmt, index);
                                                    NetUtils.anyURIToOutputStream(stringValue, blobOutputStream);
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
                if (type == QUERY || type == CALL) {
                    if (nodeCount > 1)
                        throw new ValidationException("More than one iteration on sql:query or sql:call element", new LocationData(getDocumentLocator()));
                    // Execute
                    if (SQLProcessor.logger.isDebugEnabled())
                        SQLProcessor.logger.debug("Executing query/call for statement: " + getInterpreterContext().getStatementString());
                    final boolean hasResultSet = stmt.execute();
                    ResultSetInterpreter.setResultSetInfo(getInterpreterContext(), stmt, hasResultSet);
                } else if (type == UPDATE) {
                    // We know there is only a possible update count
                    final int updateCount = stmt.executeUpdate();
                    getInterpreterContext().setUpdateCount(updateCount);//FIXME: should add?
                    if (updateCount > 0)
                    	ResultSetInterpreter.setGeneratedKeysResultSetInfo(getInterpreterContext(), stmt);
                }
            }
        } catch (Exception e) {
            // FIXME: should store exception so that it can be retrieved
            // Actually, we'll need a global exception mechanism for pipelines, so this may end up being done
            // in XPL or BPEL.
            // Log closest query related to the exception if we can find it
            String statementString = getInterpreterContext().getStatementString();
            SQLProcessor.logger.error("PreparedStatement:\n" + statementString);
            // And throw
            throw new ValidationException(e, new LocationData(getDocumentLocator()));
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
}
