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
import model.Model
import collection.JavaConverters._
import org.dom4j.QName
import org.orbeon.oxf.xforms.XFormsConstants._
import collection.mutable.{Buffer, HashMap, HashSet, LinkedHashMap}
import org.orbeon.oxf.xforms.event.EventHandlerImpl

trait PartControlsAnalysis extends TransientState {

    self: PartAnalysisImpl ⇒

    protected val controlAnalysisMap = LinkedHashMap[String, ElementAnalysis]()
    protected val controlTypes       = HashMap[String, LinkedHashMap[String, ElementAnalysis]]() // type → Map of prefixedId → ElementAnalysis
    private   val controlAppearances = HashMap[String, HashSet[QName]]()                         // type → Set of appearances

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
                    // Special appearance: when text/html mediatype is found on <textarea>, do as if an xxf:richtext
                    // appearance had been set, so that we can decide on feature usage based on appearance only.
                    appearancesForControlName += XXFORMS_RICH_TEXT_APPEARANCE_QNAME
                }
            case _ ⇒
        }

        // Register special controls
        elementAnalysis match {
            case lhha: LHHAAnalysis ⇒ lhhas += lhha
            case eventHandler: EventHandlerImpl ⇒ eventHandlers += eventHandler
            case model: Model ⇒ models += model
            case attribute: AttributeControl ⇒ attributes += attribute
            case _ ⇒
        }
    }

    protected def analyzeCustomControls(attributes: Buffer[AttributeControl]): Unit =
        if (attributes.nonEmpty) { // index attribute controls
            case class AttributeDetails(forPrefixedId: String, attributeName: String, attributeControl: AttributeControl)

            val triples =
                for (attributeControl ← attributes)
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
                case (existingMap, (forId, newAttributes)) ⇒
                    val existingAttributes = existingMap.getOrElse(forId, Map[String, AttributeControl]())
                    existingMap + (forId → (existingAttributes ++ newAttributes))
            }
        }

    protected def analyzeControlsXPath() =
        for (control ← controlAnalysisMap.values if ! control.isInstanceOf[Model] && ! control.isInstanceOf[RootControl])
            control.analyzeXPath()

    def iterateControls =
        controlAnalysisMap.valuesIterator
    
    def getControlAnalysisOpt(prefixedId: String) =
        controlAnalysisMap.get(prefixedId)

    def getControlAnalysis(prefixedId: String) =
        controlAnalysisMap.get(prefixedId) orNull

    def controlElement(prefixedId: String) =
        getControlAnalysisOpt(prefixedId) map (control ⇒ staticStateDocument.documentWrapper.wrap(control.element))

    def hasAttributeControl(prefixedForAttribute: String) =
        _attributeControls.get(prefixedForAttribute).isDefined

    def getAttributeControl(prefixedForAttribute: String, attributeName: String) =
        _attributeControls.get(prefixedForAttribute) flatMap (_.get(attributeName)) orNull

    def hasControlByName(controlName: String) =
        controlTypes.get(controlName) exists (_.nonEmpty)

    def controlsByName(controlName: String): Traversable[ElementAnalysis] =
        controlTypes.get(controlName).toList flatMap (_.values)

    def hasControlAppearance(controlName: String, appearance: QName) =
        controlAppearances.get(controlName) exists (_(appearance))

    // Repeats
    def repeats = controlTypes.get("repeat") map (_.values map (_.asInstanceOf[RepeatControl])) getOrElse Seq.empty

    def getRepeatHierarchyString(ns: String) =
        controlTypes.get("repeat") map { repeats ⇒
            val repeatsWithAncestors =
                for {
                    repeat               ← repeats.values
                    namespacedPrefixedId = ns + repeat.prefixedId
                    ancestorRepeat       = repeat.ancestorRepeatsAcrossParts.headOption
                } yield
                    ancestorRepeat match {
                        case Some(ancestorRepeat) ⇒ namespacedPrefixedId + " " + ns + ancestorRepeat.prefixedId
                        case None                 ⇒ namespacedPrefixedId
                    }

            repeatsWithAncestors mkString ","
        } getOrElse ""

    def getTopLevelControls = Seq(controlTypes("root").values.head)

    def getTopLevelControlElements =
        Seq(controlTypes("root").values.head.element) ++
            (xblBindings.allGlobals map (_.compactShadowTree.getRootElement)) asJava

    def hasControls = getTopLevelControlElements.size > 0

    override def freeTransientState() = {
        super.freeTransientState()

        for (controlAnalysis ← controlAnalysisMap.values)
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
            case model: Model              ⇒ deindexModel(model)
            case handler: EventHandlerImpl ⇒ deregisterEventHandler(handler)
            case att: AttributeControl     ⇒ _attributeControls -= att.forPrefixedId
            case _                         ⇒
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
            case childrenBuilder: ChildrenBuilderTrait ⇒
                childrenBuilder.indexedElements foreach deindexControl

                if (! self)
                    childrenBuilder.children foreach
                        (_.removeFromParent())

            case _ ⇒
        }
    }
}