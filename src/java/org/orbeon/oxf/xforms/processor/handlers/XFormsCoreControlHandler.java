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
import org.orbeon.oxf.xforms.control.XFormsValueControl;
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
        final XFormsSingleNodeControl xformsControl = handlerContext.isGenerateTemplate()
                ? null : (XFormsSingleNodeControl) containingDocument.getObjectById(effectiveId);

        if (mustOutputControl()) {
            // Get local order for control
            final String localOrder = attributes.getValue(XFormsConstants.XXFORMS_ORDER_QNAME.getNamespaceURI(),
                    XFormsConstants.XXFORMS_ORDER_QNAME.getName());

            // Use local or default config
            final String[] config = (localOrder != null) ? StringUtils.split(localOrder) : handlerContext.getDocumentOrder();

            for (int i = 0; i < config.length; i++) {
                final String current = config[i];

                if ("control".equals(current)) {
                    // Handle control
                    handleControl(uri, localname, qName, attributes, id, effectiveId, xformsControl);
                } else if ("label".equals(current)) {
                    // xforms:label
                    if (isOutputStandardLabel()) {
                        handleLabelHintHelpAlert(id, effectiveId, current, xformsControl);
                    }
                } else if ("alert".equals(current)) {
                    // xforms:alert
                    if (isOutputStandardAlert(attributes)) {
                        handleLabelHintHelpAlert(id, effectiveId, current, xformsControl);
                    }
                } else if ("hint".equals(current)) {
                    // xforms:hint
                    if (isOutputStandardHint()) {
                        handleLabelHintHelpAlert(id, effectiveId, current, xformsControl);
                    }
                } else {
                    // xforms:help
                    handleLabelHintHelpAlert(id, effectiveId, current, xformsControl);
                }
            }
        }
    }

    protected boolean mustOutputControl() {
        // May be overridden by subclasses
        return true;
    }

    protected boolean isOutputStandardLabel() {
        // May be overridden by subclasses
        return true;
    }

    protected boolean isOutputStandardHint() {
        // May be overridden by subclasses
        return true;
    }

    protected boolean isOutputStandardAlert(Attributes attributes) {
        // May be overridden by subclasses
        return true;
    }

    // Must be overridden by subclasses
    protected abstract void handleControl(String uri, String localname, String qName, Attributes attributes, String id, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException;
}
