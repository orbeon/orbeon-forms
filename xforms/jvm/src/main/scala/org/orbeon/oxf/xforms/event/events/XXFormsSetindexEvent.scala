/**
 *  Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.event.events

import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget}
import org.orbeon.oxf.xforms.event.XFormsEvent._

class XXFormsSetindexEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_SETINDEX, target, properties, bubbles = true, cancelable = false) {

  def this(target: XFormsEventTarget, index: Int) =
    this(target, Map("index" -> Option(index)))

  def index = property[Int]("index").get
}