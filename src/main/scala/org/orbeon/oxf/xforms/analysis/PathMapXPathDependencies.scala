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

import java.util.{Map ⇒ JMap}

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._
import org.orbeon.oxf.xforms.analysis.model.{Model, StaticBind}
import org.orbeon.saxon.om.{NodeInfo, VirtualNode}
import org.w3c.dom.Node._

import scala.collection.mutable

class PathMapXPathDependencies(private val containingDocument: XFormsContainingDocument)
        extends XPathDependencies
        with Logging {

    private implicit val logger = containingDocument.indentedLogger
    
    // Represent the state of changes to a model
    private class ModelState(private val modelPrefixedId: String) {

        var hasStructuralChanges = false

        var calculateMIPsEvaluatedOnce = false  // start dirty
        var validateMIPsEvaluatedOnce  = false  // start dirty

        // Meaning of a change: "the string value of the node has changed"
        var recalculateChangeset = new MapSet[String, String]   // changeset for recalculate MIPs
        var revalidateChangeset  = recalculateChangeset         // changeset for revalidate MIPs

        def markValueChanged(node: NodeInfo) {
            // Only care about path changes if there is no structural change for this model, since structural changes
            // for now disable any more subtle path-based check.
            if (! hasStructuralChanges) {

                // Create instance/path combo
                val instance = containingDocument.getInstanceForNode(node)

                val instancePrefixedId = instance.getPrefixedId

                def processNode(n: NodeInfo) {
                    val path = PathMapXPathDependencies.createFingerprintedPath(n)

                    val instancePath = instancePrefixedId → path

                    // Update model and view changesets
                    recalculateChangeset += instancePath
                    if (revalidateChangeset ne recalculateChangeset)
                        revalidateChangeset += instancePath // also add to revalidate changeset if it is different

                    RefreshState.changeset += instancePath

                    // Add parent elements as well. The idea is that if the string value of /a/b/c changed, then the
                    // string value of /a/b did as well, and so did /a's.
                    // This adds more entries to the changeset, but handles cases such as detecting changes impacting
                    // the string() or serialize() functions.
                    val parent = n.getParent
                    if ((parent ne null) && parent.getNodeKind == ELEMENT_NODE)
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
            calculateMIPsEvaluatedOnce = false
            validateMIPsEvaluatedOnce = false

            // Changesets won't be used
            recalculateChangeset.clear()
            revalidateChangeset = recalculateChangeset
        }

        // Say that for this model, calculate binds are clean and can be checked for modifications based on value changes
        def recalculateDone() {
            calculateMIPsEvaluatedOnce = true
            recalculateChangeset = clearChangeset(recalculateChangeset, revalidateChangeset)
        }

        // Say that for this model, validate binds are clean and can be checked for modifications based on value changes
        def revalidateDone() {
            validateMIPsEvaluatedOnce = true
            revalidateChangeset = clearChangeset(revalidateChangeset, recalculateChangeset)
        }

        // Return an empty changeset, trying to point to the empty right changeset if possible
        // This is so that we can try to avoid adding changes to both changesets later
        private def clearChangeset(left: MapSet[String, String], right: MapSet[String, String]) =
            if (right isEmpty) right
            else if (left ne right) { left.clear(); left }
            else new MapSet[String, String]

        def refreshDone() = ()

        def isMIPInitiallyDirty(mip: StaticBind#MIP) =
            mip.isValidateMIP && ! validateMIPsEvaluatedOnce || ! mip.isValidateMIP && ! calculateMIPsEvaluatedOnce
    }

    // State of models
    private val modelStates = new mutable.HashMap[String, ModelState]

    private def getModelState(modelPrefixedId: String) =
        modelStates.getOrElseUpdate(modelPrefixedId, new ModelState(modelPrefixedId))

    // Used between refresh/binding update start/done
    private var inRefresh = false
    private var inBindingUpdate = false

    // Keep state related to the view
    private object RefreshState {
        // Structural changes
        val structuralChangeModels = new mutable.HashSet[String]

        // Modified paths by instance
        val changeset = new MapSet[String, String]

        // Caches to speedup checks on repeated items
        val modifiedBindingCacheForRepeats = new mutable.HashMap[String, UpdateResult]
        val modifiedValueCacheForRepeats   = new mutable.HashMap[String, UpdateResult]
        val modifiedLHHACacheForRepeats    = new mutable.HashMap[String, Boolean]
        val modifiedItemsetCacheForRepeats = new mutable.HashMap[String, Boolean]

        // Statistics
        var bindingUpdateCount: Int = 0
        var valueUpdateCount: Int = 0

        var bindingXPathOptimizedCount: Int = 0
        var valueXPathOptimizedCount: Int = 0

        def getStructuralChangeModels = structuralChangeModels

        def refreshDone() {
            structuralChangeModels.clear()
            changeset.clear()

            modifiedBindingCacheForRepeats.clear()
            modifiedValueCacheForRepeats.clear()

            modifiedLHHACacheForRepeats.clear()
            modifiedItemsetCacheForRepeats.clear()

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
        require(nodeInfo.isInstanceOf[VirtualNode])
        require(model.getInstanceForNode(nodeInfo).model == model)

        getModelState(model.getPrefixedId).markValueChanged(nodeInfo)
    }

    def markStructuralChange(model: XFormsModel, instance: XFormsInstance): Unit =
        getModelState(model.getPrefixedId).markStructuralChange()

    def rebuildDone(model: Model)     = getModelState(model.prefixedId).rebuildDone()
    def recalculateDone(model: Model) = getModelState(model.prefixedId).recalculateDone()
    def revalidateDone(model: Model)  = getModelState(model.prefixedId).revalidateDone()

    def refreshStart() {
        inRefresh = true
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

        for (modelState ← modelStates.values)
            modelState.refreshDone()

        RefreshState.refreshDone()

        mipUpdateCount = 0
        mipXPathOptimizedCount = 0

        inRefresh = false
    }


    def bindingUpdateStart() {
        inBindingUpdate = true
    }

    def bindingUpdateDone() {
        inBindingUpdate = false
    }

    def afterInitialResponse() {
        outputLHHAItemsetStats()
    }

    def beforeUpdateResponse() {
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

    def afterUpdateResponse() {
        outputLHHAItemsetStats()
    }

    def notifyComputeLHHA(): Unit = lhhaEvaluationCount += 1
    def notifyOptimizeLHHA(): Unit = lhhaOptimizedCount += 1

    def notifyComputeItemset(): Unit = itemsetEvaluationCount += 1
    def notifyOptimizeItemset(): Unit = itemsetOptimizedCount += 1

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

        RefreshState.changeset += instance → PathMapXPathAnalysis.getInternalPath(namespaces, path)
    }

    private case class UpdateResult(requireUpdate: Boolean, savedEvaluations: Int)
    private val MustUpdateResultOne     = UpdateResult(requireUpdate = true,  savedEvaluations = 1)
    private val MustUpdateResultNA      = UpdateResult(requireUpdate = true,  savedEvaluations = 0)
    private val MustNotUpdateResultZero = UpdateResult(requireUpdate = false, savedEvaluations = 0)

    def requireBindingUpdate(controlPrefixedId: String): Boolean = {

        assert(inRefresh || inBindingUpdate)

        val cached = RefreshState.modifiedBindingCacheForRepeats.get(controlPrefixedId)
        val updateResult: UpdateResult =
            cached match {
                case Some(result) ⇒ result
                case None ⇒
                    val control = containingDocument.getStaticOps.getControlAnalysisOption(controlPrefixedId).get
                    val tempResult = control.getBindingAnalysis match {
                        case None ⇒
                            // Control does not have an XPath binding
                            MustNotUpdateResultZero
                        case Some(analysis) if ! analysis.figuredOutDependencies ⇒
                            // Binding dependencies are unknown
                            MustUpdateResultOne
                        case Some(analysis) ⇒
                            // Binding dependencies are known
                            UpdateResult(
                                analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsBinding(RefreshState.changeset),
                                control.bindingXPathEvaluations)
                    }

                    if (tempResult.requireUpdate && logger.isDebugEnabled)
                        logger.logDebug("dependencies", "binding requires update",
                                Array("prefixed id", controlPrefixedId, "XPath", control.getBindingAnalysis.get.xpathString): _*)

                    if (control.isWithinRepeat)
                        RefreshState.modifiedBindingCacheForRepeats += controlPrefixedId → tempResult

                    tempResult
            }

        if (updateResult.requireUpdate)
            RefreshState.bindingUpdateCount += 1
        else
            // Update not required
            RefreshState.bindingXPathOptimizedCount += updateResult.savedEvaluations

        updateResult.requireUpdate
    }

    def requireValueUpdate(controlPrefixedId: String): Boolean = {

        assert(inRefresh || inBindingUpdate)

        val cached = RefreshState.modifiedValueCacheForRepeats.get(controlPrefixedId)
        val (updateResult, valueAnalysis) =
            cached match {
                case Some(result) ⇒ (result, if (result.requireUpdate) containingDocument.getStaticOps.getControlAnalysisOption(controlPrefixedId).get.getValueAnalysis else null)
                case None ⇒ {
                    val control = containingDocument.getStaticOps.getControlAnalysisOption(controlPrefixedId).get
                    val tempValueAnalysis = control.getValueAnalysis
                    val tempUpdateResult = tempValueAnalysis match {
                        case None ⇒
                            // Control does not have a value
                            MustUpdateResultNA//TODO: should be able to return false here; change once markDirty is handled better
                        case Some(analysis) if ! analysis.figuredOutDependencies ⇒
                            // Value dependencies are unknown
                            MustUpdateResultNA
                        case Some(analysis) ⇒
                            // Value dependencies are known
                            UpdateResult(
                                analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsValue(RefreshState.changeset),
                                if (control.value.isDefined) 1 else 0)
                    }
                    if (tempUpdateResult.requireUpdate && tempValueAnalysis.isDefined && logger.isDebugEnabled)
                        logger.logDebug("dependencies", "value requires update",
                                Array("prefixed id", controlPrefixedId, "XPath", tempValueAnalysis.get.xpathString): _*)

                    if (control.isWithinRepeat)
                        RefreshState.modifiedValueCacheForRepeats += controlPrefixedId → tempUpdateResult

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

        assert(inRefresh || inBindingUpdate) // LHHA is evaluated lazily typically outside of refresh, but LHHA invalidation takes place during refresh

        val control = (
            containingDocument.getStaticOps.getControlAnalysisOption(controlPrefixedId)
            flatMap collectByErasedType[StaticLHHASupport]
            getOrElse (throw new OXFException(s"Control $controlPrefixedId not found or doesn't support LHHA"))
        )

        // NOTE: No side-effects except for stats
        def requireUpdate = {

            val analyses = (
                control.lhhaValueAnalyses(lhhaName)
                ensuring (_.nonEmpty, s"Control $controlPrefixedId doesn't have LHHA $lhhaName")
            )

            def requireUpdateForAnalysis(analysis: XPathAnalysis) =
                analysis match {
                    case analysis if ! analysis.figuredOutDependencies ⇒ // dependencies are unknown
                        lhhaUnknownDependencies += 1
                        true
                    case analysis ⇒ // dependencies are known
                        val result = analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsValue(RefreshState.changeset)
                        if (result) lhhaHitCount += 1 else lhhaMissCount += 1
                        result
                }

            // Not the most optimal, but for now require an update if any has changed
            analyses exists requireUpdateForAnalysis
        }

        if (control.isWithinRepeat)
            RefreshState.modifiedLHHACacheForRepeats.getOrElseUpdate(controlPrefixedId, requireUpdate)
        else
            requireUpdate
    }

    def requireItemsetUpdate(controlPrefixedId: String): Boolean = {

        assert(inRefresh || inBindingUpdate)

        val control = (
            containingDocument.getStaticOps.getControlAnalysisOption(controlPrefixedId)
            flatMap collectByErasedType[SelectionControlTrait]
            getOrElse (throw new OXFException(s"Control $controlPrefixedId not found or is not a selection control"))
        )

        def requireUpdate =
            control.getItemsetAnalysis match {
                case Some(analysis) if ! analysis.figuredOutDependencies ⇒ // dependencies are unknown
                    itemsetUnknownDependencies += 1
                    true
                case Some(analysis) ⇒ // dependencies are known
                    val result = analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsValue(RefreshState.changeset)
                    if (result) itemsetHitCount += 1 else itemsetMissCount += 1
                    result
                case None ⇒
                    throw new IllegalStateException("Itemset not analyzed")
            }

        if (control.isWithinRepeat)
            RefreshState.modifiedItemsetCacheForRepeats.getOrElseUpdate(controlPrefixedId, requireUpdate)
        else
            requireUpdate
    }

    def hasAnyCalculationBind(model: Model, instancePrefixedId: String) =
        ! model.figuredAllBindRefAnalysis || model.computedBindExpressionsInstances.contains(instancePrefixedId)

    def hasAnyValidationBind(model: Model, instancePrefixedId: String) =
        ! model.figuredAllBindRefAnalysis || model.validationBindInstances.contains(instancePrefixedId)

    def requireModelMIPUpdate(model: Model, bind: StaticBind, mipName: String, level: ValidationLevel): Boolean = {

        // TODO: cache must store by MIP to optimize xf:bind/@ref over multiple nodes

        // Get constraints by the level specified
        val mips = mipName match {
            case Model.Constraint.name ⇒ bind.constraintsByLevel.getOrElse(level, Nil)
            case Model.Type.name       ⇒ bind.typeMIPOpt.toList
            case _                     ⇒ bind.getXPathMIPs(mipName)
        }

        val modelState = getModelState(model.prefixedId)

        def resultForMIP(mip: StaticBind#MIP) =
            if (modelState.isMIPInitiallyDirty(mip)) {
                // We absolutely must evaluate the MIP
                MustUpdateResultOne
            } else  {
                // Check MIP dependencies for XPath and type MIPs

                // Special case for type which is not an XPath expression
                // We don't check whether we need to update the type MIP, since it is constant, but we check whether
                // the value to type check has changed.
                val valueAnalysis = mip match {
                    case xpathMIP: StaticBind#XPathMIP ⇒ Some(xpathMIP.analysis)
                    case _: StaticBind#TypeMIP         ⇒ bind.getValueAnalysis
                    case _                             ⇒ throw new IllegalStateException("Expecting XPath MIP or type MIP")
                }

                def dependsOnOtherModel(analysis: XPathAnalysis) = analysis.dependentModels exists (_ != model.prefixedId)

                val updateResult = valueAnalysis match {
                    case Some(analysis) if ! analysis.figuredOutDependencies || dependsOnOtherModel(analysis) ⇒
                        // Value dependencies are unknown OR we depend on another model
                        // A this time, if we depend on another model, we have to update because we don't have
                        // the other model's dependencies reliably available, e.g. if the other model has
                        // already done a recalculate, its dependencies are cleared.
                        MustUpdateResultOne
                    case Some(analysis) ⇒
                        // Value dependencies are known
                        UpdateResult(analysis.intersectsValue(if (mip.isValidateMIP) modelState.revalidateChangeset else modelState.recalculateChangeset), 1)
                    case _ ⇒
                        throw new IllegalStateException(s"No value analysis found for xf:bind with name = $mipName")
                }

                if (updateResult.requireUpdate && logger.isDebugEnabled)
                    logger.logDebug("dependencies", "MIP requires update",
                        Array("prefixed id", bind.prefixedId, "MIP name", mip.name, "XPath", valueAnalysis.get.xpathString): _*)

                updateResult
            }

        // Stats and return value
        // Require an update of all MIPs of the given name/level if at least one dependency fails
        mips.iterator map resultForMIP find (_.requireUpdate) match {
            case Some(firstUpdateResult) ⇒
                mipUpdateCount += mips.size
                true
            case None ⇒
                mipXPathOptimizedCount += mips.size // NOTE: stats a bit rough here
                false
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
            ancestorOrSelf.tail map (node ⇒ node.getNodeKind match {
                case ELEMENT_NODE ⇒ node.getFingerprint
                case ATTRIBUTE_NODE ⇒ "@" + node.getFingerprint
            })
        else
            Nil) mkString "/"
    }
}