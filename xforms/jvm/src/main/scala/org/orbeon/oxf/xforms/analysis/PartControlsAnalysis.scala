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

import java.{util => ju}

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.analysis.controls.SelectionControlUtil._
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.saxon.om.NodeInfo

import scala.collection.JavaConverters._
import scala.collection.mutable.{Buffer, HashMap, LinkedHashMap}

trait PartControlsAnalysis extends TransientState {

  self: PartAnalysisImpl =>

  val controlAnalysisMap = LinkedHashMap[String, ElementAnalysis]()
  protected val controlTypes       = HashMap[String, LinkedHashMap[String, ElementAnalysis]]() // type -> Map of prefixedId -> ElementAnalysis

  // Special handling of attributes
  private[PartControlsAnalysis] var _attributeControls: Map[String, Map[String, AttributeControl]] = Map()

  protected def indexNewControl(
    elementAnalysis : ElementAnalysis,
    lhhas           : Buffer[LHHAAnalysis],
    eventHandlers   : Buffer[EventHandlerImpl],
    models          : Buffer[Model],
    attributes      : Buffer[AttributeControl]
  ): Unit = {
    val controlName = elementAnalysis.localName

    // Index by prefixed id
    controlAnalysisMap += elementAnalysis.prefixedId -> elementAnalysis

    // Index by type
    val controlsMap = controlTypes.getOrElseUpdate(controlName, LinkedHashMap[String, ElementAnalysis]())
    controlsMap += elementAnalysis.prefixedId -> elementAnalysis

    // Register special controls
    elementAnalysis match {
      case lhha: LHHAAnalysis if ! TopLevelItemsetQNames(lhha.getParent.element.getQName) => lhhas += lhha
      case eventHandler: EventHandlerImpl                                                 => eventHandlers += eventHandler
      case model: Model                                                                   => models        += model
      case attribute: AttributeControl                                                    => attributes    += attribute
      case _                                                                              =>
    }
  }

  protected def analyzeCustomControls(attributes: Buffer[AttributeControl]): Unit =
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

  protected def analyzeControlsXPath() =
    for (control <- controlAnalysisMap.values if ! control.isInstanceOf[Model] && ! control.isInstanceOf[RootControl])
      control.analyzeXPath()

  def iterateControls =
    controlAnalysisMap.valuesIterator

  def findControlAnalysis(prefixedId: String) =
    controlAnalysisMap.get(prefixedId)

  def getControlAnalysis(prefixedId: String) =
    controlAnalysisMap.get(prefixedId) orNull

  def controlElement(prefixedId: String): Option[NodeInfo] =
    findControlAnalysis(prefixedId) map (control => staticStateDocument.documentWrapper.wrap(control.element))

  def hasAttributeControl(prefixedForAttribute: String) =
    _attributeControls.get(prefixedForAttribute).isDefined

  def getAttributeControl(prefixedForAttribute: String, attributeName: String) =
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

  def getTopLevelControlElements: ju.List[Element] =
    Seq(controlTypes("root").values.head.element) ++
      (allGlobals map (_.compactShadowTree.getRootElement)) asJava

  def hasControls = getTopLevelControlElements.size > 0

  override def freeTransientState() = {
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
      case model: Model              => deindexModel(model)
      case handler: EventHandlerImpl => deregisterEventHandler(handler)
      case att: AttributeControl     => _attributeControls -= att.forPrefixedId
      case _                         =>
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
      case childrenBuilder: ChildrenBuilderTrait =>
        childrenBuilder.indexedElements foreach deindexControl

        if (! self)
          childrenBuilder.children foreach
            (_.removeFromParent())

      case _ =>
    }
  }
}