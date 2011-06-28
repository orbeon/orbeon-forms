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

import analysis.controls.{LHHATrait, SelectionControl, ViewTrait}
import org.apache.commons.lang.StringUtils

// NOTE: Tried to implement this directly in StaticStateGlobalOps but this caused AbstractMethodError at runtime! So
// instead we provide concrete methods in a separate trait, which seems to work. Move back to PartGlobalOps when possible.
trait PartGlobalOpsImpl extends PartGlobalOps {

    def getControlElement(prefixedId: String) = getControlAnalysisOption(prefixedId) map (_.element) orNull
    def hasNodeBinding(prefixedId: String) = getControlAnalysisOption(prefixedId) map (_.hasNodeBinding) getOrElse false

    def getControlPosition(prefixedId: String) = getControlAnalysisOption(prefixedId) match {
        case Some(viewTrait: ViewTrait) => viewTrait.index
        case _ => -1
    }

    def getSelect1Analysis(prefixedId: String) = getControlAnalysisOption(prefixedId) match {
        case Some(selectionControl: SelectionControl) => selectionControl
        case _ => null
    }

    def isValueControl(effectiveId: String) =
        getControlAnalysisOption(XFormsUtils.getPrefixedId(effectiveId)) map (_.canHoldValue) getOrElse false

    def getLabel(prefixedId: String) = getLHHA(prefixedId, "label")
    def getHelp(prefixedId: String) = getLHHA(prefixedId, "help")
    def getHint(prefixedId: String) = getLHHA(prefixedId, "hint")
    def getAlert(prefixedId: String) = getLHHA(prefixedId, "alert")

    def appendClasses(sb: java.lang.StringBuilder, prefixedId: String) =
        getControlAnalysisOption(prefixedId) foreach { controlAnalysis =>
            val controlClasses = controlAnalysis.classes
            if (StringUtils.isNotEmpty(controlClasses)) {
                if (sb.length > 0)
                    sb.append(' ')
                sb.append(controlClasses)
            }
        }

    def getControlAnalysisOption(prefixedId: String) = Option(getControlAnalysis(prefixedId))

    private def getLHHA(prefixedId: String, lhha: String) = getControlAnalysisOption(prefixedId) match {
        case Some(lhhaTrait: LHHATrait) => lhhaTrait.getLHHA(lhha)
        case _ => null
    }
}