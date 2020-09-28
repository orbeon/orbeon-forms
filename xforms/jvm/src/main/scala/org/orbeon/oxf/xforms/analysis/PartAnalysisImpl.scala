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
import org.orbeon.oxf.util.{IndentedLogger, Logging}
import org.orbeon.oxf.xforms.XFormsProperties.ExposeXpathTypesProperty
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.{SelectionControl, TriggerControl, ValueControl}
import org.orbeon.oxf.xforms.analysis.XFormsExtractor.LastIdQName
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Model, Submission}
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.oxf.xforms.xbl.XBLBindingBuilder
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.XXBLScope
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

import scala.collection.JavaConverters._
import scala.collection.{mutable => m}

/**
 * Static analysis of a whole part, including:
 *
 * - models
 * - event handlers
 * - controls
 * - XBL bindings
 *
 * The implementation is split into a series of traits to make each chunk more palatable.
 */
class PartAnalysisImpl(
  val staticState                   : XFormsStaticState,
  val parent                        : Option[PartAnalysis],
  val startScope                    : Scope,
  val metadata                      : Metadata,
  protected val staticStateDocument : StaticStateDocument
) extends PartAnalysis
   with PartGlobalOps
   with PartModelAnalysis
   with PartEventHandlerAnalysis
   with PartControlsAnalysis
   with PartXBLAnalysis
   with Logging {

  partAnalysis =>

  def getIndentedLogger: IndentedLogger = staticState.getIndentedLogger

  private def partAnalysisIterator(start: Option[PartAnalysis]): Iterator[PartAnalysis] =
    new Iterator[PartAnalysis] {

      private[this] var theNext = start

      def hasNext: Boolean = theNext.isDefined

      def next(): PartAnalysis = {
        val newResult = theNext.get
        theNext = newResult.parent
        newResult
      }
    }

  def ancestorIterator      : Iterator[PartAnalysis] = partAnalysisIterator(partAnalysis.parent)
  def ancestorOrSelfIterator: Iterator[PartAnalysis] = partAnalysisIterator(Some(partAnalysis))

  def isTopLevel: Boolean = startScope.isTopLevelScope

  def getMark(prefixedId: String): Option[SAXStore#Mark] = metadata.getMark(prefixedId)

  def isExposeXPathTypes: Boolean = staticState.staticBooleanProperty(ExposeXpathTypesProperty)

  /**
   * Return the namespace mappings for a given element. If the element does not have an id, or if the mapping is not
   * cached, compute the mapping on the fly. Note that in this case, the resulting mapping is not added to the cache
   * as the mapping is considered transient and not sharable among pages.
   */
  def getNamespaceMapping(prefix: String, element: Element): NamespaceMapping = {

    val id = element.idOrThrow
    val prefixedId = if (prefix ne null) prefix + id else id

    metadata.getNamespaceMapping(prefixedId) getOrElse
      (throw new IllegalStateException(s"namespace mappings not cached for prefix `$prefix` on element `${element.toDebugString}`"))
  }

  def getNamespaceMapping(scope: Scope, id: String): NamespaceMapping =
    metadata.getNamespaceMapping(scope.prefixedIdForStaticId(id)) getOrElse
      (throw new IllegalStateException(s"namespace mappings not cached for scope `$scope` on element with id `$id`"))

  // Builder that produces an `ElementAnalysis` for a known incoming Element
  private def build(
    parent         : ElementAnalysis,
    preceding      : Option[ElementAnalysis],
    controlElement : Element,
    containerScope : Scope,
    index          : ElementAnalysis => Unit
  ): ElementAnalysis = {

    assert(containerScope ne null)

    val locationData = ElementAnalysis.createLocationData(controlElement)

    // Check for mandatory id
    val controlStaticId = controlElement.idOpt getOrElse
      (throw new ValidationException("Missing mandatory id for element: " + controlElement.getQualifiedName, locationData))

    // Prefixed id
    val controlPrefixedId = containerScope.fullPrefix + controlStaticId

    // Create new control if possible
    val elementAnalysisOpt =
      ControlAnalysisFactory.create(
        context        = StaticStateContext(partAnalysis, controlAnalysisMap.size + 1), // Q: Benefit vs. just passing the 2 args?
        controlElement = controlElement,
        parent         = Some(parent),
        preceding      = preceding,
        scope          = scopeForPrefixedId(controlPrefixedId)
      )

    elementAnalysisOpt  match {
      case Some(componentControl: ComponentControl) if ! componentControl.hasLazyBinding =>
        componentControl.setConcreteBinding(controlElement)
        index(componentControl)
        componentControl
      case Some(elementAnalysis) =>
        index(elementAnalysis)
        elementAnalysis
      case None =>
        throw new ValidationException(s"Unknown control: `${controlElement.getQualifiedName}`", locationData)
    }
  }

  // Analyze a subtree of controls (for `xxf:dynamic`)
  def analyzeSubtree(container: WithChildrenTrait): Unit = {

    implicit val logger = getIndentedLogger
    withDebug("performing static analysis of subtree", Seq("prefixed id" -> container.prefixedId)) {

      // Global lists of external LHHA and handlers
      val lhhas         = m.Buffer[LHHAAnalysis]()
      val eventHandlers = m.Buffer[EventHandlerImpl]()
      val models        = m.Buffer[Model]()
      val attributes    = m.Buffer[AttributeControl]()

      // Rebuild children
      PartAnalysisImpl.buildAllElemDescendants(container, build(_, _, _, _, indexNewControl(_, lhhas, eventHandlers, models, attributes)))

      // Attach LHHA
      for (lhha <- lhhas)
        lhha.attachToControl()

      // Register event handlers
      registerEventHandlers(eventHandlers)

      // Index new models
      for (model <- models)
        indexModel(model)

      // Some controls need special processing
      analyzeCustomControls(attributes)

      // NOTE: doesn't handle globals, models nested within UI, update to resources
    }
  }

  // Analyze the entire tree of controls
  def analyze(): Unit = {

    implicit val logger = getIndentedLogger
    withDebug("performing static analysis") {

      initializeScopes()

      // Global lists LHHA and handlers
      val lhhas         = m.Buffer[LHHAAnalysis]()
      val eventHandlers = m.Buffer[EventHandlerImpl]()
      val models        = m.Buffer[Model]()
      val attributes    = m.Buffer[AttributeControl]()

      // Create and index root control
      val rootControlAnalysis = new RootControl(StaticStateContext(this, 0), staticStateDocument.rootControl, startScope)
      indexNewControl(rootControlAnalysis, lhhas, eventHandlers, models, attributes)

      // Gather controls
      val buildGatherLHHAAndHandlers: PartAnalysisImpl.Builder =
        build(_, _, _, _, indexNewControl(_, lhhas, eventHandlers, models, attributes))

      PartAnalysisImpl.buildAllElemDescendants(rootControlAnalysis, buildGatherLHHAAndHandlers)

      // Issues with `xxbl:global`
      //
      // 1. It's unclear what should happen with nested parts if they have globals. Without the condition below,
      //    globals can be duplicated, once per part. This can cause issues in Form Builder for example, where a
      //    global can assume visibility on top-level Form Runner resources. As of 2013-11-14, only outputting
      //    globals at the top-level.
      // 2. Global controls are placed in the part's start scope. Is there an alternative?
      // 3. Should we allow for recursive globals?
      // 4. The code below doesn't set the `preceding` value. The main impact is no resolution of variables.
      //    It might be desirable not to scope them anyway.
      if (isTopLevel) {
        val globalsOptions =
          for {
            global        <- allGlobals
            globalElement <- global.compactShadowTree.getRootElement.jElements.asScala // children of xxbl:global
          } yield
            buildGatherLHHAAndHandlers(rootControlAnalysis, None, globalElement, startScope) match {
              case newParent: WithChildrenTrait =>
                PartAnalysisImpl.buildAllElemDescendants(newParent, buildGatherLHHAAndHandlers)
                newParent
              case other =>
                other
            }

        // Add globals to the root analysis
        rootControlAnalysis.addChildren(globalsOptions)
      } else if (allGlobals.nonEmpty)
        warn(s"There are ${allGlobals.size} xxbl:global in a child part. Those won't be processed.")

      // Attach LHHA
      for (lhha <- lhhas)
        lhha.attachToControl()

      // Register event handlers
      registerEventHandlers(eventHandlers)

      // Index new models
      for (model <- models)
        indexModel(model)

      // Some controls need special processing
      analyzeCustomControls(attributes)

      // NOTE: For now, we don't analyze the XPath of nested (dynamic) parts
      if (isTopLevel && staticState.isXPathAnalysis) {
        // Analyze root control XPath first as nested models might ask for its context
        rootControlAnalysis.analyzeXPath()
        // Analyze all models XPath
        analyzeModelsXPath()
        // Analyze controls XPath
        analyzeControlsXPath()
      }

      debugResults(Seq("controls" -> controlAnalysisMap.size.toString))
    }

    // Log if needed
    if (XFormsProperties.getDebugLogXPathAnalysis)
      PartAnalysisDebugSupport.printPartAsXml(this)

    // Clean-up to finish initialization
    freeTransientState()
  }
}

object PartAnalysisImpl {

  type Builder = (ElementAnalysis, Option[ElementAnalysis], Element, Scope) => ElementAnalysis

  private val RootChildrenToIgnore = Set(XBL_XBL_QNAME, STATIC_STATE_PROPERTIES_QNAME, LastIdQName)
  private val ModelChildrenToKeep  = Set(XFORMS_SUBMISSION_QNAME, XFORMS_INSTANCE_QNAME)

  // Return all the children to consider, including relevant shadow tree elements
  private def findXblRelevantChildrenElements(e: ComponentControl): Seq[(Element, Scope)] =
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
            (e => LHHA.isLHHA(e) && (e.attributeOpt(FOR_QNAME).isEmpty)) map
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

  private def childrenElements(e: WithChildrenTrait): Seq[(Element, Scope)] = {

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
          case (e, s) if isLHHA(e) && (e.attributeOpt(FOR_QNAME).isEmpty) || TopLevelItemsetQNames(e.getQName) || isAction(e.getQName) => (e, s)
        }
      case _: ValueControl | _: TriggerControl =>
        allChildren collect {
          case (e, s) if isLHHA(e) && (e.attributeOpt(FOR_QNAME).isEmpty) || isAction(e.getQName) => (e, s)
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
              precedingElem = Some(newControl)
              newControl
            // The element does not have children
            case newControl =>
              precedingElem = Some(newControl)
              newControl
          }
      }
    )
}