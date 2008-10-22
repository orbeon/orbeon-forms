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
import org.orbeon.oxf.processor.xinclude.XIncludeProcessor;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * This is the controller for the handlers system.
 *
 * The handler controller:
 *
 * o keeps a list of element handlers
 * o reacts to a stream of SAX events
 * o calls handlers when needed
 * o handles repeated content
 *
 * TODO: Should use pools of handlers to reduce memory consumption.
 */
public class ElementHandlerController implements ElementHandlerContext, ContentHandler {

    private Object elementHandlerContext;
    private DeferredContentHandler output;
    private Map handlerKeysToNames = new HashMap();
    private Map uriHandlerKeysToNames = new HashMap();

    private Stack handlerInfos = new Stack();
    private HandlerInfo currentHandlerInfo;
    private boolean isFillingUpSAXStore;

    private Stack elementNames = new Stack();

    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

    private XIncludeProcessor.OutputLocator locator;

    private int level = 0;

    // Class.forName is expensive, so we cache mappings
    private static Map classNameToHandlerClass = new HashMap();

    /**
     * Register a handler. The handler can match on a URI + localname, or on URI only. URI
     * matching has lower priority than URI + localname matching.
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


    public Stack getElementNames() {
        return elementNames;
    }

    public void startDocument() throws SAXException {
        try {
            output.startDocument();
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    public void endDocument() throws SAXException {
        try {
            output.endDocument();
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        try {
            // Increment level before, so that if callees like start() and startElement() use us, the level is correct
            level++;

            namespaceSupport.startElement();
            elementNames.add(XMLUtils.buildExplodedQName(uri, localname));

            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.startElement(uri, localname, qName, attributes);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Look for a new handler
                final String explodedQName = XMLUtils.buildExplodedQName(uri, localname);
                final ElementHandler elementHandler = getHandler(uri, explodedQName);

                if (elementHandler != null) {
                    // New handler found
                    elementHandler.setContext(elementHandlerContext);

                    if (elementHandler.isRepeating()) {
                        // Repeating handler will process its body later
                        currentHandlerInfo = new HandlerInfo(level, explodedQName, elementHandler, attributes, this.locator);
                        isFillingUpSAXStore = true;
                        // Push current handler
                        handlerInfos.push(currentHandlerInfo);
                    } else {
                        // Non-repeating handler processes its body immediately
                        currentHandlerInfo = new HandlerInfo(level, explodedQName, elementHandler);
                        // Push current handler
                        handlerInfos.push(currentHandlerInfo);
                        // Signal start to current handler
                        elementHandler.start(uri, localname, qName, attributes);
                    }
                } else {
                    // New handler not found, send to output
                    output.startElement(uri, localname, qName, attributes);
                }
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        try {

            if (currentHandlerInfo != null && currentHandlerInfo.level == level) {
                // End of current handler

                if (isFillingUpSAXStore) {
                    // Was filling-up SAXStore
                    isFillingUpSAXStore = false;
                    // Process body once
                    currentHandlerInfo.elementHandler.start(uri, localname, qName, currentHandlerInfo.attributes);
                    currentHandlerInfo.elementHandler.end(uri, localname, qName);
                } else {
                    // Signal end to current handler
                    currentHandlerInfo.elementHandler.end(uri, localname, qName);
                }

                // Pop current handler
                handlerInfos.pop();
                currentHandlerInfo = (HandlerInfo) ((handlerInfos.size() > 0) ? handlerInfos.peek() : null);
            } else if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.endElement(uri, localname, qName);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Just forward
                output.endElement(uri, localname, qName);
            }

            elementNames.pop();
            namespaceSupport.endElement();

            level--;

        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    /**
     * A repeated handler may call this 1 or more times to start handling the captured body.
     *
     * @throws SAXException
     */
    public void repeatBody() throws SAXException {
        // Replay content of current SAXStore
        currentHandlerInfo.saxStore.replay(this);
        if (this.locator != null) {
            // This means that the SAXStore sent out setDocumentLocator() as well
            this.locator.popLocator();
        }
    }

    /**
     * A handler may call this to start providing new dynamic content to process.
     */
    public void startBody() {
        // Just push null so that the contents is not subject to the isForwarding() test.
        handlerInfos.push(null);
        currentHandlerInfo = null;
    }

    /**
     * A handler may call this to end providing new dynamic content to process.
     */
    public void endBody() {
        handlerInfos.pop();
        currentHandlerInfo = (HandlerInfo) ((handlerInfos.size() > 0) ? handlerInfos.peek() : null);
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.characters(chars, start, length);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.characters(chars, start, length);
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.startPrefixMapping(prefix, uri);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Update global NamespaceSupport
                namespaceSupport.startPrefixMapping(prefix, uri);
                // Send to output
                output.startPrefixMapping(prefix, uri);
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    public void endPrefixMapping(String s) throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.endPrefixMapping(s);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.endPrefixMapping(s);
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.ignorableWhitespace(ch, start, length);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.ignorableWhitespace(ch, start, length);
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    public void processingInstruction(String target, String data) throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.processingInstruction(target, data);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.processingInstruction(target, data);
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    public void skippedEntity(String name) throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.skippedEntity(name);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.skippedEntity(name);
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    public void setDocumentLocator(Locator locator) {
        if (locator != null) {
            if (this.locator == null) {
                // This is likely the source's initial setDocumentLocator() call

                // Use our own locator
                this.locator = new XIncludeProcessor.OutputLocator();
                if (locator != null)
                    this.locator.pushLocator(locator);
                // We don't forward this (anyway nobody is listening initially)
            } else {
                // This is likely during a SAXStore replay

                // Push the SAXStore's locator
                this.locator.pushLocator(locator);
                // But don't forward this! SAX prevents calls to setDocumentLocator() mid-course. Our own locator will do the job.
            }
        }
    }

    public Locator getLocator() {
        return locator;
    }

    private ElementHandler getHandler(String uri, String explodedQName) {
        ElementHandler elementHandler;
        final String handlerClassName = (String) handlerKeysToNames.get(explodedQName);
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
        return elementHandler;
    }

    private ElementHandler getHandlerByClassName(String handlerClassName) {
        Class handlerClass = (Class) classNameToHandlerClass.get(handlerClassName);
        if (handlerClass == null) {
            try {
                handlerClass = Class.forName(handlerClassName);
                classNameToHandlerClass.put(handlerClassName, handlerClass);
            } catch (ClassNotFoundException e) {
                throw ValidationException.wrapException(e, new LocationData(locator));
            }
        }
        try {
            return (ElementHandler) handlerClass.newInstance();
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(locator));
        }
    }

    private static class HandlerInfo {
        public int level;
        public String explodedQName;
        public ElementHandler elementHandler;
        public Attributes attributes;

        public SAXStore saxStore;

        public HandlerInfo(int level, String explodedQName, ElementHandler elementHandler) {
            this.level = level;
            this.explodedQName = explodedQName;
            this.elementHandler = elementHandler;
        }

        public HandlerInfo(int level, String explodedQName, ElementHandler elementHandler, Attributes attributes, Locator locator) {
            this.level = level;
            this.explodedQName = explodedQName;
            this.elementHandler = elementHandler;
            this.attributes = new AttributesImpl(attributes);

            this.saxStore = new SAXStore();
            // Set initial locator so that SAXStore can obtain location data if any
            if (locator != null)
                this.saxStore.setDocumentLocator(locator);
        }
    }
}
