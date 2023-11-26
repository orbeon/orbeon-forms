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
package org.orbeon.oxf.xforms.submission

import cats.syntax.option._
import org.orbeon.connection.ConnectionResult
import org.orbeon.oxf.externalcontext.{ExternalContext, ResponseWrapper}
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.xforms.event.events.{ErrorType, XFormsSubmitErrorEvent}


object AllReplacer extends Replacer {

  type DeserializeType = Unit

  def deserialize(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters
  ): DeserializeType = ()

  def replace(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters,
    value               : DeserializeType
  )(implicit
    refContext          : RefContext
  ): ReplaceResult = {

    // When we get here, we are in a mode where we need to send the reply directly to an external context, if any.

    // Remember that we got a submission producing output
    submission.containingDocument.setGotSubmissionReplaceAll()

    val replaceAllResponse =
      new AllReplacer.ReplaceAllResponse(submission.containingDocument.responseForReplaceAll getOrElse (throw new IllegalStateException))

    XFormsModelSubmissionSupport.forwardResultToResponse(cxr, replaceAllResponse)

    // Success: "the event `xforms-submit-done` may be dispatched with appropriate context information"
    // Error: "either the document is replaced with an implementation-specific indication of an error or submission
    // processing concludes after dispatching `xforms-submit-error` with appropriate context information, including an
    // `error-type` of `resource-error`"
    if (! submissionParameters.isDeferredSubmission) {
      if (StatusCode.isSuccessCode(cxr.statusCode))
        ReplaceResult.SendDone(cxr, None)
      else
        // Here we dispatch `xforms-submit-error` upon getting a non-success error code, even though the response has
        // already been written out. This gives the form author a chance to do something in cases the response is
        // buffered, for example do a `sendError()`.
        ReplaceResult.SendError(
          new XFormsSubmissionException(
            submission       = submission,
            message          = s"xf:submission for submission id `${submission.getId}`, error code received when submitting instance: `${cxr.statusCode}`",
            description      = "processing submission response",
            submitErrorEvent = new XFormsSubmitErrorEvent(
              target           = submission,
              errorType        = ErrorType.ResourceError,
              cxrOpt           = cxr.some,
              tunnelProperties = submissionParameters.tunnelProperties
            )
          ),
          Left(cxr.some),
          submissionParameters.tunnelProperties
        )
    } else {
      // Two reasons:
      //
      // 1. We don't want to modify the document state
      // 2. This can be called outside of the document lock, see XFormsServer.
      ReplaceResult.None
    }
  }

  private class ReplaceAllResponse(val response: ExternalContext.Response)
    extends ResponseWrapper(response) {

    private var status = -1 // indicate that status was not set

    override def setStatus(status: Int): Unit = {
      assert(status > 0)
      this.status = status
      super.setStatus(status)
    }

//    def getStatus: Int = status
  }
}