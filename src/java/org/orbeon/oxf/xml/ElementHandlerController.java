package org.orbeon.oxf.xml;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;
import org.orbeon.oxf.common.OXFException;

import java.util.Map;
import java.util.HashMap;
import java.util.Stack;

import orbeon.apache.xml.utils.NamespaceSupport2;

/**
 *
 */
public class ElementHandlerController extends ForwardingContentHandler implements ElementHandlerContext, ContentHandler {

    private Object elementHandlerContext;
    private DeferredContentHandler output;
    private Map handlerKeysToNames = new HashMap();

    private Stack handlerInfos = new Stack();
    private HandlerInfo currentHandlerInfo;

    private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();

    private int level;

    public void registerHandler(String handlerClassName, String uri, String localname) {
        handlerKeysToNames.put(XMLUtils.buildExplodedQName(uri, localname), handlerClassName);
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

    public NamespaceSupport2 getNamespaceSupport() {
        return namespaceSupport;
    }

    public void startDocument() throws SAXException {
        setContentHandler(output);
        setForward(true);
        super.startDocument();
    }

    public void endDocument() throws SAXException {
        super.endDocument();
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        namespaceSupport.pushContext();

        final String explodedQName = XMLUtils.buildExplodedQName(uri, localname);
        final String handlerClassName = (String) handlerKeysToNames.get(explodedQName);
        if (handlerClassName != null) {
            // Found new handler
            final ElementHandlerNew elementHandler = getHandlerByClassName(handlerClassName);
            elementHandler.setContext(elementHandlerContext);
            elementHandler.setForward(elementHandler.isForwarding());
            elementHandler.setContentHandler(output);

            if (currentHandlerInfo != null)
                handlerInfos.push(currentHandlerInfo);
            currentHandlerInfo = new HandlerInfo(level, elementHandler);
            super.setContentHandler(elementHandler);

            elementHandler.start(uri, localname, qName, attributes);
        } else {
            super.startElement(uri, localname, qName, attributes);
        }

        level++;
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        level--;

        if (currentHandlerInfo != null && currentHandlerInfo.level == level) {
            // End of handler

            currentHandlerInfo.elementHandler.end(uri, localname, qName);

            currentHandlerInfo = (HandlerInfo) ((handlerInfos.size() > 0) ? handlerInfos.pop() : null);
            super.setContentHandler((currentHandlerInfo != null) ? (ContentHandler) currentHandlerInfo.elementHandler : output);

        } else {
            super.endElement(uri, localname, qName);
        }

        namespaceSupport.popContext();
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        super.characters(chars, start, length);
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        // Update global NamespaceSupport
        namespaceSupport.declarePrefix(prefix, uri);
        super.startPrefixMapping(prefix, uri);
    }

    public void endPrefixMapping(String s) throws SAXException {
        super.endPrefixMapping(s);
    }

    private ElementHandlerNew getHandlerByClassName(String handlerClassName) {
        try {
            final Class handlerClass = Class.forName(handlerClassName);
            return (ElementHandlerNew) handlerClass.newInstance();
        } catch (InstantiationException e) {// TODO: location data
            throw new OXFException(e);
        } catch (IllegalAccessException e) {
            throw new OXFException(e);
        } catch (ClassNotFoundException e) {
            throw new OXFException(e);
        }
    }

    private static class HandlerInfo {
        public int level;
        public ElementHandlerNew elementHandler;

        public HandlerInfo(int level, ElementHandlerNew elementHandler) {
            this.level = level;
            this.elementHandler = elementHandler;
        }
    }
}
