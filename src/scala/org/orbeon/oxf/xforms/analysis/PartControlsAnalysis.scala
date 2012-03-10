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

import controls._
import scala.collection.JavaConverters._
import org.dom4j.QName
import java.util.{Map ⇒ JMap}
import org.orbeon.oxf.xforms.XFormsConstants._
import collection.mutable.{Buffer, HashMap, HashSet, LinkedHashMap}
import org.orbeon.oxf.xforms.event.EventHandlerImpl

trait PartControlsAnalysis extends TransientState {

    self: PartAnalysisImpl ⇒

    protected val controlAnalysisMap = new LinkedHashMap[String, ElementAnalysis]
    // type → Map of prefixedId → ElementAnalysis
    protected val controlTypes = HashMap[String, LinkedHashMap[String, ElementAnalysis]]()
    // type → Set of appearances
    private val controlAppearances = HashMap[String, HashSet[QName]]();

    // Special handling of attributes
    private[PartControlsAnalysis] var attributeControls: Map[String, Map[String, AttributeControl]] = _

    // Special handling of input placeholder
    private[PartControlsAnalysis] var _hasInputPlaceholder = false
    def hasInputPlaceholder = _hasInputPlaceholder

    protected def indexNewControl(elementAnalysis: ElementAnalysis, externalLHHA: Buffer[ExternalLHHAAnalysis], eventHandlers: Buffer[EventHandlerImpl]) {
        val controlName = elementAnalysis.localName

        // Index by prefixed id
        controlAnalysisMap += elementAnalysis.prefixedId → elementAnalysis

        // Index by type
        val controlsMap = controlTypes.getOrElseUpdate(controlName, LinkedHashMap[String, ElementAnalysis]())
        controlsMap += elementAnalysis.prefixedId → elementAnalysis

        // Remember appearances in use
        val appearancesForControlName = controlAppearances.getOrElseUpdate(controlName, HashSet[QName]())

        elementAnalysis match {
            case elementWithAppearance: AppearanceTrait ⇒
                appearancesForControlName ++= elementWithAppearance.appearances

                if (controlName == "textarea" && elementWithAppearance.mediatype == Some("text/html")) {
                    // Special appearance: when text/html mediatype is found on <textarea>, do as if an xxforms:richtext
                    // appearance had been set, so that we can decide on feature usage based on appearance only.
                    appearancesForControlName += XXFORMS_RICH_TEXT_APPEARANCE_QNAME
                }
            case _ ⇒
        }

        // Register special controls
        elementAnalysis match {
            case lhha: ExternalLHHAAnalysis ⇒ externalLHHA += lhha
            case eventHandler: EventHandlerImpl ⇒ eventHandlers += eventHandler
            case _ ⇒
        }
    }

    protected def analyzeCustomControls() {
        // Check whether input controls have "placeholder/minimal" label/hint
        _hasInputPlaceholder =
            controlTypes.get("input") match {
               case Some(map) ⇒ map.values exists (inputControl ⇒ LHHAAnalysis.hasLabelOrHintPlaceholder(inputControl))
               case None ⇒ false
           }

        // Index attribute controls
        attributeControls =
            controlTypes.get("attribute") match {
                case Some(map) ⇒
                    case class AttributeDetails(forPrefixedId: String, attributeName: String, attributeControl: AttributeControl)
                    val triples =
                        for {
                            value ← map.values
                            attributeControl = value.asInstanceOf[AttributeControl]
                        } yield
                            AttributeDetails(attributeControl.forPrefixedId, attributeControl.attributeName, attributeControl)

                    // Nicely group the results in a two-level map
                    // NOTE: We assume only one control per forPrefixedId/attributeName combination, hence the assert

                    triples groupBy
                        (_.forPrefixedId) mapValues
                            (_ groupBy (_.attributeName) mapValues {a ⇒ assert(a.size == 1); a.head.attributeControl})

                case None ⇒ Map()
            }
    }

    protected def analyzeControlsXPath() =
        if (staticState.isXPathAnalysis)
            for (control ← controlAnalysisMap.values)
                control.analyzeXPath()

    def getControlAnalysis(prefixedId: String) = controlAnalysisMap.get(prefixedId) orNull

    def hasAttributeControl(prefixedForAttribute: String) =
        attributeControls.get(prefixedForAttribute).isDefined

    def getAttributeControl(prefixedForAttribute: String, attributeName: String) =
        attributeControls.get(prefixedForAttribute) flatMap (_.get(attributeName)) orNull

    def hasControlByName(controlName: String) =
        controlTypes.get(controlName).isDefined

    def hasControlAppearance(controlName: String, appearance: QName) =
        controlAppearances.get(controlName) map (_(appearance)) getOrElse false

    // Repeats
    def repeats = controlTypes.get("repeat") map (_.values map (_.asInstanceOf[RepeatControl])) getOrElse Seq.empty

    def getRepeatHierarchyString =
        controlTypes.get("repeat") map { repeats ⇒
            val repeatsWithAncestors =
                for {
                    repeat ← repeats.values
                    prefixedId = repeat.prefixedId
                    ancestorRepeat = RepeatControl.getAncestorRepeatAcrossParts(repeat)
                } yield
                    ancestorRepeat match {
                        case Some(ancestorRepeat) ⇒ prefixedId + " " + ancestorRepeat.prefixedId
                        case None ⇒ prefixedId
                    }

            repeatsWithAncestors mkString ","
        } getOrElse ""


    def getTopLevelControls = Seq(controlTypes("root").values.head)

    def getTopLevelControlElements =
        Seq(controlTypes("root").values.head.element) ++
            (xblBindings.allGlobals.values map (_.compactShadowTree.getRootElement)) asJava

    def hasControls = getTopLevelControlElements.size > 0

    override def freeTransientState() = {
        super.freeTransientState()

        for (controlAnalysis ← controlAnalysisMap.values)
            controlAnalysis.freeTransientState()
    }
}