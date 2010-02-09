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
package org.orbeon.oxf.xml;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.SAXLoggerProcessor;
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
import java.util.Iterator;
import java.util.List;

/**
 * SAXStore keeps a compact representation of SAX events sent to the ContentHandler interface.
 *
 * As of June 2009, we increase the size of buffers by 50% instead of 100%. Still not the greatest way. Possibly,
 * passed a threshold, say 10 MB or 20 MB, we could use a linked list of such big blocks.
 *
 * TODO: Handling of system IDs is not optimal in memory as system IDs are unlikely to change much within a document.
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

    private static final int INITIAL_SIZE = 10;

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

    private List<String> stringBuffer = new ArrayList<String>();

    private boolean hasDocumentLocator;
    private String publicId;

    private transient Locator locator; // used only for recording events, MUST be cleared afterwards

    private static final Mark START_MARK = new Mark();

    public static class Mark {
        public final int eventBufferPosition;
        public final int charBufferPosition;
        public final int intBufferPosition;
        public final int lineBufferPosition;
        public final int systemIdBufferPosition;
        public final int attributeCountBufferPosition;
        public final int stringBufferPosition;

        private Mark() {
            this.eventBufferPosition = 0;
            this.charBufferPosition = 0;
            this.intBufferPosition = 0;
            this.lineBufferPosition = 0;
            this.systemIdBufferPosition = 0;
            this.attributeCountBufferPosition = 0;
            this.stringBufferPosition = 0;
        }

        private Mark(final SAXStore store) {
            this.eventBufferPosition = store.eventBufferPosition;
            this.charBufferPosition = store.charBufferPosition;
            this.intBufferPosition = store.intBufferPosition;
            this.lineBufferPosition = store.lineBufferPosition;
            this.systemIdBufferPosition = store.systemIdBufferPosition;
            this.attributeCountBufferPosition = store.attributeCountBufferPosition;
            this.stringBufferPosition = store.stringBuffer.size();
        }
    }

    public long getApproximateSize() {
        long size = eventBufferPosition * 4;
        size += charBufferPosition;
        size += intBufferPosition * 4;
        size += lineBufferPosition * 4;

        {
            String previousId = null;
            for (int i = 0; i < systemIdBuffer.length; i++) {
                final String currentId = systemIdBuffer[i];
                // This is rough, but entries in the list could point to the same string, so we try to detect this case.
                if (currentId != null && currentId != previousId)
                    size += currentId.length() * 2;
                previousId = currentId;
            }
        }

        size += attributeCountBufferPosition * 4;

        {
            String previousString = null;
            for (Iterator<String> i = stringBuffer.iterator(); i.hasNext();) {
                final String currentString = i.next();
                // This is rough, but entries in the list could point to the same string, so we try to detect this case.
                if (currentString != null && currentString != previousString)
                    size += currentString.length() * 2;
                previousString = currentString;
            }
        }

        return size;
    }

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
        replay(ch, START_MARK);
    }

    public void replay(ContentHandler ch, Mark mark) throws SAXException {
        int intBufferPos = mark.intBufferPosition;
        int charBufferPos = mark.charBufferPosition;
        int stringBufferPos = mark.stringBufferPosition;
        int attributeCountBufferPos = mark.attributeCountBufferPosition;
        final int[] lineBufferPos = { mark.lineBufferPosition } ;
        final int[] systemIdBufferPos = { mark.systemIdBufferPosition } ;
        final AttributesImpl attributes = new AttributesImpl();
        int currentEventPosition = mark.eventBufferPosition;

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

        // Handle element marks
        final boolean handleElementMark = (mark != START_MARK) && (eventBuffer[currentEventPosition] == START_ELEMENT);

        int elementLevel = 0;
        eventLoop: while (currentEventPosition < eventBufferPosition) {
            final byte eventType = eventBuffer[currentEventPosition];
            final boolean eventHasLocation = hasDocumentLocator && eventType != END_PREFIX_MAPPING && eventType != START_PREFIX_MAPPING;
            switch (eventType) {
                case START_DOCUMENT: {
                    ch.startDocument();
                    break;
                }
                case START_ELEMENT: {
                    final String namespaceURI = stringBuffer.get(stringBufferPos++);
                    final String localName = stringBuffer.get(stringBufferPos++);
                    final String qName = stringBuffer.get(stringBufferPos++);
                    attributes.clear();
                    final int attributeCount = attributeCountBuffer[attributeCountBufferPos++];
                    for (int i = 0; i < attributeCount; i++) {
                        attributes.addAttribute(stringBuffer.get(stringBufferPos++),
                                stringBuffer.get(stringBufferPos++), stringBuffer.get(stringBufferPos++),
                                stringBuffer.get(stringBufferPos++), stringBuffer.get(stringBufferPos++));
                    }
                    ch.startElement(namespaceURI, localName, qName, attributes);
                    elementLevel++;
                    break;
                }
                case CHARACTERS: {
                    final int length = intBuffer[intBufferPos++];
                    ch.characters(charBuffer, charBufferPos, length);
                    charBufferPos += length;
                    break;
                }
                case END_ELEMENT: {
                    elementLevel--;
                    ch.endElement(stringBuffer.get(stringBufferPos++),
                            stringBuffer.get(stringBufferPos++),
                            stringBuffer.get(stringBufferPos++));

                    if (handleElementMark && elementLevel == 0) {
                        // Back to ground level, we are done!
                        break eventLoop;
                    }

                    break;
                }
                case END_DOCUMENT: {
                    ch.endDocument();
                    break;
                }
                case END_PREFIX_MAPPING: {
                    ch.endPrefixMapping(stringBuffer.get(stringBufferPos++));
                    break;
                }
                case IGN_WHITESPACE: {
                    final int length = intBuffer[intBufferPos++];
                    ch.ignorableWhitespace(charBuffer, charBufferPos, length);
                    charBufferPos += length;
                    break;
                }
                case PI: {
                    ch.processingInstruction(stringBuffer.get(stringBufferPos++),
                            stringBuffer.get(stringBufferPos++));
                    break;
                }
                case SKIPPED_ENTITY: {
                    ch.skippedEntity(stringBuffer.get(stringBufferPos++));
                    break;
                }
                case START_PREFIX_MAPPING: {
                    ch.startPrefixMapping(stringBuffer.get(stringBufferPos++),
                            stringBuffer.get(stringBufferPos++));
                    break;
                }
            }
            currentEventPosition++;
            if (eventHasLocation) {
                lineBufferPos[0] += 2;
                systemIdBufferPos[0]++;
            }
        }
    }

    public Mark getElementMark() {
        return new Mark(this);
    }

    /**
     * This attempts to print the content to System.out. For debug only.
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

    /**
     * This outputs the content to the SAXLoggerProcessor logger. For debug only.
     */
    public void logContents() {
        try {
            replay(new SAXLoggerProcessor.DebugContentHandler());
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

    @Override
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

    @Override
    public void endDocument() throws SAXException {

        addToEventBuffer(END_DOCUMENT);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            addToSystemIdBuffer(locator.getSystemId());
        }
        super.endDocument();

        // The resulting SAXStore should never keep references to whoever filled it
        locator = null;
    }

    @Override
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

    @Override
    public void endPrefixMapping(String s) throws SAXException {

        addToEventBuffer(END_PREFIX_MAPPING);
        // NOTE: We don't keep location data for this event as it is very unlikely to be used
        stringBuffer.add(s);

        super.endPrefixMapping(s);
    }

    @Override
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

    @Override
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

    @Override
    public void setDocumentLocator(Locator locator) {
        this.hasDocumentLocator = locator != null;
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    @Override
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

    @Override
    public void startDocument() throws SAXException {

        addToEventBuffer(START_DOCUMENT);
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            addToSystemIdBuffer(locator.getSystemId());
        }
        super.startDocument();
    }

    @Override
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
        stringBuffer.add(s);
        stringBuffer.add(s1);

        super.startPrefixMapping(s, s1);
    }


    protected void addToCharBuffer(char[] chars, int start, int length) {
        if (charBuffer.length - charBufferPosition <= length) {
            // double the array
            char[] old = charBuffer;
            try{
                charBuffer = new char[old.length * 3 / 2 + 1];
            } catch (Error e) {
                System.out.println("Out of memory: " + old.length);
                throw e;
            }
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
            try{
                intBuffer = new int[old.length * 3 / 2 + 1];
            } catch (Error e) {
                System.out.println("Out of memory: " + old.length);
                throw e;
            }
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
            try {
                lineBuffer = new int[old.length * 3 / 2 + 1];
            } catch (Error e) {
                System.out.println("Out of memory: " + old.length);
                throw e;
            }
            System.arraycopy(old, 0, lineBuffer, 0, lineBufferPosition);
            addToLineBuffer(i);
        } else {
            lineBuffer[lineBufferPosition++] = i;
        }
    }

    protected void addToSystemIdBuffer(String systemId) {

        // Try to detect contiguous system ids
        //
        // NOTE: This native method won't work during replay, will need to store number of contiguous identical strings
        // as well, and/or use intern().
//        if (systemIdBufferPosition > 0 && systemIdBuffer[systemIdBufferPosition] == systemId) {
//            return;
//        }

        if (systemIdBuffer.length - systemIdBufferPosition == 1) {
            // double the array
            String[] old = systemIdBuffer;
            try {
                systemIdBuffer = new String[old.length * 3 / 2 + 1];
            } catch (Error e) {
                System.out.println("Out of memory: " + old.length);
                throw e;
            }
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
            try {
                eventBuffer = new byte[old.length * 3 / 2 + 1];
            } catch (Error e) {
                System.out.println("Out of memory: " + old.length);
                throw e;
            }
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
            try {
                attributeCountBuffer = new int[old.length * 3 / 2 + 1];
            } catch (Error e) {
                System.out.println("Out of memory: " + old.length);
                throw e;
            }
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
            out.writeUTF(stringBuffer.get(i));

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
