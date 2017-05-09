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
package org.orbeon.oxf.xforms

import java.{util ⇒ ju}

import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.event.events.{XXFormsInvalidEvent, XXFormsValidEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, ListenersTrait, XFormsEvent}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model._
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{StructuredQName, ValueRepresentation}

import scala.collection.JavaConverters._
import scala.collection.{mutable ⇒ m}

abstract class XFormsModelBase(val container: XBLContainer, val effectiveId: String, val staticModel: Model)
  extends Logging
  with ListenersTrait {

  import Private._

  val containingDocument  = container.getContainingDocument
  val sequenceNumber: Int = containingDocument.nextModelSequenceNumber()

  implicit val indentedLogger = containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY)

  val deferredActionContext = new DeferredActionContext(container)

  // TEMP: implemented in Java subclass until we move everything to Scala
  def selfModel: XFormsModel
  def resetAndEvaluateVariables(): Unit
  def modelBindsOpt: Option[XFormsModelBinds]
  def getInstances: ju.List[XFormsInstance]
  def getInstance(instanceStaticId: String): XFormsInstance
  def getDefaultEvaluationContext: BindingContext

  def schemaValidator = _schemaValidator
  def hasSchema = _schemaValidator.hasSchema

  def getSchemaURIs: Array[String] =
    if (hasSchema)
      _schemaValidator.getSchemaURIs
    else
      null

  def markStructuralChange(instanceOpt: Option[XFormsInstance], defaultsStrategy: DefaultsStrategy): Unit = {
    deferredActionContext.markStructuralChange(defaultsStrategy, instanceOpt map (_.getId))
    // NOTE: PathMapXPathDependencies doesn't yet make use of the `instance` parameter.
    containingDocument.getXPathDependencies.markStructuralChange(selfModel, instanceOpt)
  }

  def doRebuild(): Unit = {
    if (deferredActionContext.rebuild) {
      try {
        resetAndEvaluateVariables()
        bindsIfInstance foreach { binds ⇒
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
    containingDocument.getXPathDependencies.rebuildDone(selfModel)
  }

  // Recalculate and revalidate are a combined operation
  // See https://github.com/orbeon/orbeon-forms/issues/1650
  def doRecalculateRevalidate(): Unit = {

    val instances = getInstances.asScala

    // We don't want to dispatch events while we are performing the actual recalculate/revalidate operation,
    // so we collect them here and dispatch them altogether once everything is done.
    val eventsToDispatch = m.ListBuffer[XFormsEvent]()
    def collector(event: XFormsEvent): Unit =
      eventsToDispatch += event

    def recalculateRevalidate: Option[collection.Set[String]] =
      if (deferredActionContext.recalculateRevalidate) {
        try {

          doRecalculate(deferredActionContext.defaultsStrategy, collector)
          containingDocument.getXPathDependencies.recalculateDone(selfModel)

          // Validate only if needed, including checking the flags, because if validation state is clean, validation
          // being idempotent, revalidating is not needed.
          val mustRevalidate = bindsIfInstance.isDefined || hasSchema

          mustRevalidate option {
            val invalidInstances = doRevalidate(collector)
            containingDocument.getXPathDependencies.revalidateDone(selfModel)
            invalidInstances
          }
        } finally {

          for {
            instanceId ← deferredActionContext.flaggedInstances
            doc        ← getInstance(instanceId).underlyingDocumentOpt
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
    def createAndCommitValidationEvents(invalidInstancesIds: collection.Set[String]): Seq[XFormsEvent] = {

      val changedInstances =
        for {
          instance           ← instances
          previouslyValid    = instance.valid
          currentlyValid     = ! invalidInstancesIds(instance.getEffectiveId)
          if previouslyValid != currentlyValid
        } yield
          instance

      // Update instance validity
      for (instance ← changedInstances)
        instance.valid = ! instance.valid

      // Create events
      for (instance ← changedInstances)
      yield
        if (instance.valid) new XXFormsValidEvent(instance) else new XXFormsInvalidEvent(instance)
    }

    val validationEvents =
      recalculateRevalidate map createAndCommitValidationEvents getOrElse Nil

    // Dispatch all events
    for (event ← eventsToDispatch.iterator ++ validationEvents.iterator)
      Dispatch.dispatchEvent(event)
  }

  def needRebuildRecalculateRevalidate =
    deferredActionContext.rebuild || deferredActionContext.recalculateRevalidate

  // This is called in response to dispatching xforms-refresh to this model, whether using the xf:refresh
  // action or by dispatching the event by hand.

  // NOTE: If the refresh flag is not set, we do not call synchronizeAndRefresh() because that would only have the
  // side effect of performing RRR on models, but  but not update the UI, which wouldn't make sense for xforms-refresh.
  // This said, is unlikely (impossible?) that the RRR flags would be set but not the refresh flag.
  // FIXME: See https://github.com/orbeon/orbeon-forms/issues/1650
  protected def doRefresh(): Unit =
    if (containingDocument.getControls.isRequireRefresh)
      container.synchronizeAndRefresh()

  val variableResolver: (StructuredQName, XPathContext) ⇒ ValueRepresentation =
    (variableQName: StructuredQName, xpathContext: XPathContext) ⇒
      staticModel.bindsByName.get(variableQName.getLocalName) match {
        case Some(targetStaticBind) ⇒
          // Variable value is a bind nodeset to resolve
          BindVariableResolver.resolveClosestBind(
            modelBinds          = modelBindsOpt.get, // TODO XXX
            contextBindNodeOpt  = XFormsFunction.context.data.asInstanceOf[Option[BindNode]],
            targetStaticBind    = targetStaticBind
          ) getOrElse
            (throw new IllegalStateException)
        case None ⇒
          // Try top-level model variables
          val modelVariables = getDefaultEvaluationContext.getInScopeVariables
          // NOTE: With XPath analysis on, variable scope has been checked statically
          Option(modelVariables.get(variableQName.getLocalName)) getOrElse
            (throw new ValidationException("Undeclared variable in XPath expression: $" + variableQName.getClarkName, staticModel.locationData))
      }

  private object Private {

    lazy val _schemaValidator =
      new XFormsModelSchemaValidator(staticModel.element, indentedLogger) |!> (_.loadSchemas(containingDocument))

    def doRecalculate(defaultsStrategy: DefaultsStrategy, collector: XFormsEvent ⇒ Unit): Unit =
      withDebug("performing recalculate", List("model" → effectiveId)) {

        val hasVariables = staticModel.variablesSeq.nonEmpty

        // Re-evaluate top-level variables if needed
        if (bindsIfInstance.isDefined || hasVariables)
          resetAndEvaluateVariables()

        // Apply calculate binds
        bindsIfInstance foreach { binds ⇒
          binds.applyDefaultAndCalculateBinds(defaultsStrategy, collector)
        }
      }

    def doRevalidate(collector: XFormsEvent ⇒ Unit): collection.Set[String] =
      withDebug("performing revalidate", List("model" → effectiveId)) {

        val instances = getInstances.asScala
        val invalidInstancesIds = m.LinkedHashSet[String]()

        // Clear schema validation state
        // NOTE: This could possibly be moved to rebuild(), but we must be careful about the presence of a schema
        for {
          instance ← instances
          instanceMightBeSchemaValidated = hasSchema && instance.isSchemaValidation
          if instanceMightBeSchemaValidated
        } locally {
          DataModel.visitElement(instance.rootElement, InstanceData.clearSchemaState)
        }

        // Validate using schemas if needed
        if (hasSchema)
          for {
            instance ← instances
            if instance.isSchemaValidation                   // we don't support validating read-only instances
            if ! _schemaValidator.validateInstance(instance) // apply schema
          } locally {
            // Remember that instance is invalid
            invalidInstancesIds += instance.getEffectiveId
          }

        // Validate using binds if needed
        modelBindsOpt foreach { binds ⇒
          binds.applyValidationBinds(invalidInstancesIds, collector)
        }

        invalidInstancesIds
      }

    def bindsIfInstance: Option[XFormsModelBinds] =
      if (getInstances.isEmpty)
        None
      else
        modelBindsOpt
  }
}
