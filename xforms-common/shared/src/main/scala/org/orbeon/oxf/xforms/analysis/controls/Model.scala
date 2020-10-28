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
import org.orbeon.oxf.xforms.analysis.controls.VariableAnalysisTrait
import org.orbeon.oxf.xforms.analysis.model.ModelDefs._
import org.orbeon.oxf.xforms.analysis.{EventHandler, _}
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

import scala.collection.mutable


/**
 * Static analysis of an XForms model <xf:model> element.
 */
class Model(
  index            : Int,
  elem             : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ElementAnalysis(index, elem, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
  with WithChildrenTrait
  with ModelInstances
  with ModelVariables
  with ModelSubmissions
  with ModelEventHandlers
  with BindTree
  with ModelBinds {

  require(scope ne null)

  override def freeTransientState(): Unit = {
    super.freeTransientState()
    freeVariablesTransientState()
    freeBindsTransientState()
  }
}

trait ModelInstances {

  self: Model =>

  // Instance objects
  // `lazy` because the children are evaluated after the container
  lazy val instances: collection.Map[String, Instance] = {
    val m = mutable.LinkedHashMap[String, Instance]()
    m ++= children.iterator collect { case instance: Instance => instance.staticId -> instance }
    m
  }

  // General info about instances
  lazy val hasInstances = instances.nonEmpty
  lazy val defaultInstanceOpt = instances.headOption map (_._2)
  lazy val defaultInstanceStaticId = instances.headOption map (_._1) orNull
  lazy val defaultInstancePrefixedId = Option(if (hasInstances) scope.fullPrefix + defaultInstanceStaticId else null)
  // TODO: instances on which MIPs depend
}

class ModelVariable(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ElementAnalysis(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
  with VariableAnalysisTrait

trait ModelVariables {

  self: Model =>

  // NOTE: It is possible to imagine a model having in-scope variables, but this is not supported now
//  override lazy val inScopeVariables = Map.empty[String, VariableTrait]
//
//  // Handle variables
//  val variablesSeq: Seq[VariableAnalysisTrait] = {
//
//    // NOTE: For now, all top-level variables in a model are visible first, then only are binds variables visible.
//    // In the future, we might want to change that to use document order between variables and binds, but some
//    // more thinking is needed wrt the processing model.
//
//    val someSelf = self.some
//
//    // Iterate and resolve all variables in order
//    var preceding: Option[VariableAnalysisTrait] = None
//
//    val variableElements = self.element.elements filter (e => ControlAnalysisFactory.isVariable(e.getQName))
//
//    for {
//      variableElement <- variableElements
//      analysis: VariableAnalysisTrait = {
//        val result = new ElementAnalysis(part, index /* would be wrong */, variableElement, someSelf, preceding, scope) with VariableAnalysisTrait
//        preceding = result.some
//        result
//      }
//    } yield
//      analysis
//  }
//
//  val variablesMap: Map[String, VariableAnalysisTrait] = variablesSeq map (variable => variable.name -> variable) toMap

  // `lazy` because the children are evaluated after the container
  lazy val variablesSeq: Iterable[VariableAnalysisTrait] = children collect { case v: VariableAnalysisTrait => v }
  lazy val variablesMap: Map[String, VariableAnalysisTrait] = variablesSeq map (variable => variable.name -> variable) toMap

  def freeVariablesTransientState(): Unit =
    for (variable <- variablesSeq)
      variable.freeTransientState()
}

trait ModelSubmissions {

  self: Model =>

  // Submissions (they are all direct children)
  // `lazy` because the children are evaluated after the container
  lazy val submissions: List[Submission] = children collect { case s: Submission => s } toList
}

trait ModelEventHandlers {

  self: Model =>

  // Event handlers, including on submissions and within nested actions
  lazy val eventHandlers: List[EventHandler] = descendants collect { case e: EventHandler => e } toList
}

trait ModelBinds {

  selfModel: Model =>

  // Q: Why do we pass isCustomMIP to BindTree? Init order issue?
  def isCustomMIP: QName => Boolean = {

    import ElementAnalysis.attQNameSet

    def canBeCustomMIP(qName: QName) =
      qName.namespace.prefix.nonEmpty &&
      ! qName.namespace.prefix.startsWith("xml") &&
      (StandardCustomMIPsQNames(qName) || ! NeverCustomMIPsURIs(qName.namespace.uri))

    selfModel.element.attributeOpt(XXFORMS_CUSTOM_MIPS_QNAME) match {
      case Some(_) =>
        // If the attribute is present, allow all specified QNames if valid, plus standard MIP QNames
        attQNameSet(selfModel.element, XXFORMS_CUSTOM_MIPS_QNAME, namespaceMapping) ++ StandardCustomMIPsQNames filter canBeCustomMIP
      case None    =>
        // Attribute not present: backward-compatible behavior
        canBeCustomMIP
    }
  }

  // TODO: use and produce variables introduced with xf:bind/@name

  // TODO: rename one of the 2 so we don't need the indirection
  def containsBind(bindId: String)          = bindIds(bindId)
}
