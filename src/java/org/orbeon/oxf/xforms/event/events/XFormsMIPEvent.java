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
package org.orbeon.oxf.xforms.event.events;

import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.value.StringValue;

import java.util.Collections;
import java.util.StringTokenizer;
import java.util.List;
import java.util.ArrayList;

/**
 * Base class for events related to MIP changes.
 */
public abstract class XFormsMIPEvent extends XFormsEvent {
    private XFormsControl targetXFormsControl;

    public XFormsMIPEvent(String eventName, XFormsControl targetObject) {
        super(eventName, targetObject, true, false);
        this.targetXFormsControl = targetObject;
    }

    public SequenceIterator getAttribute(String name) {
        if ("target-ref".equals(name)) {
            // Return the node to which the control is bound
            return new ListIterator(Collections.singletonList(targetXFormsControl.getBoundNode()));
        } else if ("alert".equals(name)) {
            final String alert = targetXFormsControl.getAlert(getPipelineContext());
            if (alert != null)
                return new ListIterator(Collections.singletonList(new StringValue(alert)));
            else
                return new EmptyIterator();
        } else if ("label".equals(name)) {
            final String label = targetXFormsControl.getLabel(getPipelineContext());
            if (label != null)
                return new ListIterator(Collections.singletonList(new StringValue(label)));
            else
                return new EmptyIterator();
        } else if ("hint".equals(name)) {
            final String hint = targetXFormsControl.getHint(getPipelineContext());
            if (hint != null)
                return new ListIterator(Collections.singletonList(new StringValue(hint)));
            else
                return new EmptyIterator();
        } else if ("help".equals(name)) {
            final String help = targetXFormsControl.getHelp(getPipelineContext());
            if (help != null)
                return new ListIterator(Collections.singletonList(new StringValue(help)));
            else
                return new EmptyIterator();
        } else if ("repeat-indexes".equals(name)) {
            final String effectiveTargetId = targetXFormsControl.getEffectiveId();
            final int index = (effectiveTargetId == null) ? - 1 : effectiveTargetId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);

            if (index != -1) {
                final String repeatIndexesString = effectiveTargetId.substring(index + 1);
                final StringTokenizer st = new StringTokenizer(repeatIndexesString, "" + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2);
                final List tokens = new ArrayList();
                while (st.hasMoreTokens()) {
                    final String currentToken = st.nextToken();
                    tokens.add(new StringValue(currentToken));
                }
                return new ListIterator(tokens);
            } else {
                return new EmptyIterator();
            }
        } else {
            return super.getAttribute(name);
        }
    }
}
