/**
 *  Copyright (C) 2007 Orbeon, Inc.
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

import controls.ExternalLHHAAnalysis
import scala.collection.JavaConverters._
import model.Model
import collection.mutable.{LinkedHashMap, Buffer}
import org.dom4j.Document
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.oxf.xforms.xbl.Scope

// Part analysis: models and instances information
trait PartModelAnalysis {

    this: PartAnalysisImpl =>

    protected val modelsByScope = LinkedHashMap[Scope, Buffer[Model]]()
    protected val modelsByPrefixedId = LinkedHashMap[String, Model]()
    protected val modelByInstancePrefixedId = LinkedHashMap[String, Model]()

    def getModel(prefixedId: String) =
        modelsByPrefixedId.get(prefixedId).orNull

    def getModelByInstancePrefixedId(prefixedId: String) =
        modelByInstancePrefixedId.get(prefixedId).orNull

    def getInstances(modelPrefixedId: String) =
        modelsByPrefixedId.get(modelPrefixedId).toSeq flatMap (_.instances.values) asJava

    def getDefaultModelForScope(scope: Scope) =
        modelsByScope.get(scope) flatMap (_.headOption) orNull

    def getModelByScopeAndBind(scope: Scope, bindStaticId: String) =
        modelsByScope.get(scope) flatMap
            (_ find (_.bindsById.get(bindStaticId) ne null)) orNull

    def getModelsForScope(scope: Scope) =
        modelsByScope.get(scope) getOrElse Seq() asJava

    def findInstancePrefixedId(startScope: Scope, instanceStaticId: String): String = {
        var currentScope = startScope
        while (currentScope ne null) {
            for (model ← getModelsForScope(currentScope).asScala) {
                if (model.instancesMap.containsKey(instanceStaticId)) {
                    return currentScope.prefixedIdForStaticId(instanceStaticId)
                }
            }
            currentScope = currentScope.parent
        }
        null
    }

    /**
     * Register a model document. Used by this and XBLBindings.
     *
     * @param scope             XBL scope
     * @param modelDocument     model document
     */
    def addModel(scope: Scope, modelDocument: Document, eventHandlers: Buffer[EventHandlerImpl]) = {

        val staticStateContext = StaticStateContext(this, -1)
        val newModel = new Model(staticStateContext, modelDocument.getRootElement, None, None, scope)

        // Index model and instances
        indexModel(newModel, eventHandlers)

        // Register nested event handlers and actions
        newModel.buildChildren(build(_, _, _, _, Buffer[ExternalLHHAAnalysis](), eventHandlers), newModel.scope)

        newModel
    }

    protected def indexModel(model: Model,eventHandlers: Buffer[EventHandlerImpl]) {
        val models = modelsByScope.getOrElseUpdate(model.scope, Buffer[Model]())
        models += model
        modelsByPrefixedId += model.prefixedId → model

        for (instance ← model.instances.values)
            modelByInstancePrefixedId += instance.prefixedId → model
    }

    protected def analyzeModelsXPath() =
        if (staticState.isXPathAnalysis)
            for {
                (scope, models) ← modelsByScope
                model ← models
            } yield
                model.analyzeXPath()

    // [NOT USED YET] for xxf:dynamic
    /*
    def removeModel(model: Model) {

        // TODO: Remove scripts

        // Deregister event handlers
        for (handler ← model.eventHandlers)
            deregisterActionHandler(handler)

        // Deindex by instance id
        for (instance ← model.instances.values)
            modelByInstancePrefixedId -= instance.prefixedId

        // Deindex by model id
        modelsByPrefixedId -= model.prefixedId

        // Remove from list of models
        modelsByScope.get(model.scope) foreach  { models ⇒
            models -= model
        }
    }
    */
}