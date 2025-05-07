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

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.StaticXPath.VirtualNodeType
import org.orbeon.oxf.xforms.*
import org.orbeon.oxf.xforms.analysis.controls.*
import org.orbeon.oxf.xforms.analysis.model.{MipName, Model, StaticBind}
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.analysis.model.ValidationLevel

import scala.collection.mutable as m


class PathMapXPathDependencies(
  containingDocument: XFormsContainingDocument
) extends XPathDependencies {

  import PathMapXPathDependencies._

  private implicit val logger: IndentedLogger = containingDocument.getIndentedLogger("dependencies")

  // Represent the state of changes to a model
  private class ModelState(val modelKey: ModelOrInstanceKey, val model: XFormsModel) {

    var hasStructuralChanges = false

    var calculateMIPsEvaluatedOnce = false  // start dirty
    var validateMIPsEvaluatedOnce  = false  // start dirty

    // Meaning of a change: "the string value of the node has changed"
    var recalculateChangeset = new MapSet[ModelOrInstanceKey, String]   // changeset for recalculate MIPs
    var revalidateChangeset  = recalculateChangeset                     // changeset for revalidate MIPs

    def markValueChangedForTests(instance: XFormsInstance, path: String): Unit = {
      if (! hasStructuralChanges) {

        val instanceKey  = ModelOrInstanceKey(instance)
        val instancePath = instanceKey -> path

        recalculateChangeset += instancePath
        if (revalidateChangeset ne recalculateChangeset)
          revalidateChangeset += instancePath // also add to revalidate changeset if it is different

        RefreshState.instancesByKey   += instanceKey -> instance
        RefreshState.refreshChangeset += instancePath
      }
    }

    def markValueChanged(node: om.NodeInfo): Unit = {
      // Only care about path changes if there is no structural change for this model, since structural changes
      // for now disable any more subtle path-based check.
      if (! hasStructuralChanges) {

        // Create instance/path combo
        val instance = containingDocument.instanceForNodeOpt(node).orNull // TODO: `Option`

        def processNode(n: om.NodeInfo): Unit = {
          val path = SaxonUtils.createFingerprintedPath(n)

          val instanceKey  = ModelOrInstanceKey(instance)
          val instancePath = instanceKey -> path

          // Update model and view changesets
          recalculateChangeset += instancePath
          if (revalidateChangeset ne recalculateChangeset)
            revalidateChangeset += instancePath // also add to revalidate changeset if it is different

          RefreshState.instancesByKey   += instanceKey -> instance
          RefreshState.refreshChangeset += instancePath

          // Add parent elements as well. The idea is that if the string value of /a/b/c changed, then the
          // string value of /a/b did as well, and so did /a's.
          // This adds more entries to the changeset, but handles cases such as detecting changes impacting
          // the string() or serialize() functions.
          n parent * foreach processNode
        }

        processNode(node)
      }
    }

    def markStructuralChange(): Unit = {

      // Update model and view information
      hasStructuralChanges = true
      RefreshState.structuralChangeModelKeys += modelKey

      markBindsDirty()
    }

    def rebuildDone(): Unit = {

      hasStructuralChanges = false

      markBindsDirty()
    }

    private def markBindsDirty(): Unit = {
      calculateMIPsEvaluatedOnce = false
      validateMIPsEvaluatedOnce = false

      // Changesets won't be used
      recalculateChangeset.clear()
      revalidateChangeset = recalculateChangeset
    }

    // Say that for this model, calculate binds are clean and can be checked for modifications based on value changes
    def recalculateDone(): Unit = {
      calculateMIPsEvaluatedOnce = true
      recalculateChangeset = clearChangeset(recalculateChangeset, revalidateChangeset)
    }

    // Say that for this model, validate binds are clean and can be checked for modifications based on value changes
    def revalidateDone(): Unit = {
      validateMIPsEvaluatedOnce = true
      revalidateChangeset = clearChangeset(revalidateChangeset, recalculateChangeset)
    }

    // Return an empty changeset, trying to point to the empty right changeset if possible
    // This is so that we can try to avoid adding changes to both changesets later
    private def clearChangeset(left: MapSet[ModelOrInstanceKey, String], right: MapSet[ModelOrInstanceKey, String]) =
      if (right.isEmpty)
        right
      else if (left ne right) {
        left.clear()
        left
      } else
        new MapSet[ModelOrInstanceKey, String]

    def refreshDone(): Unit = ()

    def isMIPInitiallyDirty(mip: StaticBind.MIP): Boolean =
      mip.isValidateMIP && ! validateMIPsEvaluatedOnce || ! mip.isValidateMIP && ! calculateMIPsEvaluatedOnce
  }

  // Keep state related to the view
  private object RefreshState {

    val modelStates = new m.HashMap[ModelOrInstanceKey, ModelState]

    def getOrCreateModelState(model: XFormsModel): ModelState = {
      val modelKey = ModelOrInstanceKey(model)
      modelStates.getOrElseUpdate(modelKey, new ModelState(modelKey, model))
    }

    // Used between refresh/binding update start/done
    var inRefresh = false
    var inBindingUpdate = false

    // Structural changes
    val structuralChangeModelKeys = new m.HashSet[ModelOrInstanceKey]

    // Modified paths by instance key
    val refreshChangeset = new MapSet[ModelOrInstanceKey, String]
    val instancesByKey   = m.Map[ModelOrInstanceKey, XFormsInstance]()

    // Caches to speedup checks on repeated items
    val modifiedBindingCacheForRepeats = new m.HashMap[RepeatCacheKey, UpdateResult]
    val modifiedValueCacheForRepeats   = new m.HashMap[RepeatCacheKey, UpdateResult]
    val modifiedLHHACacheForRepeats    = new m.HashMap[RepeatCacheKey, Boolean]
    val modifiedItemsetCacheForRepeats = new m.HashMap[RepeatCacheKey, Boolean]

    // Statistics
    var bindingUpdateCount: Int = 0
    var valueUpdateCount: Int = 0

    var bindingXPathOptimizedCount: Int = 0
    var valueXPathOptimizedCount: Int = 0

    private def compareWithPredicate(
      firstWithPrefixedIds   : collection.Iterable[String],
      secondWithInstanceKeys : collection.Iterable[ModelOrInstanceKey],
      predicate              : ModelOrInstanceKey => Boolean
    ): Boolean =
      firstWithPrefixedIds.exists { firstPrefixedId =>
        secondWithInstanceKeys.exists { secondInstanceKey =>
          secondInstanceKey.prefixedId == firstPrefixedId && predicate(secondInstanceKey)
        }
      }

    private def setsHaveIntersection(
      first  : collection.Set[String],
      second : collection.Set[String]
    ): Boolean =
      first.exists(second.contains)

    private def searchMatchesForInstances(
      controlIndexes         : Array[Int],
      firstWithPrefixedIds   : MapSet[String, String],
      secondWithInstanceKeys : MapSet[ModelOrInstanceKey, String]
    ): Boolean =
      compareWithPredicate(
        firstWithPrefixedIds.map.keys,
        secondWithInstanceKeys.map.keys,
        instanceKey => {

          val matchesRepeatIterations =
            controlIndexes.isEmpty ||
            controlIndexes.startsWith(XFormsId.getEffectiveIdSuffixParts(instancesByKey(instanceKey).effectiveId))

          matchesRepeatIterations &&
            setsHaveIntersection(
              firstWithPrefixedIds.map(instanceKey.prefixedId),
              secondWithInstanceKeys.map(instanceKey)
            )
        }
      )

    def intersectsStructuralChangeModel(controlIndexes: Array[Int], analysis: XPathAnalysis): Boolean = {

      val controlIsWithinRepeat = controlIndexes.nonEmpty

      val touchedModelsEffectiveIds = structuralChangeModelKeys

      // Assumption: a given analysis typically has only one dependent model
      compareWithPredicate(
        analysis.dependentModels,
        touchedModelsEffectiveIds,
        modelKey => {
          ! controlIsWithinRepeat ||
          // If there is a match we know that the control depends on the given model, and we
          // know that this means the control statically share the model's ancestor repeats,
          // because it's not possible to cross an XBL boundary as models are always in a shadow
          // tree's inner scope. So here we want the shared repeat ancestors' iterations to match.
          controlIndexes.startsWith(XFormsId.getEffectiveIdSuffixParts(modelStates(modelKey).model.effectiveId))
        }
      )
    }

    def intersectsBinding(
      controlIndexes  : Array[Int],
      bindingAnalysis : XPathAnalysis,
      changes         : MapSet[ModelOrInstanceKey, String]
    ): Boolean =
      searchMatchesForInstances(
        controlIndexes,
        bindingAnalysis.valueDependentPaths,
        changes
      )

    def intersectsValue(
      controlIndexes  : Array[Int],
      bindingAnalysis : XPathAnalysis,
      changes         : MapSet[ModelOrInstanceKey, String]
    ): Boolean =
      intersectsBinding(controlIndexes, bindingAnalysis, refreshChangeset) ||
        searchMatchesForInstances(
          controlIndexes,
          bindingAnalysis.returnablePaths,
          changes
        )

    def refreshDone(): Unit = {
      structuralChangeModelKeys.clear()
      refreshChangeset.clear()

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

  private object Stats {
    var mipUpdateCount             = 0
    var mipXPathOptimizedCount     = 0

    var lhhaEvaluationCount        = 0
    var lhhaOptimizedCount         = 0
    var lhhaUnknownDependencies    = 0
    var lhhaMissCount              = 0
    var lhhaHitCount               = 0

    var itemsetEvaluationCount     = 0
    var itemsetOptimizedCount      = 0
    var itemsetUnknownDependencies = 0
    var itemsetMissCount           = 0
    var itemsetHitCount            = 0
  }

  import RefreshState._
  import Stats._

  def markValueChanged(model: XFormsModel, nodeInfo: om.NodeInfo): Unit = {

    // Caller must only call this for a mutable node belonging to the given model
    require(nodeInfo.isInstanceOf[VirtualNodeType])
    require(model.findInstanceForNode(nodeInfo) exists (_.model eq model))

    getOrCreateModelState(model).markValueChanged(nodeInfo)
  }

  def markStructuralChange(model: XFormsModel, instanceOpt: Option[XFormsInstance]): Unit =
    getOrCreateModelState(model).markStructuralChange()

  def rebuildDone    (model: XFormsModel): Unit = getOrCreateModelState(model).rebuildDone()
  def recalculateDone(model: XFormsModel): Unit = getOrCreateModelState(model).recalculateDone()
  def revalidateDone (model: XFormsModel): Unit = getOrCreateModelState(model).revalidateDone()

  def modelDestruct(model: XFormsModel): Unit = {

    // Remove all references to concrete models and instances
    modelStates -= getOrCreateModelState(model).modelKey

    for (instance <- model.instancesIterator)
      instancesByKey -= ModelOrInstanceKey(instance)
  }

  def refreshStart(): Unit =
    inRefresh = true

  def refreshDone(): Unit = {

    debug(
      "refresh done",
      List(
        "bindings updated"        -> bindingUpdateCount.toString,
        "values updated"          -> valueUpdateCount.toString,
        "MIPs updated"            -> mipUpdateCount.toString,
        "Binding XPath optimized" -> bindingXPathOptimizedCount.toString,
        "Value XPath optimized"   -> valueXPathOptimizedCount.toString,
        "MIP XPath optimized"     -> mipXPathOptimizedCount.toString,
        "Total XPath optimized"   -> (bindingXPathOptimizedCount + valueXPathOptimizedCount + mipXPathOptimizedCount).toString
      )
    )

    for (modelState <- modelStates.values)
      modelState.refreshDone()

    RefreshState.refreshDone()

    mipUpdateCount = 0
    mipXPathOptimizedCount = 0

    inRefresh = false
  }


  def bindingUpdateStart(): Unit =
    inBindingUpdate = true

  def bindingUpdateDone(): Unit =
    inBindingUpdate = false

  def afterInitialResponse(): Unit =
    outputLHHAItemsetStats()

  def beforeUpdateResponse(): Unit = {
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

  def afterUpdateResponse(): Unit =
    outputLHHAItemsetStats()

  def notifyComputeLHHA(): Unit = lhhaEvaluationCount += 1
  def notifyOptimizeLHHA(): Unit = lhhaOptimizedCount += 1

  def notifyComputeItemset(): Unit = itemsetEvaluationCount += 1
  def notifyOptimizeItemset(): Unit = itemsetOptimizedCount += 1

  private def outputLHHAItemsetStats(): Unit = {
      debug("summary after response",
        List(
          "LHHA evaluations"             -> lhhaEvaluationCount.toString,
          "LHHA optimized"               -> lhhaOptimizedCount.toString,
          "LHHA unknown dependencies"    -> lhhaUnknownDependencies.toString,
          "LHHA intersections"           -> lhhaHitCount.toString,
          "LHHA disjoints"               -> lhhaMissCount.toString,
          "Itemset evaluations"          -> itemsetEvaluationCount.toString,
          "Itemset optimized"            -> itemsetOptimizedCount.toString,
          "Itemset unknown dependencies" -> itemsetUnknownDependencies.toString,
          "Itemset intersections"        -> itemsetHitCount.toString,
          "Itemset disjoints"            -> itemsetMissCount.toString
        )
      )
  }

  // For unit tests only
  def markStructuralChangeTest(model: XFormsModel): Unit =
    getOrCreateModelState(model).markStructuralChange()

  // For unit tests only
  def markValueChangedTest(instance: XFormsInstance, namespaces: Map[String, String], path: String): Unit =
    getOrCreateModelState(instance.model)
      .markValueChangedForTests(instance, SaxonUtils.getInternalPathForDisplayPath(namespaces, path))

  private case class UpdateResult(requireUpdate: Boolean, savedEvaluations: Int)
  private val MustUpdateResultOne     = UpdateResult(requireUpdate = true,  savedEvaluations = 1)
  private val MustUpdateResultNA      = UpdateResult(requireUpdate = true,  savedEvaluations = 0)
  private val MustNotUpdateResultZero = UpdateResult(requireUpdate = false, savedEvaluations = 0)

  private def buildRepeatResultCacheKey(
    control        : ElementAnalysis,
    analyses       : List[XPathAnalysis],
    controlIndexes : Array[Int]
  ): Option[RepeatCacheKey] = {
    if (control.isWithinRepeat) {
      analyses match {
        case analyses if analyses.nonEmpty && (analyses forall (_.figuredOutDependencies)) =>

          val allDependentModelsPrefixedIdsIt = analyses.iterator flatMap (_.dependentModels.iterator)

          // TODO: This could be cached in the ElementAnalysis. Would need for binding, value, LHHA, itemset.
          val maxDependentModelDepth =
            if (allDependentModelsPrefixedIdsIt.nonEmpty)
              allDependentModelsPrefixedIdsIt
                .map(modelPrefixedId =>
                  containingDocument
                    .staticOps
                    .getControlAnalysis(modelPrefixedId)
                    .ancestorRepeatsAcrossParts
                    .size
                )
                .max
            else
              0

          Some(
            RepeatCacheKey(
              control.prefixedId,
              controlIndexes.take(maxDependentModelDepth).toList
            )
          )
        case _ =>
          None
      }
    } else
      None
  }

  def requireBindingUpdate(control: ElementAnalysis, controlIndexes: Array[Int]): Boolean = {

    assert(inRefresh || inBindingUpdate)

    val resultCacheKey = buildRepeatResultCacheKey(control, control.bindingAnalysis.toList, controlIndexes)

    val cached = resultCacheKey flatMap modifiedBindingCacheForRepeats.get
    val updateResult: UpdateResult =
      cached match {
        case Some(result) =>
          result
        case None =>
          val tempResult = control.bindingAnalysis match {
            case None if control.hasBinding =>
              // Control has an XPath binding AND binding dependencies are unknown
              MustUpdateResultNA
            case Some(analysis) if ! analysis.figuredOutDependencies =>
              // Binding dependencies are unknown (should be the same as above)
              MustUpdateResultNA
            case None =>
              // Control does not have an XPath binding
              MustNotUpdateResultZero
            case Some(analysis) =>
              // Binding dependencies are known
              UpdateResult(
                intersectsStructuralChangeModel(controlIndexes, analysis) ||
                  intersectsBinding(controlIndexes, analysis, refreshChangeset),
                control.bindingXPathEvaluations
              )
          }

          if (tempResult.requireUpdate)
            debug(
              "binding requires update",
              List(
                "effective id" -> controlIndexes.mkString("Array(", ", ", ")"),
                "XPath"        -> control.bindingAnalysis.map(_.xpathString).orNull // XPath can be missing in offline
              )
            )

          resultCacheKey foreach { key =>
            modifiedBindingCacheForRepeats += key -> tempResult
          }

          tempResult
      }

    if (updateResult.requireUpdate)
      bindingUpdateCount += 1
    else
      bindingXPathOptimizedCount += updateResult.savedEvaluations

    updateResult.requireUpdate
  }

  def requireValueUpdate(control: ElementAnalysis, controlIndexes: Array[Int]): Boolean = {

    assert(inRefresh || inBindingUpdate)

    val resultCacheKey = buildRepeatResultCacheKey(control, control.valueAnalysis.toList, controlIndexes)

    val cached = resultCacheKey flatMap modifiedValueCacheForRepeats.get
    val (updateResult, valueAnalysis) =
      cached match {
        case Some(result) =>
          (
            result,
            if (result.requireUpdate)
              control.valueAnalysis
            else
              null
          )
        case None =>
          val tempValueAnalysis = control.valueAnalysis
          val tempUpdateResult = tempValueAnalysis match {
            case None =>
              // Control does not have a value OR dependencies could not be figured out
              MustUpdateResultNA//TODO: should be able to return false here; change once markDirty is handled better
            case Some(analysis) if ! analysis.figuredOutDependencies =>
              // Value dependencies are unknown
              MustUpdateResultNA
            case Some(analysis) =>
              // Value dependencies are known
              UpdateResult(
                intersectsStructuralChangeModel(controlIndexes, analysis) ||
                  intersectsValue(controlIndexes, analysis, refreshChangeset),
                if (control.value.isDefined) 1 else 0)
          }
          if (tempUpdateResult.requireUpdate && tempValueAnalysis.isDefined)
            debug(
              "value requires update",
              List(
                "effective id" -> controlIndexes.mkString("Array(", ", ", ")"),
                "XPath"        -> tempValueAnalysis.map(_.xpathString).orNull // XPath can be missing in offline
              )
            )

          resultCacheKey foreach { key =>
            modifiedValueCacheForRepeats += key -> tempUpdateResult
          }

          (tempUpdateResult, tempValueAnalysis)
      }

    // TODO: see above, check on valueAnalysis only because non-value controls still call this method
    if (updateResult.requireUpdate && valueAnalysis.isDefined)
      valueUpdateCount += 1
    else
      // Update not required
      valueXPathOptimizedCount += updateResult.savedEvaluations

    updateResult.requireUpdate
  }

  def requireLHHAUpdate(control: ElementAnalysis, lhha: LHHA, controlIndexes: Array[Int]): Boolean = {

    // LHHA is evaluated lazily typically outside of refresh, but LHHA invalidation takes place during refresh
    assert(inRefresh || inBindingUpdate)

    val analysesOrEmpty = {

      val lhhaControl = {
        collectByErasedType[StaticLHHASupport](control) getOrElse
        (throw new OXFException(s"Control ${controlIndexes.mkString("Array(", ", ", ")")} not found or doesn't support LHHA"))
      }

      lhhaControl.lhhaValueAnalyses(lhha)
    }

    // NOTE: No side effects except for stats
    def requireUpdate = {

      def requireUpdateForAnalysis(analysis: XPathAnalysis) =
        analysis match {
          case analysis if ! analysis.figuredOutDependencies => // dependencies are unknown
            lhhaUnknownDependencies += 1
            true
          case analysis => // dependencies are known
            val result =
              intersectsStructuralChangeModel(controlIndexes, analysis) ||
                intersectsValue(controlIndexes, analysis, refreshChangeset)
            if (result) lhhaHitCount += 1 else lhhaMissCount += 1
            result
        }

      // Not the most optimal, but for now require an update if any has changed
      analysesOrEmpty match {
        case Nil =>
          lhhaUnknownDependencies += 1
          true
        case analyses =>
          analyses exists requireUpdateForAnalysis
      }
    }

    buildRepeatResultCacheKey(control, analysesOrEmpty, controlIndexes) match {
      case Some(key) => modifiedLHHACacheForRepeats.getOrElseUpdate(key, requireUpdate)
      case None      => requireUpdate
    }
  }

  def requireItemsetUpdate(control: SelectionControlTrait, controlIndexes: Array[Int]): Boolean = {

    assert(inRefresh || inBindingUpdate)

    def requireUpdate =
      control.itemsetAnalysis match {
        case None =>
          itemsetUnknownDependencies += 1
          true
        case Some(analysis) if ! analysis.figuredOutDependencies => // dependencies are unknown
          itemsetUnknownDependencies += 1
          true
        case Some(analysis) => // dependencies are known
          val result =
            intersectsStructuralChangeModel(controlIndexes, analysis) ||
              intersectsValue(controlIndexes, analysis, refreshChangeset)
          if (result) itemsetHitCount += 1 else itemsetMissCount += 1
          result
      }

    buildRepeatResultCacheKey(control, control.itemsetAnalysis.toList, controlIndexes) match {
      case Some(key) => modifiedItemsetCacheForRepeats.getOrElseUpdate(key, requireUpdate)
      case None      => requireUpdate
    }
  }

  def hasAnyCalculationBind(model: Model, instancePrefixedId: String): Boolean =
    ! model.figuredAllBindRefAnalysis || model.computedBindExpressionsInstances.contains(instancePrefixedId)

  def hasAnyValidationBind(model: Model, instancePrefixedId: String): Boolean =
    ! model.figuredAllBindRefAnalysis || model.validationBindInstances.contains(instancePrefixedId)

  def requireModelMIPUpdate(model: XFormsModel, bind: StaticBind, mip: MipName, level: ValidationLevel): Boolean = {

    // TODO: cache must store by MIP to optimize xf:bind/@ref over multiple nodes

    // Get constraints by the level specified
    val mips = mip match {
      case MipName.Constraint      => bind.constraintsByLevel.getOrElse(level, Nil)
      case MipName.Type            => bind.typeMIPOpt.toList
      case MipName.Whitespace      => bind.nonPreserveWhitespaceMIPOpt.toList
      case xpathMip: MipName.XPath => bind.getXPathMIPs(xpathMip)
    }

    val modelState = getOrCreateModelState(model)

    def resultForMIP(mip: StaticBind.MIP): Iterator[UpdateResult] =
      if (modelState.isMIPInitiallyDirty(mip)) {
        // We absolutely must evaluate the MIP
        Iterator.single(MustUpdateResultOne)
      } else  {
        // Check MIP dependencies for XPath and type MIPs

        // Special case for type which is not an XPath expression
        // We don't check whether we need to update the type MIP, since it is constant, but we check whether
        // the value to type check has changed.
        val valueAnalysisIt = mip match {
          case xpathMIP: StaticBind.XPathMIP
            if xpathMIP.name == MipName.Calculate || xpathMIP.name == MipName.Default =>
            Iterator(Some(xpathMIP.analysis), bind.valueAnalysis)
          case xpathMIP: StaticBind.XPathMIP => Iterator.single(Some(xpathMIP.analysis))
          case _: StaticBind.TypeMIP         => Iterator.single(bind.valueAnalysis)
          case _: StaticBind.WhitespaceMIP   => Iterator.single(bind.valueAnalysis)
          case _                             => throw new IllegalStateException(s"unexpected MIP `${mip.name}`")
        }

        def dependsOnOtherModel(analysis: XPathAnalysis) =
          analysis.dependentModels exists (_ != model.getPrefixedId)

        def logDebug(valueAnalysis: Option[XPathAnalysis], updateResult: UpdateResult): Unit = {
          if (updateResult.requireUpdate)
            debug(
              "MIP requires update",
              List(
                "prefixed id" -> bind.prefixedId,
                "MIP name"    -> mip.name.aName.qualifiedName,
                "XPath"       -> valueAnalysis.map(_.xpathString).orNull // XPath can be missing in offline
              )
            )
        }

        val updateResult = valueAnalysisIt.map {
          case someAnalysis @ Some(analysis) if ! analysis.figuredOutDependencies || dependsOnOtherModel(analysis) =>
            // Value dependencies are unknown OR we depend on another model
            // A this time, if we depend on another model, we have to update because we don't have
            // the other model's dependencies reliably available, e.g. if the other model has
            // already done a recalculate, its dependencies are cleared.
            MustUpdateResultOne |!> (logDebug(someAnalysis, _))
          case someAnalysis @ Some(analysis) =>
            // Value dependencies are known
            UpdateResult(
              intersectsValue(
                XFormsId.getEffectiveIdSuffixParts(model.effectiveId),
                analysis,
                if (mip.isValidateMIP) modelState.revalidateChangeset else modelState.recalculateChangeset
              ),
              1
            ) |!> (logDebug(someAnalysis, _))
          case someAnalysis @ None =>
            MustUpdateResultOne |!> (logDebug(someAnalysis, _))
        }

        updateResult
      }

    // Stats and return value
    // Require an update of all MIPs of the given name/level if at least one dependency fails
    mips.iterator.flatMap(resultForMIP).find(_.requireUpdate) match {
      case Some(_) =>
        mipUpdateCount += mips.size
        true
      case None =>
        mipXPathOptimizedCount += mips.size // NOTE: stats a bit rough here
        false
    }
  }
}

private object PathMapXPathDependencies {

  case class RepeatCacheKey(prefixedId: String, commonIndexes: List[Int])

  // We use this custom key because we cannot index by effective id as effective ids can be updated when
  // iterations move. The sequence number on the other hand doesn't change for a concrete model or instance
  // for its lifetime.
  case class ModelOrInstanceKey(prefixedId: String, sequence: Int)

  object ModelOrInstanceKey {

    def apply(model: XFormsModel): ModelOrInstanceKey =
      ModelOrInstanceKey(model.getPrefixedId, model.sequenceNumber)

    def apply(instance: XFormsInstance): ModelOrInstanceKey =
      ModelOrInstanceKey(instance.getPrefixedId, instance.model.sequenceNumber)
  }
}