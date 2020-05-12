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
import XFormsEvent._
import org.orbeon.saxon.om.NodeInfo

class XXFormsReplaceEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_REPLACE, target, properties, bubbles = true, cancelable = false)
  with InstanceEvent {

  def this(target: XFormsEventTarget, formerNode: NodeInfo, currentNode: NodeInfo) = {
    this(target, Map("former-node" -> Option(formerNode), "current-node" -> Option(currentNode)))
  }

  def formerNode  = property[NodeInfo]("former-node").get
  def currentNode = property[NodeInfo]("current-node").get
}
