/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.processor.xforms.output.element;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsElementContext;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.SAXStore;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class ViewContentHandler extends ForwardingContentHandler {

    private XFormsElementContext elementContext;

    private SAXStore repeatSAXStore = new SAXStore();
    private int repeatElementDepth = 0;
    private boolean recordMode = false;

    public ViewContentHandler(ContentHandler contentHandler, XFormsElementContext elementContext) {
        super(contentHandler);
        this.elementContext = elementContext;
    }

    public void setDocumentLocator(Locator locator) {
        elementContext.setLocator(locator);
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        if (recordMode) {
            repeatSAXStore.startPrefixMapping(prefix, uri);
        } else {
            elementContext.getNamespaceSupport().declarePrefix(prefix, uri);
            super.startPrefixMapping(prefix, uri);
        }
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        if (recordMode) {
            repeatSAXStore.endPrefixMapping(prefix);
        } else {
            super.endPrefixMapping(prefix);
        }
    }

    public void startElement(String uri, String localname, String qname, Attributes attributes) throws SAXException {
        elementContext.getNamespaceSupport().pushContext();
        if (recordMode) {
            // Record event
            repeatElementDepth++;
            repeatSAXStore.startElement(uri, localname, qname, attributes);
        } else if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) || XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) {
            // Get ref / bind / nodeset

            elementContext.pushBinding(attributes.getValue("ref"), attributes.getValue("nodeset"), null, attributes.getValue("bind"));

            // Invoke element
            XFormsElement element = "group".equals(localname) ? new Group()
                    : "repeat".equals(localname) ? new Repeat()
                    : "itemset".equals(localname) ? new Itemset()
                    : new XFormsElement();
            elementContext.pushElement(element);
            element.start(elementContext, uri, localname, qname, attributes);

            // If this is a repeat element, record children events
            if (element.repeatChildren()) {
                recordMode = true;
                repeatSAXStore = new SAXStore();
                repeatElementDepth = 0;
            }

        } else {
            super.startElement(uri, localname, qname, attributes);
        }
    }

    public void endElement(String uri, String localname, String qname) throws SAXException {
        elementContext.getNamespaceSupport().popContext();
        if (recordMode) {
            if (repeatElementDepth == 0) {
                // We are back to the element that requested the repeat
                recordMode = false;
                XFormsElement repeatElement = elementContext.peekElement();
                SAXStore currentSAXStore = repeatSAXStore;
                while (repeatElement.nextChildren(elementContext))
                    currentSAXStore.replay(this);
            } else {
                // Record event
                repeatElementDepth--;
                repeatSAXStore.endElement(uri, localname, qname);
            }
        }

        if (!recordMode) {
            if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) || XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) {
                XFormsElement element = elementContext.peekElement();
                element.end(elementContext, uri, localname, qname);
                elementContext.popElement();
                elementContext.popBinding();
            } else {
                super.endElement(uri, localname, qname);
            }
        }
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (recordMode) {
            repeatSAXStore.characters(chars, start,  length);
        } else  {
            super.characters(chars, start, length);
        }
    }
}
