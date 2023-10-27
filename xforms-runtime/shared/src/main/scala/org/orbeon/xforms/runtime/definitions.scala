package org.orbeon.xforms.runtime

import org.orbeon.dom
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.event.XFormsEvent.SimpleProperties
import org.orbeon.oxf.xforms.submission.SubmissionParameters


case class ErrorInfo(
  element : dom.Element,
  message : String
)

trait XFormsObject {
  def getEffectiveId     : String
  def containingDocument : XFormsContainingDocument
}

case class SimplePropertyValue(
  name  : String,
  value : String,
  tunnel: Boolean
)

case class DelayedEvent(
  eventName              : String,                      // for dispatch
  targetEffectiveId      : String,                      // for dispatch
  bubbles                : Boolean,                     // for dispatch
  cancelable             : Boolean,                     // for dispatch
  time                   : Option[Long],                // for scheduling
  showProgress           : Boolean,                     // for XHR response, whether to show the progress indicator when submitting the event
  browserTarget          : Option[String],              // for XHR response, optional browser target for submit events
  submissionId           : Option[String],              // for XHR response
  isResponseResourceType : Boolean,                     // for XHR response
  properties             : SimpleProperties,            // for event dispatch
  submissionParameters   : Option[SubmissionParameters] // for event dispatch
)
