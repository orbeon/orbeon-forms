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

import controls.RepeatControl
import java.lang.String
import org.orbeon.oxf.xforms.xbl.{XBLBindings, XBLBindingsBase}
import scala.collection.JavaConversions._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.dom4j.{NodeWrapper, DocumentWrapper}
import script.ServerScript
import collection.mutable.LinkedHashMap
import org.dom4j.{QName, Element, Document, Node}
import collection.immutable.Stream._
import scala.Option._
import java.util.{ArrayList, List => JList, Map => JMap}

class PartAnalysisImpl(val staticState: XFormsStaticState, val parent: Option[PartAnalysis],
                       val startScope: XBLBindingsBase.Scope, val metadata: Metadata,
                       staticStateDocument: StaticStateDocument)
    extends PartAnalysisBase(metadata, startScope) with PartAnalysis with PartGlobalOpsImpl {

    override def analyze() {
        extractControlsModelsComponents()
        super.analyze()
    }

    private def extractControlsModelsComponents() {
        // Do this here so that xblBindings is available for scope resolution when adding models
        xblBindings = new XBLBindings(getIndentedLogger, this, metadata, staticStateDocument.xblDocuments: java.util.List[Document])

        // Get top-level models from static state document
        // FIXME: we don't get a System ID here. Is there a simple solution?
        for (modelDocument <- staticStateDocument.modelDocuments)
            addModel(startScope, modelDocument)

        getIndentedLogger.logDebug("", "created top-level model documents", "count", staticStateDocument.modelDocuments.size.toString)

        // Create controls document with all top-level controls
        controlsDocument = Dom4jUtils.createDocument
        val controlsElement = Dom4jUtils.createElement("controls")
        controlsDocument.setRootElement(controlsElement)
        controlsElement.content.asInstanceOf[JList[Node]].addAll(staticStateDocument.controlElements)
        getIndentedLogger.logDebug("", "created controls document", "top-level controls count", staticStateDocument.controlElements.size.toString)

        // Extract models nested within controls
        val controlsDocumentInfo = new DocumentWrapper(controlsDocument, null, XPathCache.getGlobalConfiguration)
        val extractedModels = PartAnalysisBase.extractNestedModels(controlsDocumentInfo, false, locationData)

        for (currentModelDocument <- extractedModels)
            addModel(startScope, currentModelDocument)

        getIndentedLogger.logDebug("", "created nested top-level model documents", "count", extractedModels.size.toString)
    }

    def locationData = staticState.locationData
    def getIndentedLogger = staticState.getIndentedLogger

    // Ancestor parts
    def ancestors: Stream[PartAnalysis] = parent match {
        case None => Stream.empty
        case Some(parent) => parent #:: parent.ancestors
    }

    def ancestorOrSelf = this #:: ancestors

    // Controls and XBL
    def getControlAnalysis(prefixedId: String) = controlAnalysisMap.get(prefixedId)
    def getAncestorControlForAction(prefixedId: String) = Option(eventHandlerAncestorsMap.get(prefixedId))
    def isComponent(binding: QName) = xblBindings.isComponent(binding)
    def getBinding(prefixedId: String) = xblBindings.getBinding(prefixedId)
    def getBindingId(prefixedId: String) = xblBindings.getBindingId(prefixedId)
    def getBindingQNames = xblBindings.abstractBindings.keys toSeq
    def getAbstractBinding(binding: QName) = xblBindings.abstractBindings.get(binding)

    def getComponentBindings = xblBindings.abstractBindings

    def getComponentFactory(qName: QName) = xblBindings.getComponentFactory(qName)

    // Search scope in ancestor or self parts
    def searchResolutionScopeByPrefixedId(prefixedId: String) =
        ancestorOrSelf map (_.getResolutionScopeByPrefixedId(prefixedId)) filter (_ ne null) head

    // Repeats
    def addMissingRepeatIndexes(repeatPrefixedIdToIndex: JMap[String, java.lang.Integer]) {
        (Option(controlTypes.get("repeat")) map (_.keySet) flatten) foreach { prefixedId =>
            if (!repeatPrefixedIdToIndex.contains(prefixedId))
                repeatPrefixedIdToIndex.put(prefixedId, 0)
        }
    }

    def getRepeatHierarchyString =
        (controlTypes.get("repeat") match {
            case null => Seq.empty
            case repeats =>
                for {
                    repeat <- repeats.values.toSeq
                    prefixedId = repeat.prefixedId
                    ancestorRepeat = RepeatControl.getAncestorRepeatAcrossParts(repeat)
                    parts = ancestorRepeat match { case Some(r) => Seq(prefixedId, r.prefixedId); case None => Seq(prefixedId) }
                } yield
                    parts mkString " "
        }) mkString ","

    // Scripts
    private val scripts = new LinkedHashMap[String, Script]
    def getScripts = scripts

    def extractXFormsScripts(documentInfo: DocumentWrapper, prefix: String) {
        // TODO: Not sure why we actually extract the scripts: we could just keep pointers on them, right? There is
        // probably not a notable performance if any at all, especially since this is needed at page generation time
        // only.
        val xpathExpression = "/descendant-or-self::xxforms:script[not(ancestor::xforms:instance) and exists(@id)]"

        val scriptNodeInfos = XPathCache.evaluate(documentInfo, xpathExpression, XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING, null, null, null, null, locationData)

        scripts ++= (
            for {
                scriptNodeInfo <- scriptNodeInfos
                currentNodeInfo = scriptNodeInfo.asInstanceOf[NodeInfo]
                scriptElement = currentNodeInfo.asInstanceOf[NodeWrapper].getUnderlyingNode.asInstanceOf[Element]
                prefixedId = prefix + XFormsUtils.getElementStaticId(scriptElement)
                isClient = scriptElement.attributeValue("runat") != "server"
                newScript = if (isClient) new Script(_, _, _, _) else new ServerScript(_, _, _, _)
                script = newScript(prefixedId, isClient, scriptElement.attributeValue("type"), scriptElement.getStringValue)
            } yield
                (prefixedId -> script))
    }

    def getGlobals = xblBindings.allGlobals

    def getXBLStyles = xblBindings.allStyles
    def getXBLScripts = xblBindings.allScripts
    def baselineResources = xblBindings.baselineResources

    def getTopLevelControlElements: JList[Element] = {
        val result = new ArrayList[Element]

        if (controlsDocument != null)
            result.add(controlsDocument.getRootElement)

        if (xblBindings != null)
            for (global <- xblBindings.allGlobals.values)
                result.add(global.compactShadowTree.getRootElement)

        result
    }

    def hasControls = getTopLevelControlElements.size > 0
}