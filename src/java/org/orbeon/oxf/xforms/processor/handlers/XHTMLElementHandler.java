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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.*;

/**
 * Handle xhtml:* for handling AVTs as well as rewriting @id and @for.
 */
public class XHTMLElementHandler extends XFormsBaseHandler {
    public XHTMLElementHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        // Start xhtml:* element
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        final String staticId = handlerContext.getId(attributes);
        final String prefixedId = handlerContext.getIdPrefix() + staticId;

        if (staticId != null) {
            final boolean hasAVT = containingDocument.getStaticState().hasAttributeControl(prefixedId);
            final String effectiveId = handlerContext.getEffectiveId(attributes);
            boolean found = false;
            if (hasAVT) {
                // This element has at least one AVT so process its attributes

                final int attributesCount = attributes.getLength();
                for (int i = 0; i < attributesCount; i++) {
                    final String attributeValue = attributes.getValue(i);
                    if (XFormsUtils.maybeAVT(attributeValue)) {
                        // This is an AVT most likely
                        found = true;

                        final String attributeLocalName = attributes.getLocalName(i);
                        final String attributeQName = attributes.getQName(i);// use qualified name so we match on "xml:lang"

                        // Get static id of attribute control associated with this particular attribute
                        final String attributeControlStaticId; {
                            final AttributeControl controlAnalysis = containingDocument.getStaticState().getAttributeControl(prefixedId, attributeQName);
                            attributeControlStaticId = controlAnalysis.element().attributeValue(XFormsConstants.ID_QNAME);
                        }

                        // Find concrete control if possible
                        final XXFormsAttributeControl attributeControl;
                        if (handlerContext.isTemplate()) {
                            attributeControl = null;
                        } else if (attributeControlStaticId != null) {
                            final String attributeControlEffectiveId = XFormsUtils.getRelatedEffectiveId(effectiveId, attributeControlStaticId);
                            attributeControl = (XXFormsAttributeControl) containingDocument.getControls().getObjectByEffectiveId(attributeControlEffectiveId);
                        } else {
                            // This should not happen
                            attributeControl = null;
                        }

                        // Determine attribute value
                        // NOTE: This also handles dummy images for the xhtml:img/@src case
                        final String effectiveAttributeValue = XXFormsAttributeControl.getExternalValue(attributeControl, attributeQName);

                        // Set the value of the attribute
                        attributes = XMLUtils.addOrReplaceAttribute(attributes, attributes.getURI(i),
                                XMLUtils.prefixFromQName(attributeQName), attributeLocalName, effectiveAttributeValue);
                    }
                }

                if (found) {
                    // Update the value of the id attribute
                    attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", "id", XFormsUtils.namespaceId(containingDocument, effectiveId));
                }
            }

            if (!found) {
                // Id was not replaced as part of AVT processing

                // Update the value of the id attribute
                attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", "id", XFormsUtils.namespaceId(containingDocument, effectiveId));
            }
        }

        // Check @for attribute
        {
            final String forAttribute = attributes.getValue("for");
            if (forAttribute != null) {
                attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", "for", handlerContext.getIdPrefix() + forAttribute + handlerContext.getIdPostfix());
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
