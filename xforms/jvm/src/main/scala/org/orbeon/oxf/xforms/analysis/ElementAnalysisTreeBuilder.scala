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

import cats.implicits.catsSyntaxOptionId
import org.orbeon.dom.Element
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.{SelectionControl, TriggerControl, ValueControl}
import org.orbeon.oxf.xforms.analysis.XFormsExtractor.LastIdQName
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Model, Submission}
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.oxf.xforms.xbl.XBLBindingBuilder
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.XXBLScope
import org.orbeon.xforms.xbl.Scope


object ElementAnalysisTreeBuilder {

  import Private._

  type Builder = (ElementAnalysis, Option[ElementAnalysis], Element, Scope) => ElementAnalysis

  // Recursively build this element's children and its descendants
  def buildAllElemDescendants(parentElem: WithChildrenTrait, builder: Builder): Unit =
    parentElem.addChildren( // TODO: consider have children add themselves to the parent?
      {
        // NOTE: Making `preceding` hold a side effect here is a bit unclear and error-prone.
        var precedingElem: Option[ElementAnalysis] = None

        // Build and collect the children
        for ((childElement, childContainerScope) <- childrenElements(parentElem))
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

  // Set the component's binding
  // Might not create the binding if the binding does not have a template.
  // Also called indirectly by `XXFormsDynamicControl`.
  def setConcreteBinding(
    e              : ComponentControl,
    elemInSource   : Element)(implicit
    indentedLogger : IndentedLogger
  ): Unit = {

    assert(! e.hasConcreteBinding)

    XBLBindingBuilder.createConcreteBindingFromElem(
      e.part,
      e.abstractBinding,
      elemInSource,
      e.prefixedId,
      e.containerScope
    ) foreach { case (newBinding, globalOpt) =>

      globalOpt foreach { global =>
        e.part.abstractBindingsWithGlobals += e.abstractBinding // TODO: indexing
        e.part.allGlobals += global                             // TODO: indexing
      }

      e.part.addBinding(e.prefixedId, newBinding)               // TODO: indexing

      e.setConcreteBinding(newBinding)
    }
  }

  private object Private {

    val RootChildrenToIgnore = Set(XBL_XBL_QNAME, STATIC_STATE_PROPERTIES_QNAME, LastIdQName)
    val ModelChildrenToKeep  = Set(XFORMS_SUBMISSION_QNAME, XFORMS_INSTANCE_QNAME)

    // Return all the children to consider, including relevant shadow tree elements
    def findXblRelevantChildrenElements(e: ComponentControl): Seq[(Element, Scope)] =
      e.bindingOpt map { binding =>

        def annotateChild(child: Element) = {
          // Inner scope in effect for the component element itself (NOT the shadow tree's scope)
          val innerScope = e.containerScope

          // Outer scope in effect for the component element itself
          def outerScope =
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

          // Children elements have not been annotated earlier (because they are nested within the bound element)
          XBLBindingBuilder.annotateSubtreeByElement(
            e.part,
            e.element,            // bound element
            child,                // child tree to annotate
            innerScope,           // handler's inner scope is the same as the component's
            outerScope,           // handler's outer scope is the same as the component's
            if (e.scope == innerScope) XXBLScope.Inner else XXBLScope.Outer,
            binding.innerScope    // handler is within the current component (this determines the prefix of ids)
          )
        }

        // Directly nested handlers (if enabled)
        def directlyNestedHandlers =
          if (e.abstractBinding.modeHandlers)
            e.element.elements filter
              EventHandlerImpl.isEventHandler map
                annotateChild
          else
            Nil

        // Directly nested LHHA (if enabled)
        def directlyNestedLHHA =
          if (e.abstractBinding.modeLHHA)
            e.element.elements filter
              (e => LHHA.isLHHA(e) && ! e.hasAttribute(FOR_QNAME)) map
                annotateChild
          else
            Nil

        val elems =
          directlyNestedHandlers ++
            directlyNestedLHHA   ++
            binding.handlers     ++
            binding.models :+ binding.compactShadowTree.getRootElement

        elems map ((_, binding.innerScope))

      } getOrElse
        Nil

    def childrenElements(e: WithChildrenTrait): Seq[(Element, Scope)] = {

      import ControlAnalysisFactory.isVariable
      import LHHA.isLHHA
      import SelectionControlUtil.TopLevelItemsetQNames
      import XFormsActions.isAction

      def allChildren: Seq[(Element, Scope)] =
        e.element.elements map ((_, e.containerScope))

      e match {
        case _: RootControl =>
          allChildren filterNot {
            case (e, _) => RootChildrenToIgnore(e.getQName)
          }
        case _: Model =>
          allChildren collect {
            case t @ (e, _) if isAction(e.getQName) || ModelChildrenToKeep(e.getQName) => t
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
          findXblRelevantChildrenElements(e)
        case _ =>
          allChildren
      }
    }
  }
}