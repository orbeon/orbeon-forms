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

import java.util.{List ⇒ JList}
import org.orbeon.oxf.xforms.action.actions.XFormsDeleteAction.DeleteInfo
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.XFormsEvents._
import XFormsEvent._
import collection.JavaConverters._
import org.orbeon.saxon.om.NodeInfo

class XFormsDeleteEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsEvent(XFORMS_DELETE, target, properties, bubbles = true, cancelable = false)
    with InstanceEvent {

    def this(target: XFormsEventTarget, deleteInfos: JList[DeleteInfo], deleteIndex: Int) = {
        this(target, Map("deleted-nodes" → Option(deleteInfos.asScala map (_.nodeInfo)), "delete-location" → Option(deleteIndex)))
        _deleteInfosOpt = Option(deleteInfos.asScala)
    }

    private var _deleteInfosOpt: Option[Seq[DeleteInfo]] = None
    def deleteInfos = _deleteInfosOpt.get

    def deletedNodes = property[Seq[NodeInfo]]("deleted-nodes").get
}
