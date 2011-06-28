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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueContainerControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.Map;

/**
 * Represents an xforms:group container control.
 */
public class XFormsGroupControl extends XFormsValueContainerControl {

    public static final String INTERNAL_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME);

    public XFormsGroupControl(XBLContainer container, XFormsControl parent, Element element, String name, String id, Map<String, Element> state) {
        super(container, parent, element, name, id);
    }

    @Override
    public boolean isStaticReadonly() {
        // Static readonly-ness doesn't seem to make much sense for xforms:group, and we don't want to see the
        // xforms-static class in the resulting HTML
        return false;
    }

    @Override
    public boolean supportAjaxUpdates() {
        return !INTERNAL_APPEARANCE.equals(getAppearance());
    }
}
