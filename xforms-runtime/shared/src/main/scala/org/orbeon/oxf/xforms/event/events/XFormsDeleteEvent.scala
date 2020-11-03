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

import org.orbeon.oxf.xforms.action.actions.XFormsDeleteAction.DeletionDescriptor
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget}
import org.orbeon.saxon.om

class XFormsDeleteEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_DELETE, target, properties, bubbles = true, cancelable = false)
  with InstanceEvent {

  def this(
    target              : XFormsEventTarget,
    deletionDescriptors : Seq[DeletionDescriptor],
    deleteIndexOpt      : Option[Int]
  ) = {
    this(
      target,
      Map(
        "deleted-nodes"   -> Option(deletionDescriptors map (_.nodeInfo)),
        "delete-location" -> deleteIndexOpt,
        "update-repeats"  -> Some(false)
      )
    )
    _deletionDescriptorsOpt = Option(deletionDescriptors)
  }

  private var _deletionDescriptorsOpt: Option[Seq[DeletionDescriptor]] = None
  def deletionDescriptors = _deletionDescriptorsOpt.get

  def deletedNodes  = property[Seq[om.NodeInfo]]("deleted-nodes").get
}
