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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.dom.Element
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.event.XFormsEvents.DOM_ACTIVATE
import org.orbeon.oxf.xforms.event.events.XFormsSubmitEvent
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.xforms.XFormsNames.SUBMISSION_QNAME


/**
 * xf:submit control.
 */
class XFormsSubmitControl(container: XBLContainer, parent: XFormsControl, element: Element, _effectiveId: String)
    extends XFormsTriggerControl(container, parent, element, _effectiveId) {

  override def performDefaultAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = {
    // Do the default stuff upon receiving a DOMActivate event
    if (event.name == DOM_ACTIVATE) {

      // Find submission id
      val submissionId =
        Option(element.attributeValue(SUBMISSION_QNAME)) getOrElse
        (throw new ValidationException("xf:submit requires a submission attribute.", getLocationData))

      resolve(submissionId) match {
        case Some(submission: XFormsModelSubmission) =>
          // Submission found, dispatch xforms-submit event to it
          Dispatch.dispatchEvent(new XFormsSubmitEvent(submission), collector)
        case _ =>
          // "If there is a null search result for the target object and the source object is an XForms action such as
          // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
          debug("submission does not refer to an existing xf:submission element, ignoring action",
            Seq("submission id" -> submissionId))
      }
    }
    super.performDefaultAction(event, collector)
  }
}