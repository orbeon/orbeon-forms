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
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.control.{XFormsContainerControl, XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.model.{BindNode, InstanceData}

import scala.collection.compat._

class XXFormsUpdateValidityAction extends XFormsAction {

  override def execute(context: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    implicit val cxt = context

    val recurse = resolveBooleanAVT("recurse", default = false)
    synchronizeAndRefreshIfNeeded(context)

    resolveStringAVT("control").to(List) flatMap
      (_.splitTo[List]())                flatMap
      resolveControl                     foreach
      (XXFormsUpdateValidityAction.updateValidity(_, recurse, context.collector))
  }
}

object XXFormsUpdateValidityAction {

  def updateValidity(initialControl: XFormsControl, recurse: Boolean, collector: ErrorEventCollector): Unit = {

    var first = true

    def updateOneThenRecurse(control: XFormsControl): Unit =
      if (control.isRelevant && ! control.isStaticReadonly) {

        for {
          singleNodeControl <- collectByErasedType[XFormsSingleNodeControl](control)
          if singleNodeControl.staticControl.explicitValidation
          controlValidation = singleNodeControl.getValidation
          nodeValidation    = singleNodeControl.readValidation
          if controlValidation != nodeValidation
        } locally {

          // This is important as we don't have a guarantee that other changes will cause a refresh
          if (first) {
            val doc = initialControl.containingDocument
            doc.controls.cloneInitialStateIfNeeded(collector)
            doc.requireRefresh()
            first = false
          }

          nodeValidation foreach singleNodeControl.setValidation
        }

        if (recurse)
          collectByErasedType[XFormsContainerControl](control) foreach
            (_.children foreach updateOneThenRecurse)
      }

    updateOneThenRecurse(initialControl)
  }

}