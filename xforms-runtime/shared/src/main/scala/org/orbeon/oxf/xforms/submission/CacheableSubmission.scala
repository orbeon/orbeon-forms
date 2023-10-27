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
import org.orbeon.oxf.http.{Headers, StatusCode, StreamedContent}
import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext
import org.orbeon.oxf.util.StaticXPath.{DocumentNodeInfoType, VirtualNodeType}
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache
import org.orbeon.oxf.xforms.event.events.{ErrorType, XFormsSubmitErrorEvent}
import org.orbeon.oxf.xforms.model.{InstanceCaching, XFormsInstance}
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


private object CacheableSubmission {
  private class ThrowableWrapper(val throwable: Throwable) extends RuntimeException
}

class CacheableSubmission(submission: XFormsModelSubmission)
  extends BaseSubmission(submission) {

  val submissionType = "cacheable"

  def isMatch(submissionParameters: SubmissionParameters, serializationParameters: SerializationParameters): Boolean =
    submissionParameters.replaceType == ReplaceType.Instance && submissionParameters.isCache

  def connect(
    submissionParameters   : SubmissionParameters,
    serializationParameters: SerializationParameters
  )(implicit
    refContext             : RefContext
  ): Option[ConnectResult Either Future[ConnectResult]] = {

    val absoluteResolvedURLString =
      getAbsoluteSubmissionURL(
        submissionParameters.actionOrResource,
        serializationParameters.queryString,
        submissionParameters.urlNorewrite,
        submissionParameters.urlType
      )

    val requestBodyHash =
      serializationParameters.messageBody.map(XFormsCrossPlatformSupport.digestBytes(_, ByteEncoding.Hex))

    val detailsLogger = getDetailsLogger(submissionParameters)

    val submissionEffectiveId = submission.getEffectiveId

    // Find and check replacement location
    val instanceToUpdate = checkInstanceToUpdate(detailsLogger, submissionParameters)
    val staticInstance   = instanceToUpdate.instance
    val instanceCaching  = InstanceCaching.fromValues(submissionParameters.timeToLive, submissionParameters.isHandleXInclude, absoluteResolvedURLString, requestBodyHash)
    val instanceStaticId = staticInstance.staticId

    def createReplacerAndConnectionResult(document: DocumentNodeInfoType): ConnectResult = {

      val connectionResult =
        ConnectionResult(
          absoluteResolvedURLString,
          StatusCode.Ok,
          Headers.EmptyHeaders,
          StreamedContent.Empty, // we used to create non-empty content because we were using `getReplacer()`
          dontHandleResponse = false
        )

      ConnectResult(
        submissionEffectiveId,
        Success((new DirectInstanceReplacer((document, instanceCaching)), connectionResult))
      )
    }

    // As an optimization, try from cache first
    // The purpose of this is to avoid starting a new thread in asynchronous mode if the instance is already in cache
    XFormsServerSharedInstancesCache.findContent(instanceCaching, submissionParameters.isReadonly, staticInstance.exposeXPathTypes)(detailsLogger) match {
      case Some(cachedDocumentInfo) =>
        Left(createReplacerAndConnectionResult(cachedDocumentInfo)).some
      case None =>
        // NOTE: somebody else could put an instance in cache between now and the obtaining of the result below
        if (detailsLogger.debugEnabled)
          detailsLogger.logDebug("", "did not find instance in cache", "id", instanceStaticId, "URI", absoluteResolvedURLString, "request hash", requestBodyHash.orNull)
        val timingLogger = getTimingLogger(submissionParameters)
        // Create deferred evaluation for synchronous or asynchronous loading
        if (submissionParameters.isAsynchronous && timingLogger.debugEnabled)
          timingLogger.startHandleOperation("", "running asynchronous submission", "id", submission.getEffectiveId, "cacheable", "true")
        try {
          if (submissionParameters.isAsynchronous) {

            val futureNewDocumentInfo =
              XFormsServerSharedInstancesCache.findContentOrLoadAsync(
                instanceCaching,
                submissionParameters.isReadonly,
                staticInstance.exposeXPathTypes,
                loadFn(submissionParameters, serializationParameters)
              )(detailsLogger)

            // xxx probably not?
            Right(futureNewDocumentInfo.map(createReplacerAndConnectionResult)).some
          } else {

            val newDocumentInfo =
              XFormsServerSharedInstancesCache.findContentOrLoad(
                instanceCaching,
                submissionParameters.isReadonly,
                staticInstance.exposeXPathTypes,
                loadFn(submissionParameters, serializationParameters)
              )(detailsLogger)

            // xxx probably not?
            Left(createReplacerAndConnectionResult(newDocumentInfo)).some
          }
        } catch {
          case throwableWrapper: CacheableSubmission.ThrowableWrapper =>
            // The ThrowableWrapper was thrown within the inner load() method above
            Left(ConnectResult(submissionEffectiveId, Failure(throwableWrapper.throwable))).some
          case NonFatal(throwable) =>
            // Any other throwable
            Left(ConnectResult(submissionEffectiveId, Failure(throwable))).some
        } finally {
          if (submissionParameters.isAsynchronous && timingLogger.debugEnabled) {

            timingLogger.setDebugResults(
              "id",
              submission.getEffectiveId,
              "asynchronous", submissionParameters.isAsynchronous.toString,
            )

            timingLogger.endHandleOperation()
          }
        }
    } // match
  }

  private def checkInstanceToUpdate(
    indentedLogger      : IndentedLogger,
    submissionParameters: SubmissionParameters
  )(implicit
    refContext          : RefContext
  ): XFormsInstance = {

    val destinationNodeInfoOpt =
      submission.evaluateTargetRef(
        refContext.xpathContext,
        submission.findReplaceInstanceNoTargetref(refContext.refInstanceOpt).orNull,
        refContext.submissionElementContextItem
      )

    destinationNodeInfoOpt match {
      case Some(destinationNodeInfo) =>
        submission.containingDocument.instanceForNodeOpt(destinationNodeInfo) match {
          case Some(updatedInstance) if updatedInstance.rootElement.isSameNodeInfo(destinationNodeInfo) =>
            if (indentedLogger.debugEnabled)
              indentedLogger.logDebug("", "using instance from application shared instance cache", "instance", updatedInstance.getEffectiveId)
            updatedInstance
          case _ =>
            // Only support replacing the root element of an instance
            // TODO: in the future, check on resolvedXXFormsReadonly to implement this restriction only when using a readonly instance
            throw new XFormsSubmissionException(
              submission,
              "targetref attribute must point to an instance root element when using cached/shared instance replacement.",
              "processing targetref attribute",
              null,
              new XFormsSubmitErrorEvent(
                submission, ErrorType.TargetError,
                None,
                submissionParameters.tunnelProperties
              )
            )
        }
      case None =>
        // Throw target-error
        // XForms 1.1: "If the processing of the targetref attribute fails,
        // then submission processing ends after dispatching the event
        // xforms-submit-error with an error-type of target-error."
        throw new XFormsSubmissionException(
          submission,
          "targetref attribute doesn't point to an element for replace=\"instance\".", "processing targetref attribute",
          null,
          new XFormsSubmitErrorEvent(
            submission,
            ErrorType.TargetError,
            None,
            submissionParameters.tunnelProperties
          )
        )
    }
  }

  private def loadFn(
    submissionParameters   : SubmissionParameters,
    serializationParameters: SerializationParameters
  )(
    instanceSourceURI: String,
    handleXInclude   : Boolean
  )(implicit
    refContext          : RefContext
  ): DocumentNodeInfoType =
    try {
      // Run `RegularSubmission` but force:
      // - synchronous execution
      // - readonly result
      val updatedSubmissionParameters = submissionParameters.copy(isAsynchronous = false, isReadonly = true)

      val submissionResult =
        new RegularSubmission(submission).connect(updatedSubmissionParameters, serializationParameters) match {
          case Some(Left(connectResult)) => connectResult
          case _                         => throw new IllegalStateException
        }

      submissionResult.result match {
        case Success((replacer @ InstanceReplacer, cxr)) =>
          // `load()` requires an immutable `TinyTree`
          // Since we forced `isReadonly` above, the result must also be a readonly instance
          replacer.deserialize(submission, cxr, updatedSubmissionParameters) match {
            case Right((_: VirtualNodeType, _)) => throw new IllegalStateException
            case Right((documentInfo, _))       => documentInfo
            case Left(Left(_))                  => throw new IllegalStateException
            case Left(Right(documentInfo))      => documentInfo
          }
        case Failure(throwable) =>
          throw new CacheableSubmission.ThrowableWrapper(throwable)
        case _ =>
          // Must not happen
          throw new IllegalStateException
      }
    } catch {
      case throwableWrapper: CacheableSubmission.ThrowableWrapper =>
        // In case we just threw it above, just propagate
        throw throwableWrapper
      case NonFatal(throwable) =>
        // Exceptions are handled further down
        throw new CacheableSubmission.ThrowableWrapper(throwable)
    }
}