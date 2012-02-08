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

import controls.{RootControl, ExternalLHHAAnalysis}
import java.lang.String
import model.Model
import scala.collection.JavaConverters._
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import org.orbeon.oxf.util.XPathCache
import org.orbeon.saxon.dom4j.{NodeWrapper, DocumentWrapper}
import org.dom4j.Element
import collection.immutable.Stream._
import org.orbeon.oxf.xforms._
import event.EventHandlerImpl
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData, Dom4jUtils}
import collection.mutable.Buffer
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xml.{NamespaceMapping, ContentHandlerHelper, XMLUtils}
import xbl.Scope

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
        val staticState: XFormsStaticState,
        val parent: Option[PartAnalysis],
        val startScope: Scope,
        val metadata: Metadata,
        protected val staticStateDocument: StaticStateDocument)
    extends PartAnalysis
    with PartGlobalOps
    with PartModelAnalysis
    with PartEventHandlerAnalysis
    with PartControlsAnalysis
    with PartXBLAnalysis {

    partAnalysis ⇒

    def locationData = staticState.locationData
    def getIndentedLogger = staticState.getIndentedLogger

    // Ancestor parts
    def ancestors: Stream[PartAnalysis] = parent match {
        case None ⇒ Stream.empty
        case Some(parent) ⇒ parent #:: parent.ancestors
    }

    def ancestorOrSelf = this #:: ancestors

    def getMark(prefixedId: String) = metadata.getMark(prefixedId)

    /**
     * Return the namespace mappings for a given element. If the element does not have an id, or if the mapping is not
     * cached, compute the mapping on the fly. Note that in this case, the resulting mapping is not added to the cache
     * as the mapping is considered transient and not sharable among pages.
     */
    def getNamespaceMapping(prefix: String, element: Element) = {
        val id = XFormsUtils.getElementStaticId(element)
        if (id ne null) {
            val prefixedId = if (prefix ne null) prefix + id else id
            val cachedMap = metadata.getNamespaceMapping(prefixedId)
            if (cachedMap ne null)
                cachedMap
            else {
                // NOTE: We hope to get rid of this case at some point as all mappings should be in the metadata (put an assert)
                getIndentedLogger.logDebug("", "namespace mappings not cached", "prefix", prefix, "element", Dom4jUtils.elementToDebugString(element))
                new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(element))
            }
        } else {
            // NOTE: We hope to get rid of this case at some point as all mappings should be in the metadata (put an assert)
            getIndentedLogger.logDebug("", "namespace mappings not available because element doesn't have an id attribute", "prefix", prefix, "element", Dom4jUtils.elementToDebugString(element))
            new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(element))
        }
    }

    // Builder that produces an ElementAnalysis for a known incoming Element
    def build(parent: ElementAnalysis, preceding: Option[ElementAnalysis], controlElement: Element, containerScope: Scope,
              externalLHHA: Buffer[ExternalLHHAAnalysis], eventHandlers: Buffer[EventHandlerImpl]) = {

        val locationData = new ExtendedLocationData(controlElement.getData.asInstanceOf[LocationData], "gathering static control information", controlElement)

        // Check for mandatory id
        val controlStaticId = XFormsUtils.getElementStaticId(controlElement)
        if (controlStaticId eq null)
            throw new ValidationException("Missing mandatory id for element: " + controlElement.getQualifiedName, locationData)

        // Prefixed id
        val controlPrefixedId = containerScope.fullPrefix + controlStaticId

        // 1. If element is not built-in, first check XBL and generate shadow content if needed
        xblBindings.processElementIfNeeded(getIndentedLogger, controlElement,
            controlPrefixedId, locationData, containerScope, eventHandlers)

        // 2. Create new control if possible
        val elementAnalysis = {
            val controlScope = getResolutionScopeByPrefixedId(controlPrefixedId)
            val staticStateContext = StaticStateContext(partAnalysis, controlAnalysisMap.size + 1)
            ControlAnalysisFactory.create(staticStateContext, controlElement, Some(parent), preceding, controlScope)
        }

        // Throw if the element is unknown (we could also just warn?)
//        getIndentedLogger.logWarning("", "Unknown control: " + controlElement.getQualifiedName)
        if (! elementAnalysis.isDefined)
            throw new ValidationException("Unknown control: " + controlElement.getQualifiedName, locationData)

        // 3. Index new control
        elementAnalysis foreach (indexNewControl(_, externalLHHA, eventHandlers))

        elementAnalysis
    }
    
    def analyze() {
        getIndentedLogger.startHandleOperation("", "performing static analysis")

        // Initialize scopes
        initializeScopes()

        // Global lists of external LHHA and handlers
        val externalLHHA = Buffer[ExternalLHHAAnalysis]()
        val eventHandlers = Buffer[EventHandlerImpl]()
        
        // Create and index root control
        val rootControlAnalysis = new RootControl(StaticStateContext(this, 0), staticStateDocument.rootControl, startScope)
        indexNewControl(rootControlAnalysis, externalLHHA, eventHandlers)

        // Gather controls
        val buildGatherLHHAAndHandlers: ChildrenBuilderTrait#Builder = build(_, _, _, _, externalLHHA, eventHandlers)
        rootControlAnalysis.buildChildren(buildGatherLHHAAndHandlers, startScope)

        // Gather new global XBL controls introduced above
        // Q: should recursively check?
        // NOTE: For now we don't set the `preceding` value. The main impact is no resolution of variables. It might be
        // desirable not to scope them anyway.
        for {
            shadowTree ← xblBindings.allGlobals.values
            globalElement ← Dom4jUtils.elements(shadowTree.compactShadowTree.getRootElement).asScala
        } yield
            buildGatherLHHAAndHandlers(rootControlAnalysis, None, globalElement, startScope) collect
                { case childrenBuilder: ChildrenBuilderTrait ⇒ childrenBuilder.buildChildren(buildGatherLHHAAndHandlers, startScope) }

        // Index models that were found in the control tree
        controlTypes.get("model") match {
            case Some(map) ⇒
                for {
                    value ← map.values
                    model = value.asInstanceOf[Model]
                } yield
                    indexModel(model, eventHandlers)
            case None ⇒
        }

        // Register event handlers
        registerEventHandlers(eventHandlers)

        // Attach external LHHA elements
        for (entry ← externalLHHA)
            entry.attachToControl()

        // Some controls need special processing
        analyzeCustomControls()

        // Analyze root control XPath if needed as nested models might ask for its context
        if (staticState.isXPathAnalysis)
            rootControlAnalysis.analyzeXPath()

        // Analyze all models XPath
        analyzeModelsXPath()

        // Analyze controls XPath
        analyzeControlsXPath()

        // Set baseline resources before freeing transient state
        xblBindings.baselineResources

        getIndentedLogger.endHandleOperation("controls", controlAnalysisMap.size.toString)

        // Log if needed
        if (XFormsProperties.getDebugLogXPathAnalysis)
            dumpAnalysis()

        // Clean-up to finish initialization
        freeTransientState()
    }

    def toXML(helper: ContentHandlerHelper) {
        XMLUtils.wrapWithRequestElement(helper, new XMLUtils.DebugXML {
            def toXML(helper: ContentHandlerHelper) {
                for {
                    controlAnalysis ← controlAnalysisMap.values
                    if !controlAnalysis.isInstanceOf[ExternalLHHAAnalysis]
                    if controlAnalysis.localName != "root" // for now don't output root as it's not an interesting container and we don't want to modify the unit tests
                } yield
                    controlAnalysis.toXML(helper, List())()
            }
        })
    }

    def dumpAnalysis() {
        if (staticState.isXPathAnalysis)
            println(Dom4jUtils.domToPrettyString(XMLUtils.createDocument(this)))
    }
}

object PartAnalysisImpl {

    def extractNestedModels(compactShadowTreeWrapper: DocumentWrapper, detach: Boolean, locationData: LocationData) = {

        // TODO: Don't use XPath here, but extract models as controls tree is visited
        val xpathExpression = "//xforms:model[not(ancestor::xforms:instance)]"
        val modelItems = XPathCache.evaluate(compactShadowTreeWrapper, xpathExpression,
            XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING, null, null, null, null, locationData)

        for {
            item ← modelItems.asScala
            nodeInfo = item.asInstanceOf[NodeInfo]
            element = nodeInfo.asInstanceOf[NodeWrapper].getUnderlyingNode.asInstanceOf[Element]
            document = Dom4jUtils.createDocumentCopyParentNamespaces(element, detach)
        } yield
            document
    }
}