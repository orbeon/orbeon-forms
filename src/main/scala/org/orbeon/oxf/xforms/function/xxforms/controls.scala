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

class XXFormsIsControlRelevant extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): BooleanValue =
    relevantControl(0)(xpathContext).nonEmpty

  // TODO: PathMap
}

class XXFormsIsControlReadonly extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): BooleanValue =
    relevantControl(0)(xpathContext) collect { case c: XFormsSingleNodeControl ⇒ c.isReadonly } contains true

  // TODO: PathMap
}

class XXFormsIsControlRequired extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): BooleanValue =
    relevantControl(0)(xpathContext) collect { case c: XFormsSingleNodeControl ⇒ c.isRequired } contains true

  // TODO: PathMap
}

class XXFormsIsControlValid extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): BooleanValue =
    relevantControl(0)(xpathContext) collect { case c: XFormsSingleNodeControl ⇒ c.isValid } contains true

  // TODO: PathMap
}