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
package org.orbeon.oxf.xml;

import org.orbeon.oxf.common.OXFException;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements a streamable subset of XPath.
 *
 * This implementation requires that all searched XPath expressions be set beforehand.
 *
 * The class can be used as a regular ContentHandler, or can use a callback to start reading the
 * input SAX stream.
 *
 * Right now, only the first result asked will be streamed. All other results will be stored in SAX
 * stores. This is not optimal, but since there are no coroutines in Java, it is hard to implement
 * the more efficient way, which is to store only what is strictly required. In the best case
 * scenario, if the matches occur in the document in the order asked, everything will be streamed
 * (at least, in single node mode).
 *
 * TODO:
 *       o Do we need to build a stack of prefix mappings?
 *       o Implement other expressions!
 *       o Handle single node vs. all nodes
 *       o Handle duplicate expressions (e.g. /a/b/c added several times)
 */
public class XPathContentHandler implements ContentHandler {

    private Map expressions;
    private List expressionsList;
    private Expression[] expressionsArray;
    private boolean canStream = true;
    private Runnable readInputCallback;
    private boolean readStarted;

    private Expression searchedExpression;
    private ContentHandler output;

    private static class Expression {
        public Expression(String xpathExpression) {
            this.xpathExpression = xpathExpression;
        }

        public String xpathExpression;
        public ContentHandler expressionContentHandler;
        public SAXStore result;
    }

    public XPathContentHandler() {
    }

    public boolean addExpresssion(String xpathExpression, boolean nodeSet) {
        if (!supportsExpression(xpathExpression))
            canStream = false;
        if (expressions == null) {
            expressions = new HashMap();
            expressionsList = new ArrayList();
        }
        // TODO: Detect duplicate expressions?
        // Create expression
        Expression expression = new Expression(xpathExpression);
        expression.expressionContentHandler = getExpressionContentHandler(expression);

        expressions.put(xpathExpression, expression);
        expressionsList.add(expression);

        return canStream;
    }

    public boolean containsExpression(String xpathExpression) {
        return expressions.containsKey(xpathExpression);
    }

    public void setReadInputCallback(Runnable readInputCallback) {
        this.readInputCallback = readInputCallback;
    }

    public void selectContentHandler(String xpathExpression, ContentHandler contentHandler) throws SAXException {
        if (!canStream)
            throw new OXFException("Cannot stream");
        Expression expression = (Expression) expressions.get(xpathExpression);
        if (expression == null)
            throw new OXFException("Undefined expression: " + xpathExpression);

        // Check if the expression has already been computed
        if (expression.result != null) {
            expression.result.replay(contentHandler);
            return;
        }

        // Start the read if needed
        if (!readStarted) {
            if (readInputCallback == null)
                throw new OXFException("Read not started and no read callback specified");
            searchedExpression = expression;
            output = contentHandler;
            readInputCallback.run();
            // At this point, all the stream is read
            searchedExpression = null;
            output = null;
        }

        // Well, if we had coroutines, we could continue the search from here. Instead, we have to
        // compute everything before. If we get here, it means we got an empty result.
    }

    public static boolean supportsExpression(String xpathExpression) {
        return getExpressionId(xpathExpression) != -1;
    }

    private static int getExpressionId(String xpathExpression) {
        // Add other expressions here when implemented
        // Identity transformation
        if ("/*".equals(xpathExpression)) // should also match on "/" ? can also match on /abc, /abc[1], etc.
            return 1;
        return -1;
    }

    private ContentHandler getExpressionContentHandler(Expression expression) {
        int expressionId = getExpressionId(expression.xpathExpression);
        switch (expressionId) {
            case 1:
                return new Handler1(expression);
//            case 2:
//                return new Handler2(expression);
            // Etc.
            default:
                return null;
        }
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        for (int i = 0; i < expressionsArray.length; i++)
            expressionsArray[i].expressionContentHandler.characters(ch, start, length);
    }

    public void endDocument() throws SAXException {
        for (int i = 0; i < expressionsArray.length; i++)
            expressionsArray[i].expressionContentHandler.endDocument();
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        for (int i = 0; i < expressionsArray.length; i++)
            expressionsArray[i].expressionContentHandler.endElement(namespaceURI, localName, qName);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        for (int i = 0; i < expressionsArray.length; i++)
            expressionsArray[i].expressionContentHandler.endPrefixMapping(prefix);
    }

    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
        for (int i = 0; i < expressionsArray.length; i++)
            expressionsArray[i].expressionContentHandler.ignorableWhitespace(ch, start,  length);
    }

    public void processingInstruction(String target, String data) throws SAXException {
        for (int i = 0; i < expressionsArray.length; i++)
            expressionsArray[i].expressionContentHandler.processingInstruction(target, data);
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void skippedEntity(String name) throws SAXException {
        for (int i = 0; i < expressionsArray.length; i++)
            expressionsArray[i].expressionContentHandler.skippedEntity(name);
    }

    public void startDocument() throws SAXException {
        // Setup expression content handlers
        expressionsArray = new Expression[expressionsList.size()];
        expressionsList.toArray(expressionsArray);
        readStarted = true;

        for (int i = 0; i < expressionsArray.length; i++)
            expressionsArray[i].expressionContentHandler.startDocument();
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        for (int i = 0; i < expressionsArray.length; i++)
            expressionsArray[i].expressionContentHandler.startElement(namespaceURI, localName, qName, atts);
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        for (int i = 0; i < expressionsArray.length; i++)
            expressionsArray[i].expressionContentHandler.startPrefixMapping(prefix, uri);
    }

    private static abstract class ExpressionSearchHandler extends ForwardingContentHandler {
        protected Expression expresssion;

        protected ExpressionSearchHandler(Expression expresssion) {
            this.expresssion = expresssion;
        }
    }

    /**
     * The root element.
     */
    private class Handler1 extends ExpressionSearchHandler {

        private int level = 0;

        public Handler1(Expression expresssion) {
            super(expresssion);
        }

        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
            level++;
            if (level == 1) {
                // Found!
                setForward(true);
                if (searchedExpression == expresssion) {
                    // Output directly
                    setContentHandler(output);
                } else {
                    // Store result
                    expresssion.result = new SAXStore();
                    setContentHandler(expresssion.result);
                }
            }
            super.startElement(namespaceURI, localName, qName, atts);
        }

        public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
            level--;
            super.endElement(namespaceURI, localName, qName);
            if (level == 0)
                setForward(false);
        }
    }

//    private class Handler2 extends ExpressionSearchHandler {
//
//        public Handler2(Expression expresssion) {
//            super(expresssion);
//        }
//
//        public void characters(char ch[], int start, int length) throws SAXException {
//            output.characters(ch, start, length);
//        }
//
//        public void endDocument() throws SAXException {
//            output.endDocument();
//        }
//
//        public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
//            output.endElement(namespaceURI, localName, qName);
//        }
//
//        public void endPrefixMapping(String prefix) throws SAXException {
//            output.endPrefixMapping(prefix);
//        }
//
//        public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
//            output.ignorableWhitespace(ch, start,  length);
//        }
//
//        public void processingInstruction(String target, String data) throws SAXException {
//            output.processingInstruction(target, data);
//        }
//
//        public void setDocumentLocator(Locator locator) {
//        }
//
//        public void skippedEntity(String name) throws SAXException {
//            output.skippedEntity(name);
//        }
//
//        public void startDocument() throws SAXException {
//            output.startDocument();
//        }
//
//        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
//            output.startElement(namespaceURI, localName, qName, atts);
//        }
//
//        public void startPrefixMapping(String prefix, String uri) throws SAXException {
//            output.startPrefixMapping(prefix, uri);
//        }
//    }
}
