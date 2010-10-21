/**
 *  Copyright (C) 2010 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import model.Model
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.saxon.om.NodeInfo
import collection.mutable.{HashSet, HashMap, Set}
import org.orbeon.saxon.dom4j.NodeWrapper
import org.orbeon.oxf.xforms._
import org.w3c.dom.Node._

class PathMapXPathDependencies(var logger: IndentedLogger, staticState: XFormsStaticState)// Constructor for unit tests
        extends XPathDependencies {

    private var containingDocument: XFormsContainingDocument = _

    // Represent the state of a model between refreshes
    class ModelState {
        var modifiedNodes: Set[NodeInfo] = new HashSet[NodeInfo]
        var structuralChanges = false
        var calculateClean = false   // start dirty
        var validateClean = false    // start dirty

        def refreshDone() {
            modifiedNodes.clear()
            structuralChanges = false
            calculateClean = false
            validateClean = false
        }

        def markStructuralChange() {
            structuralChanges = true
            calculateClean = false
            validateClean = false
        }

        def rebuildDone() {
            calculateClean = false
            validateClean = false
        }

        def mipDirty(mip: Model#Bind#MIP): Boolean = mip.isValidateMIP && !validateClean || !calculateClean
    }

    // Set of models
    private val modelStates = new HashMap[String, ModelState]

    private def getModelState(modelPrefixedId: String): ModelState = {
        val existingModelState = modelStates.get(modelPrefixedId)
        existingModelState match {
            case Some(modelState) => modelState
            case None => {
                val modelState = new ModelState
                modelStates.put(modelPrefixedId, modelState)
                modelState
            }
        }
    }

    private var modifiedPathsCached: Boolean = false
    private var structuralChangesModelsCached: Boolean = false
    private val modifiedPathsCache = new HashSet[String]
    private val structuralChangeModelsCache = new HashSet[String]

    // Cache to speedup checks on repeated items
    private val modifiedBindingCache = new HashMap[String, UpdateResult]
    private val modifiedValueCache = new HashMap[String, UpdateResult]
    private val modifiedMIPCache = new HashMap[String, UpdateResult]

    // Statistics
    private var bindingUpdateCount: Int = 0
    private var valueUpdateCount: Int = 0
    private var mipUpdateCount: Int = 0

    private var bindingXPathOptimizedCount: Int = 0
    private var valueXPathOptimizedCount: Int = 0
    private var mipXPathOptimizedCount: Int = 0

    def this(containingDocument: XFormsContainingDocument) {
        // Defer logger initialization as controls might not have been initialized at time this constructor is called.
        this(null, containingDocument.getStaticState)
        this.containingDocument = containingDocument
    }

    def getLogger: IndentedLogger = {
        if (logger eq null) // none was passed
            logger = containingDocument.getControls.getIndentedLogger
        return logger
    }

    def markValueChanged(model: XFormsModel, nodeInfo: NodeInfo) {

        // Caller must only call this for a mutable node belonging to the given model
        require(nodeInfo.isInstanceOf[NodeWrapper])
        require(model.getInstanceForNode(nodeInfo).getModel(containingDocument) ==  model)

        if (!getModelState(model.getPrefixedId).structuralChanges) {
            // Only care about path changes if there is no structural change for this model, since structural changes
            // for now disable any more subtle path-based check.
            getModelState(model.getPrefixedId).modifiedNodes.add(nodeInfo)
        }
    }

    def markStructuralChange(model: XFormsModel, instance: XFormsInstance) {
        getModelState(model.getPrefixedId).markStructuralChange()
    }

    def rebuildDone(model: Model) {
        getModelState(model.prefixedId).rebuildDone()
    }

    def recalculateDone(model: Model) {
        // Say that for this model, calculate binds are clean and can be checked for modifications based on value changes
        getModelState(model.prefixedId).calculateClean = true
    }

    def revalidateDone(model: Model) {
        // Say that for this model, validate binds are clean and can be checked for modifications based on value changes
        getModelState(model.prefixedId).validateClean = true
    }

    def refreshDone() {

        if (getLogger.isDebugEnabled)
            getLogger.logDebug("dependencies", "refresh done",
                Array("bindings updated", bindingUpdateCount.toString,
                      "values updated", valueUpdateCount.toString,
                      "MIPs updated", mipUpdateCount.toString,
                      "Binding XPath optimized", bindingXPathOptimizedCount.toString,
                      "Value XPath optimized", valueXPathOptimizedCount.toString,
                      "MIP XPath optimized", mipXPathOptimizedCount.toString,
                      "Total XPath optimized", (bindingXPathOptimizedCount + valueXPathOptimizedCount + mipXPathOptimizedCount).toString): _*)

        for (modelState <- modelStates.values)
            modelState.refreshDone()

        modifiedPathsCached = false
        structuralChangesModelsCached = false
        modifiedPathsCache.clear()
        structuralChangeModelsCache.clear()

        modifiedBindingCache.clear()
        modifiedValueCache.clear()
        modifiedMIPCache.clear()

        bindingUpdateCount = 0
        valueUpdateCount = 0
        mipUpdateCount = 0

        bindingXPathOptimizedCount = 0
        valueXPathOptimizedCount = 0
        mipXPathOptimizedCount = 0
    }

    private def getModifiedPaths: Set[String] = {
        if (!modifiedPathsCached) {
            // Add all paths for modified nodes that belong to an instance
            modifiedPathsCache ++=
                (for (model <- modelStates.values; node <- model.modifiedNodes; instance = containingDocument.getInstanceForNode(node); if instance != null)
                    yield PathMapXPathDependencies.createFingerprintPath(instance, node))

            modifiedPathsCached = true
        }
        modifiedPathsCache
    }

    private def getStructuralChangeModels: Set[String] = {
        if (!structuralChangesModelsCached) {
            // Add all model ids that have structuralChanges set to true
            structuralChangeModelsCache ++= modelStates filter (_._2.structuralChanges) map (_._1)
            structuralChangesModelsCached = true;
        }
        structuralChangeModelsCache
    }

    // For unit tests
    def markStructuralChangeTest(modelPrefixedId: String) {
        getModelState(modelPrefixedId).markStructuralChange()
    }

    // For unit tests
    def setModifiedPathTest(path: String) {
        assert(!modifiedPathsCached)

        modifiedPathsCache.add(path)
        modifiedPathsCached = true
    }

    private class UpdateResult(val requireUpdate: Boolean, val savedEvaluations: Int)

    def requireBindingUpdate(controlPrefixedId: String): Boolean = {

        val cached = modifiedBindingCache.get(controlPrefixedId)
        val updateResult: UpdateResult =
            cached match {
                case Some(result) => result
                case None => {
                    val elementAnalysis = staticState.getControlAnalysis(controlPrefixedId)
                    val tempResult =
                        if (elementAnalysis.getBindingAnalysis.isEmpty) {
                            // Control does not have an XPath binding
                            new UpdateResult(false, 0)
                        } else if (!elementAnalysis.getBindingAnalysis.get.figuredOutDependencies) {
                            // Binding dependencies are unknown
                            new UpdateResult(true, 0)// savedEvaluations is N/A
                        } else {
                            // Binding dependencies are known
                            new UpdateResult(
                                elementAnalysis.getBindingAnalysis.get.intersectsModels(getStructuralChangeModels) ||
                                        elementAnalysis.getBindingAnalysis.get.intersectsBinding(getModifiedPaths)
                                , elementAnalysis.bindingXPathEvaluations)
                        }

                    if (tempResult.requireUpdate && getLogger.isDebugEnabled) {
                        getLogger.logDebug("dependencies", "binding requires update",
                                Array("prefixed id", controlPrefixedId, "XPath", elementAnalysis.getBindingAnalysis.get.xpathString): _*)
                    }

                    modifiedBindingCache.put(controlPrefixedId, tempResult)
                    tempResult
                }
            }

        if (updateResult.requireUpdate) {
            bindingUpdateCount += 1
        } else {
            // Update not required
            bindingXPathOptimizedCount += updateResult.savedEvaluations
        }
        updateResult.requireUpdate
    }

    def requireValueUpdate(controlPrefixedId: String): Boolean = {

        val cached = modifiedValueCache.get(controlPrefixedId)
        val (updateResult, valueAnalysis) =
            cached match {
                case Some(result) => (result, if (result.requireUpdate) staticState.getControlAnalysis(controlPrefixedId).getValueAnalysis else null)
                case None => {
                    val controlAnalysis = staticState.getControlAnalysis(controlPrefixedId)
                    val tempValueAnalysis = controlAnalysis.getValueAnalysis
                    val tempUpdateResult =
                        if (tempValueAnalysis.isEmpty) {
                            // Control does not have a value
                            new UpdateResult(true, 0)//TODO: should be able to return false here; change once markDirty is handled better
                        } else if (!tempValueAnalysis.get.figuredOutDependencies) {
                            // Value dependencies are unknown
                            new UpdateResult(true, 0)// savedEvaluations is N/A
                        } else {
                            // Value dependencies are known
                            new UpdateResult(
                                tempValueAnalysis.get.intersectsModels(getStructuralChangeModels) ||
                                        tempValueAnalysis.get.intersectsValue(getModifiedPaths)
                                , if (controlAnalysis.value.isDefined) 1 else 0)
                        }
                    if (tempUpdateResult.requireUpdate && tempValueAnalysis.isDefined && getLogger.isDebugEnabled) {
                        getLogger.logDebug("dependencies", "value requires update",
                                Array("prefixed id", controlPrefixedId, "XPath", tempValueAnalysis.get.xpathString): _*)
                    }

                    modifiedValueCache.put(controlPrefixedId, tempUpdateResult)
                    (tempUpdateResult, tempValueAnalysis)
                }
            }

        if (updateResult.requireUpdate && valueAnalysis.isDefined) {// TODO: see above, check on valueAnalysis only because non-value controls still call this method
            valueUpdateCount += 1
        } else {
            // Update not required
            valueXPathOptimizedCount += updateResult.savedEvaluations
        }
        updateResult.requireUpdate
    }

    def requireLHHAUpdate(lhha: XFormsConstants.LHHA, controlPrefixedId: String): Boolean = {
        // TODO
//        final ControlAnalysis controlAnalysis = staticState.getControlAnalysis(controlPrefixedId)
        true
    }

    def hasAnyCalculationBind(model: Model, instancePrefixedId: String) = {
        !model.figuredAllBindRefAnalysis || model.computedBindExpressionsInstances.contains(instancePrefixedId)
    }

    def hasAnyValidationBind(model: Model, instancePrefixedId: String) = {
        !model.figuredAllBindRefAnalysis || model.validationBindInstances.contains(instancePrefixedId)
    }

//    public void visitInstanceNode(XFormsModel model, NodeInfo nodeInfo) {
//        if (!touchedMIPNodes.contains(nodeInfo)) {
//            // First time this is called for a NodeInfo: keep old MIP values and remember NodeInfo
//            InstanceData.saveMIPs(nodeInfo)
//            touchedMIPNodes.add(nodeInfo)
//        }
//    }

    def refreshStart() {
//        if (touchedMIPNodes.size() > 0) {
//            // All revalidations and recalculations are done, process information about nodes touched
//            for (final NodeInfo nodeInfo : touchedMIPNodes) {
//                if (InstanceData.getPreviousInheritedRelevant(nodeInfo) != InstanceData.getInheritedRelevant(nodeInfo)
//                        || InstanceData.getPreviousInheritedReadonly(nodeInfo) != InstanceData.getInheritedReadonly(nodeInfo)
//                        || InstanceData.getPreviousRequired(nodeInfo) != InstanceData.getRequired(nodeInfo)
//                        || InstanceData.getPreviousValid(nodeInfo) != InstanceData.getValid(nodeInfo)) {
//                    markMIPChanged(model, nodeInfo)
//                }
//            }
//        }
    }

    def hasAnyCalculationBind(model: Model) = model.hasCalculateComputedCustomBind
    def hasAnyValidationBind(model: Model) = model.hasValidateBind

    def requireModelMIPUpdate(model: Model, bindId: String, mipName: String): Boolean = {
        val bind = model.bindsById.get(bindId)
        bind.getMIP(mipName) match {
            case Some(mip) =>
                val cached = modifiedMIPCache.get(bind.prefixedId)
                val updateResult =
                    cached match {
                        case Some(result) => result
                        case None => {
                            val mipAnalysis = mip.analysis
                            val tempUpdateResult =
                                if (!mipAnalysis.figuredOutDependencies) {
                                    // Value dependencies are unknown
                                    new UpdateResult(true, 0)// savedEvaluations is N/A
                                } else if (getModelState(model.prefixedId).mipDirty(mip)) {
                                    // Value dependencies are known but the MIP is dirty because validation or calculation binds are dirty
                                    new UpdateResult(true, 0)// savedEvaluations is N/A
                                } else {
                                    // Value dependencies are known
                                    new UpdateResult(
                                        mipAnalysis.intersectsModels(getStructuralChangeModels)
                                                || mipAnalysis.intersectsValue(getModifiedPaths)
                                        , 1)
                                }
                            if (tempUpdateResult.requireUpdate && mipAnalysis != null && getLogger.isDebugEnabled) {
                                getLogger.logDebug("dependencies", "MIP requires update",
                                    Array("prefixed id", bind.prefixedId, "MIP name", mip.name, "XPath", mipAnalysis.xpathString): _*)
                            }

                            modifiedMIPCache.put(bind.prefixedId, tempUpdateResult)
                            tempUpdateResult
                        }
                    }

                if (updateResult.requireUpdate) {
                    mipUpdateCount += 1
                } else {
                    // Update not required
                    mipXPathOptimizedCount += updateResult.savedEvaluations
                }
                updateResult.requireUpdate
            case None =>
                false // no such MIP
        }
    }
}

object PathMapXPathDependencies {

    /**
     * Create a fingerprinted path of the form: instance('instance')/3142/1425/@1232 from a node and instance.
     */
    private def createFingerprintPath(instance: XFormsInstance, node: NodeInfo): String = {

        // Create an immutable list with ancestor-or-self nodes up to but not including the document node
        var ancestorOrSelf: List[NodeInfo] = Nil
        var currentNode = node
        while (currentNode != null && currentNode.getNodeKind != DOCUMENT_NODE) {
            ancestorOrSelf = currentNode :: ancestorOrSelf
            currentNode = currentNode.getParent
        }

        // Join instance('...') and a fingerprint representation of the element and attribute nodes
        PathMapXPathAnalysis.buildInstanceString(instance.getPrefixedId) ::
            (if (ancestorOrSelf.size > 1) // first is the root element, which we skip as we use instance('...') instead
                ancestorOrSelf.tail map (node => node.getNodeKind match {
                    case ELEMENT_NODE => node.getFingerprint
                    case ATTRIBUTE_NODE => "@" + node.getFingerprint
                })
            else
                Nil) mkString "/"
    }
}