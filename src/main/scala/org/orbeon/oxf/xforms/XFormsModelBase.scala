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

import org.orbeon.oxf.xforms.event.Dispatch.EventListener
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.xbl.XBLContainer

import java.{util ⇒ ju}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.util.ScalaUtils._
import scala.collection.JavaConverters._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.{XXFormsInvalidEvent, XXFormsValidEvent}
import collection.mutable

abstract class XFormsModelBase(val container: XBLContainer, val effectiveId: String, val staticModel: Model) extends Logging {

    // Listeners
    def addListener(eventName: String , listener: EventListener): Unit =
        throw new UnsupportedOperationException

    def removeListener(eventName: String , listener: EventListener): Unit =
        throw new UnsupportedOperationException

    def getListeners(eventName: String) = Seq.empty[EventListener]

    val containingDocument = container.getContainingDocument
    implicit val indentedLogger = containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY)

    val deferredActionContext = new DeferredActionContext(container)

    // Temporarily: implemented in Java subclass until we move everything to Scala
    def resetAndEvaluateVariables(): Unit
    def getBinds: XFormsModelBinds
    def getInstances: ju.List[XFormsInstance]
    def mustBindValidate: Boolean

    private lazy val _schemaValidator =
        new XFormsModelSchemaValidator(staticModel.element, indentedLogger) |!> (_.loadSchemas(containingDocument))

    def schemaValidator = _schemaValidator
    def hasSchema = _schemaValidator.hasSchema

    def getSchemaURIs: Array[String] =
        if (hasSchema)
            _schemaValidator.getSchemaURIs
        else
            null

    def doRecalculate(applyDefaults: Boolean): Unit = {
        if (deferredActionContext.recalculateRevalidate) {
            val hasVariables = ! staticModel.variablesSeq.isEmpty

            // Re-evaluate top-level variables if needed
            if (hasInstancesAndBinds || hasVariables)
                resetAndEvaluateVariables()

            // Apply calculate binds
            if (hasInstancesAndBinds)
                getBinds.applyCalculateBinds(applyDefaults)

            // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
            // have an immediate effect, and clear the corresponding flag."
            deferredActionContext.recalculateRevalidate = false
        }
        containingDocument.getXPathDependencies.recalculateDone(staticModel)
    }

    def doRevalidate(): Unit =  {

        val instances = getInstances.asScala
        var invalidInstancesOpt: Option[mutable.Set[String]] = None

        // Validate only if needed, including checking the flags, because if validation state is clean, validation
        // being idempotent, revalidating is not needed.
        if (deferredActionContext.revalidate) {
            val mustRevalidate = instances.nonEmpty && (mustBindValidate || hasSchema)

            if (mustRevalidate) {

                withDebug("performing revalidate", List("model id" → effectiveId)) {

                    // Clear schema validation state
                    // NOTE: This could possibly be moved to rebuild(), but we must be careful about the presence of a schema
                    for {
                        instance ← instances
                        instanceMightBeSchemaValidated = hasSchema && instance.isSchemaValidation
                        if instanceMightBeSchemaValidated
                    } locally {
                        DataModel.visitElementJava(instance.rootElement, new DataModel.NodeVisitor {
                            def visit(nodeInfo: NodeInfo) =
                                InstanceData.clearSchemaState(nodeInfo)
                        })
                    }

                    val invalidInstanceSet = mutable.LinkedHashSet[String]()
                    invalidInstancesOpt = Some(invalidInstanceSet)

                    // Validate using schemas if needed
                    if (hasSchema)
                        for {
                            instance ← instances
                            if instance.isSchemaValidation                   // we don't support validating read-only instances
                            if ! _schemaValidator.validateInstance(instance) // apply schema
                        } locally {
                            // Remember that instance is invalid
                            invalidInstanceSet += instance.getEffectiveId
                        }

                    // Validate using binds if needed
                    if (mustBindValidate)
                        getBinds.applyValidationBinds(invalidInstanceSet.asJava)
                }
            }

            // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
            // have an immediate effect, and clear the corresponding flag."
            deferredActionContext.clearRevalidate()
        }

        // Notify dependencies
        containingDocument.getXPathDependencies.revalidateDone(staticModel)

        invalidInstancesOpt foreach { invalidInstances ⇒

            // Gather events to dispatch, at most one per instance, and only if validity has changed
            // NOTE: It is possible, with binds and the use of xxf:instance(), that some instances in
            // invalidInstances do not belong to this model. Those instances won't get events with the dispatching
            // algorithm below.

            val changedInstances =
                for {
                    instance ← instances
                    previouslyValid = instance.valid
                    currentlyValid = ! invalidInstances(instance.getEffectiveId)
                    if previouslyValid != currentlyValid
                } yield
                    instance

            // Update instance validity
            for (instance ← changedInstances)
                instance.valid = ! instance.valid

            val eventsToDispatch =
                for (instance ← changedInstances)
                yield
                    if (instance.valid) new XXFormsValidEvent(instance) else new XXFormsInvalidEvent(instance)

            // Dispatch all events
            for (event ← eventsToDispatch)
                Dispatch.dispatchEvent(event)
        }
    }

    def hasInstancesAndBinds: Boolean =
        ! getInstances.isEmpty && (getBinds ne null)

    def needRebuildRecalculateRevalidate =
        deferredActionContext.rebuild || deferredActionContext.recalculateRevalidate || deferredActionContext.revalidate
}

class DeferredActionContext(container: XBLContainer) {

    var rebuild = false
    var recalculateRevalidate = false
    var revalidate = false

    def markRebuild()               = rebuild = true
    def markRecalculateRevalidate() = recalculateRevalidate = true
    def markRevalidate()            = revalidate = true

    def clearRebuild()              = rebuild = false
    def clearRevalidate()           = revalidate = false

    def markStructuralChange(): Unit = {
        // "XForms Actions that change the tree structure of instance data result in setting all four deferred update
        // flags to true for the model over which they operate"

        rebuild = true
        recalculateRevalidate = true
        revalidate = true
        container.requireRefresh()
    }

    def markValueChange(isCalculate: Boolean): Unit = {
        // "XForms Actions that change only the value of an instance node results in setting the flags for
        // recalculate, revalidate, and refresh to true and making no change to the flag for rebuild".

        // Only set recalculate when we are not currently performing a recalculate (avoid infinite loop)
        if (! isCalculate)
            recalculateRevalidate = true

        revalidate = true

        container.requireRefresh()
    }
}