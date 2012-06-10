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

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.actions.XFormsDeleteAction
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.XFormsEvents
import java.util.{List ⇒ JList}
import collection.JavaConverters._
import org.orbeon.saxon.om.SequenceIterator
import XFormsDeleteEvent._

/**
 * 4.4.5 The xforms-insert and xforms-delete Events
 *
 * Target: instance / Bubbles: Yes / Cancelable: No / Context Info: Path expression used for insert/delete (xsd:string).
 * The default action for these events results in the following: None; notification event only.
 */
class XFormsDeleteEvent(containingDocument: XFormsContainingDocument, targetObject: XFormsEventTarget,
                        val deleteInfos: JList[XFormsDeleteAction.DeleteInfo], val deleteIndex: Int)
    extends XFormsEvent(containingDocument, XFormsEvents.XFORMS_DELETE, targetObject, bubbles = true, cancelable = false) {

    def this(containingDocument: XFormsContainingDocument, targetObject: XFormsEventTarget) =
        this(containingDocument, targetObject, null, -1)

    override def getStandardAttribute(name: String) =
        StandardAttributes.get(name) orElse super.getStandardAttribute(name)
}

private object XFormsDeleteEvent {

    import XFormsEvent._

    val StandardAttributes = Map[String, XFormsDeleteEvent ⇒ SequenceIterator](
        "deleted-nodes"   → (e ⇒ listIterator(e.deleteInfos.asScala map (_.nodeInfo))),
        "delete-location" → (e ⇒ longIterator(e.deleteIndex, e.deleteIndex > 0))
    )
}