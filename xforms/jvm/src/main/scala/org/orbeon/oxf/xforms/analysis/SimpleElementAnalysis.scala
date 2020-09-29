/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.dom.Element
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{XFormsId, XFormsNames}
import org.orbeon.xml.NamespaceMapping

/**
 * Representation of a common XForms element supporting optional context, binding and value.
 */
class SimpleElementAnalysis(
   val staticStateContext : StaticStateContext,
   element                : Element,
   parent                 : Option[ElementAnalysis],
   preceding              : Option[ElementAnalysis],
   val scope              : Scope
) extends ElementAnalysis(
  staticStateContext.partAnalysis,
  element,
  parent,
  preceding
) {

  self =>

  require(scope ne null)

  // Index of the element in the view
  def index: Int = staticStateContext.index

  // Make this lazy because we don't want the model to be resolved upon construction. Instead, resolve when scopeModel
  // is used the first time. How can we check/enforce that scopeModel is only used at the right time?
  // Find the model associated with the given element, whether explicitly set with `@model`, or inherited.
  lazy val model: Option[Model] =
    // Check for local @model attribute
    element.attributeValue(XFormsNames.MODEL_QNAME) match {
      case localModelStaticId: String =>
        // Get model prefixed id and verify it belongs to this scope
        val localModelPrefixedId = scope.prefixedIdForStaticId(localModelStaticId)
        val localModel = part.getModel(localModelPrefixedId)
        if (localModel eq null)
          throw new ValidationException("Reference to non-existing model id: " + localModelStaticId, ElementAnalysis.createLocationData(element))

        Some(localModel)
      case _ =>
        // Use inherited model
        closestAncestorInScope match {
          case Some(ancestor) => ancestor.model // there is an ancestor control in the same scope, use its model id
          case None           => part.getDefaultModelForScope(scope) // top-level control in a new scope, use default model id for scope
        }
    }

  final val namespaceMapping: NamespaceMapping = part.metadata.getNamespaceMapping(prefixedId).orNull

  // Only overridden anonymously in `VariableAnalysisTrait` where it says "This is bad architecture"
  // FIXME
  lazy val inScopeVariables: Map[String, VariableTrait] = getRootVariables ++ treeInScopeVariables

  protected def getRootVariables: Map[String, VariableTrait] = Map.empty

  // Only overridden by `RootControl`
  // TODO: pass during construction?
  def containerScope: Scope = part.containingScope(prefixedId)

  final def getChildElementScope(childElement: Element): Scope = {
    val childPrefixedId =  XFormsId.getRelatedEffectiveId(prefixedId, childElement.idOrNull)
    part.scopeForPrefixedId(childPrefixedId)
  }
}
