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
package org.orbeon.oxf.fb

import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.scaxon.XPath.*
import org.orbeon.xml.NamespaceMapping

import scala.util.control.NonFatal

object DataModel {

  // Unused as of 2017-10-11
  def isAllowedBindingExpression(control: XFormsControl, expr: String): Boolean = {

    def evaluateBoundItem(namespaces: NamespaceMapping) =
      Option(evalOne(control.bindingContext.contextItem, expr, namespaces, null, inScopeContainingDocument.getRequestStats.addXPathStat))

    try {
      control.bind flatMap
        (bind => evaluateBoundItem(bind.staticBind.namespaceMapping)) exists
          (XFormsControl.isAllowedBoundItem(control, _))
    } catch {
      case NonFatal(_) => false
    }
  }
}
