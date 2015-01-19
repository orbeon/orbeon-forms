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

import org.orbeon.saxon.om.{ValueRepresentation, NodeInfo}
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.control.{Controls, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.RuntimeBind
import org.orbeon.oxf.xforms.{XFormsUtils, BindVariableResolver}

trait FormRunnerControlOps extends FormRunnerBaseOps {

    private val ControlName = """(.+)-(control|bind|grid|section|template|repeat)""".r // repeat for legacy FB

    val LHHAInOrder = Seq("label", "hint", "help", "alert")
    val LHHANames   = LHHAInOrder.to[Set]

    // Get the control name based on the control, bind, grid, section or template id
    def controlNameFromId(controlOrBindId: String) =
        getStaticIdFromId(controlOrBindId) match {
            case ControlName(name, _) ⇒ name
            case _                    ⇒ null
        }

    def controlNameFromIdOpt(controlOrBindId: String) =
        Option(controlNameFromId(controlOrBindId))

    // Whether the given id is for a control (given its reserved suffix)
    def isIdForControl(controlOrBindId: String) = controlNameFromId(controlOrBindId) ne null

    // Whether the give node corresponds to a control
    // TODO: should be more restrictive
    val IsControl: NodeInfo ⇒ Boolean = hasName

    private val PossibleControlSuffixes = List("control", "grid", "section", "repeat")

    // Find a control by name (less efficient than searching by id)
    def findControlByName(inDoc: NodeInfo, controlName: String) = (
        for {
            suffix  ← PossibleControlSuffixes.iterator
            control ← findInViewTryIndex(inDoc, controlName + '-' + suffix).iterator
        } yield
            control
    ).nextOption()

    // Find a control id by name
    def findControlIdByName(inDoc: NodeInfo, controlName: String) =
        findControlByName(inDoc, controlName) map (_.id)

    // XForms callers: find a control element by name or null (the empty sequence)
    def findControlByNameOrEmpty(inDoc: NodeInfo, controlName: String) =
        findControlByName(inDoc, controlName).orNull

    // Get the control's name based on the control element
    def getControlName(control: NodeInfo) = getControlNameOpt(control).get

    // Get the control's name based on the control element
    def getControlNameOpt(control: NodeInfo) =
        (control \@ "id" headOption) flatMap
            (id ⇒ Option(controlNameFromId(id.stringValue)))

    def hasName(control: NodeInfo) = getControlNameOpt(control).isDefined

    // Return a bind ref or nodeset attribute value if present
    def bindRefOrNodeset(bind: NodeInfo): Option[String] =
        bind \@ ("ref" || "nodeset") map (_.stringValue) headOption

    // Find a bind by name
    def findBindByName(inDoc: NodeInfo, name: String): Option[NodeInfo] =
        findBind(inDoc, isBindForName(_, name))

    // XForms callers: find a bind by name or null (the empty sequence)
    def findBindByNameOrEmpty(inDoc: NodeInfo, name: String) =
        findBindByName(inDoc, name).orNull

    // Find a bind by predicate
    def findBind(inDoc: NodeInfo, p: NodeInfo ⇒ Boolean): Option[NodeInfo] =
        findTopLevelBind(inDoc).toSeq \\ "*:bind" find p

    // NOTE: Not sure why we search for anything but id or name, as a Form Runner bind *must* have an id and a name
    def isBindForName(bind: NodeInfo, name: String) =
        hasIdValue(bind, bindId(name)) || bindRefOrNodeset(bind) == Some(name) // also check ref/nodeset in case id is not present

    // Canonical way: use the `name` attribute
    def getBindNameOrEmpty(bind: NodeInfo) =
        bind attValue "name"

    def findBindName(bind: NodeInfo) =
        bind attValueOpt "name"

    def hasHTMLMediatype(nodes: Seq[NodeInfo]) =
        nodes exists (element ⇒ (element attValue "mediatype") == "text/html")

    def isSingleSelectionControl(localName: String) =
        localName == "select1" || localName.endsWith("-select1")

    def isMultipleSelectionControl(localName: String) =
        localName == "select" || localName.endsWith("-select")

    // Resolve target bind nodes from an action source and a target control.
    //
    // Must be called from an XPath expression.
    //
    // As of 2014-01-31:
    //
    // - the source of an action is a concrete
    //     - a control
    //     - or a model
    // - the target is a control name
    //
    // We first try to resolve a concrete target control based on the source and the control name. If that works, great,
    // we then find the bound node if any. This returns 0 or 1 node.
    //
    // If that fails, for example because the target control is not relevant, we fall back to searching binds. We first
    // identify a bind for the source, if any. Then resolve the target bind. This returns 0 to n nodes.
    //
    // Other considerations:
    //
    // - in the implementation below, the source can also directly refer to a bind
    // - if the source is not found, the target is resolved from the enclosing model
    //
    def resolveTargetRelativeToActionSource(actionSourceAbsoluteId: String, targetControlName: String): ValueRepresentation = {

        val container = XFormsFunction.context.container

        def fromControl: Option[ValueRepresentation] = {

            def findControl =
                container.resolveObjectByIdInScope(
                    absoluteIdToEffectiveId(actionSourceAbsoluteId),
                    controlId(targetControlName)
                )
 
            findControl collect {
                case control: XFormsSingleNodeControl if control.isRelevant ⇒ control.boundNode
            } flatten
        }

        def fromBind: Option[ValueRepresentation] = {

            val sourceEffectiveId = XFormsFunction.context.sourceEffectiveId
            val model             = XFormsFunction.context.model

            val modelBinds        = model.getBinds
            val staticModel       = model.staticModel

            def findBindForSource =
                container.resolveObjectByIdInScope(sourceEffectiveId, actionSourceAbsoluteId) collect {
                    case control: XFormsSingleNodeControl if control.isRelevant ⇒ control.bind
                    case runtimeBind: RuntimeBind                               ⇒ Some(runtimeBind)
                } flatten

            def findBindNodeForSource =
                for (sourceRuntimeBind ← findBindForSource)
                yield
                    sourceRuntimeBind.getOrCreateBindNode(1) // a control bound via `bind` always binds to the first item

            for {
                targetStaticBind  ← staticModel.bindsById.get(bindId(targetControlName))
                value             ← BindVariableResolver.resolveClosestBind(modelBinds, findBindNodeForSource, targetStaticBind)
            } yield
                value
        }

        fromControl orElse fromBind orNull
    }

    def findRepeatedControlsForTarget(actionSourceAbsoluteId: String, targetControlName: String) = {

        val controls =
            Controls.findRepeatedControlsForTarget(
                XFormsFunction.context.containingDocument,
                XFormsUtils.absoluteIdToEffectiveId(actionSourceAbsoluteId),
                controlId(targetControlName)
            )

        controls map (_.getEffectiveId) map XFormsUtils.effectiveIdToAbsoluteId toList
    }
}
