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

import cats.Eval
import cats.syntax.option._
import org.orbeon.oxf.http.{Headers, StreamedContent}
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StaticXPath.VirtualNodeType
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache
import org.orbeon.oxf.xforms.event.events.{ErrorType, XFormsSubmitErrorEvent}
import org.orbeon.oxf.xforms.model.InstanceCaching
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.util.control.NonFatal
import scala.util.{Failure, Success}


/**
 * Cacheable remote submission going through a protocol handler.
 *
 * NOTE: This could possibly be made to work as well for optimized submissions, but currently this is not the case.
 */
private object CacheableSubmission {
  class ThrowableWrapper(val throwable: Throwable) extends RuntimeException
}

class CacheableSubmission(submission: XFormsModelSubmission)
  extends BaseSubmission(submission) {

  def getType = "cacheable"

  // Match if the submission has `replace="instance"` and `xxf:cache="true"`
  def isMatch(p: SubmissionParameters, p2: SecondPassParameters, sp: SerializationParameters): Boolean =
    p.replaceType == ReplaceType.Instance && p2.isCache

  def connect(p: SubmissionParameters, p2: SecondPassParameters, sp: SerializationParameters): Option[SubmissionResult] = {
    // Get the instance from shared instance cache
    // This can only happen is method="get" and replace="instance" and xxf:cache="true"

    // Convert URL to string
    val absoluteResolvedURLString = getAbsoluteSubmissionURL(p2.actionOrResource, sp.queryString, p.urlNorewrite, p.urlType)

    // Compute a hash of the body if needed
    val requestBodyHash =
      sp.messageBody map (XFormsCrossPlatformSupport.digestBytes(_, "hex"))

    val detailsLogger = getDetailsLogger(p, p2)

    val submissionEffectiveId = submission.getEffectiveId

    // Find and check replacement location
    val instanceToUpdate = checkInstanceToUpdate(detailsLogger, p)
    val staticInstance   = instanceToUpdate.instance
    val instanceCaching  = InstanceCaching.fromValues(p2.timeToLive, p2.isHandleXInclude, absoluteResolvedURLString, requestBodyHash)
    val instanceStaticId = staticInstance.staticId

    // Obtain replacer
    // Pass a pseudo connection result which contains information used by getReplacer()
    // We know that we will get an InstanceReplacer
    val connectionResult = createPseudoConnectionResult(absoluteResolvedURLString)
    val replacer = submission.getReplacer(connectionResult, p).asInstanceOf[InstanceReplacer]

    // As an optimization, try from cache first
    // The purpose of this is to avoid starting a new thread in asynchronous mode if the instance is already in cache
    XFormsServerSharedInstancesCache.findContent(instanceCaching, p2.isReadonly, staticInstance.exposeXPathTypes)(detailsLogger) match {
      case Some(cachedDocumentInfo) =>
        // Here we cheat a bit: instead of calling generically `deserialize()`, we directly set the instance document
        replacer.setCachedResult(cachedDocumentInfo, instanceCaching)
        SubmissionResult(submissionEffectiveId, Success((replacer, connectionResult))).some
      case None =>
        // NOTE: technically, somebody else could put an instance in cache between now and the `Eval` execution
        if (detailsLogger.debugEnabled)
          detailsLogger.logDebug("", "did not find instance in cache", "id", instanceStaticId, "URI", absoluteResolvedURLString, "request hash", requestBodyHash.orNull)
        val timingLogger = getTimingLogger(p, p2)
        // Create deferred evaluation for synchronous or asynchronous loading
        val eval = Eval.later {
          if (p2.isAsynchronous && timingLogger.debugEnabled)
            timingLogger.startHandleOperation("", "running asynchronous submission", "id", submission.getEffectiveId, "cacheable", "true")
          var loadingAttempted = false
          var deserialized = false
          try {
            val newDocumentInfo =
              XFormsServerSharedInstancesCache.findContentOrLoad(
                instanceCaching,
                p2.isReadonly,
                staticInstance.exposeXPathTypes,
                (instanceSourceURI: String, handleXInclude: Boolean) => {
                  // Update status
                  loadingAttempted = true
                  // Call regular submission
                  var submissionResultOpt: Option[SubmissionResult] = None
                  try {
                    // Run regular submission but force:
                    // - synchronous execution
                    // - readonly result

                    val updatedP2 = p2.copy(isAsynchronous = false, isReadonly = true)

                    val submissionResult =
                      List(new RegularSubmission(submission)) find (_.isMatch(p, p2, sp)) flatMap { submission =>
                        withDebug("connecting", List("type" -> submission.getType)) {
                          submission.connect(p, updatedP2, sp)
                        }(detailsLogger)
                      } getOrElse
                        (throw new IllegalArgumentException("can only cache a `RegularSubmission`"))

                    submissionResultOpt = submissionResult.some

                    submissionResult.result match {
                      case Success((replacer: InstanceReplacer, _)) =>
                        deserialized = true
                        // load() requires an immutable TinyTree
                        // Since we forced readonly above, the result must also be a readonly instance
                        replacer.resultingDocumentOpt match {
                          case Some(Right(_: VirtualNodeType)) => throw new IllegalStateException
                          case Some(Right(documentInfo))       => documentInfo
                          case _                               => throw new IllegalStateException
                        }
                      case Failure(throwable) =>
                        throw new CacheableSubmission.ThrowableWrapper(throwable)
                      case _ =>
                        // We know that `RegularSubmission` returns a `Replacer` with an instance document so this
                        // should not happen!
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
                })(detailsLogger)
            // Here we cheat a bit: instead of calling generically `deserialize()`, we directly set the `DocumentInfo`
            replacer.setCachedResult(newDocumentInfo, instanceCaching)

            SubmissionResult(submissionEffectiveId, Success((replacer, connectionResult)))
          } catch {
            case throwableWrapper: CacheableSubmission.ThrowableWrapper =>
              // The ThrowableWrapper was thrown within the inner load() method above
              SubmissionResult(submissionEffectiveId, Failure(throwableWrapper.throwable))
            case NonFatal(throwable) =>
              // Any other throwable
              SubmissionResult(submissionEffectiveId, Failure(throwable))
          } finally
            if (p2.isAsynchronous && timingLogger.debugEnabled) {

              timingLogger.setDebugResults(
                "id",
                submission.getEffectiveId,
                "asynchronous", p2.isAsynchronous.toString,
                "loading attempted", loadingAttempted.toString,
                "deserialized", deserialized.toString
              )

              timingLogger.endHandleOperation()
            }
        }

        submitEval(p, p2, eval) // returns `None` if the execution is deferred
    }
  }

  private def checkInstanceToUpdate(indentedLogger: IndentedLogger, p: SubmissionParameters) = {

    val destinationNodeInfo =
      submission.evaluateTargetRef(
        p.refContext.xpathContext,
        submission.findReplaceInstanceNoTargetref(p.refContext.refInstanceOpt),
        p.refContext.submissionElementContextItem
      )

    if (destinationNodeInfo == null)
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
          None
        )
      )

    val updatedInstance = submission.containingDocument.getInstanceForNode(destinationNodeInfo)
    if (updatedInstance == null || !updatedInstance.rootElement.isSameNodeInfo(destinationNodeInfo)) {
      // Only support replacing the root element of an instance
      // TODO: in the future, check on resolvedXXFormsReadonly to implement this restriction only when using a readonly instance
      throw new XFormsSubmissionException(
        submission,
        "targetref attribute must point to an instance root element when using cached/shared instance replacement.",
        "processing targetref attribute",
        null,
        new XFormsSubmitErrorEvent(
          submission, ErrorType.TargetError,
          None
        )
      )
    }
    if (indentedLogger.debugEnabled)
      indentedLogger.logDebug("", "using instance from application shared instance cache", "instance", updatedInstance.getEffectiveId)
    updatedInstance
  }

  // NOTE: This is really weird: the ConnectionResult returned must essentially say that it has some content.
  private def createPseudoConnectionResult(resourceURI: String): ConnectionResult =
    ConnectionResult(
      resourceURI,
      200,
      Headers.EmptyHeaders,
      StreamedContent.fromBytes(Array[Byte](0), contentType = None, title = None),
      dontHandleResponse = false
    )
}