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
package org.orbeon.oxf.xforms.processor.handlers.xhtml;

import org.dom4j.Document;
import org.orbeon.oxf.xforms.StaticStateGlobalOps;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Handle elements to which custom components are bound.
 */
public class XXFormsComponentHandler extends XFormsBaseHandlerXHTML {

    private String elementName;
    private String elementQName;

    public XXFormsComponentHandler() {
        // Don't forward, we must instead feed the handlers with the shadow content
        super(false, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final ElementHandlerController controller = handlerContext.getController();
        final ContentHandler contentHandler = controller.getOutput();

        final String staticId = handlerContext.getId(attributes);
        final String prefixedId = handlerContext.getIdPrefix() + staticId;
        final String effectiveId = handlerContext.getEffectiveId(attributes);

        final StaticStateGlobalOps staticGlobalOps = containingDocument.getStaticOps();

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();

        this.elementName = staticGlobalOps.getBinding(prefixedId).containerElementName();
        this.elementQName = XMLUtils.buildQName(xhtmlPrefix, elementName);

        // Produce class of the form xbl-foo-bar
        final String classes = "xbl-component xbl-" + qName.replace(':', '-');

        // Start xhtml:span element
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName, getAttributes(attributes, classes, effectiveId));

        // Push context
        handlerContext.pushComponentContext(prefixedId);

        // Process shadow content if present
        final Document shadowTree = staticGlobalOps.getBinding(prefixedId).fullShadowTree();
        if (shadowTree != null)
            processShadowTree(controller, shadowTree);
    }

    public static void processShadowTree(final ElementHandlerController controller, Document shadowTree) {
        // Tell the controller we are providing a new body
        controller.startBody();

        // Forward shadow content to handler
        // TODO: would be better to handle inclusion and namespaces using XIncludeProcessor facilities instead of custom code
        TransformerUtils.writeDom4j(shadowTree, new EmbeddedDocumentXMLReceiver(controller) {

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

            @Override
            public void setDocumentLocator(Locator locator) {
                // NOP for now. In the future, we should push/pop the locator on ElementHandlerController
            }
        });

        // Tell the controller we are done with the new body
        controller.endBody();
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Pop context
        handlerContext.popComponentContext();

        final ElementHandlerController controller = handlerContext.getController();
        final ContentHandler contentHandler = controller.getOutput();

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName);
    }
}
