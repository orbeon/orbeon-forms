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
package org.orbeon.oxf.xforms.analysis.model

import org.orbeon.oxf.xforms.XFormsStaticState
import org.orbeon.oxf.xforms.xbl.XBLBindings
import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis
import org.orbeon.oxf.xforms.analysis.VariableAnalysisTrait
import org.orbeon.oxf.util.PropertyContext
import org.orbeon.oxf.xml.ContentHandlerHelper


class ModelVariableAnalysis(staticState: XFormsStaticState , scope: XBLBindings#Scope, element: Element,
                       parentAnalysis: SimpleAnalysis, inScopeVariables: java.util.Map[String, SimpleAnalysis],
                       containingModel: Model)
        extends ModelAnalysis(staticState, scope, element, parentAnalysis, inScopeVariables, true, containingModel)
        with VariableAnalysisTrait {

    // Ideally the trait could directly override computeValueAnalysis but this doesn't work
    override def computeValueAnalysis = computeVariableValueAnalysis
    def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper) = { toVariableXML(propertyContext, helper) }
}
