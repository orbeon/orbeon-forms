/**
 *  Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.model

import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.{XXFormsInvalidEvent, XXFormsRebuildStartedEvent, XXFormsRecalculateStartedEvent, XXFormsValidEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector, XFormsEvent}
import org.orbeon.saxon.om

import scala.collection.mutable as m

trait XFormsModelRebuildRecalculateRevalidate {

  selfModel: XFormsModel =>

  import Private._

  val deferredActionContext = new DeferredActionContext(container)

  def schemaValidator: XFormsModelSchemaValidator = _schemaValidator
  def hasSchema: Boolean = _schemaValidator.hasSchema

  def getSchemaURIs: Option[Array[String]] =
    hasSchema option _schemaValidator.getSchemaURIs

  def markStructuralChange(instanceOpt: Option[XFormsInstance], defaultsStrategy: DefaultsStrategy): Unit = {
    deferredActionContext.markStructuralChange(defaultsStrategy, instanceOpt map (_.getId))
    // NOTE: PathMapXPathDependencies doesn't yet make use of the `instance` parameter.
    containingDocument.xpathDependencies.markStructuralChange(selfModel, instanceOpt)
  }

  def markValueChange(nodeInfo: om.NodeInfo, isCalculate: Boolean): Unit = {
    // Set the flags
    deferredActionContext.markValueChange(isCalculate)
    // Notify dependencies of the change
    if (nodeInfo ne null)
      containingDocument.xpathDependencies.markValueChanged(selfModel, nodeInfo)
  }

  def doRebuildIfNeeded(): Unit =
    if (deferredActionContext.rebuild) {
      try {
        EventCollector.withBufferCollector { collector =>
          Dispatch.dispatchEvent(new XXFormsRebuildStartedEvent(selfModel), collector)
        }
        EventCollector.withBufferCollector { collector =>
          resetAndEvaluateVariables(collector)
        }
        bindsIfInstance match {
          case Some(binds) =>
            try {
              // NOTE: `contextStack.resetBindingContext(this)` called in `evaluateVariables()`
              binds.rebuild()
              // Controls may have @bind or bind() references, so we need to mark them as dirty. Will need dependencies for
              // controls to fix this.
              // TODO: Handle XPathDependencies
            } finally {
              // In case of fatal error above, we still set the appropriate flags to avoid serious state inconsistencies
              // https://github.com/orbeon/orbeon-forms/issues/6442
              deferredActionContext.markRecalculateRevalidate(deferredActionContext.defaultsStrategy, None)
              container.requireRefresh()
            }
          case None =>
        }
      } finally {
        // In case of fatal error above, we still set the appropriate flags to avoid serious state inconsistencies
        deferredActionContext.resetRebuild()
      }
      containingDocument.xpathDependencies.rebuildDone(selfModel)
    }

  // Recalculate and revalidate are a combined operation
  // See https://github.com/orbeon/orbeon-forms/issues/1650
  def doRebuildRecalculateRevalidateIfNeeded(): Unit = {
    // https://github.com/orbeon/orbeon-forms/issues/1660
    doRebuildIfNeeded()
    doOnlyRecalculateRevalidateIfNeeded()
  }

  // Recalculate and revalidate are a combined operation
  // See https://github.com/orbeon/orbeon-forms/issues/1650
  private def doOnlyRecalculateRevalidateIfNeeded(): Unit =
    if (deferredActionContext.recalculateRevalidate) {

      def performRecalculateRevalidate(): m.Set[String] =
        try {
          EventCollector.withBufferCollector { collector =>
            Dispatch.dispatchEvent(new XXFormsRecalculateStartedEvent(selfModel), collector)
          }
          EventCollector.withBufferCollector { collector =>
            resetAndEvaluateVariables(collector)
          }
          bindsIfInstance match {
            case Some(binds) =>
              EventCollector.withBufferCollector { collector =>
                  try {
                    doRecalculate(binds, deferredActionContext.defaultsStrategy, collector)
                    containingDocument.xpathDependencies.recalculateDone(selfModel)

                    val invalidInstanceEffectiveIds = m.LinkedHashSet[String]()
                    doRevalidateWithSchema(invalidInstanceEffectiveIds)
                    doRevalidateWithBinds(binds, invalidInstanceEffectiveIds, collector)

                    containingDocument.xpathDependencies.revalidateDone(selfModel)
                    invalidInstanceEffectiveIds
                  } finally {
                    for {
                      instanceId <- deferredActionContext.flaggedInstances
                      doc        <- getInstance(instanceId).underlyingDocumentOpt
                    } locally {
                      InstanceDataOps.clearRequireDefaultValueRecursively(doc)
                    }
                }
              }
            case None =>
              val invalidInstanceEffectiveIds = m.LinkedHashSet[String]()
              doRevalidateWithSchema(invalidInstanceEffectiveIds)
              containingDocument.xpathDependencies.revalidateDone(selfModel)
              invalidInstanceEffectiveIds
          }
        } finally {
          deferredActionContext.resetRecalculateRevalidate()
        }

      // Gather events to dispatch, at most one per instance, and only if validity has changed
      // NOTE: It is possible, with binds and the use of `xxf:instance()`, that some instances in
      // invalidInstances do not belong to this model. Those instances won't get events with the dispatching
      // algorithm below.
      def createAndCommitValidationEvents(invalidInstanceEffectiveIds: collection.Set[String]): List[XFormsEvent] = {

        val changedInstancesIt =
          for {
            instance           <- instancesIterator
            previouslyValid    = instance.valid
            newlyValid         = ! invalidInstanceEffectiveIds(instance.effectiveId)
            if previouslyValid != newlyValid
          } yield {
            instance.valid = newlyValid // side-effect!
            if (newlyValid) new XXFormsValidEvent(instance) else new XXFormsInvalidEvent(instance)
          }

        changedInstancesIt.toList
      }

      // We don't want to dispatch events while we are performing the actual recalculate/revalidate operation,
      // so we collect them and dispatch them altogether once everything is done.
      for (event <- createAndCommitValidationEvents(performRecalculateRevalidate()).iterator)
        Dispatch.dispatchEvent(event, EventCollector.ToReview)
    }

  def needRebuildRecalculateRevalidate: Boolean =
    deferredActionContext.rebuild || deferredActionContext.recalculateRevalidate

  // This is called in response to dispatching xforms-refresh to this model, whether using the xf:refresh
  // action or by dispatching the event by hand.

  // NOTE: If the refresh flag is not set, we do not call synchronizeAndRefresh() because that would only have the
  // side-effect of performing RRR on models, but  but not update the UI, which wouldn't make sense for xforms-refresh.
  // This said, is unlikely (impossible?) that the RRR flags would be set but not the refresh flag.
  // FIXME: See https://github.com/orbeon/orbeon-forms/issues/1650
  protected def doRefresh(): Unit =
    if (containingDocument.controls.isRequireRefresh)
      container.synchronizeAndRefresh()

  private object Private {

    lazy val _schemaValidator: XFormsModelSchemaValidator =
      new XFormsModelSchemaValidator(staticModel.element, indentedLogger) |!> (_.loadSchemas(containingDocument))

    def doRecalculate(binds: XFormsModelBinds, defaultsStrategy: DefaultsStrategy, collector: ErrorEventCollector): Unit =
      withDebug("performing recalculate", List("model" -> effectiveId)) {
        binds.applyDefaultAndCalculateBinds(defaultsStrategy, collector)
      }

    def doRevalidateWithBinds(binds: XFormsModelBinds, invalidInstanceEffectiveIds: m.Set[String], collector: ErrorEventCollector): Unit =
      withDebug("performing revalidate with binds", List("model" -> effectiveId)) {
        binds.applyValidationBinds(invalidInstanceEffectiveIds, collector)
      }

    def doRevalidateWithSchema(invalidInstanceEffectiveIds: m.Set[String]): Unit =
      if (hasSchema)
        withDebug("performing revalidate with schema", List("model" -> effectiveId)) {

          // Clear schema validation state
          // NOTE: This could possibly be moved to `rebuild()`, but we must be careful about the presence of a schema
          for {
            instance <- instancesIterator
            if instance.isSchemaValidation
          } locally {
            DataModel.visitElement(instance.rootElement, InstanceData.clearSchemaState)
          }

          for {
            instance <- instancesIterator
            if instance.isSchemaValidation                   // we don't support validating read-only instances
            if ! _schemaValidator.validateInstance(instance) // apply schema
          } locally {
            invalidInstanceEffectiveIds += instance.effectiveId
          }
      }

    def bindsIfInstance: Option[XFormsModelBinds] =
      if (instancesIterator.isEmpty)
        None
      else
        modelBindsOpt
  }
}
