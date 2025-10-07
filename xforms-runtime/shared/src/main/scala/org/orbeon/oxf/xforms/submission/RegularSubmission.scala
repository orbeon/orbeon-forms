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
import cats.syntax.option.*
import org.orbeon.connection.{ConnectionContextSupport, ConnectionResultT, StreamedContent}
import org.orbeon.oxf.externalcontext.SafeRequestContext
import org.orbeon.oxf.http.Headers.{ContentType, firstItemIgnoreCase}
import org.orbeon.oxf.http.HttpMethod.HttpMethodsWithRequestBody
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.{Connection, IndentedLogger}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.net.URI
import scala.util.control.NonFatal


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
    refContext             : RefContext,
    indentedLogger         : IndentedLogger
  ): Option[ConnectResult Either IO[AsyncConnectResult]] = {

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
        url             = absoluteResolvedURL,
        method          = submissionParameters.httpMethod,
        hasCredentials  = submissionParameters.credentialsOpt.isDefined,
        mediatypeOpt    = serializationParameters.actualRequestMediatype.some,
        encodingForSOAP = submissionParameters.encoding,
        customHeaders   = SubmissionUtils.evaluateHeaders(
          submission,
          submissionParameters.replaceType == ReplaceType.All,
          EventCollector.Throw
        ),
        headersToForward= Connection.headersToForwardFromProperty,
        getHeader       = submission.containingDocument.headersGetter
      )(
        logger          = detailsLogger,
        safeRequestCtx  = SafeRequestContext(externalContext)
      )

    val submissionEffectiveId = submission.effectiveId

    val messageBody: Option[Array[Byte]] =
      if (HttpMethodsWithRequestBody(submissionParameters.httpMethod))
        serializationParameters.messageBody orElse Some(Array.emptyByteArray)
      else
        None

    def createConnectResult[S](cxr: ConnectionResultT[S]): ConnectResultT[S] =
      withDebug("creating connect result") {
        try {
          ConnectResultT.Success(
            submissionEffectiveId,
            submission.getReplacer(cxr, submissionParameters)(submission.getIndentedLogger),
            cxr
          )
        } catch {
          case NonFatal(throwable) =>
            ConnectResultT.Failure(
              submissionEffectiveId,
              throwable,
              Some(cxr)
          )
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
            method           = submissionParameters.httpMethod,
            url              = absoluteResolvedURL,
            credentials      = submissionParameters.credentialsOpt,
            content          = messageBody.map(StreamedContent.asyncFromBytes(_, firstItemIgnoreCase(headers, ContentType))),
            headers          = headers,
            loadState        = true,
            logBody          = BaseSubmission.isLogBody
          )(
            logger           = detailsLogger,
            safeRequestCtx   = SafeRequestContext(externalContext),
            connectionCtx    = ConnectionContextSupport.findContext(Map.empty),
            resourceResolver = submission.containingDocument.staticState.resourceResolverOpt
          ).map(createConnectResult(_))
        )
      else
        Left(
          createConnectResult(
            Connection.connectNow(
              method           = submissionParameters.httpMethod,
              url              = absoluteResolvedURL,
              credentials      = submissionParameters.credentialsOpt,
              content          = messageBody.map(StreamedContent.fromBytes(_, firstItemIgnoreCase(headers, ContentType))),
              headers          = headers,
              loadState        = true,
              saveState        = true,
              logBody          = BaseSubmission.isLogBody
            )(
              logger           = detailsLogger,
              externalContext  = externalContext,
              resourceResolver = submission.containingDocument.staticState.resourceResolverOpt
            )
          )
        )
    )
  }
}