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

import org.orbeon.oxf.xforms.analysis.{XPathAnalysis, SimpleElementAnalysis}
import org.orbeon.saxon.om.Item
import org.orbeon.oxf.xforms.model.DataModel

/**
 * Trait representing an element supporting a value, whether the string value of the binding node or whether through
 * a @value attribute.
 */
trait ValueTrait extends SimpleElementAnalysis with SingleNodeTrait {

    override protected def computeValueAnalysis: Option[XPathAnalysis] = {
        val subExpression = if (value.isDefined) "string((" + value.get + ")[1])" else "string(.)"
        Some(analyzeXPath(getChildrenContext, subExpression))
    }

    override def isAllowedBoundItem(item: Item) = DataModel.isAllowedValueBoundItem(item)

    // TODO: Move value handling from ElementAnalysis to here? Need base trait to handle value controls, variables, and LHHA.
}
