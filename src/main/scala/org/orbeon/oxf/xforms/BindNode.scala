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
package org.orbeon.oxf.xforms

import org.dom4j.QName
import org.orbeon.oxf.xforms.analysis.model.{StaticBind, Model}
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML
import org.w3c.dom.Node.ELEMENT_NODE
import collection.JavaConverters._
import java.util.{List ⇒ JList}
import org.orbeon.oxf.xforms.analysis.model.StaticBind.{ErrorLevel, ValidationLevel}
import collection.breakOut

// Holds MIPs associated with a given RuntimeBind iteration
// The constructor automatically adds the BindNode to the instance data node if any.
class BindNode(val bindStaticId: String, item: Item, typeQName: QName) {

    import BindNode._

    val (node, hasChildrenElements) =
        item match {
            case node: NodeInfo ⇒
                val hasChildrenElements = node.getNodeKind == ELEMENT_NODE && XML.hasChildElement(node)
                InstanceData.addBindNode(node, this)
                if (typeQName ne null)
                    InstanceData.setBindType(node, typeQName)
                (node, hasChildrenElements)
            case _ ⇒
                (null, false)
        }

    // Current MIP state
    private var _relevant        = Model.DEFAULT_RELEVANT // move to public var once all callers are Scala
    private var _readonly        = Model.DEFAULT_READONLY // move to public var once all callers are Scala
    private var _required        = Model.DEFAULT_REQUIRED // move to public var once all callers are Scala

    private var _typeValid       = Model.DEFAULT_VALID    // move to public var once all callers are Scala
    private var _requiredValid   = Model.DEFAULT_VALID    // move to public var once all callers are Scala

    private var _customMips = Map.empty[String, String]

    // Since there are only 3 levels we should always get an optimized immutable Map
    // For a given level, an empty List is not allowed.
    var failedConstraints = EmptyConstraints

    def constraintsSatisfiedForLevel(level: ValidationLevel) = ! failedConstraints.contains(level)

    def setRelevant(value: Boolean)            = this._relevant = value
    def setReadonly(value: Boolean)            = this._readonly = value
    def setRequired(value: Boolean)            = this._required = value

    def setTypeValid(value: Boolean)           = this._typeValid = value
    def setRequiredValid(value: Boolean)       = this._requiredValid = value

    def setCustom(name: String, value: String) = _customMips += name → value

    def relevant        = _relevant
    def readonly        = _readonly
    def required        = _required

    def typeValid       = _typeValid
    def valid           = _typeValid && _requiredValid && constraintsSatisfiedForLevel(ErrorLevel)
}

object BindNode {

    type Constraints = Map[ValidationLevel, List[StaticBind#ConstraintXPathMIP]]

    val EmptyConstraints: Constraints = Map()

    // NOTE: This takes the first custom MIP of a given name associated with the bind. We do store multiple
    // ones statically, but don't have yet a solution to combine them. Should we string-join them? See also
    // XFormsModelBindsBase.evaluateCustomMIP.
    def collectAllCustomMIPs(bindNodes: JList[BindNode]) =
        if (bindNodes eq null)
            Map.empty[String, String]
        else if (bindNodes.size == 1)
            bindNodes.get(0)._customMips
        else
            bindNodes.asScala.reverse.foldLeft(Map.empty[String, String])(_ ++ _._customMips)

    // For Java callers
    def jCollectFailedConstraints(bindNodes: JList[BindNode]) =
        collectFailedConstraints(if (bindNodes eq null) Nil else bindNodes.asScala)

    // Get all failed constraints for all levels, combining BindNodes if needed
    def collectFailedConstraints(bindNodes: Seq[BindNode]): Constraints = {
        if (bindNodes.isEmpty)
            EmptyConstraints
        else if (bindNodes.size == 1)
            bindNodes(0).failedConstraints
        else {
            // This is rather inefficient but hopefully rare
            val buildersByLevel = collection.mutable.Map[ValidationLevel, collection.mutable.Builder[StaticBind#ConstraintXPathMIP, List[StaticBind#ConstraintXPathMIP]]]()

            for {
                level       ← StaticBind.LevelsByPriority
                bindNode    ← bindNodes
                failed      = bindNode.failedConstraints.getOrElse(level, Nil)
                if failed.nonEmpty
            } locally {
                val builder = buildersByLevel.getOrElse(level, List.newBuilder[StaticBind#ConstraintXPathMIP])
                builder ++= failed
            }

            buildersByLevel.map { case (k, v) ⇒ k → v.result()} (breakOut)
        }
    }
}