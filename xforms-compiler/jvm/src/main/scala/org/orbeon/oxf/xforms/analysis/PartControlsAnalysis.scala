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

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.saxon.om

import scala.collection.mutable.{Buffer, HashMap, LinkedHashMap}

trait PartControlsAnalysis extends TransientState {

  self =>

  def findControlAnalysis(prefixedId: String): Option[ElementAnalysis]

  // For deindexing
  def metadata            : Metadata
  def unmapScopeIds(control: ElementAnalysis): Unit
  def deindexModel(model: Model): Unit
  def deregisterEventHandler(eventHandler: EventHandler): Unit

  val controlAnalysisMap     = LinkedHashMap[String, ElementAnalysis]()
  val controlTypes = HashMap[String, LinkedHashMap[String, ElementAnalysis]]() // type -> Map of prefixedId -> ElementAnalysis

  def iterateControlsNoModels: Iterator[ElementAnalysis] =
    for (control <- controlAnalysisMap.valuesIterator if ! control.isInstanceOf[Model] && ! control.isInstanceOf[RootControl])
      yield control

  // Special handling of attributes
  private[PartControlsAnalysis] var _attributeControls: Map[String, Map[String, AttributeControl]] = Map.empty

  def analyzeCustomControls(attributes: Buffer[AttributeControl]): Unit =
    if (attributes.nonEmpty) { // index attribute controls

      case class AttributeDetails(forPrefixedId: String, attributeName: String, attributeControl: AttributeControl)

      val triples =
        for (attributeControl <- attributes)
          yield AttributeDetails(attributeControl.forPrefixedId, attributeControl.attributeName, attributeControl)

      // Nicely group the results in a two-level map
      // NOTE: We assume only one control per forPrefixedId/attributeName combination, hence the assert
      val newAttributes =
        triples groupBy
          (_.forPrefixedId) mapValues
            (_ groupBy (_.attributeName) mapValues { _.ensuring(_.size == 1).head.attributeControl })

      // Accumulate new attributes into existing map by combining values for a given "for id"
      // NOTE: mapValues above ok, since we accumulate the values below into new immutable Maps.
      _attributeControls = newAttributes.foldLeft(_attributeControls) {
        case (existingMap, (forId, newAttributes)) =>
          val existingAttributes = existingMap.getOrElse(forId, Map[String, AttributeControl]())
          existingMap + (forId -> (existingAttributes ++ newAttributes))
      }
    }

  def getControlAnalysis(prefixedId: String) =
    controlAnalysisMap.get(prefixedId) orNull

  def controlElement(prefixedId: String): Option[om.NodeInfo] =
    findControlAnalysis(prefixedId) map { control =>
      val wrapper = new DocumentWrapper(control.element.getDocument, null, XPath.GlobalConfiguration)
      wrapper.wrap(control.element)
    }

  def hasAttributeControl(prefixedForAttribute: String) =
    _attributeControls.get(prefixedForAttribute).isDefined

  def getAttributeControl(prefixedForAttribute: String, attributeName: String): AttributeControl =
    _attributeControls.get(prefixedForAttribute) flatMap (_.get(attributeName)) orNull

  def hasControlByName(controlName: String) =
    controlTypes.get(controlName) exists (_.nonEmpty)

  def controlsByName(controlName: String): Iterable[ElementAnalysis] =
    controlTypes.get(controlName).toList flatMap (_.values)

  // Repeats
  def repeats: Iterable[RepeatControl] =
    controlTypes.get("repeat") map (_.values map (_.asInstanceOf[RepeatControl])) getOrElse Nil

  def getRepeatHierarchyString(ns: String): String =
    controlTypes.get("repeat") map { repeats =>
      val repeatsWithAncestors =
        for {
          repeat               <- repeats.values
          namespacedPrefixedId = ns + repeat.prefixedId
          ancestorRepeat       = repeat.ancestorRepeatsAcrossParts.headOption
        } yield
          ancestorRepeat match {
            case Some(ancestorRepeat) => namespacedPrefixedId + " " + ns + ancestorRepeat.prefixedId
            case None                 => namespacedPrefixedId
          }

      repeatsWithAncestors mkString ","
    } getOrElse ""

  def getTopLevelControls: List[ElementAnalysis] = List(controlTypes("root").values.head)

  override def freeTransientState(): Unit = {
    super.freeTransientState()

    for (controlAnalysis <- controlAnalysisMap.values)
      controlAnalysis.freeTransientState()
  }

  // Deindex the given control
  def deindexControl(control: ElementAnalysis): Unit = {
    val controlName = control.localName
    val prefixedId = control.prefixedId

    controlAnalysisMap -= prefixedId
    controlTypes.get(controlName) foreach (_ -= prefixedId)

    metadata.removeNamespaceMapping(prefixedId)
    metadata.removeBindingByPrefixedId(prefixedId)
    unmapScopeIds(control)

    control match {
      case model: Model          => deindexModel(model)
      case handler: EventHandler => deregisterEventHandler(handler)
      case att: AttributeControl => _attributeControls -= att.forPrefixedId
      case _                     =>
    }

    // NOTE: Can't update controlAppearances and _hasInputPlaceholder without checking all controls again, so for now leave that untouched
  }

  // Remove the given control and its descendants
  def deindexTree(tree: ElementAnalysis, self: Boolean): Unit = {

    if (self) {
      deindexControl(tree)
      tree.removeFromParent()
    }

    tree match {
      case childrenBuilder: WithChildrenTrait =>
        childrenBuilder.descendants foreach deindexControl

        if (! self)
          childrenBuilder.children foreach
            (_.removeFromParent())

      case _ =>
    }
  }
}