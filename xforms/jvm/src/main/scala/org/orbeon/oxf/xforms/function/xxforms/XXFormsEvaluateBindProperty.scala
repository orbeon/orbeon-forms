/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.RuntimeBind
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.Item

class XXFormsEvaluateBindProperty extends XFormsFunction {
  override def evaluateItem(xpathContext: XPathContext): Item = {

    implicit val ctx = xpathContext

    val bindId   = stringArgument(0)
    val mipQName = getQNameFromExpression(arguments(1))(xpathContext)

    XFormsFunction.context.container.resolveObjectByIdInScope(getSourceEffectiveId, bindId, bindingContext.singleItemOpt) collect {
      case bind: RuntimeBind => bind
    } flatMap { bind =>
      bind.model.modelBindsOpt.flatMap(_.evaluateBindByType(bind, position = 1, mipType = mipQName))
    } orNull
  }
}