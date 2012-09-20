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
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.saxon.om._
import collection.JavaConverters._

class XFormsInsertEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsEvent(XFORMS_INSERT, target, properties, bubbles = true, cancelable = false) {

    def this(target: XFormsEventTarget, insertedItems: JList[Item], originItems: JList[Item], insertLocationNode: NodeInfo, position: String) =
        this(target, Map(
            "inserted-nodes"       → Option(insertedItems.asScala),  // "The instance data nodes inserted."
            "origin-nodes"         → Option(originItems.asScala),    // "The instance data nodes referenced by the insert action's origin attribute if present, or the empty nodeset if not present."
            "insert-location-node" → Option(insertLocationNode),     // "The insert location node as defined by the insert action."
            "position"             → Option(position)                // "before | after | into" relative to the insert location node ("into" is an Orbeon extension)
        ))

    def insertedItems      = property[Seq[Item]]("inserted-nodes").get
    def originItems        = property[Seq[Item]]("origin-nodes").get
    def insertLocationNode = property[NodeInfo]("insert-location-node").get
    def position           = property[String]("position").get
}