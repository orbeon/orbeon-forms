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
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.XFormsEvents._

class XXFormsDndEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_DND, target, properties, bubbles = false, cancelable = false) {

  def getDndStart = property[String]("dnd-start").get
  def getDndEnd   = property[String]("dnd-end").get
}

object XXFormsDndEvent {
  val StandardProperties = Map(XXFORMS_DND -> Seq("dnd-start", "dnd-end"))
}