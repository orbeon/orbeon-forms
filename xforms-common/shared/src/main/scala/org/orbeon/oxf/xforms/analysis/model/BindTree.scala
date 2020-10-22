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

import org.orbeon.dom._
import org.orbeon.oxf.xforms.analysis._

import scala.collection.{mutable => m}


trait BindTree {

  bindTree: Model =>

  def isCustomMIP: QName => Boolean

  // All bind ids
  val bindIds = new m.LinkedHashSet[String]

  // All binds by static id
  val bindsById = new m.LinkedHashMap[String, StaticBind]

  // Binds by name (for binds with a name)
  val bindsByName = new m.LinkedHashMap[String, StaticBind]

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
  val bindInstances                    = m.LinkedHashSet[String]() // instances to which binds apply (i.e. bind/@ref point to them)
  val computedBindExpressionsInstances = m.LinkedHashSet[String]() // instances to which computed binds apply
  val validationBindInstances          = m.LinkedHashSet[String]() // instances to which validation binds apply

  // `lazy` because the children are evaluated after the container
  lazy val topLevelBinds: List[StaticBind] =
    children collect { case sb: StaticBind => sb } toList

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

  // In-scope variable on binds include variables implicitly declared with bind/@name
  // Used by XPath analysis
  lazy val allBindVariables: Map[String, VariableTrait] =
  variablesMap ++ (bindsByName map { case (k, v) => k -> new BindAsVariable(k, v) })

  class BindAsVariable(val name: String, bind: StaticBind) extends VariableTrait {
    def variableAnalysis: Option[XPathAnalysis] = bind.bindingAnalysis
  }

  // Whether we figured out all XPath ref analysis
  // NOTE: Don't eagerly call `hasBinds`. The default value can be `false` as we set it during analysis.
  var figuredAllBindRefAnalysis: Boolean = false

  var recalculateOrder : Option[List[StaticBind]] = None
  var defaultValueOrder: Option[List[StaticBind]] = None

  def freeBindsTransientState(): Unit =
    for (bind <- topLevelBinds)
      bind.freeTransientState()
}