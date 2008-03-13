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

import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handle xhtml:* when AVTs are turned on.
 */
public class XHTMLElementHandler extends HandlerBase {
    public XHTMLElementHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        // Start xhtml:* element
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        final String id = attributes.getValue("id");
        if (id != null) {
            final String effectiveId = id + handlerContext.getIdPostfix();

            final XFormsControls.ControlsState controlState = containingDocument.getXFormsControls().getCurrentControlsState();
            final boolean hasAVT = controlState.hasAttributeControl(effectiveId);
            if (hasAVT) {
                // This XHTML element has at least one AVT so process its attributes

                final int attributesCount = attributes.getLength();
                for (int i = 0; i < attributesCount; i++) {
                    final String attributeValue = attributes.getValue(i);
                    if (attributeValue.indexOf('{') != -1) {
                        // This is an AVT
                        final String attributeName = attributes.getLocalName(i);
                        final XXFormsAttributeControl attributeControl = controlState.getAttributeControl(effectiveId, attributeName);

                        // Update the value of the id attribute
                        attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", "id", effectiveId);

                        // Find effective attribute value
                        final String effectiveAttributeValue = (attributeControl != null) ? attributeControl.getExternalValue(pipelineContext) : "";

                        // Set the value of the attribute
                        attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", attributeName, effectiveAttributeValue);
                    }
                }
            }
        }

        contentHandler.startElement(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        // Close xhtml:*
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(uri, localname, qName);
    }
}
