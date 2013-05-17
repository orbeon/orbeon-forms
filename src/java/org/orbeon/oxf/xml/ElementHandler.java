/**
 *  Copyright (C) 2005 Orbeon, Inc.
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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * This class allows implementing SAX-based element handlers.
 *
 * This class is meant to be subclassed by specific element handlers.
 */
public class ElementHandler extends ForwardingContentHandler {

    private ElementHandlerContext context;
    private boolean repeating;

    private Locator documentLocator;

    private boolean doForward;
    private SAXStore saxStore;
    private ContentHandler savedOutput;
    private Attributes savedAttributes;

    private Map elementHandlers = new HashMap();
    private int forwardingLevel = -1;
    private ElementHandler currentHandler;
    private int level = 0;
    private String currentKey;

    /**
     * @param repeating     true if the body of this handler will be repeated
     */
    public ElementHandler(ElementHandlerContext context, boolean repeating) {
        this.context = context;
        this.repeating = repeating;
    }

    public ElementHandlerContext getContext() {
        return context;
    }

    /**
     * Call this from subclass to add handlers.
     *
     * @param handler
     * @param uri
     * @param localname
     */
    public void addElementHandler(ElementHandler handler, String uri, String localname) {
        elementHandlers.put("{" + uri + "}" + localname, handler);
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (forwardingLevel == -1 && elementHandlers.size() > 0) {
            final String key = "{" + uri + "}" + localname;
            final ElementHandler elementHandler = (ElementHandler) elementHandlers.get(key);
            if (elementHandler != null) {
                // Found element handler
                forwardingLevel = level;
                currentKey = key;
                if (elementHandler.isRepeating()) {
                    // Remember SAX content of the element body
                    savedOutput = super.getContentHandler();
                    savedAttributes = new AttributesImpl(attributes);
                    elementHandler.saxStore = new SAXStore();
                    elementHandler.saxStore.setDocumentLocator(documentLocator);
                    super.setContentHandler(elementHandler.saxStore);
//                    context.setOutput(new DeferredContentHandlerImpl(elementHandler.saxStore));
                } else {
                    // Notify start of element
                    currentHandler = elementHandler;
                    super.setContentHandler(currentHandler);
                    super.setForward(true);
                    elementHandler.setDocumentLocator(documentLocator);
                    elementHandler.start(uri, localname, qName, attributes);
                }
            } else
                super.startElement(uri, localname, qName, attributes);
        } else
            super.startElement(uri, localname, qName, attributes);
        level++;
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        level--;
        if (forwardingLevel == level) {
            final String key = "{" + uri + "}" + localname;
            if (!key.equals(currentKey))
                throw new ValidationException("Illegal document: expecting " + key + ", got " + currentKey, new LocationData(getDocumentLocator()));

            final ElementHandler elementHandler = (ElementHandler) elementHandlers.get(key);
            if (elementHandler.isRepeating()) {
                // Restore output
//                context.setOutput(savedOutput);
                super.setContentHandler(savedOutput);
                savedOutput = null;

                // Notify start of element
                currentHandler = elementHandler;
                super.setContentHandler(currentHandler);
                super.setForward(true);
                elementHandler.setDocumentLocator(documentLocator);
                elementHandler.start(uri, localname, qName, savedAttributes);

                // Restore state
                savedAttributes = null;
                elementHandler.saxStore = null;
            }

            // Notify end of element
            forwardingLevel = -1;
            currentKey = null;
            currentHandler = null;
            super.setForward(doForward);
            super.setContentHandler(doForward ? context.getOutput() : null);
            elementHandler.end(uri, localname, qName);
        } else
            super.endElement(uri, localname, qName);
    }

    /**
     * Call this from subclass to start a body repeat.
     *
     * @throws SAXException
     */
    protected void repeatBody() throws SAXException {
        if (!repeating)
            throw new IllegalStateException("repeatBody() can only be called when repeating is true.");

        saxStore.replay(this);
    }

    public boolean isRepeating() {
        return repeating;
    }

    protected void setDoForward(boolean doForward) {
        this.doForward = doForward;
        if (doForward && getContentHandler() == null) {
            super.setForward(true);
            super.setContentHandler(context.getOutput());
        }
    }

    public void setDocumentLocator(Locator locator) {
        this.documentLocator = locator;
        super.setDocumentLocator(locator);
    }

    public Locator getDocumentLocator() {
        return documentLocator;
    }

    public void startPrefixMapping(String s, String s1) throws SAXException {
        super.startPrefixMapping(s, s1);
    }

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

    protected boolean isInElementHandler() {
        return forwardingLevel > -1;
    }
}
