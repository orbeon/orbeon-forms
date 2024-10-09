package org.orbeon.xforms.runtime

import org.orbeon.dom
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.event.XFormsEvent.{EmptyGetter, PropertyGetter, SimpleProperties}
import org.orbeon.oxf.xforms.model.XFormsModel
import org.orbeon.oxf.xforms.submission.SubmissionParameters
import org.orbeon.oxf.xforms.xbl.XBLContainer


case class ErrorInfo(
  element : dom.Element,
  message : String
)

trait XFormsObject {
  def effectiveId       : String
  def containingDocument: XFormsContainingDocument
  def container         : XBLContainer
  def modelOpt          : Option[XFormsModel]
  def elementAnalysis   : ElementAnalysis
}

case class SimplePropertyValue(
  name  : String,
  value : java.io.Serializable, // simple serializable types returned by `evaluateKeepNodeInfo()`
  tunnel: Boolean
)

case class DelayedEvent(
  eventName             : String,                      // for dispatch
  targetEffectiveId     : String,                      // for dispatch
  bubbles               : Boolean,                     // for dispatch
  cancelable            : Boolean,                     // for dispatch
  time                  : Option[Long],                // for scheduling
  showProgress          : Boolean,                     // for XHR response, whether to show the progress indicator when submitting the event
  browserTarget         : Option[String],              // for XHR response, optional browser target for submit events
  submissionId          : Option[String],              // for XHR response
  isResponseResourceType: Boolean,                     // for XHR response
  stringProperties      : SimpleProperties,            // for event dispatch
  submissionParameters  : Option[SubmissionParameters] // for event dispatch
) {

  private def fromEventName: PropertyGetter = {
    case SubmissionParameters.EventName => submissionParameters
  }

  private def fromSubmissionParameters: PropertyGetter =
    submissionParameters.flatMap(_.actionProperties).getOrElse(EmptyGetter)

  private def fromProperties: PropertyGetter =
    new PartialFunction[String, Option[Any]] {
      def isDefinedAt(key: String): Boolean     = stringProperties.exists(_.name == key)
      def apply      (key: String): Option[Any] = stringProperties.collectFirst { case SimplePropertyValue(`key`, value, _) => value }
    }

  def properties: PropertyGetter =
    fromEventName.orElse(fromSubmissionParameters).orElse(fromProperties)
}
