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

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.SingletonIterator;
import org.orbeon.saxon.value.IntegerValue;

public class XXFormsIndexChangedEvent extends XFormsUIEvent {

    private static final String OLD_INDEX_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "old-index");
    private static final String NEW_INDEX_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "new-index");

    private int oldIndex;
    private int newIndex;

    public XXFormsIndexChangedEvent(XFormsContainingDocument containingDocument, XFormsControl targetObject,
                                      int oldIndex, int newIndex) {
        super(containingDocument, XFormsEvents.XXFORMS_INDEX_CHANGED, targetObject);

        this.oldIndex = oldIndex;
        this.newIndex = newIndex;
    }

    @Override
    public SequenceIterator getAttribute(String name) {
        if (OLD_INDEX_ATTRIBUTE.equals(name)) {
            return SingletonIterator.makeIterator(new IntegerValue(oldIndex));
        } else if (NEW_INDEX_ATTRIBUTE.equals(name)) {
            return SingletonIterator.makeIterator(new IntegerValue(newIndex));
        } else {
            return super.getAttribute(name);
        }
    }
}
