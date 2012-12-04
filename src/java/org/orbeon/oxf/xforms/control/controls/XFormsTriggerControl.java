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
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeFocusableControlBase;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

/**
 * Represents an xf:trigger control.
 *
 * TODO: Use inheritance/interface to make this a single-node control that doesn't hold a value.
 */
public class XFormsTriggerControl extends XFormsSingleNodeFocusableControlBase { // TODO: move to Scala
    public XFormsTriggerControl(XBLContainer container, XFormsControl parent, Element element, String id) {
        super(container, parent, element, id);
    }

    private static boolean[] TRIGGER_LHHA_HTML_SUPPORT = { true, true, false, true };

    @Override
    public boolean[] lhhaHTMLSupport() {
        return TRIGGER_LHHA_HTML_SUPPORT;
    }

    // NOTE: We used to make the trigger non-relevant if it was static-readonly. But this caused issues:
    //
    // o at the time computeRelevant() is called, MIPs haven't been read yet
    // o even if we specially read the readonly value from the binding here, then:
    //   o the static-readonly control becomes non-relevant
    //   o therefore its readonly value becomes false (the default)
    //   o therefore isStaticReadonly() returns false!
    //
    // So we keep the control relevant in this case.

    @Override
    public boolean supportAjaxUpdates() {
        // Don't output anything for triggers in static readonly mode
        return ! isStaticReadonly();
    }

    @Override
    public boolean setFocus(boolean inputOnly) {
        return ! inputOnly && super.setFocus(inputOnly);
    }
}
