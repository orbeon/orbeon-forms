/**
 * Copyright (C) 2007 Orbeon, Inc.
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

import cats.syntax.option._
import org.orbeon.dom.Element
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.{IndentedLogger, XPath, XPathCache}
import org.orbeon.oxf.xforms.XFormsStaticStateImpl
import org.orbeon.oxf.xforms.analysis.controls.{SelectionControl, TriggerControl, ValueControl}
import org.orbeon.oxf.xforms.analysis.XFormsExtractor.LastIdQName
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Model, Submission}
import org.orbeon.oxf.xforms.xbl.XBLBindingBuilder
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.XXBLScope
import org.orbeon.xforms.xbl.Scope


object ElementAnalysisTreeBuilder {

  import Private._

  type Builder = (ElementAnalysis, Option[ElementAnalysis], Element, Scope) => ElementAnalysis

  // Recursively build this element's children and its descendants
  def buildAllElemDescendants(
    parentElem     : WithChildrenTrait,
    builder        : Builder,
    children       : Option[Seq[(Element, Scope)]] = None)(implicit // allow explicit children so we can force processing of lazy bindings
    indentedLogger : IndentedLogger
  ): Unit =
    parentElem.addChildren( // TODO: consider have children add themselves to the parent?
      {
        // NOTE: Making `preceding` hold a side effect here is a bit unclear and error-prone.
        var precedingElem: Option[ElementAnalysis] = None

        // Build and collect the children
        for ((childElement, childContainerScope) <- children getOrElse childrenElements(parentElem))
          yield builder(parentElem, precedingElem, childElement, childContainerScope) match {
            // The element has children
            case newControl: WithChildrenTrait =>
              buildAllElemDescendants(newControl, builder)
              precedingElem = newControl.some
              newControl
            // The element does not have children
            case newControl =>
              precedingElem = newControl.some
              newControl
          }
      }
    )

  // For the given bound node prefixed id, remove the current shadow tree and create a new one
  // Can be used only in a sub-part, as this mutates the tree.
  // Used by `xxf:dynamic`.
  def createOrUpdateStaticShadowTree(existingComponent: ComponentControl, elemInSource: Option[Element]): Unit = {

    assert(! existingComponent.part.isTopLevel)

    if (existingComponent.hasConcreteBinding)
      removeConcreteBinding(part, existingComponent)

    existingComponent.rootElem = elemInSource getOrElse existingComponent.element
    existingComponent.part.analyzeSubtree(existingComponent)
  }

  // Can be used only in a sub-part, as this mutates the tree.
  // Used by `XFormsComponentControl`.
  def clearShadowTree(part: PartAnalysisImpl, existingComponent: ComponentControl): Unit = {
    assert(! part.isTopLevel)
    removeConcreteBinding(part, existingComponent)
  }

  def componentChildrenForBindingUpdate(
    e              : ComponentControl)(implicit
    indentedLogger : IndentedLogger
  ): Seq[(Element, Scope)] =
    componentChildren(e, honorLazy = false)

  // Try to figure out if we have a dynamic LHHA element, including nested xf:output and AVTs.
  def hasStaticValue(lhhaElement: Element): Boolean = {

    val SearchExpression =
      """
        not(
          exists(
            descendant-or-self::*[@ref or @nodeset or @bind or @value] |
            descendant::*[@*[contains(., '{')]]
          )
        )
      """

    XPathCache.evaluateSingle(
      contextItem        = new DocumentWrapper(
        lhhaElement.getDocument,
        null,
        XPath.GlobalConfiguration
      ).wrap(lhhaElement),
      xpathString        = SearchExpression,
      namespaceMapping   = XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING,
      variableToValueMap = null,
      functionLibrary    = null,
      functionContext    = null,
      baseURI            = null,
      locationData       = ElementAnalysis.createLocationData(lhhaElement),
      reporter           = null
    ).asInstanceOf[Boolean]
  }

  private object Private {

    val RootChildrenToIgnore = Set(XBL_XBL_QNAME, STATIC_STATE_PROPERTIES_QNAME, LastIdQName)
    val ModelChildrenToKeep  = Set(XFORMS_SUBMISSION_QNAME, XFORMS_INSTANCE_QNAME)

    // Return all the children to consider, including relevant shadow tree elements
    //
    // This also:
    //
    // - create the new scope if needed
    // - indexes globals
    // - sets the `ConcreteBinding` on the control
    //
    def componentChildren(
      e              : ComponentControl,
      honorLazy      : Boolean)(implicit
      indentedLogger : IndentedLogger
    ): Seq[(Element, Scope)] =
      if (! e.hasLazyBinding || ! honorLazy) {

        val abstractBinding =
          e.part.metadata.findAbstractBindingByPrefixedId(e.prefixedId) getOrElse
            (throw new IllegalStateException)

        XBLBindingBuilder.createConcreteBindingFromElem(
          e.part,
          abstractBinding,
          e.rootElem,
          e.prefixedId,
          e.containerScope
        ) match {
          case Some((concreteBinding, globalOpt, compactShadowTree)) =>

            globalOpt foreach { global =>
              e.part.abstractBindingsWithGlobals += abstractBinding // TODO: indexing; to know if globals have been processed already
              e.part.allGlobals += global                           // TODO: indexing
            }

            e.setConcreteBinding(concreteBinding)

            val scopesForDirectlyNestedChildren: (Scope, Scope, XXBLScope) = {

              val innerScope = e.containerScope

              // Outer scope in effect for the component element itself
              val outerScope =
                if (innerScope.isTopLevelScope)
                  innerScope
                else {
                  // Search in ancestor parts too
                  val controlId = e.containerScope.fullPrefix.init
                  val controlAnalysis =
                    ElementAnalysis.ancestorsAcrossPartsIterator(e, includeSelf = false) find
                      (_.prefixedId == controlId) getOrElse
                      (throw new IllegalStateException)

                  controlAnalysis.scope
                }

              (innerScope, outerScope, if (e.scope == innerScope) XXBLScope.Inner else XXBLScope.Outer)
            }

            def annotateChild(innerScope: Scope, outerScope: Scope, containerScope: XXBLScope)(child: Element): Element =
              XBLBindingBuilder.annotateSubtreeByElement(
                e.part,
                e.element,            // bound element
                child,                // child tree to annotate
                innerScope,
                outerScope,
                containerScope,
                concreteBinding.innerScope
              )

            // Directly nested handlers (if enabled)
            def directlyNestedHandlers =
              if (e.commonBinding.modeHandlers)
                e.element.elements            filter
                  EventHandler.isEventHandler map
                  (annotateChild _).tupled(scopesForDirectlyNestedChildren)
              else
                Nil

            // Directly nested LHHA (if enabled)
            def directlyNestedLHHA =
              if (e.commonBinding.modeLHHA)
                e.element.elements                                     filter
                  (e => LHHA.isLHHA(e) && ! e.hasAttribute(FOR_QNAME)) map
                  (annotateChild _).tupled(scopesForDirectlyNestedChildren)
              else
                Nil

            val scopesForImplementationChildren =
              (concreteBinding.innerScope, e.part.scopeForPrefixedId(e.prefixedId), XXBLScope.Inner)

            val annotatedHandlers = abstractBinding.handlers      map (annotateChild _).tupled(scopesForImplementationChildren)
            val annotatedModels   = abstractBinding.modelElements map (annotateChild _).tupled(scopesForImplementationChildren)

            val elems =
              directlyNestedHandlers ++
                directlyNestedLHHA   ++
                annotatedHandlers    ++
                annotatedModels      :+
                compactShadowTree.getRootElement

            elems map ((_, concreteBinding.innerScope))

          case None =>
            Nil // no template found for the binding
        }
      } else
        Nil // lazy binding, don't do anything yet

    def childrenElements(
      e              : WithChildrenTrait)(implicit
      indentedLogger : IndentedLogger
    ): Seq[(Element, Scope)] = {

      import ControlAnalysisFactory.isVariable
      import EventHandler.isAction
      import LHHA.isLHHA
      import SelectionControlUtil.TopLevelItemsetQNames

      def allChildren: Seq[(Element, Scope)] =
        e.element.elements map ((_, e.containerScope))

      e match {
        case _: RootControl =>
          allChildren filterNot {
            case (e, _) => RootChildrenToIgnore(e.getQName)
          }
        case _: Model =>
          allChildren collect {
            case t @ (e, _) if isAction(e.getQName) || isVariable(e.getQName) || ModelChildrenToKeep(e.getQName) => t
          }
        case _: Submission | _: VariableControl =>
          allChildren collect {
            case (e, s) if isAction(e.getQName) => (e, s)
          }
        case _: SelectionControl =>
          allChildren collect {
            case (e, s) if isLHHA(e) && ! e.hasAttribute(FOR_QNAME) || TopLevelItemsetQNames(e.getQName) || isAction(e.getQName) => (e, s)
          }
        case _: ValueControl | _: TriggerControl =>
          allChildren collect {
            case (e, s) if isLHHA(e) && ! e.hasAttribute(FOR_QNAME) || isAction(e.getQName) => (e, s)
          }
        case _: ActionTrait =>
          allChildren collect {
            case (e, s) if isAction(e.getQName) || isVariable(e.getQName) => (e, s)
          }
        case e: ComponentControl =>
          componentChildren(e, honorLazy = true)
        case _ =>
          allChildren
      }
    }

    // Remove the component's binding
    // Used by `xxf:dynamic`
    def removeConcreteBinding(part: PartAnalysisImpl, existingComponent: ComponentControl): Unit = {

      assert(! part.isTopLevel && existingComponent.hasConcreteBinding)

      existingComponent.bindingOpt foreach { binding =>
        // Remove all descendants only, keeping the current control
        part.deindexTree(existingComponent, self = false)
        part.deregisterScope(binding.innerScope)
        existingComponent.clearBinding()
      }
    }
  }
}