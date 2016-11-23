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
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.InstanceData
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.control.{XFormsContainerControl, XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.model.BindNode

class XXFormsUpdateValidityAction extends XFormsAction {

  override def execute(context: DynamicActionContext): Unit = {

    implicit val cxt = context

    val recurse = resolveBooleanAVT("recurse", default = false)
    synchronizeAndRefreshIfNeeded(context)

    resolveStringAVT("control").to[List] flatMap
      (_.splitTo[List]())                flatMap
      resolveControl                     foreach
      (XXFormsUpdateValidityAction.updateValidity(_, recurse))
  }
}

object XXFormsUpdateValidityAction {

  def updateValidity(initialControl: XFormsControl, recurse: Boolean): Unit = {

    var scheduleRefresh = false

    def updateOneThenRecurse(control: XFormsControl): Unit =
      if (control.isRelevant && ! control.isStaticReadonly) {

        for {
          singleNodeControl ← collectByErasedType[XFormsSingleNodeControl](control)
          if singleNodeControl.staticControl.explicitValidation
        } locally {

          val previousValid             = singleNodeControl.isValid
          val previousAlertLevel        = singleNodeControl.alertLevel
          val previousFailedValidations = singleNodeControl.failedValidations

          singleNodeControl.readValidation()

          scheduleRefresh |=
            previousValid             != singleNodeControl.isValid           ||
            previousAlertLevel        != singleNodeControl.alertLevel        ||
            previousFailedValidations != singleNodeControl.failedValidations
        }

        if (recurse)
          collectByErasedType[XFormsContainerControl](control) foreach
            (_.children foreach updateOneThenRecurse)
      }

    updateOneThenRecurse(initialControl)

    // This is important as we don't have a guarantee that other changes will cause a refresh
    if (scheduleRefresh)
      initialControl.containingDocument.getControls.requireRefresh()
  }

}