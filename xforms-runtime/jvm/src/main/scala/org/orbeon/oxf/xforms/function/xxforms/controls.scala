/**
 * Copyright (C) 2016 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.BooleanValue
import org.orbeon.scaxon.Implicits._

class XXFormsIsControlRelevant extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): BooleanValue = {
    implicit val ctx = xpathContext
    implicit val xfc = XFormsFunction.context
    relevantControl(0).nonEmpty
  }

  // NOTE: We depend on MIPs so we cannot compute PathMap dependencies at this point.
}

trait SingleNodeControlMipFunction extends XFormsFunction {

  protected def getMip(c: XFormsSingleNodeControl): Boolean

  override def evaluateItem(xpathContext: XPathContext): BooleanValue = {
    implicit val ctx = xpathContext
    implicit val xfc = XFormsFunction.context
    relevantControl(0) collect { case c: XFormsSingleNodeControl => getMip(c) } contains true
  }

  // NOTE: We depend on MIPs so we cannot compute PathMap dependencies at this point.
}

class XXFormsIsControlReadonly extends SingleNodeControlMipFunction {
  protected def getMip(c: XFormsSingleNodeControl): Boolean = c.isReadonly
}

class XXFormsIsControlRequired extends SingleNodeControlMipFunction {
  protected def getMip(c: XFormsSingleNodeControl): Boolean = c.isRequired
}

class XXFormsIsControlValid extends SingleNodeControlMipFunction {
  protected def getMip(c: XFormsSingleNodeControl): Boolean = c.isValid
}