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

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SAXStore extends ForwardingContentHandler implements Serializable {

    public static final byte START_DOCUMENT = 0x00;
    public static final byte END_DOCUMENT = 0x01;
    public static final byte START_ELEMENT = 0x02;
    public static final byte END_ELEMENT = 0x03;
    public static final byte CHARACTERS = 0x04;
    public static final byte END_PREFIX_MAPPING = 0x05;
    public static final byte IGN_WHITESPACE = 0x06;
    public static final byte PI = 0x07;
    public static final byte DOC_LOCATOR = 0x08;
    public static final byte SKIPPED_ENTITY = 0x09;
    public static final byte START_PREFIX_MAPPING = 0x0A;

    private static final int INITIAL_SIZE = 2;

    private byte[] eventBuffer;
    private int eventBufferPosition;

    private char[] charBuffer;
    private int charBufferPosition;

    private int[] intBuffer;
    private int intBufferPosition;

    private int[] lineBuffer;
    private int lineBufferPosition;

    private int[] attributeCountBuffer;
    private int attributeCountBufferPosition;

    private List stringBuffer = new ArrayList();

    private Locator locator = null;
    private String systemId = null;
    private String publicId = null;

    public SAXStore() {
        super.setForward(false);
        init();
    }

    public SAXStore(ContentHandler contentHandler) {
        super.setContentHandler(contentHandler);
        super.setForward(true);
        init();
    }

    public Object getValidity() {
        return new Long(eventBuffer.hashCode() * charBuffer.hashCode() * intBuffer.hashCode());
    }

    protected void init() {
        eventBufferPosition = 0;
        eventBuffer = new byte[INITIAL_SIZE];

        charBufferPosition = 0;
        charBuffer = new char[INITIAL_SIZE * 4];

        intBufferPosition = 0;
        intBuffer = new int[INITIAL_SIZE];

        lineBufferPosition = 0;
        lineBuffer = new int[INITIAL_SIZE];

        attributeCountBufferPosition = 0;
        attributeCountBuffer = new int[INITIAL_SIZE];

        stringBuffer.clear();

        locator = null;
    }


    public void replay(ContentHandler ch) throws SAXException {
        int intBufferPos = 0;
        int charBufferPos = 0;
        int stringBufferPos = 0;
        int attributeCountBufferPos = 0;
        final int[] lineBufferPos = { 0 } ;
        AttributesImpl attributes = new AttributesImpl();
        int length;
        int event = 0;

        Locator currentLocator = (locator == null) ? null : new Locator() {
            public String getPublicId() {
                return publicId;
            }

            public String getSystemId() {
                return systemId;
            }

            public int getLineNumber() {
                try {
                    return lineBuffer[lineBufferPos[0]];
                } catch (ArrayIndexOutOfBoundsException e) {// FIXME: sometimes this fails
//                    System.out.println("Incorrect line number: " + lineBufferPos[0]);
                    //e.printStackTrace();
                    return -1;
                }
            }

            public int getColumnNumber() {
                try {
                    return lineBuffer[lineBufferPos[0] + 1];
                } catch (ArrayIndexOutOfBoundsException e) {// FIXME: sometimes this fails
//                    System.out.println("Incorrect line number: " + lineBufferPos[0]);
                    //e.printStackTrace();
                    return -1;
                }
            }
        };


        while (event < eventBufferPosition) {
            switch (eventBuffer[event]) {
                case START_DOCUMENT:
                    ch.startDocument();
                    break;
                case START_ELEMENT:
                    String namespaceURI = (String) stringBuffer.get(stringBufferPos++);
                    String localName = (String) stringBuffer.get(stringBufferPos++);
                    String qName = (String) stringBuffer.get(stringBufferPos++);
                    attributes.clear();
                    int attributeCount = attributeCountBuffer[attributeCountBufferPos++];
                    for (int i = 0; i < attributeCount; i++) {
                        attributes.addAttribute((String) stringBuffer.get(stringBufferPos++),
                                (String) stringBuffer.get(stringBufferPos++), (String) stringBuffer.get(stringBufferPos++),
                                (String) stringBuffer.get(stringBufferPos++), (String) stringBuffer.get(stringBufferPos++));
                    }
                    ch.startElement(namespaceURI, localName, qName, attributes);
                    break;
                case CHARACTERS:
                    length = intBuffer[intBufferPos++];
                    ch.characters(charBuffer, charBufferPos, length);
                    charBufferPos += length;
                    break;
                case END_ELEMENT:
                    ch.endElement((String) stringBuffer.get(stringBufferPos++),
                            (String) stringBuffer.get(stringBufferPos++),
                            (String) stringBuffer.get(stringBufferPos++));
                    break;
                case END_DOCUMENT:
                    ch.endDocument();
                    break;
                case END_PREFIX_MAPPING:
                    ch.endPrefixMapping((String) stringBuffer.get(stringBufferPos++));
                    break;
                case IGN_WHITESPACE:
                    length = intBuffer[intBufferPos++];
                    ch.ignorableWhitespace(charBuffer, charBufferPos, length);
                    charBufferPos += length;
                    break;
                case PI:
                    ch.processingInstruction((String) stringBuffer.get(stringBufferPos++),
                            (String) stringBuffer.get(stringBufferPos++));
                    break;
                case DOC_LOCATOR:
                    ch.setDocumentLocator(currentLocator);
                    break;
                case SKIPPED_ENTITY:
                    ch.skippedEntity((String) stringBuffer.get(stringBufferPos++));
                    break;
                case START_PREFIX_MAPPING:
                    ch.startPrefixMapping((String) stringBuffer.get(stringBufferPos++),
                            (String) stringBuffer.get(stringBufferPos++));
                    break;
            }
            event++;
            if(locator != null)
                lineBufferPos[0] += 2;
        }

    }

    /**
     * This prints the instance with extra annotation attributes to System.out. For debug only.
     */
    public void readOut() {
        try {
            final TransformerHandler  th = TransformerUtils.getIdentityTransformerHandler();
            th.setResult(new StreamResult(System.out));
            th.startDocument();
            replay(th);
            th.endDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void clear() {
        init();
    }

    public Document getDocument() {
        try {
            LocationSAXContentHandler ch = new LocationSAXContentHandler();
            replay(ch);
            return ch.getDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        addToEventBuffer(CHARACTERS);
        addToCharBuffer(chars, start, length);
        addToIntBuffer(length);

        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
        }

        super.characters(chars, start, length);
    }

    public void endDocument() throws SAXException {
        addToEventBuffer(END_DOCUMENT);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
        }
        super.endDocument();
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        addToEventBuffer(END_ELEMENT);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
        }
        stringBuffer.add(uri);
        stringBuffer.add(localname);
        stringBuffer.add(qName);

        super.endElement(uri, localname, qName);
    }

    public void endPrefixMapping(String s) throws SAXException {
        addToEventBuffer(END_PREFIX_MAPPING);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
        }
        stringBuffer.add(s);

        super.endPrefixMapping(s);
    }

    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
        addToEventBuffer(IGN_WHITESPACE);
        addToCharBuffer(chars, start, length);
        addToIntBuffer(length);

        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
        }

        super.ignorableWhitespace(chars, start, length);
    }

    public void processingInstruction(String s, String s1) throws SAXException {
        addToEventBuffer(PI);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
        }
        stringBuffer.add(s);
        stringBuffer.add(s1);

        super.processingInstruction(s, s1);
    }

    public void setDocumentLocator(Locator locator) {
        addToEventBuffer(DOC_LOCATOR);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
        }
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    public void skippedEntity(String s) throws SAXException {
        addToEventBuffer(SKIPPED_ENTITY);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
        }
        stringBuffer.add(s);

        super.skippedEntity(s);
    }

    public void startDocument() throws SAXException {
        addToEventBuffer(START_DOCUMENT);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
        }
        super.startDocument();
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        addToEventBuffer(START_ELEMENT);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            if (systemId == null && locator.getSystemId() != null)
                systemId = locator.getSystemId();
            if (publicId == null && locator.getPublicId() != null)
                publicId = locator.getPublicId();
        }
        stringBuffer.add(uri);
        stringBuffer.add(localname);
        stringBuffer.add(qName);

        addToAttributeBuffer(attributes);

        super.startElement(uri, localname, qName, attributes);
    }

    public void startPrefixMapping(String s, String s1) throws SAXException {
        addToEventBuffer(START_PREFIX_MAPPING);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
        }
        stringBuffer.add(s);
        stringBuffer.add(s1);

        super.startPrefixMapping(s, s1);
    }


    protected void addToCharBuffer(char[] chars, int start, int length) {
        if (charBuffer.length - charBufferPosition <= length) {
            // double the array
            char[] old = charBuffer;
            charBuffer = new char[old.length * 2];
            System.arraycopy(old, 0, charBuffer, 0, charBufferPosition);
            addToCharBuffer(chars, start, length);
        } else {
            System.arraycopy(chars, start, charBuffer, charBufferPosition, length);
            charBufferPosition += length;
        }
    }

    protected void addToIntBuffer(int i) {
        if (intBuffer.length - intBufferPosition == 1) {
            // double the array
            int[] old = intBuffer;
            intBuffer = new int[old.length * 2];
            System.arraycopy(old, 0, intBuffer, 0, intBufferPosition);
            addToIntBuffer(i);
        } else {
            intBuffer[intBufferPosition++] = i;
        }
    }

    protected void addToLineBuffer(int i) {
        if (lineBuffer.length - lineBufferPosition == 1) {
            // double the array
            int[] old = lineBuffer;
            lineBuffer = new int[old.length * 2];
            System.arraycopy(old, 0, lineBuffer, 0, lineBufferPosition);
            addToLineBuffer(i);
        } else {
            lineBuffer[lineBufferPosition++] = i;
        }
    }

    protected void addToEventBuffer(byte b) {
        if (eventBuffer.length - eventBufferPosition == 1) {
            // double the array
            byte[] old = eventBuffer;
            eventBuffer = new byte[old.length * 2];
            System.arraycopy(old, 0, eventBuffer, 0, eventBufferPosition);
            addToEventBuffer(b);
        } else {
            eventBuffer[eventBufferPosition++] = b;
        }
    }

    private void addToAttributeBuffer(Attributes attributes) {
        if (attributeCountBuffer.length - attributeCountBufferPosition == 1) {
            // double the array
            int[] old = attributeCountBuffer;
            attributeCountBuffer = new int[old.length * 2];
            System.arraycopy(old, 0, attributeCountBuffer, 0, attributeCountBufferPosition);
            addToAttributeBuffer(attributes);
        } else {
            attributeCountBuffer[attributeCountBufferPosition++] = attributes.getLength();
            for (int i = 0; i < attributes.getLength(); i++) {
                stringBuffer.add(attributes.getURI(i));
                stringBuffer.add(attributes.getLocalName(i));
                stringBuffer.add(attributes.getQName(i));
                stringBuffer.add(attributes.getType(i));
                stringBuffer.add(attributes.getValue(i));
            }
        }
    }
}
