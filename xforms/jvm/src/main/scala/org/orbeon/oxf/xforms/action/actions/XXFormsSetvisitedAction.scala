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
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.control.{VisitableTrait, XFormsContainerControl, XFormsControl}

class XXFormsSetvisitedAction extends XFormsAction {

  override def execute(context: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val visited = resolveBooleanAVT("visited", default = true)(context)
    val recurse = resolveBooleanAVT("recurse", default = false)(context)

    synchronizeAndRefreshIfNeeded(context)

    // This doesn't use `visitWithAncestors()` for now. Assuming that this action is used on targets that make sense,
    // for example on `fr:attachment`.
    resolveControlAvt("control")(context) foreach (applyToVisitable(_, _.visited = visited, recurse))

    //resolveControl("control")(context).iterator flatMap (ControlsIterator(_, includeSelf = true)) foreach (_.visited = visited)
  }

  private def applyToVisitable(control: XFormsControl, visit: VisitableTrait => Any, recurse: Boolean): Unit =
    if (control.isRelevant && ! control.isStaticReadonly)
      control match {
        case containerControl: XFormsContainerControl =>
          visit(containerControl)

          if (recurse)
            for (control <- containerControl.children)
              applyToVisitable(control, visit, recurse)

        case control: VisitableTrait =>
          visit(control)
        case _ =>
      }
}
