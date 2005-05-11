/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms;

import org.dom4j.Element;

import java.util.List;
import java.util.ArrayList;

/**
 * XFormsGenericEvent represents an XForms event passed to all events and actions.
 */
public class XFormsGenericEvent {

    // Input
    private Element controlElement;
    private String value;

    // Output
    private List divsToShow;
    private List divsToHide;

    public XFormsGenericEvent() {
    }

    protected XFormsGenericEvent(Element controlElement) {
        this.controlElement = controlElement;
    }

    public XFormsGenericEvent(Element controlElement, String value) {
        this(controlElement);
        this.value = value;
    }

    public Element getControlElement() {
        return controlElement;
    }

    public String getValue() {
        return value;
    }

    public void addDivToShow(String divId) {
        if (divsToShow == null)
            divsToShow = new ArrayList();

        divsToShow.add(divId);
    }

    public void addDivToHide(String divId) {
        if (divsToHide == null)
            divsToHide = new ArrayList();
        divsToHide.add(divId);
    }

    public List getDivsToShow() {
        return divsToShow;
    }

    public List getDivsToHide() {
        return divsToHide;
    }
}
