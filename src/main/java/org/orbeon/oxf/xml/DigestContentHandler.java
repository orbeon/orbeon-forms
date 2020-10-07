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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.SecureUtils;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.Buffer;
import java.security.MessageDigest;

/**
 * This digester is based on some existing public document (not sure which). There are some
 * changes though. It is not clear anymore why we used that document as a base, as this is
 * purely internal.
 *
 * The bottom line is that the digest should change whenever the infoset of the source XML
 * document changes.
 */
public class DigestContentHandler implements XMLReceiver {

    private static final int ELEMENT_CODE = Node.ELEMENT_NODE;
    private static final int ATTRIBUTE_CODE = Node.ATTRIBUTE_NODE;
    private static final int TEXT_CODE = Node.TEXT_NODE;
    private static final int PROCESSING_INSTRUCTION_CODE = Node.PROCESSING_INSTRUCTION_NODE;
    private static final int NAMESPACE_CODE = 0XAA01;   // some code that is none of the above
    private static final int COMMENT_CODE = 0XAA02;     // some code that is none of the above

    /**
     * 4/6/2005 d : Previously we were using String.getBytes( "UnicodeBigUnmarked" ).  ( Believe
     * the code was copied from RFC 2803 ). This first tries to get a java.nio.Charset with
     * the name if this fails it uses a sun.io.CharToByteConverter.
     * Now in the case of "UnicodeBigUnmarked" there is no such Charset so a
     * CharToByteConverter, utf-16be, is used.  Unfortunately this negative lookup is expensive.
     * ( Costing us a full second in the 50thread/512MB test. )
     * The solution, of course, is just to use get the appropriate Charset and hold on to it.
     */
    private static final Charset utf16BECharset = Charset.forName("UTF-16BE");
    /**
     * Encoder has state and therefore cannot be shared across threads.
     */
    private final CharsetEncoder charEncoder = utf16BECharset.newEncoder();
    private java.nio.CharBuffer charBuff = java.nio.CharBuffer.allocate(64);
    private java.nio.ByteBuffer byteBuff = java.nio.ByteBuffer.allocate(128);

    private final MessageDigest digest = SecureUtils.defaultMessageDigest();

    /**
     * Compute a digest for a SAX source.
     */
    public static byte[] getDigest(Source source) {
        final DigestContentHandler digester = new DigestContentHandler();
        TransformerUtils.sourceToSAX(source, digester);
        return digester.getResult();
    }

    private void ensureCharBuffRemaining(final int size) {
        if (charBuff.remaining() < size) {
            final int cpcty = (charBuff.capacity() + size) * 2;
            final java.nio.CharBuffer newChBuf = java.nio.CharBuffer.allocate(cpcty);
            newChBuf.put(charBuff);
            charBuff = newChBuf;
        }
    }

    private void updateWithCharBuf() {
        final int reqSize = (int) charEncoder.maxBytesPerChar() * charBuff.position();
        if (byteBuff.capacity() < reqSize) {
            byteBuff = java.nio.ByteBuffer.allocate(2 * reqSize);
        }

        // Make ready for read
        ((Buffer) charBuff).flip(); // cast: see #4682

        final CoderResult cr = charEncoder.encode(charBuff, byteBuff, true);
        try {

            if (cr.isError()) cr.throwException();

            // Make ready for read
            ((Buffer) byteBuff).flip(); // cast: see #4682

            final byte[] byts = byteBuff.array();
            final int len = byteBuff.remaining();
            final int strt = byteBuff.arrayOffset();
            digest.update(byts, strt, len);

        } catch (final CharacterCodingException e) {
            throw new OXFException(e);
        } catch (java.nio.BufferOverflowException e) {
            throw new OXFException(e);
        } catch (java.nio.BufferUnderflowException e) {
            throw new OXFException(e);
        } finally {
            // Make ready for write
            ((Buffer) charBuff).clear(); // cast: see #4682
            ((Buffer) byteBuff).clear(); // cast: see #4682
        }
    }

    private void updateWith(final String s) {
        addToCharBuff(s);
        updateWithCharBuf();
    }

    private void updateWith(final char[] chArr, final int ofst, final int len) {
        ensureCharBuffRemaining(len);
        charBuff.put(chArr, ofst, len);
        updateWithCharBuf();
    }

    private void addToCharBuff(final char c) {
        ensureCharBuffRemaining(1);
        charBuff.put(c);
    }

    private void addToCharBuff(final String s) {
        final int size = s.length();
        ensureCharBuffRemaining(size);
        charBuff.put(s);
    }

    public byte[] getResult() {
        return digest.digest();
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void startDocument() throws SAXException {
        ((Buffer) charBuff).clear(); // cast: see #4682
        ((Buffer) byteBuff).clear(); // cast: see #4682
        charEncoder.reset();
    }

    public void endDocument() throws SAXException {
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {

        digest.update((byte) ((NAMESPACE_CODE >> 24) & 0xff));
        digest.update((byte) ((NAMESPACE_CODE >> 16) & 0xff));
        digest.update((byte) ((NAMESPACE_CODE >> 8) & 0xff));
        digest.update((byte) (NAMESPACE_CODE & 0xff));
        updateWith(prefix);
        digest.update((byte) 0);
        digest.update((byte) 0);
        updateWith(uri);
        digest.update((byte) 0);
        digest.update((byte) 0);
    }

    public void endPrefixMapping(String prefix)
            throws SAXException {
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {

        digest.update((byte) ((ELEMENT_CODE >> 24) & 0xff));
        digest.update((byte) ((ELEMENT_CODE >> 16) & 0xff));
        digest.update((byte) ((ELEMENT_CODE >> 8) & 0xff));
        digest.update((byte) (ELEMENT_CODE & 0xff));

        addToCharBuff('{');
        addToCharBuff(namespaceURI);
        addToCharBuff('}');
        addToCharBuff(localName);
        updateWithCharBuf();

        digest.update((byte) 0);
        digest.update((byte) 0);
        int attCount = atts.getLength();
        digest.update((byte) ((attCount >> 24) & 0xff));
        digest.update((byte) ((attCount >> 16) & 0xff));
        digest.update((byte) ((attCount >> 8) & 0xff));
        digest.update((byte) (attCount & 0xff));
        for (int i = 0; i < attCount; i++) {
            digest.update((byte) ((ATTRIBUTE_CODE >> 24) & 0xff));
            digest.update((byte) ((ATTRIBUTE_CODE >> 16) & 0xff));
            digest.update((byte) ((ATTRIBUTE_CODE >> 8) & 0xff));
            digest.update((byte) (ATTRIBUTE_CODE & 0xff));

            final String attURI = atts.getURI(i);
            final String attNam = atts.getLocalName(i);

            addToCharBuff('{');
            addToCharBuff(attURI);
            addToCharBuff('}');
            addToCharBuff(attNam);
            updateWithCharBuf();

            digest.update((byte) 0);
            digest.update((byte) 0);

            final String val = atts.getValue(i);
            updateWith(val);
        }
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
    }

    public void characters(char ch[], int start, int length) throws SAXException {

        digest.update((byte) ((TEXT_CODE >> 24) & 0xff));
        digest.update((byte) ((TEXT_CODE >> 16) & 0xff));
        digest.update((byte) ((TEXT_CODE >> 8) & 0xff));
        digest.update((byte) (TEXT_CODE & 0xff));

        updateWith(ch, start, length);

        digest.update((byte) 0);
        digest.update((byte) 0);
    }

    public void ignorableWhitespace(char ch[], int start, int length)
            throws SAXException {
    }

    public void processingInstruction(String target, String data) throws SAXException {

        digest.update((byte) ((PROCESSING_INSTRUCTION_CODE >> 24) & 0xff));
        digest.update((byte) ((PROCESSING_INSTRUCTION_CODE >> 16) & 0xff));
        digest.update((byte) ((PROCESSING_INSTRUCTION_CODE >> 8) & 0xff));
        digest.update((byte) (PROCESSING_INSTRUCTION_CODE & 0xff));

        updateWith(target);

        digest.update((byte) 0);
        digest.update((byte) 0);

        updateWith(data);

        digest.update((byte) 0);
        digest.update((byte) 0);
    }

    public void skippedEntity(String name) throws SAXException {
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
    }

    public void endDTD() throws SAXException {
    }

    public void startEntity(String name) throws SAXException {
    }

    public void endEntity(String name) throws SAXException {
    }

    public void startCDATA() throws SAXException {
    }

    public void endCDATA() throws SAXException {
    }

    public void comment(char[] ch, int start, int length) throws SAXException {

        // We do consider comments significant for the purpose of digesting. But should this be an option?

        digest.update((byte) ((COMMENT_CODE >> 24) & 0xff));
        digest.update((byte) ((COMMENT_CODE >> 16) & 0xff));
        digest.update((byte) ((COMMENT_CODE >> 8) & 0xff));
        digest.update((byte) (COMMENT_CODE & 0xff));

        updateWith(ch, start, length);

        digest.update((byte) 0);
        digest.update((byte) 0);
    }
}
