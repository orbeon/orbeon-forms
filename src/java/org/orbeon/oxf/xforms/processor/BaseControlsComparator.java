/**
 *  Copyright (C) 2005-2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.helpers.AttributesImpl;

public abstract class BaseControlsComparator implements ControlsComparator {

    protected static boolean addAttributeIfNeeded(AttributesImpl attributesImpl, String name, String value, boolean isNewRepeatIteration, boolean isDefaultValue) {
        if (isNewRepeatIteration && isDefaultValue) {
            return false;
        } else {
            attributesImpl.addAttribute("", name, name, ContentHandlerHelper.CDATA, value);
            return true;
        }
    }

    protected static void outputDeleteRepeatTemplate(ContentHandlerHelper ch, XFormsControl xformsControl2, int count) {
        final String repeatControlId = xformsControl2.getEffectiveId();
        final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        final String templateId = (indexOfRepeatHierarchySeparator == -1) ? repeatControlId : repeatControlId.substring(0, indexOfRepeatHierarchySeparator);
        final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "delete-repeat-elements",
                new String[] { "id", templateId, "parent-indexes", parentIndexes, "count", "" + count });
    }

    protected static void outputCopyRepeatTemplate(ContentHandlerHelper ch, XFormsRepeatControl repeatControlInfo, int startSuffix, int endSuffix) {
        final String repeatControlId = repeatControlInfo.getEffectiveId();
        final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "copy-repeat-template",
                new String[] { "id", repeatControlInfo.getRepeatId(), "parent-indexes", parentIndexes, "start-suffix",
                        Integer.toString(startSuffix), "end-suffix", Integer.toString(endSuffix) });
    }
}
