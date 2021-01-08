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
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.{IndentedLogger, XPath, XPathCache}
import org.orbeon.oxf.xforms.analysis.EventHandler.PropertyQNames
import org.orbeon.oxf.xforms.analysis.controls.{SelectionControl, TriggerControl, ValueControl}
import org.orbeon.oxf.xforms.analysis.XFormsExtractor.LastIdQName
import org.orbeon.oxf.xforms.analysis.controls.VariableAnalysis.ValueOrSequenceQNames
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Model, StaticBind, Submission}
import org.orbeon.oxf.xforms.xbl.XBLBindingBuilder
import org.orbeon.oxf.xml.XMLConstants.XML_LANG_QNAME
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.{BasicNamespaceMapping, XFormsId, XFormsNames, XXBLScope}
import org.orbeon.xforms.xbl.Scope


object ElementAnalysisTreeBuilder {

  import Private._

  type Builder = (ElementAnalysis, Option[ElementAnalysis], Element, Scope) => ElementAnalysis

  // Recursively build this element's children and its descendants
  def buildAllElemDescendants(
    partAnalysisCtx : PartAnalysisContextForTree,
    parentElem      : WithChildrenTrait,
    builder         : Builder,
    children        : Option[Seq[(Element, Scope)]] = None)(implicit // allow explicit children so we can force processing of lazy bindings
    indentedLogger  : IndentedLogger
  ): Unit = {
    parentElem.addChildren( // TODO: consider have children add themselves to the parent?
      {
        // NOTE: Making `preceding` hold a side effect here is a bit unclear and error-prone.
        var precedingElem: Option[ElementAnalysis] = None

        // Build and collect the children
        for ((childElement, childContainerScope) <- children getOrElse childrenElements(partAnalysisCtx, parentElem))
          yield builder(parentElem, precedingElem, childElement, childContainerScope) match {
            // The element has children
            case newControl: WithChildrenTrait =>
              buildAllElemDescendants(partAnalysisCtx, newControl, builder)
              precedingElem = newControl.some
              newControl
            // The element does not have children
            case newControl =>
              precedingElem = newControl.some
              newControl
          }
      }
    )
  }

  def setModelOnElement(partAnalysisCtx: PartAnalysisContextAfterTree, e: ElementAnalysis): Unit =
    e match {
      case m: Model =>
        m.model = m.some
      case e =>
        e.model =
          e.element.attributeValue(XFormsNames.MODEL_QNAME) match {
            case localModelStaticId: String =>
              // Get model prefixed id and verify it belongs to this scope
              val localModelPrefixedId = e.scope.prefixedIdForStaticId(localModelStaticId)
              val localModel = partAnalysisCtx.getModel(localModelPrefixedId)
              if (localModel eq null)
                throw new ValidationException(s"Reference to non-existing model id: `$localModelStaticId`", ElementAnalysis.createLocationData(e.element))

              Some(localModel)
            case _ =>
              // Use inherited model
              e.closestAncestorInScope match {
                case Some(ancestor) => ancestor.model // there is an ancestor control in the same scope, use its model id
                case None           => partAnalysisCtx.getDefaultModelForScope(e.scope) // top-level control in a new scope, use default model id for scope
              }
          }
    }

  // This only sets the `lang` value on elements that have directly an `xml:lang`.
  // Other elements will get their `lang` value lazily.
  private def setLangOnElement(partAnalysisCtx: PartAnalysisContextAfterTree, e: ElementAnalysis): Unit =
    e.lang =
      e.element.attributeValueOpt(XML_LANG_QNAME) match {
        case Some(v) => extractXMLLang(partAnalysisCtx, e, v)
        case None    => LangRef.Undefined
      }

  def setModelAndLangOnAllDescendants(partAnalysisCtx : PartAnalysisContextAfterTree, parentElem: WithChildrenTrait): Unit =
    parentElem.descendants foreach { e =>
      setModelOnElement(partAnalysisCtx, e)
      setLangOnElement(partAnalysisCtx, e)
    }

  def extractXMLLang(partAnalysisCtx: PartAnalysisContextAfterTree, e: ElementAnalysis, lang: String): LangRef =
    if (! lang.startsWith("#"))
      LangRef.Literal(lang)
    else {
      val staticId   = lang.substring(1)
      val prefixedId = XFormsId.getRelatedEffectiveId(e.prefixedId, staticId)
      LangRef.AVT(partAnalysisCtx.getAttributeControl(prefixedId, "xml:lang"))
    }

  // For the given bound node prefixed id, remove the current shadow tree and create a new one
  // Can be used only in a sub-part, as this mutates the tree.
  // Used by `xxf:dynamic`.
  def createOrUpdateStaticShadowTree(
    partAnalysisCtx   : NestedPartAnalysis,
    existingComponent : ComponentControl,
    elemInSource      : Option[Element])(implicit
    logger            : IndentedLogger
  ): Unit = {

    assert(! existingComponent.isTopLevelPart)

    if (existingComponent.hasConcreteBinding)
      removeConcreteBinding(partAnalysisCtx, existingComponent)

    existingComponent.rootElem = elemInSource getOrElse existingComponent.element

    PartAnalysisBuilder.analyzeSubtree(partAnalysisCtx, existingComponent)
  }

  // Can be used only in a sub-part, as this mutates the tree.
  // Used by `XFormsComponentControl`.
  def clearShadowTree(
    partAnalysisCtx   : NestedPartAnalysis,
    existingComponent : ComponentControl
  ): Unit = {
    assert(! existingComponent.isTopLevelPart)
    removeConcreteBinding(partAnalysisCtx, existingComponent)
  }

  def componentChildrenForBindingUpdate(
    partAnalysisCtx : NestedPartAnalysis,
    e               : ComponentControl)(implicit
    indentedLogger  : IndentedLogger
  ): Seq[(Element, Scope)] =
    componentChildren(partAnalysisCtx, e, honorLazy = false)

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
      namespaceMapping   = BasicNamespaceMapping.Mapping,
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
    val ModelChildrenToKeep  = Set(XFORMS_SUBMISSION_QNAME, XFORMS_INSTANCE_QNAME, XFORMS_BIND_QNAME)

    // Return all the children to consider, including relevant shadow tree elements
    //
    // This also:
    //
    // - create the new scope if needed
    // - indexes globals
    // - sets the `ConcreteBinding` on the control
    //
    def componentChildren(
      partAnalysisCtx : PartAnalysisContextForTree,
      e               : ComponentControl,
      honorLazy       : Boolean)(implicit
      indentedLogger  : IndentedLogger
    ): Seq[(Element, Scope)] =
      if (! e.hasLazyBinding || ! honorLazy) {

        val abstractBinding =
          partAnalysisCtx.metadata.findAbstractBindingByPrefixedId(e.prefixedId) getOrElse
            (throw new IllegalStateException)

        XBLBindingBuilder.createConcreteBindingFromElem(
          partAnalysisCtx,
          abstractBinding,
          e.rootElem,
          e.prefixedId,
          e.containerScope
        ) match {
          case Some((concreteBinding, globalOpt, compactShadowTree)) =>

            globalOpt foreach { global =>
              partAnalysisCtx.abstractBindingsWithGlobals += abstractBinding // TODO: indexing; to know if globals have been processed already
              partAnalysisCtx.allGlobals += global                           // TODO: indexing
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
                partAnalysisCtx,
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
              (concreteBinding.innerScope, partAnalysisCtx.scopeForPrefixedId(e.prefixedId), XXBLScope.Inner)

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
      partAnalysisCtx : PartAnalysisContextForTree,
      e               : WithChildrenTrait)(implicit
      indentedLogger  : IndentedLogger
    ): Seq[(Element, Scope)] = {

      import VariableAnalysis.VariableQNames
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
            case t @ (e, _) if isAction(e.getQName) || VariableQNames(e.getQName) || ModelChildrenToKeep(e.getQName) => t
          }
        case _: Submission =>
          allChildren collect {
            case (e, s) if isAction(e.getQName) => (e, s)
          }
        case _: VariableAnalysisTrait =>
          allChildren collect {
            case (e, s) if isAction(e.getQName) || ValueOrSequenceQNames(e.getQName) => (e, s)
          }
        case _: SelectionControl =>
          allChildren collect {
            case (e, s) if isLHHA(e) && ! e.hasAttribute(FOR_QNAME) || TopLevelItemsetQNames(e.getQName) || isAction(e.getQName) => (e, s)
          }
        case _: ValueControl | _: TriggerControl =>
          allChildren collect {
            case (e, s) if isLHHA(e) && ! e.hasAttribute(FOR_QNAME) || isAction(e.getQName) => (e, s)
          }
        case e: ActionTrait if EventHandler.isPropertiesAction(e.element.getQName) =>
          allChildren collect {
            case (e, s) if PropertyQNames(e.getQName) => (e, s)
          }
        case _: ActionTrait =>
          allChildren collect {
            case (e, s) if isAction(e.getQName) || VariableQNames(e.getQName) => (e, s)
          }
        case _: StaticBind =>
          // Q: `xf:bind` can also have nested `<xf:constraint>`, etc. Should those also be collected to `ElementAnalysis`?
          allChildren collect {
            case (e, s) if e.getQName == XFORMS_BIND_QNAME => (e, s)
          }
        case e: ComponentControl =>
          componentChildren(partAnalysisCtx, e, honorLazy = true)
        case _ =>
          allChildren
      }
    }

    // Remove the component's binding
    // Used by `xxf:dynamic`
    def removeConcreteBinding(partAnalysisCtx: NestedPartAnalysis, existingComponent: ComponentControl): Unit = {

      assert(! existingComponent.isTopLevelPart && existingComponent.hasConcreteBinding)

      existingComponent.bindingOpt foreach { binding =>
        // Remove all descendants only, keeping the current control
        partAnalysisCtx.deindexTree(existingComponent, self = false)
        partAnalysisCtx.deregisterScope(binding.innerScope)
        existingComponent.clearBinding()
      }
    }
  }
}