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
package org.orbeon.oxf.xforms.event.events

import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.XFormsEvent._

class XFormsRecalculateEvent(target: XFormsEventTarget, properties: PropertyGetter)
        extends XFormsEvent(XFORMS_RECALCULATE, target, properties, bubbles = true, cancelable = true) {

    def this(target: XFormsEventTarget, applyDefaults: Boolean) =
        this(target, Map("apply-defaults" → Option(applyDefaults)))

    def this(target: XFormsEventTarget) = this(target, Map("apply-defaults" → Some(false)))

    def applyDefaults = property[Boolean]("apply-defaults") getOrElse false
}