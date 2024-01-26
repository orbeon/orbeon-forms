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


import cats.effect.IO
import cats.syntax.option._
import org.orbeon.connection.{ConnectionContextSupport, ConnectionResultT, StreamedContent}
import org.orbeon.io.IOUtils
import org.orbeon.oxf.http.Headers.{ContentType, firstItemIgnoreCase}
import org.orbeon.oxf.http.HttpMethod.HttpMethodsWithRequestBody
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{Connection, CoreCrossPlatformSupport, IndentedLogger}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.net.URI
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


/**
  * Regular remote submission going through a protocol handler.
  */
class RegularSubmission(submission: XFormsModelSubmission)
  extends BaseSubmission(submission) {

  val submissionType = "regular"

  def isMatch(
    submissionParameters   : SubmissionParameters,
    serializationParameters: SerializationParameters
  ) = true

  def connect(
    submissionParameters   : SubmissionParameters,
    serializationParameters: SerializationParameters
  )(implicit
    refContext          : RefContext
  ): Option[ConnectResult Either IO[AsyncConnectResult]] = {

    implicit val logger: IndentedLogger = submission.getIndentedLogger

    val detailsLogger   = submission.getDetailsLogger
    val externalContext = XFormsCrossPlatformSupport.externalContext

    val absoluteResolvedURL =
      URI.create(
        getAbsoluteSubmissionURL(
          submissionParameters.actionOrResource,
          serializationParameters.queryString,
          submissionParameters.urlNorewrite,
          submissionParameters.urlType
        )
      )

    val headers =
      Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
        url                      = absoluteResolvedURL,
        method                   = submissionParameters.httpMethod,
        hasCredentials           = submissionParameters.credentialsOpt.isDefined,
        mediatypeOpt             = serializationParameters.actualRequestMediatype.some,
        encodingForSOAP          = submissionParameters.encoding,
        customHeaders            = SubmissionUtils.evaluateHeaders(
          submission,
          submissionParameters.replaceType == ReplaceType.All,
          EventCollector.Throw
        ),
        headersToForward         = Connection.headersToForwardFromProperty,
        getHeader                = submission.containingDocument.headersGetter
      )(
        logger                   = detailsLogger,
        externalContext          = externalContext,
        coreCrossPlatformSupport = CoreCrossPlatformSupport
      )

    val submissionEffectiveId = submission.getEffectiveId

    val messageBody: Option[Array[Byte]] =
      if (HttpMethodsWithRequestBody(submissionParameters.httpMethod))
        serializationParameters.messageBody orElse Some(Array.emptyByteArray)
      else
        None

    def createConnectResult[S](cxr: ConnectionResultT[S])(implicit logger: IndentedLogger): ConnectResultT[S] =
      withDebug("creating connect result") {
        try {
          ConnectResultT(
            submissionEffectiveId,
            Success(submission.getReplacer(cxr, submissionParameters)(submission.getIndentedLogger), cxr)
          )
        } catch {
          case NonFatal(throwable) =>
            // xxx need to close cxr in case of error also later after running deserialize()
            IOUtils.runQuietly(cxr.close()) // close here as it's not passed through `ConnectResult`
            ConnectResultT(submissionEffectiveId, Failure(throwable))
        } finally {
          if (submissionParameters.isAsynchronous)
            debugResults(
              List(
                "id"           -> submissionEffectiveId,
                "asynchronous" -> submissionParameters.isAsynchronous.toString
              )
            )
        }
      }

    Some(
      if (submissionParameters.isAsynchronous || submissionParameters.isDeferredSubmission)
        Right(
          Connection.connectAsync(
            method          = submissionParameters.httpMethod,
            url             = absoluteResolvedURL,
            credentials     = submissionParameters.credentialsOpt,
            content         = messageBody.map(StreamedContent.asyncFromBytes(_, firstItemIgnoreCase(headers, ContentType))),
            headers         = headers,
            loadState       = true,
            logBody         = BaseSubmission.isLogBody
          )(
            logger          = detailsLogger,
            externalContext = externalContext,
            connectionCtx   = ConnectionContextSupport.getContext(Map.empty)
          ).map(createConnectResult(_))
        )
      else
        Left(
          createConnectResult(
            Connection.connectNow(
              method          = submissionParameters.httpMethod,
              url             = absoluteResolvedURL,
              credentials     = submissionParameters.credentialsOpt,
              content         = messageBody.map(StreamedContent.fromBytes(_, firstItemIgnoreCase(headers, ContentType))),
              headers         = headers,
              loadState       = true,
              saveState       = true,
              logBody         = BaseSubmission.isLogBody
            )(
              logger          = detailsLogger,
              externalContext = externalContext
            )
          )
        )
    )
  }
}