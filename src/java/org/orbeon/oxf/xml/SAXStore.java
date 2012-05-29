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
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.SAXLoggerProcessor;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
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
public class SAXStore extends ForwardingXMLReceiver implements Externalizable {

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
    public static final byte COMMENT = 0x0B;

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
    private int attributeCount;

    private List<String> StringBuilder = new ArrayList<String>();

    private boolean hasDocumentLocator;
    private String publicId;

    private transient Locator locator; // used only for recording events, MUST be cleared afterwards

    private final Mark START_MARK = new Mark();
    
    private List<Mark> marks = null;

    public class Mark {
        public final String id;
        public final int eventBufferPosition;
        public final int charBufferPosition;
        public final int intBufferPosition;
        public final int lineBufferPosition;
        public final int systemIdBufferPosition;
        public final int attributeCountBufferPosition;
        public final int StringBuilderPosition;

        private Mark() {
            id = null;
            this.eventBufferPosition = 0;
            this.charBufferPosition = 0;
            this.intBufferPosition = 0;
            this.lineBufferPosition = 0;
            this.systemIdBufferPosition = 0;
            this.attributeCountBufferPosition = 0;
            this.StringBuilderPosition = 0;
        }

        private Mark(final SAXStore store, final String id) {
            this.id = id;
            this.eventBufferPosition = store.eventBufferPosition;
            this.charBufferPosition = store.charBufferPosition;
            this.intBufferPosition = store.intBufferPosition;
            this.lineBufferPosition = store.lineBufferPosition;
            this.systemIdBufferPosition = store.systemIdBufferPosition;
            this.attributeCountBufferPosition = store.attributeCountBufferPosition;
            this.StringBuilderPosition = store.StringBuilder.size();
            
            rememberMark();
        }
        
        private Mark(final int[] values, final String id) {
            this.id = id;
            int i = 0;
            this.eventBufferPosition = values[i++];
            this.charBufferPosition = values[i++];
            this.intBufferPosition = values[i++];
            this.lineBufferPosition = values[i++];
            this.systemIdBufferPosition = values[i++];
            this.attributeCountBufferPosition = values[i++];
            this.StringBuilderPosition = values[i++];
            
            rememberMark();
        }
        
        private void rememberMark() {
            // Keep a reference to marks, so that they can be serialized/deserialized along with the SAXStore 
            if (marks == null)
                marks = new ArrayList<Mark>();
            
            marks.add(this);
        }

        public void replay(XMLReceiver xmlReceiver) throws SAXException {
            SAXStore.this.replay(xmlReceiver, this);
        }
        
        public SAXStore saxStore() {
            return SAXStore.this;
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
            for (Iterator<String> i = StringBuilder.iterator(); i.hasNext();) {
                final String currentString = i.next();
                // This is rough, but entries in the list could point to the same string, so we try to detect this case.
                if (currentString != null && currentString != previousString)
                    size += currentString.length() * 2;
                previousString = currentString;
            }
        }

        return size;
    }

    public int getAttributesCount() {
        return attributeCount;
    }

    public SAXStore() {
        init();
    }

    public SAXStore(ObjectInput input) {
        try {
            readExternal(input);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public SAXStore(XMLReceiver xmlReceiver) {
        super.setXMLReceiver(xmlReceiver);
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

        StringBuilder.clear();

        locator = null;
    }

    public void replay(XMLReceiver xmlReceiver) throws SAXException {
        replay(xmlReceiver, START_MARK);
    }

    public void replay(XMLReceiver xmlReceiver, Mark mark) throws SAXException {
        int intBufferPos = mark.intBufferPosition;
        int charBufferPos = mark.charBufferPosition;
        int StringBuilderPos = mark.StringBuilderPosition;
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
            xmlReceiver.setDocumentLocator(outputLocator);
        }

        // Handle element marks
        final boolean handleElementMark = (mark != START_MARK) && (eventBuffer[currentEventPosition] == START_ELEMENT);

        int elementLevel = 0;
        eventLoop: while (currentEventPosition < eventBufferPosition) {
            final byte eventType = eventBuffer[currentEventPosition];
            final boolean eventHasLocation = hasDocumentLocator && eventType != END_PREFIX_MAPPING && eventType != START_PREFIX_MAPPING;
            switch (eventType) {
                case START_DOCUMENT: {
                    xmlReceiver.startDocument();
                    break;
                }
                case START_ELEMENT: {
                    final String namespaceURI = StringBuilder.get(StringBuilderPos++);
                    final String localName = StringBuilder.get(StringBuilderPos++);
                    final String qName = StringBuilder.get(StringBuilderPos++);
                    attributes.clear();
                    final int attributeCount = attributeCountBuffer[attributeCountBufferPos++];
                    for (int i = 0; i < attributeCount; i++) {
                        attributes.addAttribute(StringBuilder.get(StringBuilderPos++),
                                StringBuilder.get(StringBuilderPos++), StringBuilder.get(StringBuilderPos++),
                                StringBuilder.get(StringBuilderPos++), StringBuilder.get(StringBuilderPos++));
                    }
                    xmlReceiver.startElement(namespaceURI, localName, qName, attributes);
                    elementLevel++;
                    break;
                }
                case CHARACTERS: {
                    final int length = intBuffer[intBufferPos++];
                    xmlReceiver.characters(charBuffer, charBufferPos, length);
                    charBufferPos += length;
                    break;
                }
                case END_ELEMENT: {
                    elementLevel--;
                    xmlReceiver.endElement(StringBuilder.get(StringBuilderPos++),
                            StringBuilder.get(StringBuilderPos++),
                            StringBuilder.get(StringBuilderPos++));

                    if (handleElementMark && elementLevel == 0) {
                        // Back to ground level, we are done!
                        break eventLoop;
                    }

                    break;
                }
                case END_DOCUMENT: {
                    xmlReceiver.endDocument();
                    break;
                }
                case END_PREFIX_MAPPING: {
                    xmlReceiver.endPrefixMapping(StringBuilder.get(StringBuilderPos++));
                    break;
                }
                case IGN_WHITESPACE: {
                    final int length = intBuffer[intBufferPos++];
                    xmlReceiver.ignorableWhitespace(charBuffer, charBufferPos, length);
                    charBufferPos += length;
                    break;
                }
                case PI: {
                    xmlReceiver.processingInstruction(StringBuilder.get(StringBuilderPos++),
                            StringBuilder.get(StringBuilderPos++));
                    break;
                }
                case SKIPPED_ENTITY: {
                    xmlReceiver.skippedEntity(StringBuilder.get(StringBuilderPos++));
                    break;
                }
                case START_PREFIX_MAPPING: {
                    xmlReceiver.startPrefixMapping(StringBuilder.get(StringBuilderPos++),
                            StringBuilder.get(StringBuilderPos++));
                    break;
                }
                case COMMENT: {

                    final int length = intBuffer[intBufferPos++];
                    xmlReceiver.comment(charBuffer, charBufferPos, length);
                    charBufferPos += length;

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

    // Create a new mark
    // NOTE: This must be called *before* the startElement() event that will be the first element associated with the mark.
    public Mark getMark(String id) {
        return new Mark(this, id);
    }

    // Return all the marks created
    public List<Mark> getMarks() {
        return marks != null ? marks : Collections.<Mark>emptyList();
    }

    /**
     * Print to System.out. For debug only.
     */
    public void printOut() {
        try {
            final TransformerXMLReceiver th = TransformerUtils.getIdentityTransformerHandler();
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
            replay(new SAXLoggerProcessor.DebugXMLReceiver());
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

        addLocation();

        super.characters(chars, start, length);
    }

    @Override
    public void endDocument() throws SAXException {

        addToEventBuffer(END_DOCUMENT);
        addLocation();
        super.endDocument();

        // The resulting SAXStore should never keep references to whoever filled it
        locator = null;
    }

    @Override
    public void endElement(String uri, String localname, String qName) throws SAXException {

        addToEventBuffer(END_ELEMENT);
        addLocation();
        StringBuilder.add(uri);
        StringBuilder.add(localname);
        StringBuilder.add(qName);

        super.endElement(uri, localname, qName);
    }

    @Override
    public void endPrefixMapping(String s) throws SAXException {

        addToEventBuffer(END_PREFIX_MAPPING);
        // NOTE: We don't keep location data for this event as it is very unlikely to be used
        StringBuilder.add(s);

        super.endPrefixMapping(s);
    }

    @Override
    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {

        addToEventBuffer(IGN_WHITESPACE);
        addToCharBuffer(chars, start, length);
        addToIntBuffer(length);

        addLocation();

        super.ignorableWhitespace(chars, start, length);
    }

    @Override
    public void processingInstruction(String s, String s1) throws SAXException {

        addToEventBuffer(PI);
        addLocation();
        StringBuilder.add(s);
        StringBuilder.add(s1);

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
        addLocation();
        StringBuilder.add(s);

        super.skippedEntity(s);
    }

    @Override
    public void startDocument() throws SAXException {

        addToEventBuffer(START_DOCUMENT);
        addLocation();
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
        StringBuilder.add(uri);
        StringBuilder.add(localname);
        StringBuilder.add(qName);

        addToAttributeBuffer(attributes);

        super.startElement(uri, localname, qName, attributes);
    }

    @Override
    public void startPrefixMapping(String s, String s1) throws SAXException {

        addToEventBuffer(START_PREFIX_MAPPING);
        // NOTE: We don't keep location data for this event as it is very unlikely to be used
        StringBuilder.add(s);
        StringBuilder.add(s1);

        super.startPrefixMapping(s, s1);
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {

        addToEventBuffer(COMMENT);
        addToCharBuffer(ch, start, length);
        addToIntBuffer(length);

        addLocation();

        super.comment(ch, start, length);
    }

    private final void addLocation() {
        if (locator != null) {
            addToLineBuffer(locator.getLineNumber());
            addToLineBuffer(locator.getColumnNumber());
            addToSystemIdBuffer(locator.getSystemId());
        }
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
            final int count = attributes.getLength();
            attributeCountBuffer[attributeCountBufferPosition++] = count;
            attributeCount += count;
            for (int i = 0; i < attributes.getLength(); i++) {
                StringBuilder.add(attributes.getURI(i));
                StringBuilder.add(attributes.getLocalName(i));
                StringBuilder.add(attributes.getQName(i));
                StringBuilder.add(attributes.getType(i));
                StringBuilder.add(attributes.getValue(i));
            }
        }
    }

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
            out.writeObject(systemId == null ? "" : systemId);
        }

        out.writeInt(attributeCountBufferPosition);
        for (int i = 0; i < attributeCountBufferPosition; i++)
            out.writeInt(attributeCountBuffer[i]);

        out.writeInt(StringBuilder.size());
        for (int i = 0; i < StringBuilder.size(); i++)
            out.writeObject(StringBuilder.get(i));

        out.writeBoolean(hasDocumentLocator);
        out.writeObject(publicId == null ? "" : publicId);
        
        if (marks == null || marks.isEmpty()) {
            out.writeInt(0);
        } else {
            out.writeInt(marks.size());
            for (final Mark mark : marks) {
                out.writeObject(mark.id);
                out.writeInt(mark.eventBufferPosition);
                out.writeInt(mark.charBufferPosition);
                out.writeInt(mark.intBufferPosition);
                out.writeInt(mark.lineBufferPosition);
                out.writeInt(mark.systemIdBufferPosition);
                out.writeInt(mark.attributeCountBufferPosition);
                out.writeInt(mark.StringBuilderPosition);
            }
        }

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
            systemIdBuffer[i] = (String) in.readObject();
            if ("".equals(systemIdBuffer[i]))
                systemIdBuffer[i] = null;
        }

        attributeCountBufferPosition = in.readInt();
        attributeCountBuffer = new int[attributeCountBufferPosition];
        for (int i = 0; i < attributeCountBufferPosition; i++) {
            final int count = in.readInt();
            attributeCountBuffer[i] = count;
            attributeCount += count;
        }

        final int StringBuilderSize = in.readInt();
        for (int i = 0; i < StringBuilderSize; i++)
            StringBuilder.add((String) in.readObject());

        hasDocumentLocator = in.readBoolean();
        publicId = (String) in.readObject();
        if ("".equals(publicId))
            publicId = null;
        
        final int marksCount = in.readInt();
        if (marksCount > 0) {
            for (int i = 0; i < marksCount; i++) {
                final String id = (String) in.readObject();
                int[] values = new int[7];
                for (int j = 0; j < 7; j++)
                    values[j] = in.readInt();
                new Mark(values, id);
            }
        }
    }
}
