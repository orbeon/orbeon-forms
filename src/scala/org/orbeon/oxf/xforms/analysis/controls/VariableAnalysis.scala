/**
 *  Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.oxf.xforms.XFormsStaticState
import org.orbeon.oxf.xforms.xbl.XBLBindings
import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis.VariableAnalysisTrait
import org.orbeon.oxf.util.PropertyContext
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.oxf.xml.ContentHandlerHelper


class VariableAnalysis(propertyContext: PropertyContext, staticState: XFormsStaticState, controlsDocumentInfo: DocumentWrapper,
                       scope: XBLBindings#Scope, element: Element, index: Int, isValueControl: Boolean,
                       containerAnalysis: ContainerAnalysis, inScopeVariables: java.util.Map[String, SimpleAnalysis])
        extends ControlAnalysis(propertyContext, staticState, controlsDocumentInfo, scope, element, index,
                isValueControl, containerAnalysis, inScopeVariables)
        with VariableAnalysisTrait {

    containerAnalysis.addContainedVariable(name, this)

    // Ideally the trait could directly override computeValueAnalysis but this doesn't work
    override def computeValueAnalysis = computeVariableValueAnalysis
    override def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper) = { toVariableXML(propertyContext, helper) }
}