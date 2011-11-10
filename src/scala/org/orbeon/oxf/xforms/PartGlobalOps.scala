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
import control.XFormsControlFactory
import event.XFormsEventHandler
import xbl.XBLBindingsBase.Global
import org.dom4j.{Element, QName}
import java.util.{List => JList, Map => JMap}
import org.orbeon.oxf.xml.SAXStore
import xbl.{ConcreteBinding, XBLBindingsBase}

trait PartGlobalOps {

    // Global
    def getElementMark(prefixedId: String): SAXStore#Mark                                           // GRUN

    // Models
    def getInstances(modelPrefixedId: String): java.util.Collection[Instance]                       // RUN

    // Controls
    def getControlAnalysis(prefixedId: String): ElementAnalysis                                     // SA|GRUN
    def getAncestorControlForAction(prefixedId: String): Option[String]
    def hasControlByName(controlName: String): Boolean                                              // GRUN
    def hasControlAppearance(controlName: String, appearance: QName): Boolean                       // GRUN
    def hasInputPlaceholder: Boolean

    // Events
    def hasHandlerForEvent(eventName: String): Boolean                                              // GRUN
    def hasHandlerForEvent(eventName: String, includeAllEvents: Boolean): Boolean                   // GRUN
    def getKeyHandlers: JList[XFormsEventHandler]                                                   // GRUN

    // XBL
    def getComponentFactory(qName: QName): XFormsControlFactory.Factory                             // GRUN
    def isComponent(binding: QName): Boolean                                                        // GRUN
    def getBinding(prefixedId: String): ConcreteBinding                                             // GRUN
    def getBindingId(prefixedId: String): String                                                    // GRUN
    def getBindingQNames: Seq[QName]                                                                // GRUN
    def getGlobals: collection.Map[QName, Global]                                                   // GRUN
    def getResolutionScopeByPrefix(prefix: String): XBLBindingsBase.Scope                           // GRUN
    def getResolutionScopeByPrefixedId(prefixedId: String): XBLBindingsBase.Scope                   // SA|GRUN

    // Repeats
    def addMissingRepeatIndexes(repeatIdToIndex: JMap[String, java.lang.Integer])                   // GRUN
    def getRepeatHierarchyString: String                                                            // GRUN

    // AVTs
    def hasAttributeControl(prefixedForAttribute: String): Boolean                                  // GRUN
    def getAttributeControl(prefixedForAttribute: String, attributeName: String): AttributeControl  // GRUN

    // Client-side resources
    def getScripts: collection.Map[String, Script]                                                  // GRUN
    def getXBLStyles: Seq[Element]                                                                  // GRUN
    def getXBLScripts: Seq[Element]                                                                 // GRUN
    def baselineResources: (collection.Set[String], collection.Set[String])                         // GRUN

    // Functions derived from getControlAnalysis
    def getControlAnalysisOption(prefixedId: String): Option[ElementAnalysis]
    def getControlElement(prefixedId: String): Element
    def hasNodeBinding(prefixedId: String): Boolean
    def getControlPosition(prefixedId: String): Int
    def getSelect1Analysis(prefixedId: String): SelectionControl
    def isValueControl(effectiveId: String): Boolean
    def appendClasses(sb: java.lang.StringBuilder, prefixedId: String)
    def getLabel(prefixedId: String): LHHAAnalysis
    def getHelp(prefixedId: String): LHHAAnalysis
    def getHint(prefixedId: String): LHHAAnalysis
    def getAlert(prefixedId: String): LHHAAnalysis
}