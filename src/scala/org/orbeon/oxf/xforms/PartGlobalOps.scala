/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms

import analysis.controls._
import analysis.ElementAnalysis
import analysis.model.Instance
import event.EventHandler
import org.dom4j.{Element, QName}
import java.util.{List ⇒ JList, Map ⇒ JMap}
import org.orbeon.oxf.xml.SAXStore
import xbl.{Scope, XBLBindings, ConcreteBinding}
import org.apache.commons.lang.StringUtils

trait PartGlobalOps {

    // Global
    def getElementMark(prefixedId: String): SAXStore#Mark                                           // GRUN

    // Models
    def getInstances(modelPrefixedId: String): java.util.Collection[Instance]                       // RUN

    // Controls
    def getControlAnalysis(prefixedId: String): ElementAnalysis                                     // SA|GRUN
    def hasControlByName(controlName: String): Boolean                                              // GRUN
    def hasControlAppearance(controlName: String, appearance: QName): Boolean                       // GRUN
    def hasInputPlaceholder: Boolean

    // Events
    def hasHandlerForEvent(eventName: String): Boolean                                              // GRUN
    def hasHandlerForEvent(eventName: String, includeAllEvents: Boolean): Boolean                   // GRUN
    def getKeyHandlers: JList[EventHandler]                                                         // GRUN

    // XBL
    def isComponent(binding: QName): Boolean                                                        // GRUN
    def getBinding(prefixedId: String): ConcreteBinding                                             // GRUN
    def getBindingId(prefixedId: String): String                                                    // GRUN
    def getBindingQNames: Seq[QName]                                                                // GRUN
    def getGlobals: collection.Map[QName, XBLBindings#Global]                                       // GRUN
    def getResolutionScopeByPrefix(prefix: String): Scope                               // GRUN
    def getResolutionScopeByPrefixedId(prefixedId: String): Scope                       // SA|GRUN

    // Repeats
    def addMissingRepeatIndexes(repeatIdToIndex: JMap[String, java.lang.Integer])                   // GRUN
    def getRepeatHierarchyString: String                                                            // GRUN

    // AVTs
    def hasAttributeControl(prefixedForAttribute: String): Boolean                                  // GRUN
    def getAttributeControl(prefixedForAttribute: String, attributeName: String): AttributeControl  // GRUN

    // Client-side resources
    def scripts: collection.Map[String, Script]                                                     // GRUN
    def getXBLStyles: Seq[Element]                                                                  // GRUN
    def getXBLScripts: Seq[Element]                                                                 // GRUN
    def baselineResources: (collection.Set[String], collection.Set[String])                         // GRUN

    // Functions derived from getControlAnalysis
    def getControlAnalysisOption(prefixedId: String) = Option(getControlAnalysis(prefixedId))
    def getControlElement(prefixedId: String) = getControlAnalysisOption(prefixedId) map (_.element) orNull
    def hasNodeBinding(prefixedId: String) = getControlAnalysisOption(prefixedId) map (_.hasNodeBinding) getOrElse false

    def getControlPosition(prefixedId: String) = getControlAnalysisOption(prefixedId) match {
        case Some(viewTrait: ViewTrait) ⇒ viewTrait.index
        case _ ⇒ -1
    }

    def getSelect1Analysis(prefixedId: String) = getControlAnalysisOption(prefixedId) match {
        case Some(selectionControl: SelectionControl) ⇒ selectionControl
        case _ ⇒ null
    }

    def isValueControl(effectiveId: String) =
        getControlAnalysisOption(XFormsUtils.getPrefixedId(effectiveId)) map (_.canHoldValue) getOrElse false

    def appendClasses(sb: java.lang.StringBuilder, prefixedId: String) =
        getControlAnalysisOption(prefixedId) foreach { controlAnalysis ⇒
            val controlClasses = controlAnalysis.classes
            if (StringUtils.isNotEmpty(controlClasses)) {
                if (sb.length > 0)
                    sb.append(' ')
                sb.append(controlClasses)
            }
        }

    def getLabel(prefixedId: String) = getLHHA(prefixedId, "label")
    def getHelp(prefixedId: String) = getLHHA(prefixedId, "help")
    def getHint(prefixedId: String) = getLHHA(prefixedId, "hint")
    def getAlert(prefixedId: String) = getLHHA(prefixedId, "alert")

    private def getLHHA(prefixedId: String, lhha: String) =
        getControlAnalysisOption(prefixedId) match {
            case Some(lhhaTrait: LHHATrait) ⇒ lhhaTrait.getLHHA(lhha).orNull
            case _ ⇒ null
        }
}