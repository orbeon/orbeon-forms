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

import model.Model
import org.orbeon.saxon.om.NodeInfo
import collection.mutable.{HashSet, HashMap}
import org.orbeon.saxon.dom4j.NodeWrapper
import org.orbeon.oxf.xforms._
import analysis.controls._
import org.w3c.dom.Node._
import org.orbeon.oxf.common.OXFException
import java.util.{Map => JMap}
import java.lang.String
import collection.immutable.Nil

class PathMapXPathDependencies(private val containingDocument: XFormsContainingDocument) extends XPathDependencies {

    private val logger = containingDocument.getControls match {
        case controls: XFormsControls => controls.getIndentedLogger
        case _ => containingDocument.getIndentedLogger
    }
    
    // Represent the state of a model
    private class ModelState(private val modelPrefixedId: String) {

        var hasStructuralChanges = false

        var useCalculateChangeset = false   // start dirty
        var useValidateChangeset = false    // start dirty

        // Meaning of a change: "the string value of the node has changed"
        var recalculateChangeset = new MapSet[String, String]
        var revalidateChangeset = recalculateChangeset

        def markValueChanged(node: NodeInfo) {
            // Only care about path changes if there is no structural change for this model, since structural changes
            // for now disable any more subtle path-based check.
            if (!hasStructuralChanges) {

                // Create instance/path combo
                val instance = containingDocument.getInstanceForNode(node)

                val instancePrefixedId = instance.getPrefixedId

                def processNode(n: NodeInfo) {
                    val path = PathMapXPathDependencies.createFingerprintedPath(n)

                    val instancePath = (instancePrefixedId -> path)

                    // Update model and view changesets
                    recalculateChangeset += instancePath
                    if (revalidateChangeset ne recalculateChangeset)
                        revalidateChangeset += instancePath // also add to revalidate changeset

                    RefreshState.changeset += instancePath

                    // Add parent elements as well. The idea is that if the string value of /a/b/c changed, then the
                    // string value of /a/b did as well, and so did /a's.
                    // This adds more entries to the changeset, but handles cases such as detecting changes impacting
                    // the string() or serialize() functions.
                    val parent = n.getParent
                    if ((parent ne null) && parent.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE)
                        processNode(parent)
                }

                processNode(node)
            }
        }

        def markStructuralChange() {

            // Update model and view information
            hasStructuralChanges = true
            RefreshState.structuralChangeModels += modelPrefixedId

            markBindsDirty()
        }

        def rebuildDone() {
            hasStructuralChanges = false

            markBindsDirty()
        }

        private def markBindsDirty() {
            useCalculateChangeset = false
            useValidateChangeset = false

            // Changesets won't be used
            recalculateChangeset.clear()
            revalidateChangeset = recalculateChangeset
        }

        // Say that for this model, calculate binds are clean and can be checked for modifications based on value changes
        def recalculateDone() {
            useCalculateChangeset = true
            recalculateChangeset = clearChangeset(recalculateChangeset, revalidateChangeset)
        }
        // Say that for this model, validate binds are clean and can be checked for modifications based on value changes
        def revalidateDone() {
            useValidateChangeset = true
            revalidateChangeset = clearChangeset(revalidateChangeset, recalculateChangeset)
        }

        private def clearChangeset(left: MapSet[String, String], right: MapSet[String, String]) = {
            // Try to make both changesets point to the same object, but never clear the right changeset if not empty
            if (right isEmpty) right
            else if (left ne right) { left.clear(); left }
            else new MapSet[String, String]
        }

        def refreshDone() = ()

        def outOfDateChangesetForMip(mip: Model#Bind#MIP) = mip.isValidateMIP && !useValidateChangeset || !mip.isValidateMIP && !useCalculateChangeset

        // TODO: Scenario that can break this:
        // recalculate -> value change -> xxf-value-changed -> insert -> calculateClean = false -> recalculateDone -> calculateClean = true
        // although following rebuild will set calculateClean = false, this is not right and might still lead to issues
    }

    // State of models
    private val modelStates = new HashMap[String, ModelState]

    private def getModelState(modelPrefixedId: String): ModelState =
        modelStates.get(modelPrefixedId) match {
            case Some(modelState) => modelState
            case None =>
                val modelState = new ModelState(modelPrefixedId)
                modelStates += (modelPrefixedId -> modelState)
                modelState
        }

    // Used between refresh start and refresh done
    private var inRefresh = false

    // Keep state related to the view
    private object RefreshState {
        // Structural changes
        val structuralChangeModels = new HashSet[String]

        // Modified paths by instance
        val changeset = new MapSet[String, String]

        // Caches to speedup checks on repeated items
        val modifiedBindingCache = new HashMap[String, UpdateResult]
        val modifiedValueCache = new HashMap[String, UpdateResult]
        val modifiedLHHACache = new HashMap[String, Boolean]
        val modifiedItemsetCache = new HashMap[String, Boolean]

        // Statistics
        var bindingUpdateCount: Int = 0
        var valueUpdateCount: Int = 0

        var bindingXPathOptimizedCount: Int = 0
        var valueXPathOptimizedCount: Int = 0

        def getStructuralChangeModels = structuralChangeModels

        def refreshDone() {
            structuralChangeModels.clear()
            changeset.clear()

            modifiedBindingCache.clear()
            modifiedValueCache.clear()

            modifiedLHHACache.clear()
            modifiedItemsetCache.clear()

            bindingUpdateCount = 0
            valueUpdateCount = 0

            bindingXPathOptimizedCount = 0
            valueXPathOptimizedCount = 0
        }
    }

    private var mipUpdateCount: Int = 0
    private var mipXPathOptimizedCount: Int = 0

    private var lhhaEvaluationCount: Int = 0
    private var lhhaOptimizedCount: Int = 0
    private var lhhaUnknownDependencies: Int = 0
    private var lhhaMissCount: Int = 0
    private var lhhaHitCount: Int = 0

    private var itemsetEvaluationCount: Int = 0
    private var itemsetOptimizedCount: Int = 0
    private var itemsetUnknownDependencies: Int = 0
    private var itemsetMissCount: Int = 0
    private var itemsetHitCount: Int = 0

    def markValueChanged(model: XFormsModel, nodeInfo: NodeInfo) {

        // Caller must only call this for a mutable node belonging to the given model
        require(nodeInfo.isInstanceOf[NodeWrapper])
        require(model.getInstanceForNode(nodeInfo).getModel(containingDocument) == model)

        getModelState(model.getPrefixedId).markValueChanged(nodeInfo)
    }

    def markStructuralChange(model: XFormsModel, instance: XFormsInstance): Unit =
        getModelState(model.getPrefixedId).markStructuralChange()

    def rebuildDone(model: Model) = getModelState(model.prefixedId).rebuildDone()
    def recalculateDone(model: Model) = getModelState(model.prefixedId).recalculateDone()
    def revalidateDone(model: Model) = getModelState(model.prefixedId).revalidateDone()

    def refreshStart() {
        inRefresh = true
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

    def refreshDone() {

        if (logger.isDebugEnabled)
            logger.logDebug("dependencies", "refresh done",
                Array("bindings updated", RefreshState.bindingUpdateCount.toString,
                      "values updated", RefreshState.valueUpdateCount.toString,
                      "MIPs updated", mipUpdateCount.toString,
                      "Binding XPath optimized", RefreshState.bindingXPathOptimizedCount.toString,
                      "Value XPath optimized", RefreshState.valueXPathOptimizedCount.toString,
                      "MIP XPath optimized", mipXPathOptimizedCount.toString,
                      "Total XPath optimized", (RefreshState.bindingXPathOptimizedCount + RefreshState.valueXPathOptimizedCount + mipXPathOptimizedCount).toString): _*)

        for (modelState <- modelStates.values)
            modelState.refreshDone()

        RefreshState.refreshDone()

        mipUpdateCount = 0
        mipXPathOptimizedCount = 0

        inRefresh = false
    }

    def afterInitialResponse {
        outputLHHAItemsetStats()
    }

    def beforeUpdateResponse {
        lhhaEvaluationCount = 0
        lhhaOptimizedCount = 0
        lhhaUnknownDependencies = 0
        lhhaMissCount = 0
        lhhaHitCount = 0

        itemsetEvaluationCount = 0
        itemsetOptimizedCount = 0
        itemsetUnknownDependencies = 0
        itemsetMissCount = 0
        itemsetHitCount = 0
    }

    def afterUpdateResponse {
        outputLHHAItemsetStats()
    }

    def notifyComputeLHHA: Unit = lhhaEvaluationCount += 1
    def notifyOptimizeLHHA: Unit = lhhaOptimizedCount += 1

    def notifyComputeItemset: Unit = itemsetEvaluationCount += 1
    def notifyOptimizeItemset: Unit = itemsetOptimizedCount += 1

    private def outputLHHAItemsetStats() {
        if (logger.isDebugEnabled)
            logger.logDebug("dependencies", "summary after response",
                Array("LHHA evaluations", lhhaEvaluationCount.toString,
                      "LHHA optimized", lhhaOptimizedCount.toString,
                      "LHHA unknown dependencies", lhhaUnknownDependencies.toString,
                      "LHHA intersections", lhhaHitCount.toString,
                      "LHHA disjoints", lhhaMissCount.toString,
                      "Itemset evaluations", itemsetEvaluationCount.toString,
                      "Itemset optimized", itemsetOptimizedCount.toString,
                      "Itemset unknown dependencies", itemsetUnknownDependencies.toString,
                      "Itemset intersections", itemsetHitCount.toString,
                      "Itemset disjoints", itemsetMissCount.toString): _*)
    }

    // For unit tests
    def markStructuralChangeTest(modelPrefixedId: String) {
        getModelState(modelPrefixedId).markStructuralChange()
    }

    // For unit tests
    def setModifiedPathTest(instance: String, namespaces: JMap[String, String], path: String) {
        assert(RefreshState.changeset.isEmpty)

        RefreshState.changeset += (instance -> PathMapXPathAnalysis.getInternalPath(namespaces, path))
    }

    private class UpdateResult(val requireUpdate: Boolean, val savedEvaluations: Int)

    def requireBindingUpdate(controlPrefixedId: String): Boolean = {

        assert(inRefresh)

        val cached = RefreshState.modifiedBindingCache.get(controlPrefixedId)
        val updateResult: UpdateResult =
            cached match {
                case Some(result) => result
                case None => {
                    val control = containingDocument.getStaticOps.getControlAnalysisOption(controlPrefixedId).get
                    val tempResult = control.getBindingAnalysis match {
                        case None =>
                            // Control does not have an XPath binding
                            new UpdateResult(false, 0)
                        case Some(analysis) if !analysis.figuredOutDependencies =>
                            // Binding dependencies are unknown
                            new UpdateResult(true, 0)// savedEvaluations is N/A
                        case Some(analysis) =>
                            // Binding dependencies are known
                            new UpdateResult(
                                analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsBinding(RefreshState.changeset)
                                , control.bindingXPathEvaluations)
                    }

                    if (tempResult.requireUpdate && logger.isDebugEnabled)
                        logger.logDebug("dependencies", "binding requires update",
                                Array("prefixed id", controlPrefixedId, "XPath", control.getBindingAnalysis.get.xpathString): _*)

                    if (control.isWithinRepeat)
                        RefreshState.modifiedBindingCache += (controlPrefixedId -> tempResult)
                    tempResult
                }
            }

        if (updateResult.requireUpdate)
            RefreshState.bindingUpdateCount += 1
        else
            // Update not required
            RefreshState.bindingXPathOptimizedCount += updateResult.savedEvaluations

        updateResult.requireUpdate
    }

    def requireValueUpdate(controlPrefixedId: String): Boolean = {

        assert(inRefresh)

        val cached = RefreshState.modifiedValueCache.get(controlPrefixedId)
        val (updateResult, valueAnalysis) =
            cached match {
                case Some(result) => (result, if (result.requireUpdate) containingDocument.getStaticOps.getControlAnalysisOption(controlPrefixedId).get.getValueAnalysis else null)
                case None => {
                    val control = containingDocument.getStaticOps.getControlAnalysisOption(controlPrefixedId).get
                    val tempValueAnalysis = control.getValueAnalysis
                    val tempUpdateResult = tempValueAnalysis match {
                        case None =>
                            // Control does not have a value
                            new UpdateResult(true, 0)//TODO: should be able to return false here; change once markDirty is handled better
                        case Some(analysis) if !analysis.figuredOutDependencies =>
                            // Value dependencies are unknown
                            new UpdateResult(true, 0)// savedEvaluations is N/A
                        case Some(analysis) =>
                            // Value dependencies are known
                            new UpdateResult(analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsValue(RefreshState.changeset)
                                , if (control.value.isDefined) 1 else 0)
                    }
                    if (tempUpdateResult.requireUpdate && tempValueAnalysis.isDefined && logger.isDebugEnabled)
                        logger.logDebug("dependencies", "value requires update",
                                Array("prefixed id", controlPrefixedId, "XPath", tempValueAnalysis.get.xpathString): _*)

                    if (control.isWithinRepeat)
                        RefreshState.modifiedValueCache += (controlPrefixedId -> tempUpdateResult)
                    (tempUpdateResult, tempValueAnalysis)
                }
            }

        if (updateResult.requireUpdate && valueAnalysis.isDefined) // TODO: see above, check on valueAnalysis only because non-value controls still call this method
            RefreshState.valueUpdateCount += 1
        else
            // Update not required
            RefreshState.valueXPathOptimizedCount += updateResult.savedEvaluations

        updateResult.requireUpdate
    }

    def requireLHHAUpdate(lhhaName: String, controlPrefixedId: String): Boolean = {

        assert(inRefresh) // LHHA is evaluated lazily typically outside of refresh, but LHHA invalidation takes place during refresh

        RefreshState.modifiedLHHACache.get(controlPrefixedId) match {
            case Some(result) => result // cached
            case None => // not cached
                containingDocument.getStaticOps.getControlAnalysisOption(controlPrefixedId) match {
                    case Some(control: LHHATrait) => // control found
                        val result = control.getLHHAValueAnalysis(lhhaName) match {
                            case Some(analysis) if !analysis.figuredOutDependencies => // dependencies are unknown
                                lhhaUnknownDependencies += 1
                                true
                            case Some(analysis) => // dependencies are known
                                val result = analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsValue(RefreshState.changeset)
                                if (result) lhhaHitCount += 1 else lhhaMissCount += 1
                                result
                            case None => throw new OXFException("Control " + controlPrefixedId + " doesn't have LHHA " + lhhaName)
                        }
                        if (control.isWithinRepeat)
                            RefreshState.modifiedLHHACache += (controlPrefixedId -> result)
                        result
                    case _ => throw new OXFException("Control " + controlPrefixedId + " not found")
                }
        }
    }


    def requireItemsetUpdate(controlPrefixedId: String): Boolean = {

        assert(inRefresh)

        RefreshState.modifiedItemsetCache.get(controlPrefixedId) match {
            case Some(result) => result // cached
            case None => // not cached
                containingDocument.getStaticOps.getControlAnalysisOption(controlPrefixedId) match {
                    case Some(control: SelectionControl) => // control found
                        val result = control.getItemsetAnalysis match {
                            case Some(analysis) if !analysis.figuredOutDependencies => // dependencies are unknown
                                itemsetUnknownDependencies += 1
                                true
                            case Some(analysis) => // dependencies are known
                                val result = analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsValue(RefreshState.changeset)
                                if (result) itemsetHitCount += 1 else itemsetMissCount += 1
                                result
                            case None => throw new IllegalStateException("Itemset not analyzed")
                        }
                        if (control.isWithinRepeat)
                            RefreshState.modifiedItemsetCache += (controlPrefixedId -> result)
                        result
                    case _ => throw new OXFException("Control " + controlPrefixedId + " not found")
                }
        }
    }

    def hasAnyCalculationBind(model: Model, instancePrefixedId: String) =
        !model.figuredAllBindRefAnalysis || model.computedBindExpressionsInstances.contains(instancePrefixedId)

    def hasAnyValidationBind(model: Model, instancePrefixedId: String) =
        !model.figuredAllBindRefAnalysis || model.validationBindInstances.contains(instancePrefixedId)

//    public void visitInstanceNode(XFormsModel model, NodeInfo nodeInfo) {
//        if (!touchedMIPNodes.contains(nodeInfo)) {
//            // First time this is called for a NodeInfo: keep old MIP values and remember NodeInfo
//            InstanceData.saveMIPs(nodeInfo)
//            touchedMIPNodes += nodeInfo
//        }
//    }

    def requireModelMIPUpdate(model: Model, bind: Model#Bind, mipName: String): Boolean = {

        // TODO: cache must store by MIP to optimize xf:bind/@ref over multiple nodes

        bind.getMIP(mipName) match {
            case Some(mip) =>

                val modelState = getModelState(model.prefixedId)
                val updateResult =
                    if (modelState.outOfDateChangesetForMip(mip)) {
                        // Can't check dependencies because the changeset is out of date
                        new UpdateResult(true, 0)// savedEvaluations is N/A
                    } else {
                        // XPath MIPs

                        // Special case for type which is not an XPath expression
                        // We don't check whether we need to update the type MIP, since it is constant, but whether we check whether
                        // the value to type check has changed.
                        val valueAnalysis = mip match {
                            case xpathMIP: bind.XPathMIP => Some(xpathMIP.analysis)
                            case typeMIP: bind.TypeMIP => bind.getValueAnalysis
                            case _ => throw new IllegalStateException("Expecting XPath MIP or type MIP")
                        }

                        val tempUpdateResult = valueAnalysis match {
                            case Some(analysis) if !analysis.figuredOutDependencies =>
                                // Value dependencies are unknown
                                new UpdateResult(true, 0)// savedEvaluations is N/A
                            case Some(analysis) =>
                                // Value dependencies are known
                                // NOTE: Assume bind/@ref or MIP points to/depends only on the containing model
                                new UpdateResult(analysis.intersectsValue(if (mip.isValidateMIP) modelState.revalidateChangeset else modelState.recalculateChangeset), 1)
                            case _ => throw new IllegalStateException("No value analysis found for xf:bind with " + mipName)
                        }

                        if (tempUpdateResult.requireUpdate && logger.isDebugEnabled)
                            logger.logDebug("dependencies", "MIP requires update",
                                Array("prefixed id", bind.prefixedId, "MIP name", mip.name, "XPath", valueAnalysis.get.xpathString): _*)

                        tempUpdateResult
                    }

                if (updateResult.requireUpdate)
                    mipUpdateCount += 1
                else
                    // Update not required
                    mipXPathOptimizedCount += updateResult.savedEvaluations

                updateResult.requireUpdate

            case None => throw new IllegalArgumentException("No MIP found for name: " + mipName)
        }
    }
}

object PathMapXPathDependencies {

    /**
     * Create a fingerprinted path of the form: 3142/1425/@1232 from a node.
     */
    private def createFingerprintedPath(node: NodeInfo): String = {

        // Create an immutable list with ancestor-or-self nodes up to but not including the document node
        var ancestorOrSelf: List[NodeInfo] = Nil
        var currentNode = node
        while (currentNode != null && currentNode.getNodeKind != DOCUMENT_NODE) {
            ancestorOrSelf = currentNode :: ancestorOrSelf
            currentNode = currentNode.getParent
        }

        // Fingerprint representation of the element and attribute nodes
        (if (ancestorOrSelf.size > 1) // first is the root element, which we skip as that corresponds to instance('...')
            ancestorOrSelf.tail map (node => node.getNodeKind match {
                case ELEMENT_NODE => node.getFingerprint
                case ATTRIBUTE_NODE => "@" + node.getFingerprint
            })
        else
            Nil) mkString "/"
    }
}