/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.common.{OXFException, OrbeonLocationException}
import org.orbeon.oxf.http.{HttpStatusCode, StatusCode}
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.xforms.BindingErrorReason
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml._
import org.orbeon.saxon.trans.XPathException
import org.orbeon.xforms.{ServerError, XFormsCrossPlatformSupport, XFormsNames}


// What kind of errors get here:
//
// - setvalue errors (binding exceptions except in actions)
//    - @calculate, @xxf:default
//    - write xf:upload file metadata
//    - store external value from control
//    - xf:setvalue
//    - use of XFormsAPI's setvalue
//    - instance mirror (XBL, xxf:dynamic)
//    - xf:switch/@caseref and xf:repeat/@indexref
//    - xf:submission[@replace = 'text']
// - XPath errors
//    - during model rebuild
//    - evaluating MIPs
//    - evaluating variables
//    - evaluating bindings except for actions
//        - control and LHHA bindings
//        - itemsets
//        - xf:submission/@ref
//        - submission headers
//        - xf:upload/xf:output metadata
//    - evaluating value attributes, as with xf:label/@value
//    - evaluating control AVTs
//    - evaluating control @format, @unformat, @value
// - action errors
//    - XPath errors
//    - any other action error
object XFormsError {

  // `xxforms-binding-error` in model and on controls
  def handleNonFatalBindingError(target: XFormsEventTarget, locationData: Option[LocationData], reason: Option[BindingErrorReason]): Unit = {
    val containingDocument = target.container.containingDocument
    val message            = reason.map(_.message).getOrElse("exception while setting value")
    containingDocument.indentedLogger.logInfo("", message)
    containingDocument.addServerError(ServerError(message, locationData))
  }

  // `container`: used for `PartAnalysis` and `XFCD`
  def handleNonFatalXPathError(
    container   : XBLContainer,
    throwableOpt: Option[Throwable],
    message     : String
  ): Unit =
    handleNonFatalXFormsError(
      container,
      ServerError(message, None, None),
      throwableOpt,
      isNormallyRecoverableOnInit = ! ( // https://github.com/orbeon/orbeon-forms/issues/5844
        throwableOpt.iterator.flatMap(XFormsCrossPlatformSupport.causesIterator) exists {
          case e: XPathException if e.isStaticError => true
          case _                                    => false
        }
      ),
      mustNotRecoverOnInit =
        throwableOpt.iterator.flatMap(XFormsCrossPlatformSupport.causesIterator) exists {
          case status: HttpStatusCode if ! StatusCode.isSuccessCode(status.code)  => true
          case _                                                                  => false
        }
    )

  def handleNonFatalActionError(target: XFormsEventTarget, throwableOpt: Option[Throwable]): Unit =
    handleNonFatalXFormsError(
      target.container,
      serverErrorFromThrowable(throwableOpt, "exception while running action"),
      throwableOpt,
      isNormallyRecoverableOnInit = false,
      mustNotRecoverOnInit        = false
    )

  // Relevant issues:
  //
  // - https://github.com/orbeon/orbeon-forms/issues/2194
  // - https://github.com/orbeon/orbeon-forms/issues/5751
  // - https://github.com/orbeon/orbeon-forms/issues/5845
  // - https://github.com/orbeon/orbeon-forms/issues/5543
  private def handleNonFatalXFormsError(
    container                  : XBLContainer,
    serverError                : ServerError,
    throwableOpt               : Option[Throwable],
    isNormallyRecoverableOnInit: Boolean,
    mustNotRecoverOnInit       : Boolean
  ): Unit = {

    if (
      container.partAnalysis.isTopLevelPart     &&   // LATER: Other sub-parts could be fatal, depending on settings on `xxf:dynamic`.
      container.containingDocument.initializing && (
        mustNotRecoverOnInit || (
          ! isNormallyRecoverableOnInit &&
          ! container.containingDocument.allowErrorRecoveryOnInit
        )
      )
    ) {
      throw throwableOpt.getOrElse(new OXFException(serverError.message))
    } else {
      val containingDocument = container.containingDocument
      containingDocument.indentedLogger.logInfo("", serverError.message)
      containingDocument.addServerError(serverError)
    }
  }

  private def serverErrorFromThrowable(throwableOpt: Option[Throwable], defaultMessage: => String): ServerError = {
    val rootThrowableOpt = throwableOpt.map(XFormsCrossPlatformSupport.getRootThrowable)
    ServerError(
      rootThrowableOpt.map(_.getMessage).getOrElse(defaultMessage),
      rootThrowableOpt.flatMap(OrbeonLocationException.getRootLocationData),
      rootThrowableOpt.map(_.getClass.getName)
    )
  }

  import XMLReceiverSupport._

  // Insert server errors into the Ajax response
  def outputAjaxErrors(errors: Seq[ServerError])(implicit xmlReceiver: XMLReceiver): Unit =
    withElement(localName = "errors", prefix = "xxf", uri = XFormsNames.XXFORMS_NAMESPACE_URI) {
      for (error <- errors)
        element(
          localName = "error",
          prefix    = "xxf",
          uri       = XFormsNames.XXFORMS_NAMESPACE_URI,
          atts      = ServerError.getDetailsAsList(error),
          text      = error.message
        )
    }
}