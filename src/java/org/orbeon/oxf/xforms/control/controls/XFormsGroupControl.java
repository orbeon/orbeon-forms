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
import org.dom4j.QName;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.analysis.controls.ContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueContainerControl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents an xforms:group container control.
 */
public class XFormsGroupControl extends XFormsValueContainerControl {

    private ContainerControl containerControl;

    // List of attributes to handle as AVTs
    private static final QName[] TD_EXTENSION_ATTRIBUTES = {
        QName.get("rowspan"),
        QName.get("colspan")
    };

    @Override
    protected QName[] getExtensionAttributes() {
        // Extension attributes depend on the name of the element
        final QName elementQName = containerControl.elementQName();
        if (elementQName != null && "td".equals(elementQName.getName()))
            return TD_EXTENSION_ATTRIBUTES;
        else
            return null;
    }

    public XFormsGroupControl(XBLContainer container, XFormsControl parent, Element element, String name, String id, Map<String, Element> state) {
        super(container, parent, element, name, id);

        // TODO: every control should point to its static analysis
        this.containerControl = (ContainerControl) container.getPartAnalysis().getControlAnalysis(getPrefixedId());
    }

    @Override
    public boolean isStaticReadonly() {
        // Static readonly-ness doesn't seem to make much sense for xforms:group, and we don't want to see the
        // xforms-static class in the resulting HTML
        return false;
    }

    @Override
    public boolean supportAjaxUpdates() {
        return !getAppearances().contains(XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME);
    }

    // Allow DOMActivate on group
    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.DOM_ACTIVATE);
    }

    @Override
    protected Set<String> getAllowedExternalEvents() {
        return ALLOWED_EXTERNAL_EVENTS;
    }
}
