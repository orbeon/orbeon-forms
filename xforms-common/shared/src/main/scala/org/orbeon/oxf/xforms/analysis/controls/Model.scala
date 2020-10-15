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

import cats.syntax.option._
import org.orbeon.dom._
import org.orbeon.oxf.xforms.analysis.controls.VariableAnalysisTrait
import org.orbeon.oxf.xforms.analysis.model.ModelDefs._
import org.orbeon.oxf.xforms.analysis.{EventHandler, _}
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope

import scala.collection.mutable


/**
 * Static analysis of an XForms model <xf:model> element.
 */
class Model(
  part      : PartAnalysisImpl,
  index     : Int,
  elem      : Element,
  parent    : Option[ElementAnalysis],
  preceding : Option[ElementAnalysis],
  scope     : Scope
) extends ElementAnalysis(part, index, elem, parent, preceding, scope)
  with WithChildrenTrait
  with ModelInstances
  with ModelVariables
  with ModelSubmissions
  with ModelEventHandlers
  with ModelBinds {

  require(scope ne null)

  override lazy val model = this.some

  override def getChildrenContext: Option[XPathAnalysis] =
    defaultInstancePrefixedId map { defaultInstancePrefixedId => // instance('defaultInstanceId')
      PathMapXPathAnalysis(
        partAnalysis              = part,
        xpathString               = PathMapXPathAnalysis.buildInstanceString(defaultInstancePrefixedId),
        namespaceMapping          = null,
        baseAnalysis              = None,
        inScopeVariables          = Map.empty,
        pathMapContext            = null,
        scope                     = scope,
        defaultInstancePrefixedId = defaultInstancePrefixedId.some,
        locationData              = locationData,
        element                   = element,
        avt                       = false
      )
    }

  // Above we only create actions, submissions and instances as children. But binds are also indexed so add them.
  override def indexedElements: Iterator[ElementAnalysis] =
    super.indexedElements ++ bindsById.valuesIterator

  override def freeTransientState(): Unit = {
    super.freeTransientState()
    freeVariablesTransientState()
    freeBindsTransientState()
  }
}

trait ModelInstances {

  self: Model =>

  // Instance objects
  lazy val instances: collection.Map[String, Instance] =
    mutable.LinkedHashMap(children collect { case instance: Instance => instance.staticId -> instance }: _*)

  // General info about instances
  lazy val hasInstances = instances.nonEmpty
  lazy val defaultInstanceOpt = instances.headOption map (_._2)
  lazy val defaultInstanceStaticId = instances.headOption map (_._1) orNull
  lazy val defaultInstancePrefixedId = Option(if (hasInstances) scope.fullPrefix + defaultInstanceStaticId else null)
  // TODO: instances on which MIPs depend
}

trait ModelVariables {

  self: Model =>

  // NOTE: It is possible to imagine a model having in-scope variables, but this is not supported now
  override lazy val inScopeVariables = Map.empty[String, VariableTrait]

  // Get *:variable/*:var elements
  private val variableElements = self.element.elements filter (e => ControlAnalysisFactory.isVariable(e.getQName))

  // Handle variables
  val variablesSeq: Seq[VariableAnalysisTrait] = {

    // NOTE: For now, all top-level variables in a model are visible first, then only are binds variables visible.
    // In the future, we might want to change that to use document order between variables and binds, but some
    // more thinking is needed wrt the processing model.

    val someSelf = self.some

    // Iterate and resolve all variables in order
    var preceding: Option[VariableAnalysisTrait] = None

    for {
      variableElement <- variableElements
      analysis: VariableAnalysisTrait = {
        val result = new ElementAnalysis(part, index /* would be wrong */, variableElement, someSelf, preceding, scope) with VariableAnalysisTrait
        preceding = result.some
        result
      }
    } yield
      analysis
  }

  val variablesMap: Map[String, VariableAnalysisTrait] = variablesSeq map (variable => variable.name -> variable) toMap

  def freeVariablesTransientState(): Unit =
    for (variable <- variablesSeq)
      variable.freeTransientState()
}

trait ModelSubmissions {

  self: Model =>

  // Submissions (they are all direct children)
  lazy val submissions: Seq[Submission] = children collect { case s: Submission => s }
}

trait ModelEventHandlers {

  self: Model =>

  // Event handlers, including on submissions and within nested actions
  lazy val eventHandlers: Seq[EventHandler] = descendants collect { case e: EventHandler => e } toList
}

trait ModelBinds {

  selfModel: Model =>

  // FIXME: A bit unhappy with this. Laziness desired because of init order issues with the superclass. There has to be a simpler way!
  private class LazyConstant[T](evaluate: => T) extends (() => T) {
    private lazy val result = evaluate
    def apply() = result
  }

  // Q: Why do we pass isCustomMIP to BindTree? Init order issue?
  private def isCustomMIP: QName => Boolean = {

    import ElementAnalysis.attQNameSet

    def canBeCustomMIP(qName: QName) =
      qName.namespace.prefix.nonEmpty &&
      ! qName.namespace.prefix.startsWith("xml") &&
      (StandardCustomMIPsQNames(qName) || ! NeverCustomMIPsURIs(qName.namespace.uri))

    Option(selfModel.element.attribute(XXFORMS_CUSTOM_MIPS_QNAME)) match {
      case Some(_) =>
        // If the attribute is present, allow all specified QNames if valid, plus standard MIP QNames
        attQNameSet(selfModel.element, XXFORMS_CUSTOM_MIPS_QNAME, namespaceMapping) ++ StandardCustomMIPsQNames filter canBeCustomMIP
      case None    =>
        // Attribute not present: backward-compatible behavior
        canBeCustomMIP
    }
  }

  private var _bindTree = //replaceBinds(selfModel.element.elements(XFORMS_BIND_QNAME))
    new LazyConstant(
      new BindTree(
        selfModel,
        selfModel.element.elements(XFORMS_BIND_QNAME),
        isCustomMIP
      )
    )

  def bindTree: BindTree = _bindTree()

  // For `xxf:dynamic`
  def replaceBinds(bindElements: => Seq[Element]): Unit = {
    _bindTree().destroy()
    _bindTree = new LazyConstant(
      new BindTree(
        selfModel,
        bindElements,
        isCustomMIP
      )
    )
    _bindTree() // here we can be eager
  }

  def bindsById                             = _bindTree().bindsById
  def bindsByName                           = _bindTree().bindsByName

  def hasDefaultValueBind                   = _bindTree().hasDefaultValueBind
  def hasCalculateBind                      = _bindTree().hasCalculateBind
  def hasTypeBind                           = _bindTree().hasTypeBind
  def hasRequiredBind                       = _bindTree().hasRequiredBind
  def hasConstraintBind                     = _bindTree().hasConstraintBind
  def hasNonPreserveWhitespace              = _bindTree().hasNonPreserveWhitespace

  def mustRevalidate                        = _bindTree().mustRevalidate
  def mustRecalculate                       = _bindTree().mustRecalculate

  def bindInstances                         = _bindTree().bindInstances
  def computedBindExpressionsInstances      = _bindTree().computedBindExpressionsInstances
  def validationBindInstances               = _bindTree().validationBindInstances

  // TODO: use and produce variables introduced with xf:bind/@name

  def topLevelBinds                         = _bindTree().topLevelBinds

  def hasBinds                              = _bindTree().hasBinds
  def containsBind(bindId: String)          = _bindTree().bindIds(bindId)

  def figuredAllBindRefAnalysis             = _bindTree().figuredAllBindRefAnalysis
  def recalculateOrder                      = _bindTree().recalculateOrder
  def defaultValueOrder                     = _bindTree().defaultValueOrder

  def freeBindsTransientState()             = _bindTree().freeBindsTransientState()
}
