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

import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Handler for label, help, hint and alert when those are placed outside controls.
 */
public class XFormsLabelHintHelpAlertHandler extends XFormsBaseHandler {

    public XFormsLabelHintHelpAlertHandler() {
        super(false, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final String lhhaEffectiveId = handlerContext.getEffectiveId(attributes);
        final String forAttribute = attributes.getValue("for");
        if (forAttribute == null) {
            // This can happen if the author forgot a @for attribute, but also for xforms:group/xforms:label[not(@for)]
            return;
        }

        // Find control effective id based on @for attribute
        final String controlEffectiveId = XFormsUtils.getRelatedEffectiveId(lhhaEffectiveId, forAttribute);

        final boolean isTemplate = handlerContext.isTemplate();
        final XFormsControl xformsControl;
        if (!isTemplate) {
            // Get concrete control
            xformsControl = (XFormsControl) containingDocument.getObjectByEffectiveId(controlEffectiveId);

            if (!(xformsControl instanceof XFormsSingleNodeControl)) {
                XFormsServer.logger.warn("Control referred to with @for attribute on <" + localname + "> element is not a single node control: "
                        + ((xformsControl != null) ? xformsControl.getClass().getName() : null));
                return;
            }
        } else {
            // We can't get a control
            xformsControl = null;
        }

        // Output element
        handleLabelHintHelpAlert(controlEffectiveId, localname, (XFormsSingleNodeControl) xformsControl, isTemplate);
    }
}
