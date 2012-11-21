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
import org.orbeon.oxf.xforms.analysis.model.{Model, Instance}
import event.EventHandler
import org.dom4j.{Element, QName}
import java.util.{List ⇒ JList, Collection ⇒ JCollection}
import org.orbeon.oxf.xml.SAXStore
import xbl.{Scope, XBLBindings, ConcreteBinding}
import org.apache.commons.lang3.StringUtils
import collection.JavaConverters._

trait PartGlobalOps {

    // Global
    def getMark(prefixedId: String): SAXStore#Mark

    // Models
    def getModelsForScope(scope: Scope): Seq[Model]
    def jGetModelsForScope(scope: Scope) = getModelsForScope(scope).asJava
    def getInstances(modelPrefixedId: String): JCollection[Instance]

    // Controls
    def getControlAnalysis(prefixedId: String): ElementAnalysis
    def hasControlByName(controlName: String): Boolean
    def controlsByName(controlName: String): Traversable[ElementAnalysis]
    def hasControlAppearance(controlName: String, appearance: QName): Boolean
    def hasInputPlaceholder: Boolean

    // Events
    def hasHandlerForEvent(eventName: String): Boolean
    def hasHandlerForEvent(eventName: String, includeAllEvents: Boolean): Boolean
    def getKeyHandlers: JList[EventHandler]

    // XBL
    def isComponent(binding: QName): Boolean
    def getBinding(prefixedId: String): Option[ConcreteBinding]
    def getBindingId(prefixedId: String): String
    def getBindingQNames: Seq[QName]
    def getGlobals: collection.Map[QName, XBLBindings#Global]

    // Return the scope associated with the given prefixed id (the scope is directly associated with the prefix of the id)
    def containingScope(prefixedId: String): Scope
    def scopeForPrefixedId(prefixedId: String): Scope

    // Repeats
    def repeats: Traversable[RepeatControl]
    def getRepeatHierarchyString(ns: String): String

    // AVTs
    def hasAttributeControl(prefixedForAttribute: String): Boolean
    def getAttributeControl(prefixedForAttribute: String, attributeName: String): AttributeControl

    // Client-side resources
    def scripts: Map[String, Script]
    def uniqueClientScripts: Seq[(String, String)]
    def getXBLStyles: Seq[Element]
    def getXBLScripts: Seq[Element]
    def baselineResources: (collection.Set[String], collection.Set[String])

    // Functions derived from getControlAnalysis
    def getControlAnalysisOption(prefixedId: String) = Option(getControlAnalysis(prefixedId))
    def getControlElement(prefixedId: String) = getControlAnalysisOption(prefixedId) map (_.element) orNull
    def hasBinding(prefixedId: String) = getControlAnalysisOption(prefixedId) map (_.hasBinding) getOrElse false

    def getControlPosition(prefixedId: String) = getControlAnalysisOption(prefixedId) match {
        case Some(viewTrait: ViewTrait) ⇒ viewTrait.index
        case _ ⇒ -1
    }

    def getSelect1Analysis(prefixedId: String) = getControlAnalysisOption(prefixedId) match {
        case Some(selectionControl: SelectionControlTrait) ⇒ selectionControl
        case _ ⇒ null
    }

    def isValueControl(effectiveId: String) =
        getControlAnalysisOption(XFormsUtils.getPrefixedId(effectiveId)) map (_.isInstanceOf[ValueTrait]) getOrElse false

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

    def getLHHA(prefixedId: String, lhha: String) =
        getControlAnalysisOption(prefixedId) match {
            case Some(lhhaTrait: LHHATrait) ⇒ lhhaTrait.getLHHA(lhha).orNull
            case _ ⇒ null
        }
}