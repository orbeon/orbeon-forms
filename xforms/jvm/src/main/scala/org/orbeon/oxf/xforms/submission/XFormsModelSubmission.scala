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
import java.util.concurrent.Callable

import org.apache.log4j.Logger
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEventTarget, XFormsEvents}
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xforms.submission.XFormsModelSubmissionBase.getRequestedSerialization
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsError, XFormsProperties}
import org.orbeon.oxf.xml.dom.LocationData
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{RelevanceHandling, XFormsId}

import scala.util.control.NonFatal

/**
 * Represents an XForms model submission instance.
 *
 * TODO: Refactor handling of serialization to separate classes.
 */
object XFormsModelSubmission {

  val LOGGING_CATEGORY = "submission"
  val logger: Logger = LoggerFactory.createLogger(classOf[XFormsModelSubmission])

  /**
   * Run the given submission callable. This must be a callable for a replace="all" submission.
   *
   * @param callable callable run
   * @param response response to write to if needed
   */
  def runDeferredSubmission(callable: Callable[SubmissionResult], response: ExternalContext.Response) {
    // Run submission
    val result = callable.call()
    if (result != null) {
      // Callable did not do all the work, completed it here
      try
        if (result.getReplacer != null) {
          // Replacer provided, perform replacement
          result.getReplacer match {
            case _: AllReplacer => AllReplacer.forwardResultToResponse(result.connectionResult, response)
            case _: RedirectReplacer => RedirectReplacer.doReplace(result.connectionResult, response)
            case _ => assert(result.getReplacer.isInstanceOf[NoneReplacer])
          }
        } else if (result.getThrowable != null) {
          // Propagate throwable, which might have come from a separate thread
          throw new OXFException(result.getThrowable)
        } else {
          // Should not happen
        }
      finally
        result.close()
    }
  }

  private def getNewLogger(
    p               : SubmissionParameters,
    p2              : SecondPassParameters,
    indentedLogger  : IndentedLogger,
    newDebugEnabled : Boolean
  ) =
    if (p2.isAsynchronous && !ReplaceType.isReplaceNone(p.replaceType)) {
      // Background asynchronous submission creates a new logger with its own independent indentation
      val newIndentation = new IndentedLogger.Indentation(indentedLogger.getIndentation.indentation)
      new IndentedLogger(indentedLogger, newIndentation, newDebugEnabled)
    } else if (indentedLogger.isDebugEnabled != newDebugEnabled) {
      // Keep shared indentation but use new debug setting
      new IndentedLogger(indentedLogger, indentedLogger.getIndentation, newDebugEnabled)
    } else {
      // Synchronous submission or foreground asynchronous submission uses current logger
      indentedLogger
    }

  private def isLogDetails = XFormsProperties.getDebugLogging.contains("submission-details")

  // Only allow xxforms-submit from client
  private val ALLOWED_EXTERNAL_EVENTS = new util.HashSet[String]
  ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_SUBMIT)
}

class XFormsModelSubmission(
  val container        : XBLContainer,
  val staticSubmission : org.orbeon.oxf.xforms.analysis.model.Submission,
  val model            : XFormsModel
) extends XFormsModelSubmissionBase {

  thisXFormsModelSubmission =>

  val containingDocument: XFormsContainingDocument = container.getContainingDocument

  // All the submission types in the order they must be checked
  val submissions =
    List(
      new EchoSubmission(this),
      new ClientGetAllSubmission(this),
      new CacheableSubmission(this),
      new RegularSubmission(this)
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
      case e: XXFormsActionErrorEvent                 => XFormsError.handleNonFatalActionError(this, e.throwable)
      case e if e.name == XFormsEvents.XXFORMS_SUBMIT => doSubmit(e)
      case _ =>
    }

  private def doSubmit(event: XFormsEvent): Unit = {
    val indentedLogger = getIndentedLogger
    // Variables declared here as they are used in a catch/finally block
    var p: SubmissionParameters = null
    var resolvedActionOrResource: String = null
    var submitDoneOrErrorRunnable: Runnable = null
    try {
      try {
        // Big bag of initial runtime parameters
        p = SubmissionParameters(event.name)(thisXFormsModelSubmission)
        if (indentedLogger.isDebugEnabled) {
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
            this.getEffectiveId
          )
          return
        }

        /* ***** Check for pending uploads ********************************************************************** */

        // We can do this first, because the check just depends on the controls, instance to submit, and pending
        // submissions if any. This does not depend on the actual state of the instance.
        if (p.serialize && p.xxfUploads &&
          SubmissionUtils.hasBoundRelevantPendingUploadControls(containingDocument, p.refContext.refInstanceOpt))
          throw new XFormsSubmissionException(
            this,
            "xf:submission: instance to submit has at least one pending upload.",
            "checking pending uploads",
            null,
            new XFormsSubmitErrorEvent(
              thisXFormsModelSubmission,
              ErrorType.XXFORMS_PENDING_UPLOADS,
              null
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
        val p2 = SecondPassParameters(thisXFormsModelSubmission, p)
        resolvedActionOrResource = p2.actionOrResource // in case of exception

        /* ***** Serialization ********************************************************************************** */

        getRequestedSerialization(p.serializationOpt, p.xformsMethod, p.httpMethod) match {
          case None =>
            throw new XFormsSubmissionException(
              this,
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
                new XFormsSubmitSerializeEvent(thisXFormsModelSubmission, p.refContext.refNodeInfo, requestedSerialization)

              Dispatch.dispatchEvent(serializeEvent)

              // TODO: rest of submission should happen upon default action of event
              serializeEvent.submissionBodyAsString
            } else {
              null
            }

          // Serialize
          val sp = SerializationParameters(this, p, p2, requestedSerialization, documentToSubmit, overriddenSerializedData)

          /* ***** Submission connection ************************************************************************** */

          // Result information
          val submissionResultOpt =
            submissions find (_.isMatch(p, p2, sp)) flatMap { submission =>
              if (indentedLogger.isDebugEnabled)
                indentedLogger.startHandleOperation("", "connecting", "type", submission.getType)
              try {
                 Option(submission.connect(p, p2, sp))
              } finally
                if (indentedLogger.isDebugEnabled)
                  indentedLogger.endHandleOperation()
            }

          /* ***** Submission result processing ******************************************************************* */
          // NOTE: handleSubmissionResult() catches Throwable and returns a Runnable

          // `None` in case the submission is running asynchronously, AND when ???
          submissionResultOpt foreach { submissionResult =>
            submitDoneOrErrorRunnable =
              handleSubmissionResult(p, p2, submissionResult, initializeXPathContext = true) // true because function context might have changed
          }
        }
      } catch {
        case throwable: Throwable =>
          /* ***** Handle errors ********************************************************************************** */
          val pVal = p
          val resolvedActionOrResourceVal = resolvedActionOrResource
          submitDoneOrErrorRunnable = new Runnable() {
            override def run() {
              if (pVal != null && pVal.isDeferredSubmissionSecondPass && containingDocument.isLocalSubmissionForward) { // It doesn't serve any purpose here to dispatch an event, so we just propagate the exception
                throw new XFormsSubmissionException(thisXFormsModelSubmission, "Error while processing xf:submission", "processing submission", throwable, null)
              }
              else { // Any exception will cause an error event to be dispatched
                sendSubmitError(throwable, resolvedActionOrResourceVal)
              }
            }
          }
      }
    } finally {
      // Log total time spent in submission
      if (p != null && indentedLogger.isDebugEnabled)
        indentedLogger.endHandleOperation()
    }
    // Execute post-submission code if any
    // This typically dispatches xforms-submit-done/xforms-submit-error, or may throw another exception
    if (submitDoneOrErrorRunnable != null) {
      // We do this outside the above catch block so that if a problem occurs during dispatching xforms-submit-done
      // or xforms-submit-error we don't dispatch xforms-submit-error (which would be illegal).
      // This will also close the connection result if needed.
      submitDoneOrErrorRunnable.run()
    }
  }

  /*
   * Process the response of an asynchronous submission.
   */
  private[submission]
  def doSubmitReplace(submissionResult: SubmissionResult): Unit = {
    require(submissionResult ne null)
    val p = SubmissionParameters(null)(thisXFormsModelSubmission)
    val p2 = SecondPassParameters(thisXFormsModelSubmission, p)
    val submitDoneRunnable = handleSubmissionResult(p, p2, submissionResult, initializeXPathContext = false)
    // Execute submit done runnable if any
    if (submitDoneRunnable != null)
      // Do this outside the handleSubmissionResult catch block so that if a problem occurs during dispatching
      // xforms-submit-done we don't dispatch xforms-submit-error (which would be illegal)
      submitDoneRunnable.run()
  }

  private def handleSubmissionResult(
    p                      : SubmissionParameters,
    p2                     : SecondPassParameters,
    submissionResult       : SubmissionResult,
    initializeXPathContext : Boolean
  ): Runnable = {

    require(p ne null)
    require(p2 ne null)
    require(submissionResult ne null)

    val submitDoneOrErrorRunnable: Runnable =
      try {
        val indentedLogger = getIndentedLogger
        if (indentedLogger.isDebugEnabled)
          indentedLogger.startHandleOperation("", "handling result")
        try {
          // Get fresh XPath context if requested
          val updatedP =
            if (initializeXPathContext)
              SubmissionParameters.withUpdatedRefContext(p)(thisXFormsModelSubmission)
            else
                p
          // Process the different types of response
          if (submissionResult.getThrowable != null) {
            () => sendSubmitError(submissionResult.getThrowable, submissionResult)
          } else {
            assert(submissionResult.getReplacer != null)
            submissionResult.getReplacer.replace(submissionResult.connectionResult, updatedP, p2)
          }
        } finally
          if (indentedLogger.isDebugEnabled)
            indentedLogger.endHandleOperation()
      } catch {
        case NonFatal(throwable) =>
          () => sendSubmitError(throwable, submissionResult)
      }
    // Create wrapping runnable to make sure the submission result is closed
    () =>
      try
        if (submitDoneOrErrorRunnable ne null)
          submitDoneOrErrorRunnable.run()
      finally {
        // Close only after the submission result has run
        submissionResult.close()
      }
  }

  def sendSubmitDone(connectionResult: ConnectionResult): Runnable =
    new Runnable {
      def run() {
        // After a submission, the context might have changed
        model.resetAndEvaluateVariables()
        Dispatch.dispatchEvent(new XFormsSubmitDoneEvent(thisXFormsModelSubmission, connectionResult))
      }
    }

  def getReplacer(connectionResult: ConnectionResult, p: SubmissionParameters): Replacer = {
    // NOTE: This can be called from other threads so it must NOT modify the XFCD or submission
    if (connectionResult != null) {
      // Handle response
      if (connectionResult.dontHandleResponse) {
        // Always return a replacer even if it does nothing, this way we don't have to deal with null
        new NoneReplacer(this, containingDocument)
      } else if (NetUtils.isSuccessCode(connectionResult.statusCode)) { // Successful response
        if (connectionResult.hasContent) { // There is a body
          // Get replacer
          if (ReplaceType.isReplaceAll(p.replaceType))
            new AllReplacer(this, containingDocument)
          else if (ReplaceType.isReplaceInstance(p.replaceType))
            new InstanceReplacer(this, containingDocument)
          else if (ReplaceType.isReplaceText(p.replaceType))
            new TextReplacer(this, containingDocument)
          else if (ReplaceType.isReplaceNone(p.replaceType))
            new NoneReplacer(this, containingDocument)
          else if (ReplaceType.isReplaceBinary(p.replaceType))
            new BinaryReplacer(this, containingDocument)
          else
            throw new XFormsSubmissionException(
              thisXFormsModelSubmission,
              "xf:submission: invalid replace attribute: " + p.replaceType,
              "processing instance replacement",
              null,
              new XFormsSubmitErrorEvent(
                this,
                ErrorType.XXFORMS_INTERNAL_ERROR,
                connectionResult
              )
            )
        } else {
          // There is no body, notify that processing is terminated
          if (ReplaceType.isReplaceInstance(p.replaceType) || ReplaceType.isReplaceText(p.replaceType)) {
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
          new NoneReplacer(this, containingDocument)
        }
      } else if (NetUtils.isRedirectCode(connectionResult.statusCode)) {
        // Got a redirect
        // Currently we don't know how to handle a redirect for replace != "all"
        if (! ReplaceType.isReplaceAll(p.replaceType))
          throw new XFormsSubmissionException(
            this,
            "xf:submission for submission id: " + getId + ", redirect code received with replace=\"" + p.replaceType + "\"",
            "processing submission response",
            null,
            new XFormsSubmitErrorEvent(
              this,
              ErrorType.RESOURCE_ERROR,
              connectionResult
            )
          )
        new RedirectReplacer(this, containingDocument)
      } else {
        // Error code received
        throw new XFormsSubmissionException(
          this,
          "xf:submission for submission id: " + getId + ", error code received when submitting instance: " + connectionResult.statusCode,
          "processing submission response",
          null,
          new XFormsSubmitErrorEvent(
            this,
            ErrorType.RESOURCE_ERROR,
            connectionResult
          )
        )
      }
    } else
      null
  }

  def findReplaceInstanceNoTargetref(refInstance: Option[XFormsInstance]): XFormsInstance =
    if (staticSubmission.xxfReplaceInstanceIdOrNull != null)
      container.findInstanceOrNull(staticSubmission.xxfReplaceInstanceIdOrNull)
    else if (staticSubmission.replaceInstanceIdOrNull != null)
      model.getInstance(staticSubmission.replaceInstanceIdOrNull)
    else if (refInstance.isEmpty)
      model.getDefaultInstance
    else
      refInstance.get

  def evaluateTargetRef(
    xpathContext                 : XPathCache.XPathContext,
    defaultReplaceInstance       : XFormsInstance,
    submissionElementContextItem : Item
  ): NodeInfo = {
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
      case node: NodeInfo if node.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE => node
      case _ => null
    }
  }

  def getIndentedLogger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)

  def getDetailsLogger(p: SubmissionParameters, p2: SecondPassParameters): IndentedLogger =
    XFormsModelSubmission.getNewLogger(p, p2, getIndentedLogger, XFormsModelSubmission.isLogDetails)

  def getTimingLogger(p: SubmissionParameters, p2: SecondPassParameters): IndentedLogger = {
    val indentedLogger = getIndentedLogger
    XFormsModelSubmission.getNewLogger(p, p2, indentedLogger, indentedLogger.isDebugEnabled)
  }

  def allowExternalEvent(eventName: String): Boolean =
    XFormsModelSubmission.ALLOWED_EXTERNAL_EVENTS.contains(eventName)
}