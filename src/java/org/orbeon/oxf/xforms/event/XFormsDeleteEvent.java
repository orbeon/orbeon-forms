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
package org.orbeon.oxf.xforms.event;

import org.orbeon.oxf.xforms.XFormsEvent;
import org.orbeon.oxf.xforms.XFormsEvents;

/**
 * 4.4.5 The xforms-insert and xforms-delete Events
 */
public class XFormsDeleteEvent extends XFormsEvent {

    private String xpathExpression;

    public XFormsDeleteEvent(String xpathExpression) {
        super(XFormsEvents.XFORMS_INSERT);
        this.xpathExpression = xpathExpression;
    }

    public String getXpathExpression() {
        return xpathExpression;
    }
}
