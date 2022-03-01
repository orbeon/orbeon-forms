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


import java.net.URI

import cats.Eval
import cats.syntax.option._
import org.orbeon.oxf.http.Headers.{ContentType, firstItemIgnoreCase}
import org.orbeon.oxf.http.HttpMethod.HttpMethodsWithRequestBody
import org.orbeon.oxf.http.StreamedContent
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{Connection, ConnectionResult, CoreCrossPlatformSupport}
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  * Regular remote submission going through a protocol handler.
  */
class RegularSubmission(submission: XFormsModelSubmission) extends BaseSubmission(submission) {

  def getType = "regular"
  def isMatch(p: SubmissionParameters, p2: SecondPassParameters, sp: SerializationParameters) = true

  def connect(
    p  : SubmissionParameters,
    p2 : SecondPassParameters,
    sp : SerializationParameters
  ): Option[ConnectResult] = {

    val absoluteResolvedURL = new URI(getAbsoluteSubmissionURL(p2.actionOrResource, sp.queryString, p.urlNorewrite, p.urlType))

    val timingLogger  = getTimingLogger(p, p2)
    val detailsLogger = getDetailsLogger(p, p2)

    val externalContext = XFormsCrossPlatformSupport.externalContext

    val headers =
      Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
        url                      = absoluteResolvedURL,
        method                   = p.httpMethod,
        hasCredentials           = p2.credentialsOpt.isDefined,
        mediatypeOpt             = sp.actualRequestMediatype.some,
        encodingForSOAP          = p2.encoding,
        customHeaders            = SubmissionUtils.evaluateHeaders(submission, p.replaceType == ReplaceType.All),
        headersToForward         = Connection.headersToForwardFromProperty,
        getHeader                = containingDocument.headersGetter)(
        logger                   = detailsLogger,
        externalContext          = externalContext,
        coreCrossPlatformSupport = CoreCrossPlatformSupport
      )

    val submissionEffectiveId = submission.getEffectiveId

    val messageBody: Option[Array[Byte]] =
      if (HttpMethodsWithRequestBody(p.httpMethod)) sp.messageBody orElse Some(Array.emptyByteArray) else None

    val content = messageBody map
      (StreamedContent.fromBytes(_, firstItemIgnoreCase(headers, ContentType)))

    // Prepare Connection in this thread as async submission can't access the request object
    val connectionResultEval =
      Connection.connectLater(
        method          = p.httpMethod,
        url             = absoluteResolvedURL,
        credentials     = p2.credentialsOpt,
        content         = content,
        headers         = headers,
        loadState       = true,
        logBody         = BaseSubmission.isLogBody)(
        logger          = detailsLogger,
        externalContext = externalContext
      )

    // Pack external call into an `Eval` so it can be run:
    // - now and synchronously
    // - now and asynchronously
    // - later as a "foreground" asynchronous submission
    def processSubmissionResultEval(cxr: ConnectionResult) = Eval.later {
      // Here we just want to run the submission and not touch the XFCD. Remember, we can't change XFCD
      // because it may get out of the caches and not be picked up by further incoming Ajax requests.
      if (p2.isAsynchronous && timingLogger.debugEnabled)
        timingLogger.startHandleOperation("", "running asynchronous submission", "id", submissionEffectiveId)

      // Open the connection
      var connected    = false
      var deserialized = false

      var connectionResultOpt: Option[ConnectionResult] = None
      try {

        val connectionResult =
          withDebug("opening connection") {
            // Connect, and cleanup
            // TODO: Consider how the state could be saved. Maybe do this before connect() in the initiating thread?
            // Or make sure it's ok to touch app/session (but not request) from other thread (`ExternalContext`
            // in scope + synchronization)
            cxr
          }(detailsLogger)

        connectionResultOpt = connectionResult.some
        connected = true

        // TODO: This refers to Submission.
        val replacer = submission.getReplacer(connectionResult, p)(submission.getIndentedLogger)

        // Deserialize here so it can run in parallel
        replacer.deserialize(connectionResult, p, p2)
        // Update status
        deserialized = true

        ConnectResult(submissionEffectiveId, Success((replacer, connectionResult)))
      } catch {
        case NonFatal(throwable) =>
          // Exceptions are handled further down
          connectionResultOpt foreach (_.close())
          ConnectResult(submissionEffectiveId, Failure(throwable))
      } finally {
        if (p2.isAsynchronous && timingLogger.debugEnabled) {
          timingLogger.setDebugResults(
            "id",
            submissionEffectiveId,
            "asynchronous",
            p2.isAsynchronous.toString,
            "connected",
            connected.toString,
            "deserialized",
            deserialized.toString
          )
          timingLogger.endHandleOperation()
        }
      }
    }

    submitEval(p, p2, connectionResultEval flatMap processSubmissionResultEval)
  }
}