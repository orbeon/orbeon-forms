package org.orbeon.oxf.xml;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This class takes care of cleaning-up namespaces.
 *
 * Some tools generate namespace "un-declarations" or the form xmlns:abc="". While this is needed to
 * keep the XML infoset correct, it is illegal to generate such declarations in XML 1.0 (but it is
 * legal in XML 1.1).
 */
public class NamespaceCleanupContentHandler extends ForwardingContentHandler {

    private boolean filterNamespaceEvents;

    public NamespaceCleanupContentHandler(ContentHandler contentHandler, boolean serializeXML11) {
        super(contentHandler);
        this.filterNamespaceEvents = !serializeXML11;
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        if (!filterNamespaceEvents || !(prefix.length() > 0 && uri.equals("")))
            super.startPrefixMapping(prefix, uri);
    }
}
