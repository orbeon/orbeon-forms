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
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 *
 */
public class ElementHandlerController extends ForwardingContentHandler implements ElementHandlerContext, ContentHandler {

    private Object elementHandlerContext;
    private DeferredContentHandler output;
    private Map handlerKeysToNames = new HashMap();
    private Map uriHandlerKeysToNames = new HashMap();

    private Stack handlerInfos = new Stack();
    private HandlerInfo currentHandlerInfo;
    private boolean isFillingUpSAXStore;

    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

    private Locator locator;

    private int level;

    // Class.forName is expensive, so we cache mappings
    private static Map classNameToHandlerClass = new HashMap();

    /**
     * Register a handler. The handler can match on a URI + localname, or on URI only. URI
     * matching has lower priority than URI + localname matching.
     *
     *
     * @param handlerClassName  class name for the handler
     * @param uri               URI of the element that triggers the handler
     * @param localname         local name of the element that triggers the handler
     */
    public void registerHandler(String handlerClassName, String uri, String localname) {
        if (localname != null)
            handlerKeysToNames.put(XMLUtils.buildExplodedQName(uri, localname), handlerClassName);
        else
            uriHandlerKeysToNames.put(uri, handlerClassName);
    }

    public Object getElementHandlerContext() {
        return elementHandlerContext;
    }

    public String getParentHandlerExplodedQName() {
        if (handlerInfos == null || handlerInfos.size() < 2)
            return null;

        final HandlerInfo parentHandlerInfo = (HandlerInfo) handlerInfos.get(handlerInfos.size() - 2);
        return parentHandlerInfo.explodedQName;
    }

    public void setElementHandlerContext(Object elementHandlerContext) {
        this.elementHandlerContext = elementHandlerContext;
    }

    public DeferredContentHandler getOutput() {
        return output;
    }

    public void setOutput(DeferredContentHandler output) {
        this.output = output;
    }

    public NamespaceSupport3 getNamespaceSupport() {
        return namespaceSupport;
    }

    public void startDocument() throws SAXException {
        try {
            setContentHandler(output);
            setForward(true);
            super.startDocument();
        } catch (Exception e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    public void endDocument() throws SAXException {
        try {
            super.endDocument();
        } catch (Exception e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        try {
            namespaceSupport.startElement();

            if (!isFillingUpSAXStore) {
//                namespaceSupport.pushContext();

                final String explodedQName = XMLUtils.buildExplodedQName(uri, localname);
                final String handlerClassName = (String) handlerKeysToNames.get(explodedQName);

                final ElementHandlerNew elementHandler;
                if (handlerClassName != null) {
                    // Found handler
                    elementHandler = getHandlerByClassName(handlerClassName);
                } else {
                    // Search for URI-based handler
                    final String uriHandlerClassName = (String) uriHandlerKeysToNames.get(uri);
                    if (uriHandlerClassName != null) {
                        // Found handler
                        elementHandler = getHandlerByClassName(uriHandlerClassName);
                    } else {
                        elementHandler = null;
                    }
                }

                if (elementHandler != null) {
                    elementHandler.setContext(elementHandlerContext);
                    elementHandler.setForward(elementHandler.isForwarding());
                    elementHandler.setContentHandler(output);

                    if (currentHandlerInfo != null)
                        handlerInfos.push(currentHandlerInfo);

                    if (elementHandler.isRepeating()) {
                        currentHandlerInfo = new HandlerInfo(level, explodedQName, elementHandler, attributes);
                        super.setContentHandler(currentHandlerInfo.saxStore);
                        isFillingUpSAXStore = true;
                    } else {
                        currentHandlerInfo = new HandlerInfo(level, explodedQName, elementHandler);
                        super.setContentHandler(elementHandler);
                        elementHandler.start(uri, localname, qName, attributes);
                    }
                } else {

                    super.startElement(uri, localname, qName, attributes);
                }
            } else {
                super.startElement(uri, localname, qName, attributes);
            }

            level++;
        } catch (Exception e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        try {
            level--;

            if (currentHandlerInfo != null && currentHandlerInfo.level == level) {
                // End of handler

                if (currentHandlerInfo.elementHandler.isRepeating()) {
                    isFillingUpSAXStore = false;
                    super.setContentHandler(currentHandlerInfo.elementHandler);
                    level++;
                    currentHandlerInfo.elementHandler.start(uri, localname, qName, currentHandlerInfo.attributes);
                    currentHandlerInfo.elementHandler.end(uri, localname, qName);
                    level--;
                } else {
                    currentHandlerInfo.elementHandler.end(uri, localname, qName);
                }

                currentHandlerInfo = (HandlerInfo) ((handlerInfos.size() > 0) ? handlerInfos.pop() : null);
                super.setContentHandler((currentHandlerInfo != null) ? (ContentHandler) currentHandlerInfo.elementHandler : output);

//                namespaceSupport.popContext();

            } else {
                super.endElement(uri, localname, qName);
//                if (!isFillingUpSAXStore)
//                    namespaceSupport.popContext();
            }

            namespaceSupport.endElement();

        } catch (Exception e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    public void repeatBody() throws SAXException {
        currentHandlerInfo.saxStore.replay(this);
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        try {
            super.characters(chars, start, length);
        } catch (Exception e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        try {
            // Update global NamespaceSupport
            namespaceSupport.startPrefixMapping(prefix, uri);
            super.startPrefixMapping(prefix, uri);
        } catch (Exception e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    public void endPrefixMapping(String s) throws SAXException {
        try {
            super.endPrefixMapping(s);
        } catch (Exception e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    private ElementHandlerNew getHandlerByClassName(String handlerClassName) {

        Class handlerClass = (Class) classNameToHandlerClass.get(handlerClassName);
        if (handlerClass == null) {
            try {
                handlerClass = Class.forName(handlerClassName);
                classNameToHandlerClass.put(handlerClassName, handlerClass);
            } catch (ClassNotFoundException e) {
                throw new ValidationException(e, new LocationData(locator));
            }
        }
        try {
            return (ElementHandlerNew) handlerClass.newInstance();
        } catch (Exception e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    private static class HandlerInfo {
        public int level;
        public String explodedQName;
        public ElementHandlerNew elementHandler;
        public Attributes attributes;

        public SAXStore saxStore;

        public HandlerInfo(int level, String explodedQName, ElementHandlerNew elementHandler) {
            this.level = level;
            this.explodedQName = explodedQName;
            this.elementHandler = elementHandler;
        }

        public HandlerInfo(int level, String explodedQName, ElementHandlerNew elementHandler, Attributes attributes) {
            this.level = level;
            this.explodedQName = explodedQName;
            this.elementHandler = elementHandler;
            this.attributes = new AttributesImpl(attributes);

            this.saxStore = new SAXStore();
        }
    }
}
