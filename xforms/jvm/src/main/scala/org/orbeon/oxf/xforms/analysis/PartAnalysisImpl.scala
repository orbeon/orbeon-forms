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


import org.orbeon.oxf.xml.dom4j.Dom4jUtils.DebugXML

import collection.JavaConverters._
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import org.orbeon.oxf.util.XPathCache
import org.orbeon.dom.Element
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationData}

import collection.mutable.Buffer
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xml.{NamespaceMapping, XMLReceiverHelper}
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.xforms.analysis.controls.{AttributeControl, LHHAAnalysis, RootControl}
import org.orbeon.saxon.om.{NodeInfo, VirtualNode}
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.XFormsProperties.EXPOSE_XPATH_TYPES_PROPERTY

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

  partAnalysis ⇒

  def getIndentedLogger = staticState.getIndentedLogger

  private def iterator(start: Option[PartAnalysis]): Iterator[PartAnalysis] = new Iterator[PartAnalysis] {

    private[this] var theNext = start

    def hasNext = theNext.isDefined
    def next() = {
      val newResult = theNext.get
      theNext = newResult.parent
      newResult
    }
  }

  def ancestorIterator       = iterator(partAnalysis.parent)
  def ancestorOrSelfIterator = iterator(Some(partAnalysis))

  def getMark(prefixedId: String) = metadata.getMark(prefixedId)

  def isTopLevel = startScope.isTopLevelScope

  def isExposeXPathTypes = staticState.staticBooleanProperty(EXPOSE_XPATH_TYPES_PROPERTY)

  /**
   * Return the namespace mappings for a given element. If the element does not have an id, or if the mapping is not
   * cached, compute the mapping on the fly. Note that in this case, the resulting mapping is not added to the cache
   * as the mapping is considered transient and not sharable among pages.
   */
  def getNamespaceMapping(prefix: String, element: Element) = {
    val id = XFormsUtils.getElementId(element)

    require(id ne null)

    val prefixedId = if (prefix ne null) prefix + id else id

    Option(metadata.getNamespaceMapping(prefixedId)) getOrElse {
      // NOTE: We hope to get rid of this case at some point as all mappings should be in the metadata (put an assert)
      getIndentedLogger.logDebug("", "namespace mappings not cached", "prefix", prefix, "element", Dom4jUtils.elementToDebugString(element))
      new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(element))
    }
  }

  // Builder that produces an ElementAnalysis for a known incoming Element
  def build(
    parent         : ElementAnalysis,
    preceding      : Option[ElementAnalysis],
    controlElement : Element,
    containerScope : Scope,
    index          : ElementAnalysis ⇒ Unit
  ) = {

    assert(containerScope ne null)

    val locationData = ElementAnalysis.createLocationData(controlElement)

    // Check for mandatory id
    val controlStaticId = XFormsUtils.getElementId(controlElement)
    if (controlStaticId eq null)
      throw new ValidationException("Missing mandatory id for element: " + controlElement.getQualifiedName, locationData)

    // Prefixed id
    val controlPrefixedId = containerScope.fullPrefix + controlStaticId

    // 1. If element is not built-in, first check XBL and generate shadow content if needed
    xblBindings.processElementIfNeeded(controlElement, controlPrefixedId, locationData, containerScope)

    // 2. Create new control if possible
    val elementAnalysis = {
      val controlScope = scopeForPrefixedId(controlPrefixedId)
      // NOTE: Wondering if there is a benefit to use separate StaticStateContext vs. just passing the 2 args
      val staticStateContext = StaticStateContext(partAnalysis, controlAnalysisMap.size + 1)
      ControlAnalysisFactory.create(staticStateContext, controlElement, Some(parent), preceding, controlScope)
    }

    // Throw if the element is unknown (we could also just warn?)
    if (elementAnalysis.isEmpty)
      throw new ValidationException("Unknown control: " + controlElement.getQualifiedName, locationData)

    // 3. Index new control
    elementAnalysis foreach index

    elementAnalysis
  }

  // Analyze a subtree of controls
  def analyzeSubtree(container: ChildrenBuilderTrait): Unit = {

    implicit val logger = getIndentedLogger
    withDebug("performing static analysis of subtree", Seq("prefixed id" → container.prefixedId)) {

      // Global lists of external LHHA and handlers
      val lhhas         = Buffer[LHHAAnalysis]()
      val eventHandlers = Buffer[EventHandlerImpl]()
      val models        = Buffer[Model]()
      val attributes    = Buffer[AttributeControl]()

      // Rebuild children
      container.build(build(_, _, _, _, indexNewControl(_, lhhas, eventHandlers, models, attributes)))

      // Attach LHHA
      for (lhha ← lhhas)
        lhha.attachToControl()

      // Register event handlers
      registerEventHandlers(eventHandlers)

      // Index new models
      for (model ← models)
        indexModel(model, eventHandlers)

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
      val lhhas         = Buffer[LHHAAnalysis]()
      val eventHandlers = Buffer[EventHandlerImpl]()
      val models        = Buffer[Model]()
      val attributes    = Buffer[AttributeControl]()

      // Create and index root control
      val rootControlAnalysis = new RootControl(StaticStateContext(this, 0), staticStateDocument.rootControl, startScope)
      indexNewControl(rootControlAnalysis, lhhas, eventHandlers, models, attributes)

      // Gather controls
      val buildGatherLHHAAndHandlers: ChildrenBuilderTrait#Builder = build(_, _, _, _, indexNewControl(_, lhhas, eventHandlers, models, attributes))
      rootControlAnalysis.build(buildGatherLHHAAndHandlers)

      // Issues with xxbl:global
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
            global        ← xblBindings.allGlobals
            globalElement ← global.compactShadowTree.getRootElement.elements.asScala // children of xxbl:global
          } yield
            buildGatherLHHAAndHandlers(rootControlAnalysis, None, globalElement, startScope) collect {
              case childrenBuilder: ChildrenBuilderTrait ⇒
                childrenBuilder.build(buildGatherLHHAAndHandlers)
                childrenBuilder
              case other ⇒ other
            }

        // Add globals to the root analysis
        rootControlAnalysis.addChildren(globalsOptions.flatten.toSeq) // TODO: unclear is .toSeq is needed
      } else if (xblBindings.allGlobals.nonEmpty)
        warn(s"There are ${xblBindings.allGlobals.size} xxbl:global in a child part. Those won't be processed.")

      // Attach LHHA
      for (lhha ← lhhas)
        lhha.attachToControl()

      // Register event handlers
      registerEventHandlers(eventHandlers)

      // Index new models
      for (model ← models)
        indexModel(model, eventHandlers)

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

      debugResults(Seq("controls" → controlAnalysisMap.size.toString))
    }

    // Log if needed
    if (XFormsProperties.getDebugLogXPathAnalysis)
      dumpAnalysis()

    // Clean-up to finish initialization
    freeTransientState()
  }

  def toXML(helper: XMLReceiverHelper) =
    controlAnalysisMap(startScope.prefixedIdForStaticId("#document")).toXML(helper)

  def dumpAnalysis() =
    println(Dom4jUtils.domToPrettyString(Dom4jUtils.createDocument(this)))
}

object PartAnalysisImpl {

  def extractNestedModels(compactShadowTreeWrapper: DocumentWrapper, detach: Boolean, locationData: LocationData) = {

    // TODO: Don't use XPath here, but extract models as controls tree is visited
    val xpathExpression = "//xf:model[not(ancestor::xf:instance)]"
    val modelItems = XPathCache.evaluate(compactShadowTreeWrapper, xpathExpression,
      XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING, null, null, null, null, locationData, null)

    for {
      item ← modelItems.asScala
      nodeInfo = item.asInstanceOf[NodeInfo]
      element = nodeInfo.asInstanceOf[VirtualNode].getUnderlyingNode.asInstanceOf[Element]
      document = Dom4jUtils.createDocumentCopyParentNamespaces(element, detach)
    } yield
      document
  }
}