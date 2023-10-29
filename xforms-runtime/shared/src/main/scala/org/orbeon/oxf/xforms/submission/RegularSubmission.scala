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
import org.orbeon.io.IOUtils
import org.orbeon.oxf.http.Headers.{ContentType, firstItemIgnoreCase}
import org.orbeon.oxf.http.HttpMethod.HttpMethodsWithRequestBody
import org.orbeon.oxf.http.StreamedContent
import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{Connection, ConnectionResult, CoreCrossPlatformSupport, IndentedLogger}
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.net.URI
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


/**
  * Regular remote submission going through a protocol handler.
  */
class RegularSubmission(submission: XFormsModelSubmission) extends BaseSubmission(submission) {

  def getType = "regular"
  def isMatch(p: SubmissionParameters, p2: SecondPassParameters, sp: SerializationParameters) = true

  def connect(
    p : SubmissionParameters,
    p2: SecondPassParameters,
    sp: SerializationParameters
  ): Option[ConnectResult Either Future[ConnectResult]] = {

    val absoluteResolvedURL = URI.create(getAbsoluteSubmissionURL(p2.actionOrResource, sp.queryString, p.urlNorewrite, p.urlType))

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
        getHeader                = submission.containingDocument.headersGetter)(
        logger                   = detailsLogger,
        externalContext          = externalContext,
        coreCrossPlatformSupport = CoreCrossPlatformSupport
      )

    val submissionEffectiveId = submission.getEffectiveId

    val messageBody: Option[Array[Byte]] =
      if (HttpMethodsWithRequestBody(p.httpMethod)) sp.messageBody orElse Some(Array.emptyByteArray) else None

    val content = messageBody map
      (StreamedContent.fromBytes(_, firstItemIgnoreCase(headers, ContentType)))

    def createConnectResult(cxr: ConnectionResult)(implicit logger: IndentedLogger): ConnectResult =
      withDebug("creating connect result") {
        try {
          ConnectResult(
            submissionEffectiveId,
            Success(submission.getReplacer(cxr, p)(submission.getIndentedLogger), cxr)
          )
        } catch {
          case NonFatal(throwable) =>
            // xxx need to close cxr in case of error also later after running deserialize()
            IOUtils.runQuietly(cxr.close()) // close here as it's not passed through `ConnectResult`
            ConnectResult(submissionEffectiveId, Failure(throwable))
        } finally {
          if (p2.isAsynchronous)
            debugResults(
              List(
                "id"           -> submissionEffectiveId,
                "asynchronous" -> p2.isAsynchronous.toString
              )
            )
        }
      }

    Some(
      if (p2.isAsynchronous || p.isDeferredSubmissionSecondPass)
        Right(
          Connection.connectAsync(
            method          = p.httpMethod,
            url             = absoluteResolvedURL,
            credentials     = p2.credentialsOpt,
            content         = content,
            headers         = headers,
            loadState       = true,
            logBody         = BaseSubmission.isLogBody)(
            logger          = detailsLogger,
            externalContext = externalContext
          ).map(createConnectResult(_)(timingLogger))
        )
      else
        Left(
          createConnectResult(
            Connection.connectNow(
              method          = p.httpMethod,
              url             = absoluteResolvedURL,
              credentials     = p2.credentialsOpt,
              content         = content,
              headers         = headers,
              loadState       = true,
              saveState       = true,
              logBody         = BaseSubmission.isLogBody)(
              logger          = detailsLogger,
              externalContext = externalContext
            )
          )(timingLogger)
        )
    )
  }
}