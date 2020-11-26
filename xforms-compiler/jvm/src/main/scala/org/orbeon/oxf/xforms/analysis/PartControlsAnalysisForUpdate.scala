/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.Model

import scala.collection.mutable


trait PartControlsAnalysisForUpdate {

  val controlAnalysisMap: mutable.LinkedHashMap[String, ElementAnalysis]
  val controlTypes: mutable.HashMap[String, mutable.LinkedHashMap[String, ElementAnalysis]]

  var _attributeControls: Map[String, Map[String, AttributeControl]]

  // For deindexing
  def metadata            : Metadata
  def unmapScopeIds(control: ElementAnalysis): Unit
  def deindexModel(model: Model): Unit
  def deregisterEventHandler(eventHandler: EventHandler): Unit
  def deregisterScript(eventHandler: EventHandler): Unit

  // Deindex the given control
  def deindexControl(control: ElementAnalysis): Unit = {
    val controlName = control.localName
    val prefixedId = control.prefixedId

    controlAnalysisMap -= prefixedId
    controlTypes.get(controlName) foreach (_ -= prefixedId)

    metadata.removeNamespaceMapping(prefixedId)
    metadata.removeBindingByPrefixedId(prefixedId)
    unmapScopeIds(control)

    control match {
      case model: Model          => deindexModel(model)
      case handler: EventHandler =>
        deregisterEventHandler(handler)
        deregisterScript(handler)
      case att: AttributeControl => _attributeControls -= att.forPrefixedId
      case _                     =>
    }

    // NOTE: Can't update controlAppearances and _hasInputPlaceholder without checking all controls again, so for now leave that untouched
  }

  // Remove the given control and its descendants
  def deindexTree(tree: ElementAnalysis, self: Boolean): Unit = {

    if (self) {
      deindexControl(tree)
      tree.removeFromParent()
    }

    tree match {
      case childrenBuilder: WithChildrenTrait =>
        childrenBuilder.descendants foreach deindexControl

        if (! self)
          childrenBuilder.children foreach
            (_.removeFromParent())

      case _ =>
    }
  }
}