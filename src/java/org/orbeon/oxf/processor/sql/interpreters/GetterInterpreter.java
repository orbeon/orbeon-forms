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
package org.orbeon.oxf.processor.sql.interpreters;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.NamespaceSupport;

import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class GetterInterpreter extends SQLProcessor.InterpreterContentHandler {

    private ResultSetMetaData metadata;

    private int getColumnsLevel;
    private String getColumnsFormat;
    private String getColumnsPrefix;
    private boolean getColumnsAllElements;
    private boolean inExclude;
    private StringBuffer getColumnsCurrentExclude;
    private Map getColumnsExcludes;

    public GetterInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, false);
    }

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
            final String levelString = attributes.getValue("ancestor");
            final int level = (levelString == null) ? 0 : Integer.parseInt(levelString);
            final ResultSet resultSet = interpreterContext.getResultSet(level);
            metadata = resultSet.getMetaData();
            if ("get-columns".equals(localname)) {
                // Remember attributes
                getColumnsLevel = level;
                getColumnsFormat = attributes.getValue("format");
                getColumnsPrefix = attributes.getValue("prefix");
                getColumnsAllElements = "true".equals(attributes.getValue("all-elements"));
            } else if ("get-column".equals(localname) || "get-column-value".equals(localname)) {
                final String columnName;
                {
                    final String columnNameAttribute = attributes.getValue("column-name");
                    final String columnAttribute = attributes.getValue("column");
                    if (columnNameAttribute != null)
                        columnName = columnNameAttribute;
                    else if (columnAttribute != null)
                        columnName = columnAttribute;
                    else
                        columnName = interpreterContext.getColumnName();
                }
                final int columnIndex = resultSet.findColumn(columnName);
                final int columnType = metadata.getColumnType(columnIndex);

                final String xmlType = getXMLTypeFromAttributeStringHandleDefault(getDocumentLocator(), interpreterContext.getPropertySet(), attributes.getValue("type"), interpreterContext.getPrefixesMap(), columnType);
                if (Dom4jUtils.qNameToExplodedQName(XMLConstants.OPS_XMLFRAGMENT_QNAME).equals(xmlType)) {
                    // XML fragment requested
                    String columnTypeName = metadata.getColumnTypeName(columnIndex);
                    if (columnType == Types.CLOB) {
                        // The fragment is stored as a Clob
                        Clob clob = resultSet.getClob(columnName);
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
                        org.w3c.dom.Node node = interpreterContext.getDelegate().getDOM(resultSet, columnName);
                        // NOTE: XML comments not supported yet
                        if (node != null) {
                            TransformerUtils.getIdentityTransformer().transform(new DOMSource(node),
                                    new SAXResult(new SQLProcessor.ForwardingContentHandler() {
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
                        String value = resultSet.getString(columnName);
                        if (value != null)
                            XMLUtils.parseDocumentFragment(value, interpreterContext.getOutput());
                    }
                } else {
                    // xs:*
                    Object o = getColumnValue(resultSet, getDocumentLocator(), columnIndex, xmlType);
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
            } else if ("get-column-name".equals(localname)) {
                final String columnName = attributes.getValue("column-name");
                final String columnIndex = attributes.getValue("column-index");

                final String result;
                if (columnName == null && columnIndex == null) {
                    // Get from context
                    result = interpreterContext.getColumnName();
                } else if (columnName != null){
                    // Trivial case
                    result = columnName;
                } else {
                    // Get name from index
                    result = metadata.getColumnName(Integer.parseInt(columnIndex));
                }

                final char[] charResult = result.toCharArray();
                interpreterContext.getOutput().characters(charResult, 0, charResult.length);

            } else if ("get-column-index".equals(localname)) {
                final String columnName = attributes.getValue("column-name");
                final String columnIndex = attributes.getValue("column-index");

                final String result;
                if (columnName == null && columnIndex == null) {
                    // Get from context
                    result = Integer.toString(interpreterContext.getColumnIndex());
                } else if (columnName != null){
                    // Get index from name
                    result = Integer.toString(resultSet.findColumn(columnName));
                } else {
                    // Trivial case
                    result = columnIndex;
                }

                final char[] charResult = result.toCharArray();
                interpreterContext.getOutput().characters(charResult, 0, charResult.length);
            } else if ("get-column-type".equals(localname)) {
                final String columnName = attributes.getValue("column-name");
                final String columnIndex = attributes.getValue("column-index");

                final String result;
                if (columnName == null && columnIndex == null) {
                    // Get from context
                    result = interpreterContext.getColumnType();
                } else if (columnName != null){
                    //
                    result = metadata.getColumnTypeName(resultSet.findColumn(columnName));
                } else {
                    // Get type from index
                    result = metadata.getColumnTypeName(Integer.parseInt(columnIndex));
                }

                final char[] charResult = result.toCharArray();
                interpreterContext.getOutput().characters(charResult, 0, charResult.length);
            } else {
                // Simple getter (deprecated)
                final String columnName = (attributes.getValue("column-name") != null) ? attributes.getValue("column-name") : attributes.getValue("column");
                final int columnIndex = resultSet.findColumn(columnName);
                final Object o = getColumnValue(resultSet, getDocumentLocator(), columnIndex, getXMLTypeFromLegacyGetterName(localname));
                if (o != null) {
                    if (o instanceof Clob) {
                        final Reader reader = ((Clob) o).getCharacterStream();
                        try {
                            XMLUtils.readerToCharacters(reader, interpreterContext.getOutput());
                        } finally {
                            reader.close();
                        }
                    } else if (o instanceof Blob) {
                        final InputStream is = ((Blob) o).getBinaryStream();
                        try {
                            XMLUtils.inputStreamToBase64Characters(is, interpreterContext.getOutput());
                        } finally {
                            is.close();
                        }
                    } else if (o instanceof InputStream) {
                        final InputStream is = (InputStream) o;
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
                final ResultSet resultSet = interpreterContext.getResultSet(getColumnsLevel);
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
                    Clob clobValue = null;
                    Blob blobValue = null;
                    String stringValue = null;
                    if (columnType == Types.CLOB) {
                        clobValue = resultSet.getClob(i);
                    } else if (columnType == Types.BLOB) {
                        blobValue = resultSet.getBlob(i);
                    } else {
                        stringValue = getColumnStringValue(resultSet, i, columnType);
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

    // Mapping for legacy getters
    private static final Map getterToXMLType = new HashMap();
    static {
        getterToXMLType.put("get-string", "{http://www.w3.org/2001/XMLSchema}string");
        getterToXMLType.put("get-int", "{http://www.w3.org/2001/XMLSchema}int");
        getterToXMLType.put("get-boolean", "{http://www.w3.org/2001/XMLSchema}boolean");
        getterToXMLType.put("get-decimal", "{http://www.w3.org/2001/XMLSchema}decimal");
        getterToXMLType.put("get-float", "{http://www.w3.org/2001/XMLSchema}float");
        getterToXMLType.put("get-double", "{http://www.w3.org/2001/XMLSchema}double");
        getterToXMLType.put("get-timestamp", "{http://www.w3.org/2001/XMLSchema}dateTime");
        getterToXMLType.put("get-date", "{http://www.w3.org/2001/XMLSchema}date");
        getterToXMLType.put("get-base64binary", "{http://www.w3.org/2001/XMLSchema}base64Binary");
    }

    private static final Map sqlTypesToDefaultXMLTypes = new HashMap();
    static {
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.CHAR), "{http://www.w3.org/2001/XMLSchema}string");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.VARCHAR), "{http://www.w3.org/2001/XMLSchema}string");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.LONGVARCHAR), "{http://www.w3.org/2001/XMLSchema}string");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.NUMERIC), "{http://www.w3.org/2001/XMLSchema}decimal");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.DECIMAL), "{http://www.w3.org/2001/XMLSchema}decimal");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.BIT), "{http://www.w3.org/2001/XMLSchema}boolean");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.BOOLEAN), "{http://www.w3.org/2001/XMLSchema}boolean");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.TINYINT), "{http://www.w3.org/2001/XMLSchema}byte");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.SMALLINT), "{http://www.w3.org/2001/XMLSchema}short");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.INTEGER), "{http://www.w3.org/2001/XMLSchema}int");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.BIGINT), "{http://www.w3.org/2001/XMLSchema}long");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.REAL), "{http://www.w3.org/2001/XMLSchema}float");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.FLOAT), "{http://www.w3.org/2001/XMLSchema}double");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.DOUBLE), "{http://www.w3.org/2001/XMLSchema}double");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.BINARY), "{http://www.w3.org/2001/XMLSchema}base64Binary");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.VARBINARY), "{http://www.w3.org/2001/XMLSchema}base64Binary");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.LONGVARBINARY), "{http://www.w3.org/2001/XMLSchema}base64Binary");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.DATE), "{http://www.w3.org/2001/XMLSchema}date");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.TIME), "{http://www.w3.org/2001/XMLSchema}time");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.TIMESTAMP), "{http://www.w3.org/2001/XMLSchema}dateTime");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.CLOB), "{http://www.w3.org/2001/XMLSchema}string");
        sqlTypesToDefaultXMLTypes.put(new Integer(Types.BLOB), "{http://www.w3.org/2001/XMLSchema}base64Binary");
//        sqlTypesToDefaultXMLTypes.put(new Integer(Types.ARRAY), "{http://www.w3.org/2001/XMLSchema}");
//        sqlTypesToDefaultXMLTypes.put(new Integer(Types.DISTINCT), "{http://www.w3.org/2001/XMLSchema}");
//        sqlTypesToDefaultXMLTypes.put(new Integer(Types.STRUCT), "{http://www.w3.org/2001/XMLSchema}");
//        sqlTypesToDefaultXMLTypes.put(new Integer(Types.REF), "{http://www.w3.org/2001/XMLSchema}");
//        sqlTypesToDefaultXMLTypes.put(new Integer(Types.DATALINK), "{http://www.w3.org/2001/XMLSchema}");
//        sqlTypesToDefaultXMLTypes.put(new Integer(Types.JAVA_OBJECT), "{http://www.w3.org/2001/XMLSchema}");`
    }

//    private static final Map xmlTypesToDefaultSQLTypes = new HashMap();
//    static {
//        xmlTypesToDefaultSQLTypes.put("{http://www.w3.org/2001/XMLSchema}string", new Integer(Types.CHAR));
//
//    }

    /**
     * Return a Clob or Clob object or a String.
     */
    public static Object getColumnValue(ResultSet resultSet, Locator locator, int columnIndex, String xmlTypeName) throws SQLException {
        try {
            final int columnType = resultSet.getMetaData().getColumnType(columnIndex);
            final String defaultXMLType = (String) sqlTypesToDefaultXMLTypes.get(new Integer(columnType));
            if (xmlTypeName != null && !xmlTypeName.equals(defaultXMLType))
                throw new ValidationException("Illegal XML type for SQL type: " + xmlTypeName + ", " + resultSet.getMetaData().getColumnTypeName(columnIndex), new LocationData(locator));

            if (columnType == Types.CLOB) {
                // The actual column is a CLOB
                return resultSet.getClob(columnIndex);
            } else if (columnType == Types.BLOB) {
                // The actual column is a BLOB
                return resultSet.getBlob(columnIndex);
            } else if (columnType == Types.BINARY || columnType == Types.VARBINARY || columnType == Types.LONGVARBINARY) {
                // The actual column is binary
                return resultSet.getBinaryStream(columnIndex);
            } else {
                // The actual column is not a CLOB or BLOB, in which case we use regular ResultSet getters
                return getColumnStringValue(resultSet, columnIndex, columnType);
            }
        } catch (SQLException e) {
            throw new ValidationException("Exception while getting column: " + (resultSet.getMetaData().getColumnName(columnIndex)), e, new LocationData(locator));
        }
    }

    /**
     *
     * Return the String value of a given column using the default XML type for the given SQL type.
     *
     * This is not meant to work with CLOB and BLOB types.
     */
    public static String getColumnStringValue(ResultSet resultSet, int columnIndex, int columnType) throws SQLException {
        String stringValue = null;
        if (columnType == Types.DATE) {
            final Date value = resultSet.getDate(columnIndex);
            if (value != null)
                stringValue = ISODateUtils.formatDate(value, ISODateUtils.XS_DATE);
        } else if (columnType == Types.TIMESTAMP) {
            final Timestamp value = resultSet.getTimestamp(columnIndex);
            if (value != null)
                stringValue = ISODateUtils.formatDate(value, ISODateUtils.XS_DATE_TIME_LONG);
        } else if (columnType == Types.DECIMAL
                || columnType == Types.NUMERIC) {
            final BigDecimal value = resultSet.getBigDecimal(columnIndex);
            stringValue = (resultSet.wasNull()) ? null : value.toString();
        } else if (columnType == Types.BOOLEAN) {
            final boolean value = resultSet.getBoolean(columnIndex);
            stringValue = (resultSet.wasNull()) ? null : (value ? "true" : "false");
        } else if (columnType == Types.INTEGER
                || columnType == Types.SMALLINT
                || columnType == Types.TINYINT
                || columnType == Types.BIGINT) {
            final long value = resultSet.getLong(columnIndex);
            stringValue = (resultSet.wasNull()) ? null : Long.toString(value);
        } else if (columnType == Types.DOUBLE
                || columnType == Types.FLOAT
                || columnType == Types.REAL) {
            final double value = resultSet.getDouble(columnIndex);
            // For XPath 1.0, we have to get rid of the scientific notation
            stringValue = resultSet.wasNull() ? null : XMLUtils.removeScientificNotation(value);
        } else if (columnType == Types.CLOB) {
            throw new OXFException("Cannot get String value for CLOB type.");
        } else if (columnType == Types.BLOB) {
            throw new OXFException("Cannot get String value for BLOB type.");
        } else {
            // Assume the type is compatible with getString()
            stringValue = resultSet.getString(columnIndex);
        }
        return stringValue;
    }

    public static String getXMLTypeFromLegacyGetterName(String getterName) {
        return (String) getterToXMLType.get(getterName);
    }

    public static String getXMLTypeFromAttributeStringHandleDefault(Locator locator, PropertySet propertySet, String typeAttribute, Map prefixesMap, int columnType) {
        String xmlType;
        if (typeAttribute != null) {
            // User specified an XML type
            xmlType = getXMLTypeFromAttributeString(locator, propertySet, typeAttribute, prefixesMap);
        } else {
            // Get default XML type for SQL type
            xmlType = getDefaultXMLTypeFromSQLType(columnType);
        }
        return xmlType;
    }

    public static String getDefaultXMLTypeFromSQLType(int type) {
        return (String) sqlTypesToDefaultXMLTypes.get(new Integer(type));
    }

    public static String getXMLTypeFromAttributeString(Locator locator, PropertySet propertySet, String typeAttribute, Map prefixesMap) {

        final int colonIndex = typeAttribute.indexOf(':');
        if (colonIndex < 1)
            throw new ValidationException("Invalid column type:" + typeAttribute, new LocationData(locator));

        final String typePrefix = typeAttribute.substring(0, colonIndex);
        final String typeLocalname = typeAttribute.substring(colonIndex + 1);

        final String typeURI;
        if (prefixesMap.get(typePrefix) == null && !(Boolean.TRUE.equals(propertySet.getBoolean("legacy-implicit-prefixes")))) {
            throw new ValidationException("Undeclared type prefix for type:" + typeAttribute, new LocationData(locator));
        } else if (prefixesMap.get(typePrefix) == null) {
            // LEGACY BEHAVIOR: use implicit prefixes
            if (typePrefix.equals("xs"))
                typeURI = XMLConstants.XSD_URI;
            else if (typePrefix.equals("oxf"))
                typeURI = XMLConstants.OPS_TYPES_URI;
            else
                throw new ValidationException("Invalid type prefix for type:" + typeAttribute, new LocationData(locator));
        } else {
            // NEW BEHAVIOR: use actual mappings
            typeURI = (String) prefixesMap.get(typePrefix);
        }

        return "{" + typeURI + "}" + typeLocalname;
    }
}