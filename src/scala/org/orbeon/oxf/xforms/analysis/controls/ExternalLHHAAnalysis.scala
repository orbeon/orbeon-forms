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
import org.dom4j._
import org.orbeon.oxf.xforms._
import analysis._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import xbl.XBLBindingsBase

/**
 * Represent an LHHA element with a "for" attribute. Behaves like a control from the point of view of static analysis.
 */
class ExternalLHHAAnalysis(staticStateContext: StaticStateContext, element: Element, parent: ContainerTrait, preceding: Option[ElementAnalysis], scope: XBLBindingsBase.Scope)
        extends LHHAAnalysis(staticStateContext, element, parent, preceding, scope) with ViewTrait {

    val isLocal = false

    /**
     * Attach this LHHA to a control, assuming we are an external LHHA.
     */
    def attachToControl() {
        val forAttribute = element.attributeValue(XFormsConstants.FOR_QNAME)
        assert(forAttribute ne null)

        // Try to find associated control
        staticStateContext.partAnalysis.getControlAnalysis(scope.getPrefixedIdForStaticId(forAttribute)) match {
            case lhhaControl: LHHATrait => lhhaControl.setExternalLHHA(ExternalLHHAAnalysis.this)
            case _ => staticStateContext.partAnalysis.getIndentedLogger.logWarning("", "cannot attach exernal LHHA to control",
                Array("type", element.getName, "element", Dom4jUtils.elementToDebugString(element)): _*)
        }
    }
}
