/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.event

import org.orbeon.oxf.xforms.event.XFormsEvent._

/**
 * Custom event.
 */
class XFormsCustomEvent(eventName: String, target: XFormsEventTarget, properties: PropertyGetter, bubbles: Boolean, cancelable: Boolean)
    extends XFormsEvent(eventName, target, properties, bubbles, cancelable) {

  // Don't warn for unsupported attributes on custom events
  override def warnIfMissingProperty = false
}