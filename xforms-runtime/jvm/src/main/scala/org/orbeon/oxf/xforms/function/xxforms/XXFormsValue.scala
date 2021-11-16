/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.SequenceIterator
import org.orbeon.scaxon.Implicits._
import shapeless.syntax.typeable._

class XXFormsValue extends XFormsFunction {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {
    implicit val ctx = xpathContext
    relevantControls(0, booleanArgument(1, default = true)) flatMap (_.narrowTo[XFormsValueControl]) flatMap (_.valueOpt)
  }

  // TODO: PathMap
}

class XXFormsFormattedValue extends XFormsFunction {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {
    implicit val ctx = xpathContext
    relevantControls(0, booleanArgument(1, default = true)) flatMap (_.narrowTo[XFormsValueControl]) flatMap (_.getFormattedValue)
  }

  // TODO: PathMap
}