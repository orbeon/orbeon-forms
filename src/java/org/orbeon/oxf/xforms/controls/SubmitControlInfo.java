/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.controls;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsModelSubmission;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitEvent;

/**
 * Represents an xforms:submit control.
 */
public class SubmitControlInfo extends ControlInfo {
    public SubmitControlInfo(XFormsContainingDocument containingDocument, ControlInfo parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        // Do the default stuff upon receiving a DOMActivate event
        if (XFormsEvents.XFORMS_DOM_ACTIVATE.equals(event.getEventName())) {

            // Find submission id
            final String submissionId =  XFormsUtils.namespaceId(containingDocument, getElement().attributeValue("submission"));
            if (submissionId == null)
                throw new OXFException("xforms:submit requires a submission attribute.");

            // Find submission object and dispatch submit event to it
            final Object object = containingDocument.getObjectById(pipelineContext, submissionId);
            if (object instanceof XFormsModelSubmission) {
                final XFormsModelSubmission submission = (XFormsModelSubmission) object;
                containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitEvent(submission));
            } else {
                throw new OXFException("xforms:submit submission attribute must point to an xforms:submission element: " + submissionId);
            }
        }
        super.performDefaultAction(pipelineContext, event);
    }
}
