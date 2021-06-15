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

import java.util

import cats.Eval
import cats.syntax.option._
import org.log4s.Logger
import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEventTarget, XFormsEvents}
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xforms.submission.XFormsModelSubmissionBase.getRequestedSerialization
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsError, XFormsGlobalProperties}
import org.orbeon.saxon.om
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{RelevanceHandling, XFormsId}

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
 * Represents an XForms model submission instance.
 *
 * TODO: Refactor handling of serialization to separate classes.
 */
object XFormsModelSubmission {

  val LOGGING_CATEGORY = "submission"
  val logger: Logger = LoggerFactory.createLogger(classOf[XFormsModelSubmission])

  /**
   * Run the given submission `Eval`. This must be for a `replace="all"` submission.
   */
  def runDeferredSubmission(eval: Eval[SubmissionResult], response: ExternalContext.Response): Unit =
    eval.value.result match {
      case Success((replacer, connectionResult)) =>
        try {
          replacer match {
            case _: AllReplacer      => AllReplacer.forwardResultToResponse(connectionResult, response)
            case _: RedirectReplacer => RedirectReplacer.updateResponse(connectionResult, response)
            case _: NoneReplacer     => // NOP
            case r                   => throw new IllegalArgumentException(r.getClass.getName)
          }
        } finally {
          connectionResult.close()
        }
      case Failure(throwable) =>
        // Propagate throwable, which might have come from a separate thread
        throw new OXFException(throwable)
    }

  private def getNewLogger(
    p               : SubmissionParameters,
    p2              : SecondPassParameters,
    indentedLogger  : IndentedLogger,
    newDebugEnabled : Boolean
  ) =
    if (p2.isAsynchronous && p.replaceType != ReplaceType.None) {
      // Background asynchronous submission creates a new logger with its own independent indentation
      val newIndentation = new IndentedLogger.Indentation(indentedLogger.indentation.indentation)
      new IndentedLogger(indentedLogger, newIndentation, newDebugEnabled)
    } else if (indentedLogger.debugEnabled != newDebugEnabled) {
      // Keep shared indentation but use new debug setting
      new IndentedLogger(indentedLogger, indentedLogger.indentation, newDebugEnabled)
    } else {
      // Synchronous submission or foreground asynchronous submission uses current logger
      indentedLogger
    }

  private def isLogDetails = XFormsGlobalProperties.getDebugLogging.contains("submission-details")

  // Only allow xxforms-submit from client
  private val ALLOWED_EXTERNAL_EVENTS = new util.HashSet[String]
  ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_SUBMIT)
}

class XFormsModelSubmission(
  val container        : XBLContainer,
  val staticSubmission : org.orbeon.oxf.xforms.analysis.model.Submission,
  val model            : XFormsModel
) extends XFormsModelSubmissionBase {

  thisSubmission =>

  val containingDocument: XFormsContainingDocument = container.getContainingDocument

  // All the submission types in the order they must be checked
  val submissions =
    List(
      new EchoSubmission(thisSubmission),
      new ClientGetAllSubmission(thisSubmission),
      new CacheableSubmission(thisSubmission),
      new RegularSubmission(thisSubmission)
    )

  def getId               : String            = staticSubmission.staticId
  def getPrefixedId       : String            = XFormsId.getPrefixedId(getEffectiveId)
  def scope               : Scope             = staticSubmission.scope
  def getEffectiveId      : String            = XFormsId.getRelatedEffectiveId(model.getEffectiveId, getId)
  def getLocationData     : LocationData      = staticSubmission.locationData
  def parentEventObserver : XFormsEventTarget = model

  def performTargetAction(event: XFormsEvent): Unit = ()

  def performDefaultAction(event: XFormsEvent): Unit =
    event match {
      case e: XFormsSubmitEvent                       => doSubmit(e)
      case e: XXFormsActionErrorEvent                 => XFormsError.handleNonFatalActionError(thisSubmission, e.throwable)
      case e if e.name == XFormsEvents.XXFORMS_SUBMIT => doSubmit(e)
      case _ =>
    }

  private def doSubmit(event: XFormsEvent): Unit = {
    val indentedLogger = getIndentedLogger
    // Variables declared here as they are used in a catch/finally block
    var p: SubmissionParameters = null
    var resolvedActionOrResource: String = null
    var submitDoneOrErrorEvalOpt: Option[Eval[Unit]] = None
    try {
      try {
        // Big bag of initial runtime parameters
        p = SubmissionParameters(event.name)(thisSubmission)
        if (indentedLogger.debugEnabled) {
          val message =
            if (p.isDeferredSubmissionFirstPass)
              "submission first pass"
            else if (p.isDeferredSubmissionSecondPass)
              "submission second pass"
            else
              "submission"
          indentedLogger.startHandleOperation("", message, "id", getEffectiveId)
        }
        // If a submission requiring a second pass was already set, then we ignore a subsequent submission but
        // issue a warning
        val twoPassParams = containingDocument.findTwoPassSubmitEvent
        if (p.isDeferredSubmission && twoPassParams.isDefined) {
          indentedLogger.logWarning(
            "",
            "another submission requiring a second pass already exists",
            "existing submission",
            twoPassParams.get.targetEffectiveId,
            "new submission",
            getEffectiveId
          )
          return
        }

        /* ***** Check for pending uploads ********************************************************************** */

        // We can do this first, because the check just depends on the controls, instance to submit, and pending
        // submissions if any. This does not depend on the actual state of the instance.
        if (p.serialize && p.xxfUploads &&
          SubmissionUtils.hasBoundRelevantPendingUploadControls(containingDocument, p.refContext.refInstanceOpt))
          throw new XFormsSubmissionException(
            thisSubmission,
            "xf:submission: instance to submit has at least one pending upload.",
            "checking pending uploads",
            null,
            new XFormsSubmitErrorEvent(
              thisSubmission,
              ErrorType.XXFormsPendingUploads,
              None
            )
          )

        /* ***** Update data model ****************************************************************************** */

        val relevanceHandling = p.relevanceHandling

        // "The data model is updated"
        p.refContext.refInstanceOpt foreach { refInstance =>
          val modelForInstance = refInstance.model
          // NOTE: XForms 1.1 says that we should rebuild/recalculate the "model containing this submission".
          // Here, we rebuild/recalculate instead the model containing the submission's single-node binding.
          // This can be different than the model containing the submission if using e.g. xxf:instance().
          // NOTE: XForms 1.1 seems to say this should happen regardless of whether we serialize or not. If
          // the instance is not serialized and if no instance data is otherwise used for the submission,
          // this seems however unneeded so we optimize out.

          // Rebuild impacts validation, relevance and calculated values (set by recalculate)
          if (p.validate || relevanceHandling != RelevanceHandling.Keep || p.xxfCalculate)
            modelForInstance.doRebuild()

          // Recalculate impacts relevance and calculated values
          if (relevanceHandling != RelevanceHandling.Keep || p.xxfCalculate)
            modelForInstance.doRecalculateRevalidate()
        }

        /* ***** Handle deferred submission ********************************************************************* */

        // Deferred submission: end of the first pass
        if (p.isDeferredSubmissionFirstPass) {
          // Create (but abandon) document to submit here because in case of error, an Ajax response will still be produced
          if (p.serialize)
            createDocumentToSubmit(
              p.refContext.refNodeInfo,
              p.refContext.refInstanceOpt,
              p.validate,
              relevanceHandling,
              p.xxfAnnotate,
              p.xxfRelevantAttOpt)(
              indentedLogger
            )
          containingDocument.addTwoPassSubmitEvent(TwoPassSubmissionParameters(getEffectiveId, p))
          return
        }

        /* ***** Submission second pass ************************************************************************* */

        // Compute parameters only needed during second pass
        val p2 = SecondPassParameters(thisSubmission, p)
        resolvedActionOrResource = p2.actionOrResource // in case of exception

        /* ***** Serialization ********************************************************************************** */

        getRequestedSerialization(p.serializationOpt, p.xformsMethod, p.httpMethod) match {
          case None =>
            throw new XFormsSubmissionException(
              thisSubmission,
              "xf:submission: invalid submission method requested: " + p.xformsMethod,
              "serializing instance",
              null,
              null
            )
          case Some(requestedSerialization) =>
            val documentToSubmit =
              if (p.serialize) {
                // Check if a submission requires file upload information

                // Annotate before re-rooting/pruning
                if (requestedSerialization.startsWith("multipart/") && p.refContext.refInstanceOpt.isDefined)
                  SubmissionUtils.annotateBoundRelevantUploadControls(containingDocument, p.refContext.refInstanceOpt.get)

                // Create document to submit
                createDocumentToSubmit(
                  p.refContext.refNodeInfo,
                  p.refContext.refInstanceOpt,
                  p.validate,
                  relevanceHandling,
                  p.xxfAnnotate,
                  p.xxfRelevantAttOpt)(
                  indentedLogger
                )
              } else {
                // Don't recreate document
                null
              }

          val overriddenSerializedData =
            if (! p.isDeferredSubmissionSecondPass && p.serialize) {
              // Fire `xforms-submit-serialize`
              // "The event xforms-submit-serialize is dispatched. If the submission-body property of the event
              // is changed from the initial value of empty string, then the content of the submission-body
              // property string is used as the submission serialization. Otherwise, the submission serialization
              // consists of a serialization of the selected instance data according to the rules stated at 11.9
              // Submission Options."
              val serializeEvent =
                new XFormsSubmitSerializeEvent(thisSubmission, p.refContext.refNodeInfo, requestedSerialization)

              Dispatch.dispatchEvent(serializeEvent)

              // TODO: rest of submission should happen upon default action of event
              serializeEvent.submissionBodyAsString
            } else {
              null
            }

          // Serialize
          val sp = SerializationParameters(thisSubmission, p, p2, requestedSerialization, documentToSubmit, overriddenSerializedData)

          /* ***** Submission connection ************************************************************************** */

          // Result information
          val submissionResultOpt =
            submissions find (_.isMatch(p, p2, sp)) flatMap { submission =>
              withDebug("connecting", List("type" -> submission.getType)) {
                submission.connect(p, p2, sp)
              }(indentedLogger)
            }

          /* ***** Submission result processing ******************************************************************* */

          // `None` in case the submission is running asynchronously, AND when ???
          submissionResultOpt foreach { submissionResult =>
            submitDoneOrErrorEvalOpt =
              handleSubmissionResult(p, p2, submissionResult, initializeXPathContext = true) // true because function context might have changed
          }
        }
      } catch {
        case NonFatal(throwable) =>
          /* ***** Handle errors ********************************************************************************** */
          val pVal = p
          val resolvedActionOrResourceVal = resolvedActionOrResource
          submitDoneOrErrorEvalOpt = Some(Eval.later {
            // It doesn't serve any purpose here to dispatch an event, so we just propagate the exception
            if (pVal != null && pVal.isDeferredSubmissionSecondPass && containingDocument.isLocalSubmissionForward)
              throw new XFormsSubmissionException(
                thisSubmission,
                "Error while processing xf:submission", "processing submission",
                throwable,
                null
              )
            else
              sendSubmitError(throwable, resolvedActionOrResourceVal) // any exception will cause an error event to be dispatched
          })
      }
    } finally {
      // Log total time spent in submission
      if (p != null && indentedLogger.debugEnabled)
        indentedLogger.endHandleOperation()
    }
    // Execute post-submission code if any
    // This typically dispatches xforms-submit-done/xforms-submit-error, or may throw another exception
    submitDoneOrErrorEvalOpt foreach { submitDoneOrErrorEval =>
      // We do this outside the above catch block so that if a problem occurs during dispatching xforms-submit-done
      // or xforms-submit-error we don't dispatch xforms-submit-error (which would be illegal).
      // This will also close the connection result if needed.
      submitDoneOrErrorEval.value
    }
  }

  /*
   * Process the response of an asynchronous submission.
   */
  private[submission]
  def doSubmitReplace(submissionResult: SubmissionResult): Unit = {

    require(submissionResult ne null)

    val p = SubmissionParameters(null)(thisSubmission)
    val p2 = SecondPassParameters(thisSubmission, p)

    handleSubmissionResult(p, p2, submissionResult, initializeXPathContext = false) foreach { submitDoneEval=>
      // Do this outside the `handleSubmissionResult` catch block so that if a problem occurs during dispatching
      // `xforms-submit-done` we don't dispatch `xforms-submit-error` (which would be illegal).
      submitDoneEval.value
    }
  }

  private def handleSubmissionResult(
    p                      : SubmissionParameters,
    p2                     : SecondPassParameters,
    submissionResult       : SubmissionResult,
    initializeXPathContext : Boolean
  ): Option[Eval[Unit]] = {

    require(p ne null)
    require(p2 ne null)
    require(submissionResult ne null)

    try {
      val indentedLogger = getIndentedLogger
      withDebug("handling result") {

        val updatedP =
          if (initializeXPathContext)
            SubmissionParameters.withUpdatedRefContext(p)(thisSubmission)
          else
              p

        submissionResult.result match {
          case Success((replacer, connectionResult)) =>
            try {
              replacer.replace(connectionResult, updatedP, p2)
            } finally {
              connectionResult.close()
            }
          case Failure(throwable) =>
            Eval.later(sendSubmitError(throwable, submissionResult)).some
        }
      }(indentedLogger)
    } catch {
      case NonFatal(throwable) =>
        Eval.later(sendSubmitError(throwable, submissionResult)).some
    }
  }

  def sendSubmitDone(connectionResult: ConnectionResult): Eval[Unit] =
    Eval.later {
      // After a submission, the context might have changed
      model.resetAndEvaluateVariables()
      Dispatch.dispatchEvent(new XFormsSubmitDoneEvent(thisSubmission, connectionResult))
    }

  def getReplacer(connectionResult: ConnectionResult, p: SubmissionParameters): Replacer = {
    // NOTE: This can be called from other threads so it must NOT modify the XFCD or submission
    // Handle response
    if (connectionResult.dontHandleResponse) {
      // Always return a replacer even if it does nothing, this way we don't have to deal with null
      new NoneReplacer(thisSubmission)
    } else if (StatusCode.isSuccessCode(connectionResult.statusCode)) {
      // Successful response
      if (connectionResult.hasContent) {
        // There is a body
        // Get replacer
        if (p.replaceType == ReplaceType.All)
          new AllReplacer(thisSubmission, containingDocument)
        else if (p.replaceType == ReplaceType.Instance)
          new InstanceReplacer(thisSubmission, containingDocument)
        else if (p.replaceType == ReplaceType.Text)
          new TextReplacer(thisSubmission, containingDocument)
        else if (p.replaceType == ReplaceType.None)
          new NoneReplacer(thisSubmission)
        else if (p.replaceType == ReplaceType.Binary)
          new BinaryReplacer(thisSubmission, containingDocument)
        else
          throw new XFormsSubmissionException(
            thisSubmission,
            "xf:submission: invalid replace attribute: " + p.replaceType,
            "processing instance replacement",
            null,
            new XFormsSubmitErrorEvent(
              thisSubmission,
              ErrorType.XXFormsInternalError,
              connectionResult.some
            )
          )
      } else {
        // There is no body, notify that processing is terminated
        if (p.replaceType == ReplaceType.Instance || p.replaceType == ReplaceType.Text) {
          // XForms 1.1 says it is fine not to have a body, but in most cases you will want to know that
          // no instance replacement took place
          val indentedLogger = getIndentedLogger
          indentedLogger.logWarning(
            "",
            "instance or text replacement did not take place upon successful response because no body was provided.",
            "submission id",
            getEffectiveId
          )
        }
        // "For a success response not including a body, submission processing concludes after dispatching
        // xforms-submit-done"
        new NoneReplacer(thisSubmission)
      }
    } else if (StatusCode.isRedirectCode(connectionResult.statusCode)) {
      // Got a redirect
      // Currently we don't know how to handle a redirect for replace != "all"
      if (p.replaceType != ReplaceType.All)
        throw new XFormsSubmissionException(
          thisSubmission,
          "xf:submission for submission id: " + getId + ", redirect code received with replace=\"" + p.replaceType + "\"",
          "processing submission response",
          null,
          new XFormsSubmitErrorEvent(
            thisSubmission,
            ErrorType.ResourceError,
            connectionResult.some
          )
        )
      new RedirectReplacer(containingDocument)
    } else {
      // Error code received
      if (p.replaceType == ReplaceType.All && connectionResult.hasContent) {
        // For `replace="all"`, if we received content, which might be an error page, we still want to serve it
        new AllReplacer(thisSubmission, containingDocument)
      } else
        throw new XFormsSubmissionException(
          thisSubmission,
          "xf:submission for submission id: " + getId + ", error code received when submitting instance: " + connectionResult.statusCode,
          "processing submission response",
          null,
          new XFormsSubmitErrorEvent(
            thisSubmission,
            ErrorType.ResourceError,
            connectionResult.some
          )
        )
    }
  }

  def findReplaceInstanceNoTargetref(refInstance: Option[XFormsInstance]): Option[XFormsInstance] =
    if (staticSubmission.xxfReplaceInstanceIdOrNull != null)
      container.findInstance(staticSubmission.xxfReplaceInstanceIdOrNull)
    else if (staticSubmission.replaceInstanceIdOrNull != null)
      model.findInstance(staticSubmission.replaceInstanceIdOrNull)
    else if (refInstance.isEmpty)
      model.defaultInstanceOpt
    else
      refInstance

  def evaluateTargetRef(
    xpathContext                 : XPathCache.XPathContext,
    defaultReplaceInstance       : XFormsInstance,
    submissionElementContextItem : om.Item
  ): om.NodeInfo = {
    val destinationObject =
      if (staticSubmission.targetrefOpt.isEmpty) {
        // There is no explicit @targetref, so the target is implicitly the root element of either the instance
        // pointed to by @ref, or the instance specified by @instance or @xxf:instance.
        defaultReplaceInstance.rootElement
      } else {
        // There is an explicit @targetref, which must be evaluated.
        // "The in-scope evaluation context of the submission element is used to evaluate the expression." BUT ALSO "The
        // evaluation context for this attribute is the in-scope evaluation context for the submission element, except
        // the context node is modified to be the document element of the instance identified by the instance attribute
        // if it is specified."
        val hasInstanceAttribute = staticSubmission.xxfReplaceInstanceIdOrNull != null || staticSubmission.replaceInstanceIdOrNull != null
        val targetRefContextItem =
          if (hasInstanceAttribute)
            defaultReplaceInstance.rootElement
          else
            submissionElementContextItem
        // Evaluate destination node
        // "This attribute is evaluated only once a successful submission response has been received and if the replace
        // attribute value is "instance" or "text". The first node rule is applied to the result."
        XPathCache.evaluateSingleWithContext(
          xpathContext,
          targetRefContextItem,
          staticSubmission.targetrefOpt.get,
          containingDocument.getRequestStats.getReporter
        )
      }
    // TODO: Also detect readonly node/ancestor situation
    destinationObject match {
      case node: om.NodeInfo if node.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE => node
      case _ => null
    }
  }

  private def getIndentedLogger: IndentedLogger =
    containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)

  def getDetailsLogger(p: SubmissionParameters, p2: SecondPassParameters): IndentedLogger =
    XFormsModelSubmission.getNewLogger(p, p2, getIndentedLogger, XFormsModelSubmission.isLogDetails)

  def getTimingLogger(p: SubmissionParameters, p2: SecondPassParameters): IndentedLogger = {
    val indentedLogger = getIndentedLogger
    XFormsModelSubmission.getNewLogger(p, p2, indentedLogger, indentedLogger.debugEnabled)
  }

  def allowExternalEvent(eventName: String): Boolean =
    XFormsModelSubmission.ALLOWED_EXTERNAL_EVENTS.contains(eventName)
}