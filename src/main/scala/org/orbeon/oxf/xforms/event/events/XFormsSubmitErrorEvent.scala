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
package org.orbeon.oxf.xforms.event.events

import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.util.ConnectionResult
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent._
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget}


class XFormsSubmitErrorEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsEvent(XFORMS_SUBMIT_ERROR, target, properties, bubbles = true, cancelable = false)
    with SubmitResponseEvent {

  def this(target: XFormsEventTarget) = {
    this(target, Map("error-type" → Some(XXFORMS_INTERNAL_ERROR.name)))
    _errorType = XXFORMS_INTERNAL_ERROR
  }

  // Event can be dispatched before the resource URI is resolved so the resource URI is optional
  def this(target: XFormsEventTarget, resourceURI: Option[String], errorType: ErrorType, statusCode: Int) = {
    this(
      target     = target,
      properties = Map(
        "error-type"           → Some(errorType.name),
        "resource-uri"         → resourceURI,
        "response-status-code" → Some(statusCode)
      )
    )
    _errorType = errorType
  }

  def this(target: XFormsEventTarget, errorType: ErrorType, connectionResult: ConnectionResult) = {
    this(
      target     = target,
      properties = Map("error-type" → Some(errorType.name))
    )
    _errorType = errorType
    _connectionResult = Option(connectionResult)
  }

  private[this] var _errorType: ErrorType = _
  def errorType = _errorType

  private[this] var _connectionResult: Option[ConnectionResult] = None
  def connectionResult = _connectionResult

  def logThrowable(throwable: Throwable): Unit =
    if (errorType != VALIDATION_ERROR)
      indentedLogger.logError("xforms-submit-error", "setting throwable", "throwable", OrbeonFormatter.format(throwable))

  def logMessage(throwable: Throwable): Unit =
    if (errorType != VALIDATION_ERROR)
      indentedLogger.logError("xforms-submit-error", OrbeonFormatter.message(throwable))
}

object XFormsSubmitErrorEvent {

  sealed abstract class ErrorType(val name: String)
  object SubmissionInProgress  extends ErrorType("submission-in-progress")
  object NoData                extends ErrorType("no-data")
  object ValidationError       extends ErrorType("validation-error")
  object ResourceError         extends ErrorType("resource-error")
  object ParseError            extends ErrorType("parse-error")
  object TargetError           extends ErrorType("target-error")
  object XXFormsPendingUploads extends ErrorType("xxforms-pending-uploads")
  object XXFormsInternalError  extends ErrorType("xxforms-internal-error")

  // For Java callers
  def SUBMISSION_IN_PROGRESS  = SubmissionInProgress
  def NO_DATA                 = NoData
  def VALIDATION_ERROR        = ValidationError
  def RESOURCE_ERROR          = ResourceError
  def PARSE_ERROR             = ParseError
  def TARGET_ERROR            = TargetError
  def XXFORMS_PENDING_UPLOADS = XXFormsPendingUploads
  def XXFORMS_INTERNAL_ERROR  = XXFormsInternalError
}