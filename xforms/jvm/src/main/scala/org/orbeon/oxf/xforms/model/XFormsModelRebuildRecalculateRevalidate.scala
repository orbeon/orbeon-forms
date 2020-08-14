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

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.event.events.{XFormsRebuildEvent, XFormsRecalculateEvent, XXFormsInvalidEvent, XXFormsValidEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.saxon.om.NodeInfo

import scala.collection.{mutable => m}

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

  def markValueChange(nodeInfo: NodeInfo, isCalculate: Boolean): Unit = {
    // Set the flags
    deferredActionContext.markValueChange(isCalculate)
    // Notify dependencies of the change
    if (nodeInfo ne null)
      containingDocument.xpathDependencies.markValueChanged(selfModel, nodeInfo)
  }

  // NOP now that deferredActionContext is always created
  def startOutermostActionHandler(): Unit = ()

  def rebuildRecalculateRevalidateIfNeeded(): Unit = {
    // Process deferred behavior
    val currentDeferredActionContext = deferredActionContext

    // NOTE: We used to clear `deferredActionContext`, but this caused events to be dispatched in a different
    // order. So we are now leaving the flag as is, and waiting until they clear themselves.
    if (currentDeferredActionContext.rebuild)
      containingDocument.withOutermostActionHandler {
        Dispatch.dispatchEvent(new XFormsRebuildEvent(selfModel))
      }

    if (currentDeferredActionContext.recalculateRevalidate)
      containingDocument.withOutermostActionHandler {
        Dispatch.dispatchEvent(new XFormsRecalculateEvent(selfModel))
      }
  }

  def doRebuild(): Unit = {
    if (deferredActionContext.rebuild) {
      try {
        resetAndEvaluateVariables()
        bindsIfInstance foreach { binds =>
          // NOTE: contextStack.resetBindingContext(this) called in evaluateVariables()
          binds.rebuild()

          // Controls may have @bind or bind() references, so we need to mark them as dirty. Will need dependencies for controls to fix this.
          // TODO: Handle XPathDependencies
          container.requireRefresh()
        }
      } finally {
        deferredActionContext.resetRebuild()
      }
    }
    containingDocument.xpathDependencies.rebuildDone(selfModel)
  }

  // Recalculate and revalidate are a combined operation
  // See https://github.com/orbeon/orbeon-forms/issues/1650
  def doRecalculateRevalidate(): Unit = {

    // We don't want to dispatch events while we are performing the actual recalculate/revalidate operation,
    // so we collect them here and dispatch them altogether once everything is done.
    val eventsToDispatch = m.ListBuffer[XFormsEvent]()
    def collector(event: XFormsEvent): Unit =
      eventsToDispatch += event

    def recalculateRevalidate: Option[collection.Set[String]] =
      if (deferredActionContext.recalculateRevalidate) {
        try {

          doRecalculate(deferredActionContext.defaultsStrategy, collector)
          containingDocument.xpathDependencies.recalculateDone(selfModel)

          // Validate only if needed, including checking the flags, because if validation state is clean, validation
          // being idempotent, revalidating is not needed.
          val mustRevalidate = bindsIfInstance.isDefined || hasSchema

          mustRevalidate option {
            val invalidInstances = doRevalidate(collector)
            containingDocument.xpathDependencies.revalidateDone(selfModel)
            invalidInstances
          }
        } finally {

          for {
            instanceId <- deferredActionContext.flaggedInstances
            doc        <- getInstance(instanceId).underlyingDocumentOpt
          } locally {
            InstanceDataOps.clearRequireDefaultValueRecursively(doc)
          }

          deferredActionContext.resetRecalculateRevalidate()
        }
      } else
        None

    // Gather events to dispatch, at most one per instance, and only if validity has changed
    // NOTE: It is possible, with binds and the use of xxf:instance(), that some instances in
    // invalidInstances do not belong to this model. Those instances won't get events with the dispatching
    // algorithm below.
    def createAndCommitValidationEvents(invalidInstancesIds: collection.Set[String]): List[XFormsEvent] = {

      val changedInstancesIt =
        for {
          instance           <- instancesIterator
          previouslyValid    = instance.valid
          newlyValid         = ! invalidInstancesIds(instance.getEffectiveId)
          if previouslyValid != newlyValid
        } yield {
          instance.valid = newlyValid // side-effect!
          if (newlyValid) new XXFormsValidEvent(instance) else new XXFormsInvalidEvent(instance)
        }

      changedInstancesIt.toList
    }

    val validationEvents =
      recalculateRevalidate map createAndCommitValidationEvents getOrElse Nil

    // Dispatch all events
    for (event <- eventsToDispatch.iterator ++ validationEvents.iterator)
      Dispatch.dispatchEvent(event)
  }

  def needRebuildRecalculateRevalidate: Boolean =
    deferredActionContext.rebuild || deferredActionContext.recalculateRevalidate

  // This is called in response to dispatching xforms-refresh to this model, whether using the xf:refresh
  // action or by dispatching the event by hand.

  // NOTE: If the refresh flag is not set, we do not call synchronizeAndRefresh() because that would only have the
  // side effect of performing RRR on models, but  but not update the UI, which wouldn't make sense for xforms-refresh.
  // This said, is unlikely (impossible?) that the RRR flags would be set but not the refresh flag.
  // FIXME: See https://github.com/orbeon/orbeon-forms/issues/1650
  protected def doRefresh(): Unit =
    if (containingDocument.controls.isRequireRefresh)
      container.synchronizeAndRefresh()

  private object Private {

    lazy val _schemaValidator: XFormsModelSchemaValidator =
      new XFormsModelSchemaValidator(staticModel.element, indentedLogger) |!> (_.loadSchemas(containingDocument))

    def doRecalculate(defaultsStrategy: DefaultsStrategy, collector: XFormsEvent => Unit): Unit =
      withDebug("performing recalculate", List("model" -> effectiveId)) {

        val hasVariables = staticModel.variablesSeq.nonEmpty

        // Re-evaluate top-level variables if needed
        if (bindsIfInstance.isDefined || hasVariables)
          resetAndEvaluateVariables()

        // Apply calculate binds
        bindsIfInstance foreach { binds =>
          binds.applyDefaultAndCalculateBinds(defaultsStrategy, collector)
        }
      }

    def doRevalidate(collector: XFormsEvent => Unit): collection.Set[String] =
      withDebug("performing revalidate", List("model" -> effectiveId)) {

        val invalidInstancesIds = m.LinkedHashSet[String]()

        // Clear schema validation state
        // NOTE: This could possibly be moved to rebuild(), but we must be careful about the presence of a schema
        for {
          instance                       <- instancesIterator
          instanceMightBeSchemaValidated = hasSchema && instance.isSchemaValidation
          if instanceMightBeSchemaValidated
        } locally {
          DataModel.visitElement(instance.rootElement, InstanceData.clearSchemaState)
        }

        // Validate using schemas if needed
        if (hasSchema)
          for {
            instance <- instancesIterator
            if instance.isSchemaValidation                   // we don't support validating read-only instances
            if ! _schemaValidator.validateInstance(instance) // apply schema
          } locally {
            // Remember that instance is invalid
            invalidInstancesIds += instance.getEffectiveId
          }

        // Validate using binds if needed
        modelBindsOpt foreach { binds =>
          binds.applyValidationBinds(invalidInstancesIds, collector)
        }

        invalidInstancesIds
      }

    def bindsIfInstance: Option[XFormsModelBinds] =
      if (instancesIterator.isEmpty)
        None
      else
        modelBindsOpt
  }
}
