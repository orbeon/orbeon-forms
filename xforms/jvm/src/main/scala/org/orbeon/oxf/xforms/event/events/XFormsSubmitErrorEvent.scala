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

import enumeratum._
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.util.ConnectionResult
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget}

class XFormsSubmitErrorEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsEvent(XFORMS_SUBMIT_ERROR, target, properties, bubbles = true, cancelable = false)
    with SubmitResponseEvent {

  def this(target: XFormsEventTarget) = {
    this(target, Map("error-type" -> Some(ErrorType.XXFormsInternalError.entryName)))
    _errorType = ErrorType.XXFormsInternalError
  }

  // Event can be dispatched before the resource URI is resolved so the resource URI is optional
  def this(target: XFormsEventTarget, resourceURI: Option[String], errorType: ErrorType, statusCode: Int) = {
    this(
      target     = target,
      properties = Map(
        "error-type"           -> Some(errorType.entryName),
        "resource-uri"         -> resourceURI,
        "response-status-code" -> Some(statusCode)
      )
    )
    _errorType = errorType
  }

  def this(target: XFormsEventTarget, errorType: ErrorType, connectionResult: Option[ConnectionResult]) = {
    this(
      target     = target,
      properties = Map("error-type" -> Some(errorType.entryName))
    )
    _errorType = errorType
    _connectionResult = connectionResult
  }

  private var _errorType: ErrorType = _
  def errorType: ErrorType = _errorType

  private var _connectionResult: Option[ConnectionResult] = None
  def connectionResult: Option[ConnectionResult] = _connectionResult

  def logThrowable(throwable: Throwable): Unit =
    if (errorType != ErrorType.ValidationError)
      indentedLogger.logError("xforms-submit-error", "setting throwable", "throwable", OrbeonFormatter.format(throwable))

  def logMessage(throwable: Throwable): Unit =
    if (errorType != ErrorType.ValidationError)
      indentedLogger.logError("xforms-submit-error", OrbeonFormatter.message(throwable))
}

sealed abstract class ErrorType(override val entryName: String) extends EnumEntry

object ErrorType extends Enum[ErrorType] {

  val values = findValues

  case object SubmissionInProgress  extends ErrorType("submission-in-progress")  // TODO: use
  case object NoData                extends ErrorType("no-data")
  case object NoRelevantData        extends ErrorType("no-relevant-data")        // TODO: use
  case object ValidationError       extends ErrorType("validation-error")
  case object ParseError            extends ErrorType("parse-error")
  case object ResourceError         extends ErrorType("resource-error")
  case object ResultMediaType       extends ErrorType("result-media-type")       // TODO: use
  case object ResultTextMediaType   extends ErrorType("result-text-media-type")  // TODO: use
  case object ResultErrorResponse   extends ErrorType("result-error-response")   // TODO: use
  case object TargetModelError      extends ErrorType("target-model-error")      // TODO: use
  case object TargetEmpty           extends ErrorType("target-empty")            // TODO: use
  case object TargetReadonly        extends ErrorType("target-readonly")         // TODO: use
  case object TargetNonElement      extends ErrorType("target-non-element")      // TODO: use
  case object TargetError           extends ErrorType("target-error")

  case object XXFormsPendingUploads extends ErrorType("xxforms-pending-uploads") // CHECK: doc
  case object XXFormsInternalError  extends ErrorType("xxforms-internal-error")  // CHECK: doc
  case object XXFormsMethodError    extends ErrorType("xxforms-method-error")    // CHECK: doc
}
