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

import org.dom4j.Node;
import org.jaxen.UnresolvableException;
import org.jaxen.VariableContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.trans.XPathException;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.NamespaceSupport;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 */
public class ValueOfCopyOfInterpreter extends SQLProcessor.InterpreterContentHandler {
    final DocumentWrapper wrapper;

    public ValueOfCopyOfInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, false);
        this.wrapper = new DocumentWrapper(interpreterContext.getCurrentNode().getDocument(), null, XPathCache.getGlobalConfiguration());
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
                    if (!SQLProcessor.SQL_NAMESPACE_URI.equals(namespaceURI))
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
                    final List listResult = (List) result;
                    if ("value-of".equals(localname)) {
                        // Get string value

                        final String stringValue;
                        if (listResult.size() > 0) {
                            // Wrap all elements of the list
                            final List newList = new ArrayList(listResult.size());
                            for (Iterator i = listResult.iterator(); i.hasNext();) {
                                newList.add(wrapper.wrap(i.next()));
                            }

                            stringValue = XPathCache.evaluateAsString(newList, 1, "string(.)",
                                null, null, null, null, null, null);
                        } else {
                            // It's just an empty list
                            stringValue = "";
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
                        // TODO: use XPathCache.evaluateAsString()
                        PooledXPathExpression expr = XPathCache.getXPathExpression(
                                wrapper.getConfiguration(), wrapper.wrap(result), "string(.)", null);
                        String stringValue;
                        try {
                            stringValue = (String) expr.evaluateSingle();
                        } catch (XPathException e) {
                            throw new OXFException(e);
                        } finally {
                            if (expr != null)
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