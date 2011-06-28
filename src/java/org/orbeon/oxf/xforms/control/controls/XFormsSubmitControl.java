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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitEvent;
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

import java.util.Map;

/**
 * Represents an xforms:submit control.
 */
public class XFormsSubmitControl extends XFormsTriggerControl {
    public XFormsSubmitControl(XBLContainer container, XFormsControl parent, Element element, String name, String id, Map<String, Element> state) {
        super(container, parent, element, name, id, state);
    }

    @Override
    public void performDefaultAction(XFormsEvent event) {
        // Do the default stuff upon receiving a DOMActivate event
        if (XFormsEvents.DOM_ACTIVATE.equals(event.getName())) {

            // Find submission id
            final String submissionId =  getControlElement().attributeValue(XFormsConstants.SUBMISSION_QNAME);
            if (submissionId == null)
                throw new ValidationException("xforms:submit requires a submission attribute.", getLocationData());

            // Find submission object and dispatch submit event to it

            final Object object = getXBLContainer().findResolutionScope(XFormsUtils.getPrefixedId(getEffectiveId())).resolveObjectById(getEffectiveId(), submissionId, null);
            if (object instanceof XFormsModelSubmission) {
                final XFormsModelSubmission submission = (XFormsModelSubmission) object;
                submission.getXBLContainer(containingDocument).dispatchEvent(new XFormsSubmitEvent(containingDocument, submission));
            } else {
                // "If there is a null search result for the target object and the source object is an XForms action such as
                // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
                final IndentedLogger indentedLogger = containingDocument.getControls().getIndentedLogger();
                if (indentedLogger.isDebugEnabled())
                    indentedLogger.logDebug("xforms:submit", "submission does not refer to an existing xforms:submission element, ignoring action",
                            "submission id", submissionId);
            }
        }
        super.performDefaultAction(event);
    }
}
