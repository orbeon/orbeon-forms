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
import org.orbeon.oxf.http.HttpStatusCode
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.model.StaticDataModel.Reason
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml._
import org.orbeon.saxon.trans.XPathException
import org.orbeon.xforms.{ServerError, XFormsCrossPlatformSupport, XFormsNames}

import scala.collection.compat._


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
  def handleNonFatalSetvalueError(target: XFormsEventTarget, locationData: LocationData, reason: Reason): Unit = {
    val containingDocument = target.container.getContainingDocument
    containingDocument.indentedLogger.logDebug("", reason.message)
    containingDocument.addServerError(ServerError(reason.message, Option(locationData)))
  }

  // `container`: used for `PartAnalysis` and `XFCD`
  def handleNonFatalXPathError(
    container    : XBLContainer,
    throwable    : Throwable,
    expressionOpt: Option[String] = None
  ): Unit = {
    val expressionForMessage = expressionOpt.map(e => s" `$e`").getOrElse("")
    val message = "exception while evaluating XPath expression" + expressionForMessage
    handleNonFatalXFormsError(
      container,
      message,
      throwable,
      isRecoverableOnInit = ! ( // https://github.com/orbeon/orbeon-forms/issues/5844
        XFormsCrossPlatformSupport.causesIterator(throwable) exists {
          case e: XPathException if e.isStaticError => true
          case _: HttpStatusCode                    => true // this is probably not needed for MIP
          case _                                    => false
        }
      )
    )
  }

  def handleNonFatalActionError(target: XFormsEventTarget, throwable: Throwable): Unit =
    handleNonFatalXFormsError(target.container, "exception while running action", throwable, isRecoverableOnInit = false)

  private def handleNonFatalXFormsError(container: XBLContainer, message: String, t: Throwable, isRecoverableOnInit: Boolean): Unit = {

    // NOTE: We want to catch a status code exception which happen during an XPathException. And in that case, the XPathException
    // is dynamic, so we cannot simply exclude dynamic XPath exceptions. So we have to be inclusive and consider which types of
    // errors are fatal.
    // See https://github.com/orbeon/orbeon-forms/issues/2194

    // 2023-04-07: Make all initialization errors fatal.
    // See https://github.com/orbeon/orbeon-forms/issues/5751

    if (
      container.getPartAnalysis.isTopLevelPart     &&   // LATER: Other sub-parts could be fatal, depending on settings on `xxf:dynamic`.
      container.getContainingDocument.initializing &&
      ! isRecoverableOnInit
    ) {
      throw new OXFException(t)
    } else {

      def serverErrorFromThrowable(t: Throwable): ServerError = {
        val root = XFormsCrossPlatformSupport.getRootThrowable(t)
        ServerError(
          root.getMessage,
          OrbeonLocationException.getRootLocationData(t),
          Some(root.getClass.getName)
        )
      }

      val containingDocument = container.getContainingDocument
      containingDocument.indentedLogger.logDebug("", message, t)
      containingDocument.addServerError(serverErrorFromThrowable(t))
    }
  }

  import XMLReceiverSupport._

  // Insert server errors into the Ajax response
  def outputAjaxErrors(errors: Seq[ServerError])(implicit xmlReceiver: XMLReceiver): Unit = {
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
}