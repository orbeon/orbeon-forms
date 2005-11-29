package org.orbeon.oxf.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *
 */
public abstract class ElementHandlerNew extends ForwardingContentHandler {

    private Object context;

    /**
     * Override this to detect that the element has started.
     *
     * @param uri
     * @param localname
     * @param qName
     * @param attributes
     * @throws SAXException
     */
    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
    }

    /**
     * Override this to detect that the element has ended.
     *
     * @param uri
     * @param localname
     * @param qName
     * @throws SAXException
     */
    public void end(String uri, String localname, String qName) throws SAXException {
    }

    public abstract boolean isRepeating();

    public abstract boolean isForwarding();

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }
}
