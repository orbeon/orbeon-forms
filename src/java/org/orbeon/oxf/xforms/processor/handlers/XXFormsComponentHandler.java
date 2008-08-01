/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor.handlers;

import org.dom4j.Element;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handle elements to which custom components are bound.
 */
public class XXFormsComponentHandler extends HandlerBase {

    public XXFormsComponentHandler() {
        // Don't forward, we must instead feed the handlers with the shadow content
        super(false, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final ElementHandlerController controller = handlerContext.getController();
        final ContentHandler contentHandler = controller.getOutput();

        final String staticId = handlerContext.getId(attributes);
        final String effectiveId = handlerContext.getEffectiveId(attributes);
        final String elementName = "div";
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String elementQName = XMLUtils.buildQName(xhtmlPrefix, elementName);

        // Produce class of the form xbl-foo-bar
        final String classes = "xbl-" + qName.replace(':', '-');

        // Start xhtml:span element
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName, getAttributes(attributes, classes, effectiveId));

        // Push context
        handlerContext.pushComponentContext(staticId);

        // Process shadow content if present
        final Element shadowTree = containingDocument.getStaticState().getFullShadowTree(staticId);
        if (shadowTree != null) {
            // TODO: handle inclusion and namespaces using XIncludeProcessor facilities
            TransformerUtils.writeDom4j(shadowTree, new ElementFilterContentHandler(controller) {
                protected boolean isFilterElement(String uri, String localname, String qName, Attributes attributes) {
                    // We filter out XForms elements that are not controls
                    return !XFormsControlFactory.isBuiltinControl(localname)
                            && (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)
                                || XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)
                                || XFormsConstants.XBL_NAMESPACE_URI.equals(uri));
                }

                public void startDocument() throws SAXException {
                }

                public void endDocument() throws SAXException {
                }

                private int level = 0;

                public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

                    if (level != 0)
                        super.startElement(uri, localname, qName, attributes);

                    level++;
                }

                public void endElement(String uri, String localname, String qName) throws SAXException {

                    level--;

                    if (level != 0)
                        super.endElement(uri, localname, qName);
                }
            });
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Pop context
        handlerContext.popComponentContext();

        final ElementHandlerController controller = handlerContext.getController();
        final ContentHandler contentHandler = controller.getOutput();

        final String elementName = "div";
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String elementQName = XMLUtils.buildQName(xhtmlPrefix, elementName);

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName);
    }
}
