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
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.value.Int64Value;

import java.util.ArrayList;
import java.util.List;

public class XXFormsNodesetChangedEvent extends XFormsUIEvent {

    private static final String OLD_POSITIONS_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "from-positions");
    private static final String NEW_POSITIONS_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "to-positions");
    private static final String NEW_ITERATIONS_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "new-positions");

    private final List<XFormsRepeatIterationControl> newIterations;
    private final List<Integer> oldIterationPositions;
    private final List<Integer> newIterationPositions;

    public XXFormsNodesetChangedEvent(XFormsContainingDocument containingDocument, XFormsControl targetObject,
                                      List<XFormsRepeatIterationControl> newIterations,
                                      List<Integer> oldIterationPositions, List<Integer> newIterationPositions) {
        super(containingDocument, XFormsEvents.XXFORMS_NODESET_CHANGED, targetObject);

        this.newIterations = newIterations;
        this.oldIterationPositions = oldIterationPositions;
        this.newIterationPositions = newIterationPositions;
    }

    @Override
    public SequenceIterator getAttribute(String name) {
        if (NEW_ITERATIONS_ATTRIBUTE.equals(name)) {
            return iterationsToSequenceIterator(newIterations);
        } else if (OLD_POSITIONS_ATTRIBUTE.equals(name)) {
            return integersToSequenceIterator(oldIterationPositions);
        } else if (NEW_POSITIONS_ATTRIBUTE.equals(name)) {
            return integersToSequenceIterator(newIterationPositions);
        } else {
            return super.getAttribute(name);
        }
    }

    private static SequenceIterator iterationsToSequenceIterator(List<XFormsRepeatIterationControl> list) {
        if (list.size() > 0) {
            final List<Item> items = new ArrayList<Item>(list.size());
            for (XFormsRepeatIterationControl i: list)
                items.add(new Int64Value(i.getIterationIndex()));
            return new ListIterator(items);
        } else {
            return EmptyIterator.getInstance();
        }
    }

    private static SequenceIterator integersToSequenceIterator(List<Integer> list) {
        if (list.size() > 0) {
            final List<Item> items = new ArrayList<Item>(list.size());
            for (Integer i: list)
                items.add(new Int64Value(i));
            return new ListIterator(items);
        } else {
            return EmptyIterator.getInstance();
        }
    }
}