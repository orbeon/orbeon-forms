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
 * 4.2.1 The xforms-model-construct Event
 *
 * Target: model / Bubbles: Yes / Cancelable: No / Context Info: None
 */
public class XFormsModelConstructEvent extends XFormsEvent {

    public XFormsModelConstructEvent(Object targetObject) {
        super(org.orbeon.oxf.xforms.event.XFormsEvents.XFORMS_MODEL_CONSTRUCT, targetObject, true, false);
    }
}
