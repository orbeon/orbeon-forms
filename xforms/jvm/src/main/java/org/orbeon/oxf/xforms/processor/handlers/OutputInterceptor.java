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

import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.xml.*;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Intercept SAX output and annotate resulting elements and/or text with classes and spans.
 */
public class OutputInterceptor extends ForwardingXMLReceiver {

    private final String spanQName;
    private final Listener beginDelimiterListener;
    private final boolean isAroundTableOrListElement;

    private boolean gotElements = false;

    private String delimiterNamespaceURI;
    private String delimiterPrefix;
    private String delimiterLocalName;

    private String addedClasses;

    private boolean mustGenerateFirstDelimiters = true;

    private int level = 0;
    private StringBuilder currentCharacters = new StringBuilder();

    protected AttributesImpl reusableAttributes = new AttributesImpl();

    public OutputInterceptor(XMLReceiver output, String spanQName, Listener beginDelimiterListener, boolean isAroundTableOrListElement) {
        super(output);
        this.spanQName = spanQName;
        this.beginDelimiterListener = beginDelimiterListener;
        this.isAroundTableOrListElement = isAroundTableOrListElement;

        // Default to <xhtml:span>
        delimiterNamespaceURI = XMLConstants.XHTML_NAMESPACE_URI;
        delimiterPrefix       = XMLUtils.prefixFromQName(spanQName);
        delimiterLocalName    = XMLUtils.localNameFromQName(spanQName);
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        level++;
        final boolean topLevelElement = level == 1;

        if (! gotElements) {
            // Override default as we just go an element

            assert topLevelElement;

            delimiterNamespaceURI = uri;
            delimiterPrefix       = XMLUtils.prefixFromQName(qName);
            delimiterLocalName    = XMLUtils.localNameFromQName(qName);

            gotElements = true;
        }

        flushCharacters(false, topLevelElement);
        generateFirstDelimitersIfNeeded();

        // Add or update classes on element if needed
        super.startElement(uri, localname, qName, topLevelElement ? getAttributesWithClass(attributes) : attributes);
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        flushCharacters(false, false);
        super.endElement(uri, localname, qName);

        level--;
    }

    public void characters(char[] chars, int start, int length) {
        currentCharacters.append(chars, start, length);
    }

    public void flushCharacters(boolean finalFlush, boolean topLevelCharacters) throws SAXException {

        final String currentString = currentCharacters.toString();

        if (topLevelCharacters && ! isAroundTableOrListElement) {
            // We handle top-level characters specially and wrap them in a span so we can hide them
            generateTopLevelSpanWithCharacters(currentCharacters.toString());
        } else {
            // Just output characters as is in deeper levels, or when around at table or list element
            final char[] chars = currentString.toCharArray();
            super.characters(chars, 0, chars.length);
        }

        currentCharacters.setLength(0);

        if (finalFlush)
            generateFirstDelimitersIfNeeded();
    }

    private void generateTopLevelSpanWithCharacters(String characters) throws SAXException {
        // The first element received determines the type of separator
        generateFirstDelimitersIfNeeded();

        // Wrap any other text within an xhtml:span
        super.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, getAttributesWithClass(SAXUtils.EMPTY_ATTRIBUTES));
        final char[] chars = characters.toCharArray();
        super.characters(chars, 0, chars.length);
        super.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }

    public void generateFirstDelimitersIfNeeded() throws SAXException {
        if (mustGenerateFirstDelimiters) {
            // Generate first delimiter
            beginDelimiterListener.generateFirstDelimiter(this);
            mustGenerateFirstDelimiters = false;
        }
    }

    private Attributes getAttributesWithClass(Attributes originalAttributes) {
        String newClassAttribute = originalAttributes.getValue("class");

        if (addedClasses != null && addedClasses.length() > 0) {
            if (newClassAttribute == null || newClassAttribute.length() == 0) {
                newClassAttribute = addedClasses;
            } else {
                newClassAttribute += " " + addedClasses;
            }
        }

        if (newClassAttribute != null)
            return SAXUtils.addOrReplaceAttribute(originalAttributes, "", "", "class", newClassAttribute);
        else
            return originalAttributes;
    }

    public boolean isMustGenerateFirstDelimiters() {
        return mustGenerateFirstDelimiters;
    }

    public void setAddedClasses(String addedClasses) {
        this.addedClasses = addedClasses;
    }

    public void outputDelimiter(ContentHandler contentHandler, String classes, String id) throws SAXException {

        reusableAttributes.clear();
        if (id != null)
            reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, id);

        if (classes != null)
            reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, classes);

        final String delimiterQName = XMLUtils.buildQName(delimiterPrefix, delimiterLocalName);
        contentHandler.startElement(delimiterNamespaceURI, delimiterLocalName, delimiterQName, reusableAttributes);
        contentHandler.endElement(delimiterNamespaceURI, delimiterLocalName, delimiterQName);
    }

    public interface Listener {
        public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException;
    }
}
