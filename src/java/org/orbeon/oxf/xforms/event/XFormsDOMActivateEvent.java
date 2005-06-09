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


/**
 * 4.4.1 The DOMActivate Event
 *
 * Target: form control / Bubbles: Yes / Cancelable: Yes / Context Info: None
 * The default action for this event results in the following: None; notification event only.
 */
public class XFormsDOMActivateEvent extends XFormsEvent {

    public XFormsDOMActivateEvent(Object targetObject) {
        super(XFormsEvents.XFORMS_DOM_ACTIVATE, targetObject, true, true);
    }
}
