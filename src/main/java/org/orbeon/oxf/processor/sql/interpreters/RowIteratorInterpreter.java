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

import org.jaxen.Function;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.xml.DeferredXMLReceiver;
import org.orbeon.oxf.xml.DeferredXMLReceiverImpl;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class RowIteratorInterpreter extends SQLProcessor.InterpreterContentHandler {

    private DeferredXMLReceiver savedOutput;

    private boolean hiding;
    private int rowNum = 1;
    private int groupCount = 0;
    private List groups;

    public RowIteratorInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        // Repeating interpreter
        super(interpreterContext, true);
        setForward(true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        addAllDefaultElementHandlers();

        final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();

        final ResultSet resultSet = interpreterContext.getResultSet();
        try {
            boolean hasNext = !interpreterContext.isEmptyResultSet();

            if (SQLProcessor.logger.isDebugEnabled())
                SQLProcessor.logger.debug("Preparing to execute row: hasNext = " + hasNext + ", statement = " + interpreterContext.getStatementString());

            // Functions in this context
            Map functions = new HashMap();
            functions.put("{" + SQLProcessor.SQL_NAMESPACE_URI + "}" + "row-position", new Function() {
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
                        groupCount = 0;
                    }
                    // Set variables
                    interpreterContext.setRowPosition(rowNum);

                    if (SQLProcessor.logger.isDebugEnabled())
                        SQLProcessor.logger.debug("Execute row: rowNum = " + rowNum);

                    // Interpret row
                    repeatBody();
                    // Go to following row
                    hasNext = resultSet.next();
                    rowNum++;
                }
                // Output last footers
                if (groups != null) {
                    for (int i = groups.size() - 1; i >= 0; i--) {
                        Group group = (Group) groups.get(i);
                        group.getFooter().replay(interpreterContext.getOutput());
                        group.getFooter().clear();
                    }
                }
            } finally {
                interpreterContext.popFunctions();
            }
        } catch (Exception e) {
            throw new ValidationException(e, new LocationData(getDocumentLocator()));
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        // Restore state
        hiding = false;
        rowNum = 1;
        groupCount = 0;
        groups = null;
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        super.startPrefixMapping(prefix, uri);
        getInterpreterContext().declarePrefix(prefix, uri);
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (!isInElementHandler() && SQLProcessor.SQL_NAMESPACE_URI.equals(uri)) {
//        if (SQLProcessor.SQL_NAMESPACE_URI.equals(uri)) {
            if (localname.equals("group")) {
                if (groups == null)
                    groups = new ArrayList();
                try {
                    ResultSet resultSet = getInterpreterContext().getResultSet();
                    // Save group information if first row
                    if (rowNum == 1) {
                        final String columnName = (attributes.getValue("column-name") != null) ? attributes.getValue("column-name") : attributes.getValue("column");
                        groups.add(new Group(columnName, resultSet));
                    }

                    // Get current group information
                    Group currentGroup = (Group) groups.get(groupCount);

                    if (rowNum == 1 || columnChanged(resultSet, groups, groupCount)) {
                        // Need to display group's header and footer
                        currentGroup.setShowHeader(true);
                        hiding = false;
                        currentGroup.setColumnValue(resultSet);
                    } else {
                        // Hide group's header
                        currentGroup.setShowHeader(false);
                        hiding = true;
                    }

                    groupCount++;

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
        if (!isInElementHandler() && SQLProcessor.SQL_NAMESPACE_URI.equals(uri)) {
//        if (SQLProcessor.SQL_NAMESPACE_URI.equals(uri)) {
            final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
            if (localname.equals("group")) {
                groupCount--;
                // Restore sending to the regular output
                Group currentGroup = (Group) groups.get(groupCount);
                if (currentGroup.isShowHeader())
                    interpreterContext.setOutput(savedOutput);
            } else if (localname.equals("member")) {
                Group currentGroup = (Group) groups.get(groupCount - 1);
                // The first time, everything is sent to the footer SAXStore
                if (currentGroup.isShowHeader()) {
                    savedOutput = interpreterContext.getOutput();
                    interpreterContext.setOutput(new DeferredXMLReceiverImpl(currentGroup.getFooter()));
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

    private boolean columnChanged(ResultSet resultSet, List groups, int level) throws SQLException {
        for (int i = level; i >= 0; i--) {
            Group group = (Group) groups.get(i);
            if (group.columnChanged(resultSet))
                return true;
        }
        return false;
    }

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
}