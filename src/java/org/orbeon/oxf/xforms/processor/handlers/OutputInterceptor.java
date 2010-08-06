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
package org.orbeon.oxf.xforms.processor.handlers;

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xml.*;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Intercept SAX output and annotate resulting elements and/or text with classes and spans.
 */
public class OutputInterceptor extends ForwardingXMLReceiver {

    private Listener beginDelimiterListener;

    private String spanQName;

    private String delimiterNamespaceURI;
    private String delimiterPrefix;
    private String delimiterLocalName;

    private String addedClasses;

    private boolean mustGenerateFirstDelimiters = true ;

    private int level;
    private boolean isCharacters;
    private StringBuilder currentCharacters = new StringBuilder();

    protected AttributesImpl reusableAttributes = new AttributesImpl();
    public OutputInterceptor(XMLReceiver output, String spanQName, Listener beginDelimiterListener) {
        super(output);
        this.spanQName = spanQName;
        this.beginDelimiterListener = beginDelimiterListener;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        level++;
        final boolean topLevel = level == 1;

        flushCharacters(false, topLevel);

        // The first element received determines the type of separator
        checkDelimiters(uri, qName, topLevel);

        // Add or update classes on element if needed
        super.startElement(uri, localname, qName, topLevel ? getAttributesWithClass(attributes) : attributes);
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        flushCharacters(false, false);
        super.endElement(uri, localname, qName);

        level--;
    }

    public void characters(char[] chars, int start, int length) {
        currentCharacters.append(chars, start, length);
        isCharacters = true;
    }

    public void flushCharacters(boolean finalFlush, boolean topLevel) throws SAXException {

        if (currentCharacters.length() > 0) {

            final String currentString = currentCharacters.toString();
            final char[] chars = currentString.toCharArray();
            if (StringUtils.isBlank(currentString) || !topLevel) {
                // Just output whitespace as is
                super.characters(chars, 0, chars.length);
            } else {

                // The first element received determines the type of separator
                checkDelimiters(XMLConstants.XHTML_NAMESPACE_URI, spanQName, topLevel);

                // Wrap any other text within an xhtml:span
                super.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, getAttributesWithClass(XMLUtils.EMPTY_ATTRIBUTES));
                super.characters(chars, 0, chars.length);
                super.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }

            isCharacters = false;
            currentCharacters.setLength(0);
        }

        if (finalFlush)
            checkDelimiters(XMLConstants.XHTML_NAMESPACE_URI, spanQName, topLevel);
    }

    private void checkDelimiters(String uri, String qName, boolean topLevel) throws SAXException {

        if (topLevel && delimiterNamespaceURI == null) {
            delimiterNamespaceURI = uri;
            delimiterPrefix = XMLUtils.prefixFromQName(qName);
            delimiterLocalName = XMLUtils.localNameFromQName(qName);
        }

        if (mustGenerateFirstDelimiters) {
            // Generate first delimiter
            beginDelimiterListener.generateFirstDelimiter(this);
            mustGenerateFirstDelimiters = false;
        }
    }

    private Attributes getAttributesWithClass(Attributes originalAttributes) {
        String newClassAttribute = originalAttributes.getValue("class");

        if (addedClasses != null && addedClasses.length() > 0) {
            if (newClassAttribute == null) {
                newClassAttribute = addedClasses;
            } else {
                newClassAttribute += " " + addedClasses;
            }
        }

        if (newClassAttribute != null)
            return XMLUtils.addOrReplaceAttribute(originalAttributes, "", "", "class", newClassAttribute);
        else
            return originalAttributes;
    }

    public String getDelimiterNamespaceURI() {
        return delimiterNamespaceURI;
    }

    public String getDelimiterPrefix() {
        return delimiterPrefix;
    }

    public String getDelimiterLocalName() {
        return delimiterLocalName;
    }

    public boolean isMustGenerateFirstDelimiters() {
        return mustGenerateFirstDelimiters;
    }

    public void setAddedClasses(String addedClasses) {
        this.addedClasses = addedClasses;
    }

    public void outputDelimiter(ContentHandler contentHandler, String delimiterNamespaceURI, String delimiterPrefix, String delimiterLocalName, String classes, String id) throws SAXException {

        reusableAttributes.clear();
        if (id != null)
            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, id);

        if (classes != null)
            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, classes);

        final String delimiterQName = XMLUtils.buildQName(delimiterPrefix, delimiterLocalName);
        contentHandler.startElement(delimiterNamespaceURI, delimiterLocalName, delimiterQName, reusableAttributes);
        contentHandler.endElement(delimiterNamespaceURI, delimiterLocalName, delimiterQName);
    }

    public interface Listener {
        public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException;
    }
}
