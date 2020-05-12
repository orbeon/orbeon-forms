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
import org.orbeon.oxf.xforms.XXBLScope
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.dom4j.Dom4jUtils

import scala.collection.{mutable => m}
import scala.collection.compat._

class BindTree(val model: Model, bindElements: Seq[Element], val isCustomMIP: QName => Boolean) {

  bindTree =>

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

  // Create static binds hierarchy and yield top-level binds
  val topLevelBinds: List[StaticBind] = {
    // NOTE: For now, do as if binds follow all top-level variables
    val preceding = model.variablesSeq.lastOption

    val staticBinds =
      for (bindElement <- bindElements)
        yield new StaticBind(bindTree, bindElement, model, preceding)

    staticBinds.to(List)
  }

  def hasBinds = topLevelBinds.nonEmpty

  // Destroy the tree of binds
  def destroy(): Unit =
    bindsById.values foreach model.part.unmapScopeIds

  // Add a new bind
  def addBind(rawBindElement: Element, parentId: String, precedingId: Option[String]): Unit = {

    assert(! model.part.isTopLevel)

    // First annotate tree
    val annotatedTree =
      model.part.xblBindings.annotateSubtree1(
        None,
        Dom4jUtils.createDocumentCopyParentNamespaces(rawBindElement),
        model.scope,
        model.scope,
        XXBLScope.Inner,
        model.containerScope,
        hasFullUpdate = false,
        ignoreRoot = false)

    // Add new bind to parent
    bindsById(parentId).addBind(annotatedTree.getRootElement, precedingId)

    // NOTE: We are not in a top-level part, so for now XPath analysis doesn't need to be updated
  }

  // Remove an existing bind
  def removeBind(bind: StaticBind): Unit = {

    assert(! model.part.isTopLevel)

    bind.parent match {
      case Some(parentBind: StaticBind) => parentBind.removeBind(bind)
      case _ => throw new IllegalArgumentException // for now, cannot remove top-level binds
    }

    // TODO: update has*
    // NOTE: We are not in a top-level part, so for now XPath analysis doesn't need to be updated
  }

  // In-scope variable on binds include variables implicitly declared with bind/@name
  // Used by XPath analysis
  lazy val allBindVariables = model.variablesMap ++ (bindsByName map { case (k, v) => k -> new BindAsVariable(k, v) })

  class BindAsVariable(val name: String, bind: StaticBind) extends VariableTrait {
    def variableAnalysis = bind.getBindingAnalysis
  }

  // Whether we figured out all XPath ref analysis
  var figuredAllBindRefAnalysis = ! hasBinds // default value sets to true if no binds

  private var _recalculateOrder: Option[List[StaticBind]] = None
  def recalculateOrder = _recalculateOrder

  private var _defaultValueOrder: Option[List[StaticBind]] = None
  def defaultValueOrder = _defaultValueOrder

  def analyzeBindsXPath(): Unit = {
    // Analyze all binds and return whether all of them were successfully analyzed
    figuredAllBindRefAnalysis = (topLevelBinds map (_.analyzeXPathGather)).foldLeft(true)(_ && _)

    // Analyze all MIPs
    // NOTE: Do this here, because MIPs can depend on bind/@name, which requires all bind/@ref to be analyzed first
    topLevelBinds foreach (_.analyzeMIPs())

    if (! figuredAllBindRefAnalysis) {
      bindInstances.clear()
      computedBindExpressionsInstances.clear()
      validationBindInstances.clear()
      // keep bindAnalysis as those can be used independently from each other
    }

    if (model.part.staticState.isCalculateDependencies) {
      _recalculateOrder  = Some(DependencyAnalyzer.determineEvaluationOrder(this, Model.Calculate))
      _defaultValueOrder = Some(DependencyAnalyzer.determineEvaluationOrder(this, Model.Default))
    }
  }

  def bindsToXML(helper: XMLReceiverHelper): Unit =
    // Output binds information
    if (topLevelBinds.nonEmpty) {
      helper.startElement("binds")
      for (bind <- topLevelBinds)
        bind.toXML(helper)
      helper.endElement()
    }

  def freeBindsTransientState(): Unit =
    for (bind <- topLevelBinds)
      bind.freeTransientState()
}