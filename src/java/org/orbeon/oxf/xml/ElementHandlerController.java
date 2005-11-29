package org.orbeon.oxf.xml;

import orbeon.apache.xml.utils.NamespaceSupport2;
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

    private Stack handlerInfos = new Stack();
    private HandlerInfo currentHandlerInfo;
    private boolean isFillingUpSAXStore;

    private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();

    private Locator locator;

    private int level;
    private boolean mustPushContext = true;

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
            if (mustPushContext)
                namespaceSupport.pushContext();
            else
                mustPushContext = true;

            if (!isFillingUpSAXStore) {
//                namespaceSupport.pushContext();

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

                    if (elementHandler.isRepeating()) {
                        currentHandlerInfo = new HandlerInfo(level, elementHandler, attributes);
                        super.setContentHandler(currentHandlerInfo.saxStore);
                        isFillingUpSAXStore = true;
                    } else {
                        currentHandlerInfo = new HandlerInfo(level, elementHandler);
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

            namespaceSupport.popContext();
            mustPushContext = true;

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
            if (mustPushContext) {
                namespaceSupport.pushContext();
                mustPushContext = false;
            }

            // Update global NamespaceSupport
            namespaceSupport.declarePrefix(prefix, uri);
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
        try {
            final Class handlerClass = Class.forName(handlerClassName);
            return (ElementHandlerNew) handlerClass.newInstance();
        } catch (InstantiationException e) {
            throw new ValidationException(e, new LocationData(locator));
        } catch (IllegalAccessException e) {
            throw new ValidationException(e, new LocationData(locator));
        } catch (ClassNotFoundException e) {
            throw new ValidationException(e, new LocationData(locator));
        }
    }

    private static class HandlerInfo {
        public int level;
        public ElementHandlerNew elementHandler;
        public Attributes attributes;

        public SAXStore saxStore;

        public HandlerInfo(int level, ElementHandlerNew elementHandler) {
            this.level = level;
            this.elementHandler = elementHandler;
        }

        public HandlerInfo(int level, ElementHandlerNew elementHandler, Attributes attributes) {
            this.level = level;
            this.elementHandler = elementHandler;
            this.attributes = new AttributesImpl(attributes);

            this.saxStore = new SAXStore();
        }
    }
}
