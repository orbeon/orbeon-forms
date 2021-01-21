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
package org.orbeon.oxf.xforms.analysis.model

import cats.Eval
import org.orbeon.dom._
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.controls.VariableTrait

import scala.collection.mutable


trait BindTree {

  bindTree: Model =>

  def isCustomMIP: QName => Boolean

  // Field below are updated by `StaticBind`
  // All bind ids
  val bindIds = new mutable.LinkedHashSet[String]

  // All binds by static id
  val bindsById = new mutable.LinkedHashMap[String, StaticBind]

  // Binds by name (for binds with a name)
  val bindsByName = new mutable.LinkedHashMap[String, StaticBind]

  // Types of binds we have
  var hasDefaultValueBind            = false
  var hasCalculateBind               = false
  var hasTypeBind                    = false
  var hasRequiredBind                = false
  var hasConstraintBind              = false
  var hasNonPreserveWhitespace       = false

  var mustRecalculate                = false
  var mustRevalidate                 = false

  // Instances affected by binding XPath expressions
  val bindInstances                    = mutable.LinkedHashSet[String]() // instances to which binds apply (i.e. bind/@ref point to them)
  val computedBindExpressionsInstances = mutable.LinkedHashSet[String]() // instances to which computed binds apply
  val validationBindInstances          = mutable.LinkedHashSet[String]() // instances to which validation binds apply

  private def newLazyTopLevelBinds = Eval.later {

    class BindAsVariable(val name: String, bind: StaticBind) extends VariableTrait {
      def variableAnalysis: Option[XPathAnalysis] = bind.bindingAnalysis
    }

    val topLevelBinds = children collect { case sb: StaticBind => sb } toList
    val allBindVariables = variablesMap ++ (bindsByName map { case (k, v) => k -> new BindAsVariable(k, v) })

    (topLevelBinds, allBindVariables)
  }

  // `lazy` because the children are evaluated after the container
  // But we need a re-initializable lazy value, so we use `Eval` and a `var`.
  private var lazyValues: Eval[(List[StaticBind], Map[String, VariableTrait])] = newLazyTopLevelBinds

  def topLevelBinds: List[StaticBind] = lazyValues.value._1

  // In-scope variable on binds include variables implicitly declared with bind/@name
  // Used by XPath analysis
  def allBindVariables: Map[String, VariableTrait] = lazyValues.value._2

  // Whether we figured out all XPath ref analysis
  // NOTE: Don't eagerly call `hasBinds`. The default value can be `false` as we set it during analysis.
  var figuredAllBindRefAnalysis: Boolean = false

  var recalculateOrder : Option[List[StaticBind]] = None
  var defaultValueOrder: Option[List[StaticBind]] = None

  def hasBinds: Boolean = topLevelBinds.nonEmpty

  // Add a new bind
  // 2020-10-01: No usages!
//  def addBind(rawBindElement: Element, parentId: String, precedingId: Option[String]): Unit = {
//
//    assert(! model.part.isTopLevel)
//
//    // First annotate tree
//    val annotatedTree =
//      XBLBindingBuilder.annotateSubtree(
//        model.part,
//        None,
//        rawBindElement.createDocumentCopyParentNamespaces(detach = false),
//        model.scope,
//        model.scope,
//        XXBLScope.Inner,
//        model.containerScope,
//        hasFullUpdate = false,
//        ignoreRoot = false
//      )
//
//    // Add new bind to parent
//    bindsById(parentId).addBind(annotatedTree.getRootElement, precedingId)
//
//    // NOTE: We are not in a top-level part, so for now XPath analysis doesn't need to be updated
//  }

  // Remove an existing bind
//  def removeBind(bind: StaticBind): Unit = {
//
//    assert(! model.part.isTopLevel)
//
//    bind.parent match {
//      case Some(parentBind: StaticBind) => parentBind.removeBind(bind)
//      case _ => throw new IllegalArgumentException // for now, cannot remove top-level binds
//    }
//
//    // TODO: update has*
//    // NOTE: We are not in a top-level part, so for now XPath analysis doesn't need to be updated
//  }

  // For `xxf:dynamic`
  def resetBinds(): Unit = {

    bindIds.clear()
    bindsById.clear()
    bindsByName.clear()

    hasDefaultValueBind            = false
    hasCalculateBind               = false
    hasTypeBind                    = false
    hasRequiredBind                = false
    hasConstraintBind              = false
    hasNonPreserveWhitespace       = false

    mustRecalculate                = false
    mustRevalidate                 = false

    bindInstances.clear()
    computedBindExpressionsInstances.clear()
    validationBindInstances.clear()

    lazyValues = newLazyTopLevelBinds

    // Nested parts don't evaluate this
    recalculateOrder  = None
    defaultValueOrder = None
  }

  def freeBindsTransientState(): Unit =
    for (bind <- topLevelBinds)
      bind.freeTransientState()
}