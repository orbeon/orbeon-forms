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
import org.orbeon.dom.io.XMLWriter
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.{IndentedLogger, Logging}
import org.orbeon.oxf.xforms.XFormsProperties.EXPOSE_XPATH_TYPES_PROPERTY
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.controls.{AttributeControl, ComponentControl, LHHAAnalysis, RootControl}
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils.DebugXML
import org.orbeon.oxf.xml.{SAXStore, XMLReceiverHelper}
import org.orbeon.xforms.Constants
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
   with Logging
   with DebugXML {

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

  def isExposeXPathTypes: Boolean = staticState.staticBooleanProperty(EXPOSE_XPATH_TYPES_PROPERTY)

  /**
   * Return the namespace mappings for a given element. If the element does not have an id, or if the mapping is not
   * cached, compute the mapping on the fly. Note that in this case, the resulting mapping is not added to the cache
   * as the mapping is considered transient and not sharable among pages.
   */
  def getNamespaceMapping(prefix: String, element: Element): NamespaceMapping = {

    val id = XFormsUtils.getElementId(element) ensuring (_ ne null)
    val prefixedId = if (prefix ne null) prefix + id else id

    metadata.getNamespaceMapping(prefixedId) getOrElse
      (throw new IllegalStateException(s"namespace mappings not cached for prefix `$prefix` on element `${Dom4jUtils.elementToDebugString(element)}`"))
  }

  def getNamespaceMapping(scope: Scope, id: String): NamespaceMapping = {
    metadata.getNamespaceMapping(scope.prefixedIdForStaticId(id)) getOrElse
      (throw new IllegalStateException(s"namespace mappings not cached for scope `$scope` on element with id `$id`"))
  }

  // Builder that produces an `ElementAnalysis` for a known incoming Element
  def build(
    parent         : ElementAnalysis,
    preceding      : Option[ElementAnalysis],
    controlElement : Element,
    containerScope : Scope,
    index          : ElementAnalysis => Unit
  ): Option[ElementAnalysis] = {

    assert(containerScope ne null)

    val locationData = ElementAnalysis.createLocationData(controlElement)

    // Check for mandatory id
    val controlStaticId = XFormsUtils.getElementId(controlElement)
    if (controlStaticId eq null)
      throw new ValidationException("Missing mandatory id for element: " + controlElement.getQualifiedName, locationData)

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

    // Throw if the element is unknown (we could also just warn?)
    if (elementAnalysisOpt.isEmpty)
      throw new ValidationException("Unknown control: " + controlElement.getQualifiedName, locationData)

    elementAnalysisOpt foreach {
      case componentControl: ComponentControl if ! componentControl.hasLazyBinding =>
        componentControl.setConcreteBinding(controlElement)
      case _ =>
    }

    // 3. Index new control
    elementAnalysisOpt foreach index

    elementAnalysisOpt
  }

  // Analyze a subtree of controls (for `xxf:dynamic`)
  def analyzeSubtree(container: ChildrenBuilderTrait): Unit = {

    implicit val logger = getIndentedLogger
    withDebug("performing static analysis of subtree", Seq("prefixed id" -> container.prefixedId)) {

      // Global lists of external LHHA and handlers
      val lhhas         = m.Buffer[LHHAAnalysis]()
      val eventHandlers = m.Buffer[EventHandlerImpl]()
      val models        = m.Buffer[Model]()
      val attributes    = m.Buffer[AttributeControl]()

      // Rebuild children
      container.build(build(_, _, _, _, indexNewControl(_, lhhas, eventHandlers, models, attributes)))

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
      val buildGatherLHHAAndHandlers: ChildrenBuilderTrait#Builder = build(_, _, _, _, indexNewControl(_, lhhas, eventHandlers, models, attributes))
      rootControlAnalysis.build(buildGatherLHHAAndHandlers)

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
            globalElement <- global.compactShadowTree.getRootElement.elements.asScala // children of xxbl:global
          } yield
            buildGatherLHHAAndHandlers(rootControlAnalysis, None, globalElement, startScope) collect {
              case childrenBuilder: ChildrenBuilderTrait =>
                childrenBuilder.build(buildGatherLHHAAndHandlers)
                childrenBuilder
              case other => other
            }

        // Add globals to the root analysis
        rootControlAnalysis.addChildren(globalsOptions.iterator.flatten)
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
      dumpAnalysis()

    // Clean-up to finish initialization
    freeTransientState()
  }

  def toXML(helper: XMLReceiverHelper): Unit =
    controlAnalysisMap(startScope.prefixedIdForStaticId(Constants.DocumentId)).toXML(helper)

  def dumpAnalysis(): Unit =
    println(Dom4jUtils.createDocument(this).getRootElement.serializeToString(XMLWriter.PrettyFormat))
}
