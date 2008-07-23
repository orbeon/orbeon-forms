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
package org.orbeon.oxf.xforms.processor.handlers;

import org.xml.sax.SAXException;
import org.xml.sax.Attributes;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.apache.commons.lang.StringUtils;

/**
 * Base class for controls with values.
 */
public abstract class XFormsCoreControlHandler extends HandlerBase {

    protected XFormsCoreControlHandler(boolean repeating) {
        super(repeating, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final String id = handlerContext.getId(attributes);
        final String effectiveId = handlerContext.getEffectiveId(attributes);
        final XFormsSingleNodeControl xformsControl = handlerContext.isTemplate()
                ? null : (XFormsSingleNodeControl) containingDocument.getObjectById(effectiveId);

        // Give the handler a chance to do some prep work
        prepareHandler(uri, localname, qName, attributes, id, effectiveId, xformsControl);

        if (isMustOutputControl(xformsControl)) {
            // Get local order for control
            final String localOrder = attributes.getValue(XFormsConstants.XXFORMS_ORDER_QNAME.getNamespaceURI(),
                    XFormsConstants.XXFORMS_ORDER_QNAME.getName());

            // Use local or default config
            final String[] config = (localOrder != null) ? StringUtils.split(localOrder) : handlerContext.getDocumentOrder();

            final boolean isTemplate = handlerContext.isTemplate();

            for (int i = 0; i < config.length; i++) {
                final String current = config[i];

                if ("control".equals(current)) {
                    // Handle control
                    handleControl(uri, localname, qName, attributes, id, effectiveId, xformsControl);
                } else if ("label".equals(current)) {
                    // xforms:label
                    if (isMustOutputStandardLabel(xformsControl)) {
                        handleLabelHintHelpAlert(id, effectiveId, current, xformsControl, isTemplate);
                    }
                } else if ("alert".equals(current)) {
                    // xforms:alert
                    if (isMustOutputStandardAlert(xformsControl, attributes)) {
                        handleLabelHintHelpAlert(id, effectiveId, current, xformsControl, isTemplate);
                    }
                } else if ("hint".equals(current)) {
                    // xforms:hint
                    if (isMustOutputStandardHint(xformsControl)) {
                        handleLabelHintHelpAlert(id, effectiveId, current, xformsControl, isTemplate);
                    }
                } else {
                    // xforms:help
                    handleLabelHintHelpAlert(id, effectiveId, current, xformsControl, isTemplate);
                }
            }
        }
    }

    protected void prepareHandler(String uri, String localname, String qName, Attributes attributes, String id, String effectiveId, XFormsSingleNodeControl xformsControl) {
        // May be overridden by subclasses
    }

    protected boolean isMustOutputControl(XFormsSingleNodeControl xformsControl) {
        // May be overridden by subclasses
        return true;
    }

    protected boolean isMustOutputStandardLabel(XFormsSingleNodeControl xformsControl) {
        // May be overridden by subclasses
        return true;
    }

    protected boolean isMustOutputStandardHint(XFormsSingleNodeControl xformsControl) {
        // May be overridden by subclasses
        return true;
    }

    protected boolean isMustOutputStandardAlert(XFormsSingleNodeControl xformsControl, Attributes attributes) {
        // May be overridden by subclasses
        return true;
    }

    // Must be overridden by subclasses
    protected abstract void handleControl(String uri, String localname, String qName, Attributes attributes, String id, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException;
}
