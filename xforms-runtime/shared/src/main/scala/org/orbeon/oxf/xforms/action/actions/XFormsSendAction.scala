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
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsSubmitEvent
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission
import org.orbeon.xforms.XFormsNames

/**
 * 10.1.10 The send Element
 */
class XFormsSendAction extends XFormsAction {

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val interpreter   = actionContext.interpreter
    val actionElement = actionContext.element

    // Find submission object
    val submissionId = actionElement.attributeValue(XFormsNames.SUBMISSION_QNAME)
    if (submissionId eq null)
      throw new OXFException("Missing mandatory submission attribute on xf:send element.")

    // Resolve AVT
    val resolvedSubmissionStaticId = interpreter.resolveAVTProvideValue(actionContext.analysis, submissionId)

    if (resolvedSubmissionStaticId eq null)
      return

    // Find actual target
    interpreter.resolveObject(actionContext.analysis, resolvedSubmissionStaticId) match {
      case submission: XFormsModelSubmission =>
        // Dispatch event to submission object
        val newEvent =
          new XFormsSubmitEvent(
            submission,
            XFormsAction.eventProperties(
              interpreter,
              actionContext.analysis,
              actionContext.interpreter.eventObserver,
              actionContext.collector
            )
          )
        Dispatch.dispatchEvent(newEvent, actionContext.collector)
      case _ =>
        // "If there is a null search result for the target object and the source object is an XForms action such as
        // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
        warn(
          "xf:send: submission does not refer to an existing xf:submission element, ignoring action",
          List("submission id" -> submissionId)
        )
    }
  }
}
