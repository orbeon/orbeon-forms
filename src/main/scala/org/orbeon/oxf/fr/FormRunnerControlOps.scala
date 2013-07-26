/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.XFormsUtils._

trait FormRunnerControlOps extends FormRunnerBaseOps {

    private val ControlName = """(.+)-(control|bind|grid|section|template|repeat)""".r // repeat for legacy FB

    val LHHAInOrder = Seq("label", "hint", "help", "alert")
    val LHHANames   = LHHAInOrder.to[Set]

    // Get the control name based on the control, bind, grid, section or template id
    def controlName(controlOrBindId: String) =
        getStaticIdFromId(controlOrBindId) match {
            case ControlName(name, _) ⇒ name
            case _ ⇒ null
        }

    // Whether the given id is for a control (given its reserved suffix)
    def isIdForControl(controlOrBindId: String) = controlName(controlOrBindId) ne null

    // Whether the give node corresponds to a control
    // TODO: should be more restrictive
    val IsControl: NodeInfo ⇒ Boolean = hasName(_)

    // Find a control by name (less efficient than searching by id)
    def findControlByName(inDoc: NodeInfo, controlName: String) =
        Stream("control", "grid", "section", "repeat") flatMap // repeat for legacy FB
            (suffix ⇒ byId(inDoc, controlName + '-' + suffix)) headOption

    // Find a control id by name
    def findControlIdByName(inDoc: NodeInfo, controlName: String) =
        findControlByName(inDoc, controlName) map (_ attValue "id")

    // XForms callers: find a control element by name or null (the empty sequence)
    def findControlByNameOrEmpty(inDoc: NodeInfo, controlName: String) =
        findControlByName(inDoc, controlName).orNull

    // Get the control's name based on the control element
    def getControlName(control: NodeInfo) = getControlNameOption(control).get

    // Get the control's name based on the control element
    def getControlNameOption(control: NodeInfo) =
        (control \@ "id" headOption) flatMap
            (id ⇒ Option(controlName(id.stringValue)))

    def hasName(control: NodeInfo) = getControlNameOption(control).isDefined

    // Return a bind ref or nodeset attribute value if present
    def bindRefOrNodeset(bind: NodeInfo): Option[String] =
        bind \@ ("ref" || "nodeset") map (_.stringValue) headOption

    // Find a bind by name
    def findBindByName(inDoc: NodeInfo, name: String): Option[NodeInfo] = findBind(inDoc, isBindForName(_, name))

    // XForms callers: find a bind by name or null (the empty sequence)
    def findBindByNameOrEmpty(inDoc: NodeInfo, name: String) = findBindByName(inDoc, name).orNull

    // Find a bind by predicate
    def findBind(inDoc: NodeInfo, p: NodeInfo ⇒ Boolean): Option[NodeInfo] =
        findTopLevelBind(inDoc).toSeq \\ "*:bind" find p

    def isBindForName(bind: NodeInfo, name: String) =
        hasIdValue(bind, bindId(name)) || bindRefOrNodeset(bind) == Some(name) // also check ref/nodeset in case id is not present

    def hasHTMLMediatype(nodes: Seq[NodeInfo]) =
        nodes exists (element ⇒ (element attValue "mediatype") == "text/html")
}
