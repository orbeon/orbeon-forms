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

import analysis.ElementAnalysis
import analysis.model.Model
import xbl.XBLBindingsBase
import java.util.{List => JList}
import org.dom4j.Element
import org.orbeon.oxf.xml.NamespaceMapping

// Operations on a part that are used during static analysis
trait PartStaticAnalysisOps {

    def getNamespaceMapping(prefix: String, element: Element): NamespaceMapping                     // SA(to handle XBL resources)|GRUN

    def getModel(prefixedId: String): Model                                                         // SA
    def getDefaultModelForScope(scope: XBLBindingsBase.Scope): Model                                // SA
    def getModelByInstancePrefixedId(prefixedId: String): Model                                     // SA
    def getModelByScopeAndBind(scope: XBLBindingsBase.Scope, bindStaticId: String): Model           // SA
    def findInstancePrefixedId(startScope: XBLBindingsBase.Scope, instanceStaticId: String): String // SA

    def getModelsForScope(scope: XBLBindingsBase.Scope): JList[Model]                               // SA|RUN
    
    def getControlAnalysis(prefixedId: String): ElementAnalysis                                     // SA|GRUN
    def getResolutionScopeByPrefixedId(prefixedId: String): XBLBindingsBase.Scope                   // SA|GRUN
    def searchResolutionScopeByPrefixedId(prefixedId: String): XBLBindingsBase.Scope                // SA
}