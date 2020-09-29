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

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.xforms.analysis.model.{Instance, Model}
import org.orbeon.xforms.xbl.Scope

import scala.collection.mutable

// Part analysis: models and instances information
trait PartModelAnalysis extends TransientState {

  self: PartAnalysisImpl =>

  private[PartModelAnalysis] val modelsByScope             = mutable.LinkedHashMap[Scope, mutable.Buffer[Model]]()
  private[PartModelAnalysis] val modelsByPrefixedId        = mutable.LinkedHashMap[String, Model]()
  private[PartModelAnalysis] val modelByInstancePrefixedId = mutable.LinkedHashMap[String, Model]()

  def iterateModels: Iterator[Model] =
    for {
      models <- modelsByScope.valuesIterator
      model  <- models.iterator
    } yield
      model

  def getModel(prefixedId: String): Model =
    modelsByPrefixedId.get(prefixedId).orNull

  def getModelByInstancePrefixedId(prefixedId: String): Model =
    modelByInstancePrefixedId.get(prefixedId).orNull

  def getInstances(modelPrefixedId: String): Seq[Instance] =
    modelsByPrefixedId.get(modelPrefixedId).toSeq flatMap (_.instances.values)

  def defaultModel: Option[Model] =
    getDefaultModelForScope(startScope)

  def getDefaultModelForScope(scope: Scope): Option[Model] =
    modelsByScope.get(scope) flatMap (_.headOption)

  def getModelByScopeAndBind(scope: Scope, bindStaticId: String): Model =
    modelsByScope.get(scope) flatMap
      (_ find (_.bindsById.contains(bindStaticId))) orNull

  def getModelsForScope(scope: Scope): Seq[Model] =
    modelsByScope.getOrElse(scope, Seq())

  def findInstanceInScope(scope: Scope, instanceStaticId: String): Option[Instance] =
    getModelsForScope(scope).iterator flatMap
      (_.instances.iterator)          collectFirst
      { case (`instanceStaticId`, instance) => instance }

  // NOTE: This searches ancestor scopes as well.
  def findInstancePrefixedId(startScope: Scope, instanceStaticId: String): Option[String] = {

    val prefixedIdIt =
      for {
        scope <- Iterator.iterateOpt(startScope)(_.parent)
        model <- getModelsForScope(scope)
        if model.instances.contains(instanceStaticId)
      } yield
        scope.prefixedIdForStaticId(instanceStaticId)

    prefixedIdIt.nextOption()
  }

  protected def indexModel(model: Model): Unit = {
    val models = modelsByScope.getOrElseUpdate(model.scope, mutable.Buffer[Model]())
    models += model
    modelsByPrefixedId += model.prefixedId -> model

    for (instance <- model.instances.values)
      modelByInstancePrefixedId += instance.prefixedId -> model
  }

  protected def deindexModel(model: Model): Unit = {
    modelsByScope.get(model.scope) foreach (_ -= model)
    modelsByPrefixedId -= model.prefixedId

    for (instance <- model.instances.values)
      modelByInstancePrefixedId -= instance.prefixedId
  }

  override def freeTransientState(): Unit = {
    super.freeTransientState()

    for (model <- modelsByPrefixedId.values)
      model.freeTransientState()
  }
}