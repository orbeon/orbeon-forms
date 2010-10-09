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
package org.orbeon.oxf.xforms.analysis.model

import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis
import org.dom4j.Element
import org.orbeon.oxf.xforms.xbl.XBLBindings
import org.orbeon.oxf.xforms.XFormsStaticState

class ModelAnalysis(staticState: XFormsStaticState, scope: XBLBindings#Scope, element: Element,
                    parentAnalysis: SimpleAnalysis, val inScopeVariables: java.util.Map[String, SimpleAnalysis],
                    canHoldValue: Boolean, containingModel: Model)
        extends SimpleAnalysis(staticState, scope, element, parentAnalysis, canHoldValue, containingModel) {

    def getInScopeVariables = inScopeVariables
}