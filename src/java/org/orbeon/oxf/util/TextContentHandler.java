package org.orbeon.oxf.util;

import org.orbeon.oxf.xml.ContentHandlerAdapter;
import org.orbeon.oxf.common.OXFException;
import org.xml.sax.SAXException;

import java.io.*;

/**
 * This ContentHandler writes all character events to a Writer.
 */
public class TextContentHandler extends ContentHandlerAdapter {

    private Writer writer;

    public TextContentHandler(Writer writer) {
        this.writer = writer;
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        try {
            writer.write(ch, start, length);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

}
