/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.model

import org.orbeon.dom.Node
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.analysis.model.ModelDefs.{Required, Type}
import org.orbeon.oxf.xforms.analysis.model.{ModelDefs, StaticBind}
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.w3c.dom.Node.ELEMENT_NODE

import java.{util => ju}
import scala.collection.{breakOut, mutable}
import scala.jdk.CollectionConverters._

// Holds MIPs associated with a given RuntimeBind iteration
// The constructor automatically adds the BindNode to the instance data node if any.
class BindNode(val parentBind: RuntimeBind, val position: Int, val item: om.Item) {

  import BindNode._

  require(parentBind ne null)

  val (node, hasChildrenElements) =
    item match {
      case node: om.NodeInfo =>
        val hasChildrenElements = node.getNodeKind == ELEMENT_NODE && node.hasChildElement
        InstanceData.addBindNode(node, this)
        // The last type wins
        staticBind.dataType foreach (InstanceData.setBindType(node, _))
        (node, hasChildrenElements)
      case _ =>
        (null, false)
    }

  // Current MIP state
  private var _relevant = ModelDefs.DEFAULT_RELEVANT // move to public var once all callers are Scala
  private var _readonly = ModelDefs.DEFAULT_READONLY // move to public var once all callers are Scala
  private var _required = ModelDefs.DEFAULT_REQUIRED // move to public var once all callers are Scala

  private var _invalidTypeValidation: StaticBind.MIP = null
  private var _requiredValidation: StaticBind.MIP    = null

  private var _customMips = Map.empty[String, String]

  // Since there are only 3 levels we should always get an optimized immutable `Map`
  // For a given level, an empty `List` is not allowed.
  var failedConstraints = EmptyValidations

  // Failed validations for the given level, including type/required
  def failedValidations(level: ValidationLevel): List[StaticBind.MIP] = level match {
    case level @ ValidationLevel.ErrorLevel if ! typeValid || ! requiredValid =>
      // Add type/required if needed
      (! typeValid     list invalidTypeValidation)     :::
      (! requiredValid list invalidRequiredValidation) :::
      failedConstraints.getOrElse(level, Nil)
    case level =>
      // Cannot be type/required as those only have ErrorLevel
      failedConstraints(level)
  }

  // Highest failed validation level, including type/required
  def highestValidationLevel = {
    def typeOrRequiredLevel = (! typeValid || ! requiredValid) option ValidationLevel.ErrorLevel
    def constraintLevel     = ValidationLevel.LevelsByPriority find failedConstraints.contains

    typeOrRequiredLevel orElse constraintLevel
  }

  // All failed validations, including type/required
  def failedValidationsForAllLevels: Validations =
    if (typeValid && requiredValid)
      failedConstraints
    else
      failedConstraints + (ValidationLevel.ErrorLevel -> failedValidations(ValidationLevel.ErrorLevel))

  def staticBind = parentBind.staticBind
  def locationData = staticBind.locationData

  def setRelevant(value: Boolean) = this._relevant = value
  def setReadonly(value: Boolean) = this._readonly = value
  def setRequired(value: Boolean) = this._required = value

  def setTypeValid(value: Boolean, mip: StaticBind.MIP)             = this._invalidTypeValidation = if (! value) mip else null
  def setRequiredValid(value: Boolean, mip: Option[StaticBind.MIP]) = this._requiredValidation    = if (! value) mip.orNull else null

  def setCustom(name: String, value: String): Unit = _customMips += name -> value
  def clearCustom(name: String): Unit = _customMips -= name

  def relevant        = _relevant
  def readonly        = _readonly
  def required        = _required

  def invalidTypeValidation     = _invalidTypeValidation
  def typeValid                 = _invalidTypeValidation eq null
  def invalidRequiredValidation = _requiredValidation
  def requiredValid             = _requiredValidation eq null

  def constraintsSatisfiedForLevel(level: ValidationLevel) = ! failedConstraints.contains(level)
  def valid = typeValid && requiredValid && constraintsSatisfiedForLevel(ValidationLevel.ErrorLevel)

  def ancestorOrSelfBindNodes =
    Iterator.iterate(this)(_.parentBind.parentIteration) takeWhile (_ ne null)
}

object BindNode {

  type Validations = Map[ValidationLevel, List[StaticBind.MIP]]

  val EmptyValidations: Validations = Map()

  // NOTE: This takes the first custom MIP of a given name associated with the bind. We do store multiple
  // ones statically, but don't have yet a solution to combine them. Should we string-join them? See also
  // XFormsModelBindsBase.evaluateCustomMIP.
  def collectAllClientCustomMIPs(bindNodes: ju.List[BindNode]): Map[String, String] =
    if (bindNodes eq null)
      Map.empty[String, String]
    else if (bindNodes.size == 1)
      bindNodes.get(0)._customMips.filterKeys(_.contains(':')) // NOTE: `filterKeys` is a view on the original map.
    else
      bindNodes.asScala.reverse.foldLeft(Map.empty[String, String])(_ ++ _._customMips).filterKeys(_.contains(':')) // NOTE: `filterKeys` is a view on the original map.

  // NOTE: This finds the first custom MIP with the given name found.
  def findCustomMip(bindNodes: ju.List[BindNode], mipName: String): Option[String] =
    if (bindNodes eq null)
      None
    else if (bindNodes.size == 1)
      bindNodes.get(0)._customMips.get(mipName)
    else
      bindNodes.asScala.iterator flatMap (_._customMips.iterator) collectFirst {
        case (`mipName`, mipValue) => mipValue
      }

  // - prioritize failed required error validation, see https://github.com/orbeon/orbeon-forms/issues/1830. It
  //   might be better to use another validation level, for example Missing, to handle this. But supporting this
  //   would have more impact (Form Builder, Form Runner error summary) so we would need to investigate more. For
  //   now, we consider that, for a control, required error validations take precedence over other validations.
  // - also prioritize failed datatype validation, as part of https://github.com/orbeon/orbeon-forms/issues/2242
  private def prioritizeValidations(mipsForLevel: (ValidationLevel, List[StaticBind.MIP])) =
    mipsForLevel match {
      case (ValidationLevel.ErrorLevel, mips) if mips exists (_.name == Required.name) =>
        ValidationLevel.ErrorLevel -> (mips filter (_.name == Required.name))
      case (ValidationLevel.ErrorLevel, mips) if mips exists (_.name == Type.name) =>
        ValidationLevel.ErrorLevel -> (mips filter (_.name == Type.name))
      case validations =>
        validations
    }

  // Get all failed constraints for all levels, combining BindNodes if needed.
  // 2014-07-18: Used by XFormsModelSubmissionBase.annotateWithAlerts only.
  def failedValidationsForAllLevelsPrioritizeRequired(node: Node) =
    failedValidationsForAllLevels(node) map prioritizeValidations

  def failedValidationsForAllLevels(node: Node): Validations =
    collectFailedValidationsForAllLevels(
      Option(InstanceData.getLocalInstanceData(node))
      map (_.getBindNodes.asScala)
      getOrElse Nil
    )

  private def collectFailedValidationsForAllLevels(bindNodes: Iterable[BindNode]): Validations =
    if (bindNodes.isEmpty)
      EmptyValidations
    else if (bindNodes.size == 1)
      bindNodes.head.failedValidationsForAllLevels
    else {
      // This is rather inefficient but hopefully rare
      val buildersByLevel =
        mutable.Map[ValidationLevel, collection.mutable.Builder[StaticBind.MIP, List[StaticBind.MIP]]]()

      for {
        level       <- ValidationLevel.LevelsByPriority
        bindNode    <- bindNodes
        failed      = bindNode.failedValidationsForAllLevels.getOrElse(level, Nil)
        if failed.nonEmpty
      } locally {
        val builder = buildersByLevel.getOrElseUpdate(level, List.newBuilder[StaticBind.MIP])
        builder ++= failed
      }

      buildersByLevel.map { case (k, v) => k -> v.result()} (breakOut)
    }

  // Get all failed constraints for the highest level only, combining BindNodes if needed.
  // 2014-07-18: Used by XFormsSingleNodeControl only.
  def failedValidationsForHighestLevelPrioritizeRequired(nodeInfo: om.NodeInfo) =
    failedValidationsForHighestLevel(nodeInfo) map prioritizeValidations

  def failedValidationsForHighestLevel(nodeInfo: om.NodeInfo) =
    collectFailedValidationsForHighestLevel(
      Option(InstanceData.getLocalInstanceData(nodeInfo, forUpdate = false))
      map (_.getBindNodes.asScala)
      getOrElse Nil
    )

  private def collectFailedValidationsForHighestLevel(
    bindNodes : Seq[BindNode]
  ): Option[(ValidationLevel, List[StaticBind.MIP])] =
    collectFailedValidationsForLevel(bindNodes, _.highestValidationLevel)

  private def collectFailedValidationsForLevel(
    bindNodes : Seq[BindNode],
    findLevel : BindNode => Option[ValidationLevel]
  ): Option[(ValidationLevel, List[StaticBind.MIP])] =
    if (bindNodes.isEmpty)
      None
    else {
      val consideredLevels = bindNodes flatMap (node => findLevel(node) map (level => (level, node)))
      val highestLevelOpt  = consideredLevels.nonEmpty option (consideredLevels map (_._1) max)

      highestLevelOpt map {
        highestLevel =>

          val failedForHighest =
            consideredLevels.toList collect {
              case (`highestLevel`, node) => node.failedValidations(highestLevel)
            } flatten

          (highestLevel, failedForHighest)
      }
    }
}

// Bind node that also contains nested binds
class BindIteration(
  parentBind                         : RuntimeBind,
  position                           : Int,
  item                               : om.Item,
  childrenBindsHaveSingleNodeContext : Boolean,
  childrenStaticBinds                : List[StaticBind],
  collector                          : ErrorEventCollector
) extends BindNode(parentBind, position, item) {

  require(childrenStaticBinds.nonEmpty)

  // Iterate over children and create children binds
  val childrenBinds: List[RuntimeBind] =
    for (staticBind <- childrenStaticBinds)
      yield new RuntimeBind(parentBind.model, staticBind, this, childrenBindsHaveSingleNodeContext, collector)

  def forStaticId = parentBind.staticId

  def applyBinds(fn: BindNode => Unit): Unit =
    for (currentBind <- childrenBinds)
      currentBind.applyBinds(fn)

  def findChildBindByStaticId(bindId: String) =
    childrenBinds find (_.staticBind.staticId == bindId)
}