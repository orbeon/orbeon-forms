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
package org.orbeon.oxf.xforms.function

import org.orbeon.oxf.xforms.function.xxforms.XXFormsMIPFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.BooleanValue
import org.orbeon.scaxon.Implicits.*

/**
 * xf:valid() as xs:boolean
 * xf:valid($items as item()*) as xs:boolean
 * xf:valid($items as item()*, $relevant as xs:boolean) as xs:boolean
 * xf:valid($items as item()*, $relevant as xs:boolean, $recurse as xs:boolean) as xs:boolean
 */
class XFormsValid extends XXFormsMIPFunction {

  override def evaluateItem(xpathContext: XPathContext): BooleanValue = {

    implicit val xpc = xpathContext

    val pruneNonRelevant = booleanArgument(1, default = true)
    val recurse          = booleanArgument(2, default = true)

    ValidSupport.isValid(asScalaIterator(itemsArgumentOrContextOpt(0)), pruneNonRelevant, recurse)
  }
}

/**
 * xxf:valid() as xs:boolean
 * xxf:valid($item as item()*) as xs:boolean
 * xxf:valid($item as item()*, $recurse as xs:boolean) as xs:boolean
 * xxf:valid($item as item()*, $recurse as xs:boolean, $relevant as xs:boolean) as xs:boolean
 */
class XXFormsValid extends XXFormsMIPFunction {

  override def evaluateItem(xpathContext: XPathContext): BooleanValue = {

    implicit val xpc = xpathContext

    val recurse          = booleanArgument(1, default = false)
    val pruneNonRelevant = booleanArgument(2, default = false)

    ValidSupport.isValid(asScalaIterator(itemsArgumentOrContextOpt(0)), pruneNonRelevant, recurse)
  }
}
