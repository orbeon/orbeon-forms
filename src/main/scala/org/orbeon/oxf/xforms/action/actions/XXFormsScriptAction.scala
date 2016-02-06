/**
 *  Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}

// Extension `xxf:script` action, also available as `xf:action`.
class XXFormsScriptAction extends XFormsAction {

  override def execute(actionContext: DynamicActionContext): Unit = {

    val actionInterpreter = actionContext.interpreter
    val actionElement     = actionContext.element
    val bindingContext    = actionInterpreter.actionXPathContext.getCurrentBindingContext

    val partAnalysis = actionInterpreter.actionXPathContext.container.getPartAnalysis

    partAnalysis.scriptsByPrefixedId(actionInterpreter.getActionPrefixedId(actionElement)) match {
      case script @ StaticScript(_, JavaScriptScriptType, paramExpressions, _) ⇒
          actionInterpreter.containingDocument.addScriptToRun(
            script,
            actionContext.interpreter.event,
            actionContext.interpreter.eventObserver,
            // https://github.com/orbeon/orbeon-forms/issues/2499
            paramExpressions map { expr ⇒
              actionInterpreter.evaluateAsString(
                actionElement,
                bindingContext.nodeset,
                bindingContext.position,
                expr
              )
            }
          )
      case StaticScript(_, XPathScriptType, params, ShareableScript(_, _, body, _)) ⇒
        // Evaluate XPath expression for its side effects only
        actionInterpreter.evaluateKeepItems(
          actionElement,
          bindingContext.nodeset,
          bindingContext.position,
          body
        )
    }
  }
}
