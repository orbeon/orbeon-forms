package org.orbeon.oxf.xml;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Forwards all the SAX events to a content handler, except for startDocument and endDocument.
 */
public class EmbeddedDocumentContentHandler extends SimpleForwardingContentHandler {
    public EmbeddedDocumentContentHandler(ContentHandler contentHandler) {
        super(contentHandler);
    }

    public void startDocument() throws SAXException {}
    public void endDocument() throws SAXException {}
}
