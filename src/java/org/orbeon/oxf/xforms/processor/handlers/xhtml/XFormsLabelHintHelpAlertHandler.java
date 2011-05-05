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

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.ElementHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Handler for label, help, hint and alert when those are placed outside controls.
 */
public class XFormsLabelHintHelpAlertHandler extends XFormsBaseHandlerXHTML {

    public XFormsLabelHintHelpAlertHandler() {
        super(false, false);
    }

    @Override
    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final String controlStaticId = attributes.getValue("for");
        if (controlStaticId == null) {
            // This can happen if the author forgot a @for attribute, but also for xforms:group/xforms:label[not(@for)]
            return;
        }

        // Find control ids based on @for attribute
        final String lhhaEffectiveId = handlerContext.getEffectiveId(attributes);
        final String controlPrefixedId = handlerContext.getIdPrefix() + controlStaticId;
        final String controlEffectiveId = XFormsUtils.getRelatedEffectiveId(lhhaEffectiveId, controlStaticId);

        final boolean isTemplate = handlerContext.isTemplate();
        final XFormsControl xformsControl;
        if (!isTemplate) {
            // Get concrete control
            xformsControl = (XFormsControl) containingDocument.getObjectByEffectiveId(controlEffectiveId);

            if (!(xformsControl instanceof XFormsSingleNodeControl)) {
                final XFormsControls xformsControls = containingDocument.getControls();
                xformsControls.getIndentedLogger().logWarning("", "Control referred to with @for attribute is not a single node control",
                        "element", qName, "class", ((xformsControl != null) ? xformsControl.getClass().getName() : null));
                return;
            }
        } else {
            // We can't get a control
            xformsControl = null;
        }

        // Find control element so we know which handler to use
        final Element controlElement = containingDocument.getStaticOps().getControlElement(controlPrefixedId);
        if (controlElement != null) {
            // Get handler
            final ElementHandler handler = handlerContext.getController().getHandler(controlElement);
            if (handler instanceof XFormsControlLifecyleHandler) {
                final XFormsControlLifecyleHandler xformsHandler = (XFormsControlLifecyleHandler) handler;

                // Perform minimal handler initialization because we just want to use it to get the effective id
                xformsHandler.setContext(handlerContext);

                final String controlNamespaceURI = controlElement.getNamespaceURI();
                final String controlPrefix = controlElement.getNamespacePrefix();
                final String controlLocalname = controlElement.getName();
                xformsHandler.init(controlNamespaceURI, controlLocalname,
                        XMLUtils.buildQName(controlPrefix, controlLocalname),
                        XMLUtils.getSAXAttributes(controlElement));

                final String forEffectiveId = xformsHandler.getForEffectiveId();
                handleLabelHintHelpAlert(controlEffectiveId, forEffectiveId, LHHAC.valueOf(localname.toUpperCase()), (XFormsSingleNodeControl) xformsControl, isTemplate, true);
            }
        }
    }
}
    