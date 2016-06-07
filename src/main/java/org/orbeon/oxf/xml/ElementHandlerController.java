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

import org.orbeon.dom.Element;
import org.orbeon.oxf.common.OrbeonLocationException;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import scala.Tuple2;

import java.util.*;

/**
 * This is the controller for the handlers system.
 *
 * The handler controller:
 *
 * - keeps a list of element handlers
 * - reacts to a stream of SAX events
 * - calls handlers when needed
 * - handles repeated content
 *
 * TODO: Should use pools of handlers to reduce memory consumption?
 */
public class ElementHandlerController implements ElementHandlerContext, XMLReceiver {

    private Object elementHandlerContext;
    private DeferredXMLReceiver output;

    private final Map<String, List<HandlerMatcher>> handlerMatchers = new HashMap<String, List<HandlerMatcher>>();
    private final Map<String, String> uriHandlers = new HashMap<String, String>();
    private final List<HandlerMatcher> customMatchers = new ArrayList<HandlerMatcher>();

    private final Stack<HandlerInfo> handlerInfos = new Stack<HandlerInfo>();
    private HandlerInfo currentHandlerInfo;
    private boolean isFillingUpSAXStore;

    private final NamespaceContext namespaceContext = new NamespaceContext();

    private OutputLocator locator;

    private int level = 0;

    // Class.forName is expensive, so we cache mappings
    private static Map<String, Class<ElementHandler>> classNameToHandlerClass = new HashMap<String, Class<ElementHandler>>();

    /**
     * Register a handler. The handler can match on a URI + localname + custom matcher, URI + localname, or on URI only
     * in that order.
     *
     * @param handlerClassName      class name for the handler
     * @param uri                   URI of the element that triggers the handler
     * @param localname             local name of the element that triggers the handler, or null if match on URI only
     * @param matcher               matcher on attributes, or null
     */
    public void registerHandler(String handlerClassName, String uri, String localname, Matcher matcher) {
        if (localname != null) {
            // Match on URI + localname and optionally custom matcher
            final String key = XMLUtils.buildExplodedQName(uri, localname);
            List<HandlerMatcher> handlerMatchers = this.handlerMatchers.get(key);
            if (handlerMatchers == null) {
                handlerMatchers = new ArrayList<HandlerMatcher>();
                this.handlerMatchers.put(key, handlerMatchers);
            }
            handlerMatchers.add(new HandlerMatcher(handlerClassName, matcher != null ? matcher : ALL_MATCHER));
        } else {
            // Match on URI only
            uriHandlers.put(uri, handlerClassName);
        }
    }

    public void registerHandler(String handlerClassName, Matcher matcher) {
        customMatchers.add(new HandlerMatcher(handlerClassName, matcher));
    }

    public void setElementHandlerContext(Object elementHandlerContext) {
        this.elementHandlerContext = elementHandlerContext;
    }

    public DeferredXMLReceiver getOutput() {
        return output;
    }

    public void setOutput(DeferredXMLReceiver output) {
        this.output = output;
    }

    public NamespaceContext getNamespaceContext() {
        return namespaceContext;
    }

    public void startDocument() throws SAXException {
        try {
            output.startDocument();
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    public void endDocument() throws SAXException {
        try {
            output.endDocument();
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        try {
            // Increment level before, so that if callees like start() and startElement() use us, the level is correct
            level++;

            namespaceContext.startElement();

            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.startElement(uri, localname, qName, attributes);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Look for a new handler
                final String explodedQName = XMLUtils.buildExplodedQName(uri, localname);

                final HandlerInfo handlerInfo = getHandler(uri, explodedQName, attributes);

                if (handlerInfo != null) {
                    // New handler found
                    final ElementHandler elementHandler = handlerInfo.elementHandler;
                    elementHandler.setContext(elementHandlerContext);

                    // Push current handler
                    currentHandlerInfo = handlerInfo;
                    handlerInfos.push(currentHandlerInfo);

                    if (elementHandler.isRepeating()) {
                        // Repeating handler will process its body later
                        isFillingUpSAXStore = true;
                    } else {
                        // Non-repeating handler processes its body immediately
                        // Signal init/start to current handler
                        elementHandler.init(uri, localname, qName, attributes, handlerInfo.matched);
                        elementHandler.start(uri, localname, qName, attributes);
                    }
                } else {
                    // New handler not found, send to output
                    output.startElement(uri, localname, qName, attributes);
                }
            }
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
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
                    currentHandlerInfo.elementHandler.init(uri, localname, qName, currentHandlerInfo.attributes, currentHandlerInfo.matched);
                    currentHandlerInfo.elementHandler.start(uri, localname, qName, currentHandlerInfo.attributes);
                    currentHandlerInfo.elementHandler.end(uri, localname, qName);
                } else {
                    // Signal end to current handler
                    currentHandlerInfo.elementHandler.end(uri, localname, qName);
                }

                // Pop current handler
                handlerInfos.pop();
                currentHandlerInfo = ((handlerInfos.size() > 0) ? handlerInfos.peek() : null);
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

            namespaceContext.endElement();

            level--;

        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    /**
     * A repeated handler may call this 1 or more times to start handling the captured body.
     *
     * @throws SAXException
     */
    public void repeatBody() throws SAXException {
        // Replay content of current SAXStore

        final int beforeLocatorCount = (this.locator != null) ? this.locator.size() : 0;
        currentHandlerInfo.saxStore.replay(this);
        final int afterLocatorCount = (this.locator != null) ? this.locator.size() : 0;
        if (beforeLocatorCount != afterLocatorCount) {
            // This means that the SAXStore replay called setDocumentLocator()
            assert afterLocatorCount == beforeLocatorCount + 1 : "incorrect locator stack state";
            this.locator.pop();
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
        currentHandlerInfo = ((handlerInfos.size() > 0) ? handlerInfos.peek() : null);
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
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
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
                // Update global NamespaceContext
                namespaceContext.startPrefixMapping(prefix, uri);
                // Send to output
                output.startPrefixMapping(prefix, uri);
            }
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
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
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
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
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
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
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
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
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    public void setDocumentLocator(Locator locator) {
        // NOTE: This is called by the outer caller. Then it can be called by repeat or component body replay, which
        // recursively hit this controller. The outer caller may or may not call setDocumentLocator() once. If there is
        // one, repeat body replay recursively calls setDocumentLocator(), which is pushed on the stack, and then popped
        // after the repeat body has been entirely replayed.
        if (locator != null) {
            if (this.locator == null) {
                // This is likely the source's initial setDocumentLocator() call

                // Use our own locator
                this.locator = new OutputLocator();
                this.locator.push(locator);
                // We don't forward this (anyway nobody is listening initially)
            } else {
                // This is a repeat or component body replay (otherwise it's a bug)
                // Push the SAXStore's locator
                this.locator.push(locator);
                // But don't forward this! SAX prevents calls to setDocumentLocator() mid-course. Our own locator will do the job.
            }
        }
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.startDTD(name, publicId, systemId);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.startDTD(name, publicId, systemId);
            }
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    public void endDTD() throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.endDTD();
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.endDTD();
            }
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    public void startEntity(String name) throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.startEntity(name);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.startEntity(name);
            }
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    public void endEntity(String name) throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.endEntity(name);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.endEntity(name);
            }
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    public void startCDATA() throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.startCDATA();
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.startCDATA();
            }
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    public void endCDATA() throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.endCDATA();
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.endCDATA();
            }
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    public void comment(char[] ch, int start, int length) throws SAXException {
        try {
            if (isFillingUpSAXStore) {
                // Fill-up SAXStore
                currentHandlerInfo.saxStore.comment(ch, start, length);
            } else if (currentHandlerInfo != null && !currentHandlerInfo.elementHandler.isForwarding()) {
                // The current handler doesn't want forwarding
                // Just ignore content
            } else {
                // Send to output
                output.comment(ch, start, length);
            }
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    public Locator getLocator() {
        return locator;
    }

    /**
     * Get an ElementHandler based on a dom4j element.
     *
     * @param element   element
     * @return          handler if found
     */
    public Tuple2<ElementHandler, Object> getHandler(Element element) {
        final HandlerInfo handlerInfo =
            getHandler(
                element.getNamespaceURI(),
                XMLUtils.buildExplodedQName(element.getNamespaceURI(), element.getName()),
                Dom4jUtils.getSAXAttributes(element)
            );

        return (handlerInfo != null)
            ? new scala.Tuple2<ElementHandler, Object>(handlerInfo.elementHandler, handlerInfo.matched)
            : null;
    }

    private HandlerInfo getHandler(String uri, String explodedQName, Attributes attributes) {

        // 1: Try custom matchers
        {
            final HandlerInfo handlerInfo = runMatchers(customMatchers, explodedQName, attributes);
            if (handlerInfo != null)
                return handlerInfo;
        }

        // 2: Try full matchers
        final List<HandlerMatcher> handlerMatchers = this.handlerMatchers.get(explodedQName);
        if (handlerMatchers != null) {
            final HandlerInfo handlerInfo = runMatchers(handlerMatchers, explodedQName, attributes);
            if (handlerInfo != null)
                return handlerInfo;
        }

        // 3: Try URI-based handler
        final String uriHandlerClassName = uriHandlers.get(uri);
        if (uriHandlerClassName != null) {
            final ElementHandler elementHandler = getHandlerByClassName(uriHandlerClassName);
            return new HandlerInfo(level, explodedQName, elementHandler, attributes, null, this.locator);
        }

        return null;
    }

    private HandlerInfo runMatchers(List<HandlerMatcher> matchers, String explodedQName, Attributes attributes) {
        for (HandlerMatcher handlerMatcher: matchers) {
            final Object matched = handlerMatcher.matcher.match(attributes, elementHandlerContext);
            if (matched != null) {
                final ElementHandler elementHandler = getHandlerByClassName(handlerMatcher.handlerClassName);
                return new HandlerInfo(level, explodedQName, elementHandler, attributes, matched, this.locator);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ElementHandler getHandlerByClassName(String handlerClassName) {
        Class<ElementHandler> handlerClass = classNameToHandlerClass.get(handlerClassName);
        if (handlerClass == null) {
            try {
                handlerClass = (Class<ElementHandler>) Class.forName(handlerClassName);
                classNameToHandlerClass.put(handlerClassName, handlerClass);
            } catch (ClassNotFoundException e) {
                throw OrbeonLocationException.wrapException(e, new LocationData(locator));
            }
        }
        try {
            return handlerClass.newInstance();
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new LocationData(locator));
        }
    }

    private static class HandlerInfo {
        public final int level;
        public final String explodedQName;
        public final ElementHandler elementHandler;
        public final Attributes attributes;
        public final Object matched;

        public final SAXStore saxStore;

        public HandlerInfo(int level, String explodedQName, ElementHandler elementHandler, Attributes attributes, Object matched, Locator locator) {
            this.level = level;
            this.explodedQName = explodedQName;
            this.elementHandler = elementHandler;
            this.attributes = elementHandler.isRepeating() ? new AttributesImpl(attributes) : null; // NOTE: could keep attributes if needed
            this.matched = matched;

            this.saxStore = elementHandler.isRepeating() ? new SAXStore() : null;

            // Set initial locator so that SAXStore can obtain location data if any
            if (this.saxStore != null && locator != null)
                this.saxStore.setDocumentLocator(locator);
        }
    }

    public interface Matcher<T> {
        T match(Attributes attributes, Object handlerContext);
    }

    private final Matcher ALL_MATCHER = new Matcher<Boolean>() {
        public Boolean match(Attributes attributes, Object handlerContext) {
            return Boolean.TRUE;
        }
    };

    private static class HandlerMatcher {
        public String handlerClassName;
        public Matcher matcher;

        private HandlerMatcher(String handlerClassName, Matcher matcher) {
            this.handlerClassName = handlerClassName;
            this.matcher = matcher;
        }
    }
}
