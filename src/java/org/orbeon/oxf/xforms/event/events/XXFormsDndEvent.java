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
package org.orbeon.oxf.xforms.event.events;

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;

import java.util.Map;

/**
 * Internal xxforms-dnd event.
 */
public class XXFormsDndEvent extends XFormsEvent {

    private static final String DND_START_ATTRIBUTE = "dnd-start";
    private static final String DND_END_ATTRIBUTE = "dnd-end";

    public XXFormsDndEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, Map<String, String> parameters) {
        super(containingDocument, XFormsEvents.XXFORMS_DND, targetObject, false, false);

        // Store as attributes so we can test for this event by using xxforms:context
        if (parameters != null) {
            setAttributeAsString(DND_START_ATTRIBUTE, parameters.get(DND_START_ATTRIBUTE));
            setAttributeAsString(DND_END_ATTRIBUTE, parameters.get(DND_END_ATTRIBUTE));
        }
    }

    public String getDndStart() {
        return getAttributeAsString(DND_START_ATTRIBUTE);
    }

    public String getDndEnd() {
        return getAttributeAsString(DND_END_ATTRIBUTE);
    }
}
