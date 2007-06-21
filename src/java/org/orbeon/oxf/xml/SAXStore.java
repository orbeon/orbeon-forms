/**
 *  Copyright (C) 2004-2007 Orbeon, Inc.
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
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SAXStore keeps a compact representation of SAX events sent to the ContentHandler interface.
 *
 * TODO: Handling of system IDs is not optimal in memory as system IDs are unlikely to change much within a document.
 * TODO: For "large" documents, growing by doubling the capacity is not optimal.
 */
public class SAXStore extends ForwardingContentHandler implements Serializable, Externalizable {

    public static final byte START_DOCUMENT = 0x00;
    public static final byte END_DOCUMENT = 0x01;
    public static final byte START_ELEMENT = 0x02;
    public static final byte END_ELEMENT = 0x03;
    public static final byte CHARACTERS = 0x04;
    public static final byte END_PREFIX_MAPPING = 0x05;
    public static final byte IGN_WHITESPACE = 0x06;
    public static final byte PI = 0x07;
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

    private String[] systemIdBuffer;
    private int systemIdBufferPosition;

    private int[] attributeCountBuffer;
    private int attributeCountBufferPosition;

    private List stringBuffer = new ArrayList();

    private boolean hasDocumentLocator;
    private String publicId;

    private transient Locator locator; // used only for recording events

    public SAXStore() {
        super.setForward(false);
        init();
    }

    public SAXStore(ObjectInput input) {
        super.setForward(false);
        try {
            readExternal(input);
        } catch (Exception e) {
            throw new OXFException(e);
        }
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

        systemIdBufferPosition = 0;
        systemIdBuffer = new String[INITIAL_SIZE];

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
        final int[] systemIdBufferPos = { 0 } ;
        AttributesImpl attributes = new AttributesImpl();
        int length;
        int event = 0;

        final Locator outputLocator = !hasDocumentLocator ? null : new Locator() {
            public String getPublicId() {
                return publicId;
            }

            public String getSystemId() {
                try {
                    return systemIdBuffer[systemIdBufferPos[0]];
                } catch (ArrayIndexOutOfBoundsException e) {
                    return null;
                }
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

        if (hasDocumentLocator) {
            ch.setDocumentLocator(outputLocator);
        }

        while (event < eventBufferPosition) {
            final byte eventType = eventBuffer[event];
            final boolean eventHasLocation = hasDocumentLocator && eventType != END_PREFIX_MAPPING && eventType != START_PREFIX_MAPPING;
            switch (eventType) {
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
                case SKIPPED_ENTITY:
                    ch.skippedEntity((String) stringBuffer.get(stringBufferPos++));
                    break;
                case START_PREFIX_MAPPING:
                    ch.startPrefixMapping((String) stringBuffer.get(stringBufferPos++),
                            (String) stringBuffer.get(stringBufferPos++));
                    break;
            }
            event++;
            if (eventHasLocation) {
                lineBufferPos[0] += 2;
                systemIdBufferPos[0]++;
            }
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
            addToSystemIdBuffer(locator.getSystemId());
        }

        super.characters(chars, start, length);
    }

    public void endDocument() throws SAXException {

        addToEventBuffer(END_DOCUMENT);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            addToSystemIdBuffer(locator.getSystemId());
        }
        super.endDocument();
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        addToEventBuffer(END_ELEMENT);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            addToSystemIdBuffer(locator.getSystemId());
        }
        stringBuffer.add(uri);
        stringBuffer.add(localname);
        stringBuffer.add(qName);

        super.endElement(uri, localname, qName);
    }

    public void endPrefixMapping(String s) throws SAXException {

        addToEventBuffer(END_PREFIX_MAPPING);
        // NOTE: We don't keep location data for this event as it is very unlikely to be used
//        if (locator != null) {
//            addToLineBuffer(locator.getLineNumber());
//            addToLineBuffer(locator.getColumnNumber());
//            addToSystemIdBuffer(locator.getSystemId());
//        }
        stringBuffer.add(s);

        super.endPrefixMapping(s);
    }

    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {

//        if (ended) return;

        addToEventBuffer(IGN_WHITESPACE);
        addToCharBuffer(chars, start, length);
        addToIntBuffer(length);

        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            addToSystemIdBuffer(locator.getSystemId());
        }

        super.ignorableWhitespace(chars, start, length);
    }

    public void processingInstruction(String s, String s1) throws SAXException {

        addToEventBuffer(PI);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            addToSystemIdBuffer(locator.getSystemId());
        }
        stringBuffer.add(s);
        stringBuffer.add(s1);

        super.processingInstruction(s, s1);
    }

    public void setDocumentLocator(Locator locator) {
        this.hasDocumentLocator = locator != null;
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    public void skippedEntity(String s) throws SAXException {

        addToEventBuffer(SKIPPED_ENTITY);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            addToSystemIdBuffer(locator.getSystemId());
        }
        stringBuffer.add(s);

        super.skippedEntity(s);
    }

    public void startDocument() throws SAXException {

        addToEventBuffer(START_DOCUMENT);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            addToSystemIdBuffer(locator.getSystemId());
        }
        super.startDocument();
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        addToEventBuffer(START_ELEMENT);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            addToSystemIdBuffer(locator.getSystemId());
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
        // NOTE: We don't keep location data for this event as it is very unlikely to be used
//        if (locator != null) {
//            addToLineBuffer(locator.getLineNumber());
//            addToLineBuffer(locator.getColumnNumber());
//            addToSystemIdBuffer(locator.getSystemId());
//        }
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

    protected void addToSystemIdBuffer(String systemId) {
        if (systemIdBuffer.length - systemIdBufferPosition == 1) {
            // double the array
            String[] old = systemIdBuffer;
            systemIdBuffer = new String[old.length * 2];
            System.arraycopy(old, 0, systemIdBuffer, 0, systemIdBufferPosition);
            addToSystemIdBuffer(systemId);
        } else {
            systemIdBuffer[systemIdBufferPosition++] = systemId;
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

//    private int getNewCapacity(int oldCapacity) {
//
//    }


    public void writeExternal(ObjectOutput out) throws IOException {

        out.writeInt(eventBufferPosition);
        out.write(eventBuffer, 0, eventBufferPosition);

        out.writeInt(charBufferPosition);
        for (int i = 0; i < charBufferPosition; i++)
            out.writeChar(charBuffer[i]);

        out.writeInt(intBufferPosition);
        for (int i = 0; i < intBufferPosition; i++)
            out.writeInt(intBuffer[i]);

        out.writeInt(lineBufferPosition);
        for (int i = 0; i < lineBufferPosition; i++)
            out.writeInt(lineBuffer[i]);

        out.writeInt(systemIdBufferPosition);
        for (int i = 0; i < systemIdBufferPosition; i++) {
            final String systemId = systemIdBuffer[i];
            out.writeUTF(systemId == null ? "" : systemId);
        }

        out.writeInt(attributeCountBufferPosition);
        for (int i = 0; i < attributeCountBufferPosition; i++)
            out.writeInt(attributeCountBuffer[i]);

        out.writeInt(stringBuffer.size());
        for (int i = 0; i < stringBuffer.size(); i++)
            out.writeUTF((String) stringBuffer.get(i));

        out.writeBoolean(hasDocumentLocator);
        out.writeUTF(publicId == null ? "" : publicId);

        out.flush();
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        eventBufferPosition = in.readInt();
        eventBuffer = new byte[eventBufferPosition];
        for (int i = 0; i < eventBufferPosition; i++)
            eventBuffer[i] = in.readByte();

        charBufferPosition = in.readInt();
        charBuffer = new char[charBufferPosition];
        for (int i = 0; i < charBufferPosition; i++)
            charBuffer[i] = in.readChar();

        intBufferPosition = in.readInt();
        intBuffer = new int[intBufferPosition];
        for (int i = 0; i < intBufferPosition; i++)
            intBuffer[i] = in.readInt();

        lineBufferPosition = in.readInt();
        lineBuffer = new int[lineBufferPosition];
        for (int i = 0; i < lineBufferPosition; i++)
            lineBuffer[i] = in.readInt();

        systemIdBufferPosition = in.readInt();
        systemIdBuffer = new String[systemIdBufferPosition];
        for (int i = 0; i < systemIdBufferPosition; i++) {
            systemIdBuffer[i] = in.readUTF();
            if ("".equals(systemIdBuffer[i]))
                systemIdBuffer[i] = null;
        }

        attributeCountBufferPosition = in.readInt();
        attributeCountBuffer = new int[attributeCountBufferPosition];
        for (int i = 0; i < attributeCountBufferPosition; i++)
            attributeCountBuffer[i] = in.readInt();

        final int stringBufferSize = in.readInt();
        for (int i = 0; i < stringBufferSize; i++)
            stringBuffer.add(in.readUTF());

        hasDocumentLocator = in.readBoolean();
        publicId = in.readUTF();
        if ("".equals(publicId))
            publicId = null;
    }
}
