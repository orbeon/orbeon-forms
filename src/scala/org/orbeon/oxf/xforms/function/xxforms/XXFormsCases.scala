/**
 *  Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.oxf.xforms.control.controls.XFormsSwitchControl
import org.orbeon.saxon.value.StringValue
import org.orbeon.saxon.om.{SequenceIterator, ListIterator, EmptyIterator}
import collection.JavaConverters._

/**
 * Extension xxforms:cases($switch-id as xs:string) as xs:string* function.
 */
class XXFormsCases extends XFormsFunction {
    override def iterate(xpathContext: XPathContext): SequenceIterator = {
        val switchStaticId = argument(0).evaluateAsString(xpathContext).toString

        Option(getXBLContainer(xpathContext).resolveObjectByIdInScope(getSourceEffectiveId(xpathContext), switchStaticId, null)) collect {
            case switchControl: XFormsSwitchControl if switchControl.isRelevant ⇒
                new ListIterator(switchControl.getChildrenCases.asScala map (_.getId) map StringValue.makeStringValue asJava)
        } getOrElse
                EmptyIterator.getInstance
    }
}
