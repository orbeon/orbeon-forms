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
import org.log4s.Logger
import org.orbeon.connection.{ConnectionResult, ConnectionResultT}
import org.orbeon.datatypes.LocationData
import org.orbeon.dom.{Document, QName}
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.XFormsEvent.TunnelProperties
import org.orbeon.oxf.xforms.event._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xforms.submission.SubmissionParameters.createRefContext
import org.orbeon.oxf.xforms.submission.SubmissionUtils.convertConnectResult
import org.orbeon.oxf.xforms.submission.XFormsModelSubmissionSupport.{isSatisfiesValidity, prepareXML, requestedSerialization}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{Loggers, XFormsContainingDocument, XFormsError}
import org.orbeon.saxon.om
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{RelevanceHandling, XFormsId}
import shapeless.syntax.typeable._

import java.net.URI
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


object XFormsModelSubmission {

  val LOGGING_CATEGORY = "submission"
  val logger: Logger = LoggerFactory.createLogger(classOf[XFormsModelSubmission])

  private def getNewLogger(
    submissionParameters: SubmissionParameters,
    indentedLogger      : IndentedLogger,
    newDebugEnabled     : Boolean
  ) =
    if (submissionParameters.isAsynchronous && submissionParameters.replaceType != ReplaceType.None) {
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

  private def isLogDetails = Loggers.isDebugEnabled("submission-details")

  private val AllowedExternalEvents = Set(XFormsEvents.XXFORMS_SUBMIT)
}

/**
 * Represents an XForms model submission instance.
 *
 * TODO: Refactor handling of serialization to separate classes.
 */
class XFormsModelSubmission(
  val container        : XBLContainer,
  val staticSubmission : org.orbeon.oxf.xforms.analysis.model.Submission,
  val model            : XFormsModel
) extends ListenersTrait
     with XFormsEventTarget {

  thisSubmission =>

  import Private._

  val containingDocument: XFormsContainingDocument = container.getContainingDocument

  // All the submission types in the order they must be checked
  val submissions: List[BaseSubmission] =
    List(
      new EchoSubmission(thisSubmission),
      new ClientGetAllSubmission(thisSubmission),
      new CacheableSubmission(thisSubmission),
      new RegularSubmission(thisSubmission)
    )

  def getId               : String            = staticSubmission.staticId
  def getPrefixedId       : String            = staticSubmission.prefixedId
  def scope               : Scope             = staticSubmission.scope
  def getEffectiveId      : String            = XFormsId.getRelatedEffectiveId(model.getEffectiveId, getId)
  def getLocationData     : LocationData      = staticSubmission.locationData
  def parentEventObserver : XFormsEventTarget = model

  def performTargetAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = ()

  def performDefaultAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = {
    implicit val indentedLogger: IndentedLogger = getIndentedLogger
    event match {
      case e: XFormsSubmitEvent                       => doSubmit(e).foreach(processReplaceResultAndCloseConnection)
      case e: XXFormsActionErrorEvent                 => XFormsError.handleNonFatalActionError(thisSubmission, e.throwableOpt)
      case e if e.name == XFormsEvents.XXFORMS_SUBMIT =>

        implicit val refContext: RefContext = createRefContext(thisSubmission)

        // `processDueDelayedEvents()` passes us `SubmissionParameters.EventName`
        val submissionParameters = e.property[SubmissionParameters](SubmissionParameters.EventName).getOrElse(throw new IllegalStateException)
        doSubmitSecondPass(submissionParameters).foreach(processReplaceResultAndCloseConnection)

      case _ =>
    }
  }

  private[submission]
  def processAsyncSubmissionResponse(
    connectResultTry    : Try[ConnectResult],
    submissionParameters: SubmissionParameters
  )(implicit
    logger              : IndentedLogger
  ): Unit =
    processReplaceResultAndCloseConnection(
      connectResultTry match {
        case Success(connectResult) =>

          implicit val refContext: RefContext = createRefContext(thisSubmission)

          handleConnectResult(
            submissionParameters   = submissionParameters,
            connectResult          = connectResult,
            initializeXPathContext = false
          )
        case Failure(t) =>
          (ReplaceResult.SendError(t, Left(None), submissionParameters.tunnelProperties), None)
      }
    )


  def getReplacer(cxr: ConnectionResultT[_], submissionParameters: SubmissionParameters)(implicit logger: IndentedLogger): Replacer = {
    // NOTE: This can be called from other threads so it must NOT modify the XFCD or submission
    // Handle response
    if (cxr.dontHandleResponse) {
      // Always return a replacer even if it does nothing, this way we don't have to deal with null
      NoneReplacer
    } else if (StatusCode.isSuccessCode(cxr.statusCode)) {
      // Successful response
      if (cxr.hasContent) {
        // There is a body
        // Get replacer
        if (submissionParameters.replaceType == ReplaceType.All)
          AllReplacer
        else if (submissionParameters.replaceType == ReplaceType.Instance)
          InstanceReplacer
        else if (submissionParameters.replaceType == ReplaceType.Text)
          TextReplacer
        else if (submissionParameters.replaceType == ReplaceType.None)
          NoneReplacer
        else if (submissionParameters.replaceType == ReplaceType.Binary)
          BinaryReplacer
        else
          throw new XFormsSubmissionException(
            thisSubmission,
            s"xf:submission: invalid replace attribute: `${submissionParameters.replaceType}`",
            "processing instance replacement",
            null,
            new XFormsSubmitErrorEvent(
              target           = thisSubmission,
              errorType        = ErrorType.XXFormsInternalError,
              cxrOpt           = cxr.some,
              tunnelProperties = submissionParameters.tunnelProperties
            )
          )
      } else {
        // There is no body, notify that processing is terminated
        if (submissionParameters.replaceType == ReplaceType.Instance || submissionParameters.replaceType == ReplaceType.Text) {
          // XForms 1.1 says it is fine not to have a body, but in most cases you will want to know that
          // no instance replacement took place
          warn(
            "instance or text replacement did not take place upon successful response because no body was provided.",
            List(
              "submission id" -> getEffectiveId
            )
          )
        }
        // "For a success response not including a body, submission processing concludes after dispatching
        // xforms-submit-done"
        NoneReplacer
      }
    } else if (StatusCode.isRedirectCode(cxr.statusCode)) {
      // Got a redirect
      // Currently we don't know how to handle a redirect for replace != "all"
      if (submissionParameters.replaceType != ReplaceType.All)
        throw new XFormsSubmissionException(
          thisSubmission,
          // Scala 2.13: just use `"` and `\"` (use of `"""` due to a bug in Scala 2.12!)
          s"""xf:submission for submission id: `$getId`, redirect code received with replace="${submissionParameters.replaceType.entryName}"""",
          "processing submission response",
          null,
          new XFormsSubmitErrorEvent(
            target           = thisSubmission,
            errorType        = ErrorType.ResourceError,
            cxrOpt           = cxr.some,
            tunnelProperties = submissionParameters.tunnelProperties
          )
        )
      RedirectReplacer
    } else {
      // Error code received
      if (submissionParameters.replaceType == ReplaceType.All && cxr.hasContent) {
        // For `replace="all"`, if we received content, which might be an error page, we still want to serve it
        AllReplacer
      } else
        throw new XFormsSubmissionException(
          thisSubmission,
          s"xf:submission for submission id: `$getId`, error code received when submitting instance: ${cxr.statusCode}",
          "processing submission response",
          null,
          new XFormsSubmitErrorEvent(
            target           = thisSubmission,
            errorType        = ErrorType.ResourceError,
            cxrOpt           = cxr.some,
            tunnelProperties = submissionParameters.tunnelProperties
          )
        )
    }
  }

  def findReplaceInstanceNoTargetref(refInstance: Option[XFormsInstance]): Option[XFormsInstance] =
    staticSubmission.xxfReplaceInstanceIdOpt.map(container.findInstance)
      .orElse(staticSubmission.replaceInstanceIdOpt.map(model.findInstance))
      .getOrElse(refInstance.orElse(model.defaultInstanceOpt))

  def evaluateTargetRef(
    xpathContext                 : XPathCache.XPathContext,
    defaultReplaceInstance       : XFormsInstance,
    submissionElementContextItem : om.Item
  ): Option[om.NodeInfo] = {
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
        val hasInstanceAttribute = staticSubmission.xxfReplaceInstanceIdOpt.isDefined || staticSubmission.replaceInstanceIdOpt.isDefined
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
      case node: om.NodeInfo if node.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE => node.some
      case _ => None
    }
  }

  def getIndentedLogger: IndentedLogger =
    containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)

  def getDetailsLogger(submissionParameters: SubmissionParameters): IndentedLogger =
    XFormsModelSubmission.getNewLogger(submissionParameters, getIndentedLogger, XFormsModelSubmission.isLogDetails)

  def getTimingLogger(submissionParameters: SubmissionParameters): IndentedLogger = {
    val indentedLogger = getIndentedLogger
    XFormsModelSubmission.getNewLogger(submissionParameters, indentedLogger, indentedLogger.debugEnabled)
  }

  def allowExternalEvent(eventName: String): Boolean =
    XFormsModelSubmission.AllowedExternalEvents.contains(eventName)

  private object Private {

    def doSubmit(
      event         : XFormsEvent
    )(implicit
      indentedLogger: IndentedLogger
    ): Option[(ReplaceResult, Option[ConnectionResult])] = {

      // Create the big bag of initial runtime parameters, which can throw
      val (submissionParameters, explicitRefContext) =
        try {
          val refContext = createRefContext(thisSubmission)
          (
            SubmissionParameters(thisSubmission, event.actionProperties)(refContext),
            refContext
          )
        } catch {
          case NonFatal(throwable: Throwable) =>
            return (ReplaceResult.SendError(throwable, Right(None), event.tunnelProperties), None).some
        }

      implicit val refContext: RefContext = explicitRefContext

      withDebug(
        if (submissionParameters.isDeferredSubmission)
          "submission first pass"
        else
          "submission",
        List("id" -> getEffectiveId)
      ) {
        try {

          /* ***** Check for pending uploads ********************************************************************** */

          // We can do this first, because the check just depends on the controls, instance to submit, and pending
          // submissions if any. This does not depend on the actual state of the instance.
          if (
            submissionParameters.serialize  &&
            submissionParameters.xxfUploads &&
            SubmissionUtils.hasBoundRelevantPendingUploadControls(containingDocument, refContext.refInstanceOpt)
          ) throw new XFormsSubmissionException(
              thisSubmission,
              "xf:submission: instance to submit has at least one pending upload.",
              "checking pending uploads",
              null,
              new XFormsSubmitErrorEvent(
                target           = thisSubmission,
                errorType        = ErrorType.XXFormsPendingUploads,
                cxrOpt           = None,
                tunnelProperties = event.tunnelProperties
              )
            )

          /* ***** Update data model ****************************************************************************** */

          // "The data model is updated"
          refContext.refInstanceOpt foreach { refInstance =>
            val modelForInstance = refInstance.model
            // NOTE: XForms 1.1 says that we should rebuild/recalculate the "model containing this submission".
            // Here, we rebuild/recalculate instead the model containing the submission's single-node binding.
            // This can be different than the model containing the submission if using e.g. xxf:instance().
            // NOTE: XForms 1.1 seems to say this should happen regardless of whether we serialize or not. If
            // the instance is not serialized and if no instance data is otherwise used for the submission,
            // this seems however unneeded so we optimize out.

            // Rebuild impacts validation, relevance and calculated values (set by recalculate)
            if (submissionParameters.validate || submissionParameters.relevanceHandling != RelevanceHandling.Keep || submissionParameters.xxfCalculate)
              modelForInstance.doRebuild()

            // Recalculate impacts relevance and calculated values
            if (submissionParameters.relevanceHandling != RelevanceHandling.Keep || submissionParameters.xxfCalculate)
              modelForInstance.doRecalculateRevalidate()
          }

          /* ***** Handle deferred submission ********************************************************************* */

          debug(s"submissionParameters.isDeferredSubmission = ${submissionParameters.isDeferredSubmission}")

          // Deferred submission: end of the first pass
          if (submissionParameters.isDeferredSubmission) {
            // Create (but abandon) document to submit here because in case of error, an Ajax response will still be produced
            if (submissionParameters.serialize)
              createUriOrDocumentToSubmit(submissionParameters)
            containingDocument.addTwoPassSubmitEvent(TwoPassSubmissionParameters(getEffectiveId, submissionParameters))
            (ReplaceResult.None, None).some // TODO: could introduce `ReplaceResult.ScheduleSecondPass`
          } else {
            doSubmitSecondPass(submissionParameters)
          }
        } catch {
          case NonFatal(throwable) =>
            /* ***** Handle errors ********************************************************************************** */
            (
              ReplaceResult.SendError(throwable, Right(submissionParameters.actionOrResource.some), submissionParameters.tunnelProperties),
              None
            ).some
        }
      } // withDebug
    }

    def doSubmitSecondPass(
      submissionParameters: SubmissionParameters,
    )(implicit
      refContext          : RefContext,
      indentedLogger      : IndentedLogger
    ): Option[(ReplaceResult, Option[ConnectionResult])] =
      withDebug(
        if (submissionParameters.isDeferredSubmission)
          "submission second pass"
        else
          "submission",
        List("id" -> getEffectiveId)
      ) {
        try {

          /* ***** Serialization ********************************************************************************** */

          requestedSerialization(submissionParameters) match {
            case None =>
              throw new XFormsSubmissionException(
                thisSubmission,
                s"xf:submission: invalid submission method requested: `${submissionParameters.xformsMethod}`",
                "serializing instance",
                null,
                null
              )
            case Some(requestedSerialization) =>

              val uriOrDocumentToSubmitOpt =
                if (submissionParameters.serialize) {
                  // Check if a submission requires file upload information

                  // Annotate before re-rooting/pruning
                  if (requestedSerialization.startsWith("multipart/") && refContext.refInstanceOpt.isDefined)
                    SubmissionUtils.annotateBoundRelevantUploadControls(containingDocument, refContext.refInstanceOpt.get)

                  createUriOrDocumentToSubmit(submissionParameters).some
                } else {
                  // Don't recreate document
                  None
                }

              val overriddenSerializedData =
                if (! submissionParameters.isDeferredSubmission && submissionParameters.serialize) {
                  // Fire `xforms-submit-serialize`
                  // "The event xforms-submit-serialize is dispatched. If the submission-body property of the event
                  // is changed from the initial value of empty string, then the content of the submission-body
                  // property string is used as the submission serialization. Otherwise, the submission serialization
                  // consists of a serialization of the selected instance data according to the rules stated at 11.9
                  // Submission Options."
                  val serializeEvent =
                    new XFormsSubmitSerializeEvent(thisSubmission, refContext.refNodeInfo, requestedSerialization)

                  Dispatch.dispatchEvent(serializeEvent, EventCollector.Throw)

                  // TODO: rest of submission should happen upon default action of event
                  serializeEvent.submissionBodyAsString
                } else {
                  null
                }

              // Serialize
              val serializationParameters =
                SerializationParameters(
                  thisSubmission,
                  submissionParameters,
                  requestedSerialization,
                  uriOrDocumentToSubmitOpt,
                  overriddenSerializedData
                )

              /* ***** Submission connection ************************************************************************** */

              // Result information
              val connectResultOpt =
                submissions.find(_.isMatch(submissionParameters, serializationParameters)).flatMap { submission =>
                  withDebug("connecting", List("type" -> submission.submissionType)) {
                    submission.connect(submissionParameters, serializationParameters)
                  }
                }

              /* ***** Submission result processing ******************************************************************* */

              connectResultOpt match {
                case Some(Left(connectResult)) =>
                  handleConnectResult(
                    submissionParameters   = submissionParameters,
                    connectResult          = connectResult,
                    initializeXPathContext = true // function context might have changed
                  ).some
                case Some(Right(connectResultF)) if submissionParameters.isDeferredSubmission =>
                  containingDocument.setReplaceAllFuture(connectResultF)
                  None
                case Some(Right(connectResultIo)) =>
                  containingDocument
                    .getAsynchronousSubmissionManager
                    .addAsynchronousCompletion(
                      description   = s"submission id: `${thisSubmission.getEffectiveId}`",
                      computation   = connectResultIo.flatMap(convertConnectResult), // running asynchronously
                      continuation  = (connectResultTry: Try[ConnectResult]) =>      // running synchronously when we process the completed submission
                        containingDocument
                          .getObjectByEffectiveId(thisSubmission.getEffectiveId).asInstanceOf[XFormsModelSubmission]
                          .processAsyncSubmissionResponse(
                            connectResultTry,
                            submissionParameters
                          ),
                      awaitInCurrentRequest = submissionParameters.responseMustAwait
                    )
                  None
                case None =>
                  // Nothing to do here (case of `ClientGetAllSubmission`)
                  None
              }
          }
        } catch {
          case NonFatal(throwable) =>
            /* ***** Handle errors ********************************************************************************** */
            (
              if (submissionParameters.isDeferredSubmission && containingDocument.isLocalSubmissionForward)
                ReplaceResult.Throw( // no purpose to dispatch an event so we just propagate the exception
                  new XFormsSubmissionException(
                    thisSubmission,
                    "Error while processing `xf:submission`",
                    "processing submission",
                    throwable,
                    null
                  )
                )
              else
                ReplaceResult.SendError(throwable, Right(submissionParameters.actionOrResource.some), submissionParameters.tunnelProperties),
              None
            ).some
        }
      }

    def processReplaceResultAndCloseConnection(replaceResultWithCxr: (ReplaceResult, Option[ConnectionResult])): Unit =
      try {
        replaceResultWithCxr._1 match {
          case ReplaceResult.None                                => ()
          case ReplaceResult.SendDone (cxr, tunnelProperties)    => sendSubmitDone(cxr, tunnelProperties)
          case ReplaceResult.SendError(t, ctx, tunnelProperties) => sendSubmitError(t, ctx, tunnelProperties)
          case ReplaceResult.Throw    (t)                        => throw t // used by second pass of a two-pass submission
        }
      } finally {
        // https://github.com/orbeon/orbeon-forms/issues/5224
        replaceResultWithCxr._2.foreach(_.close())
      }

    private def sendSubmitDone(cxr: ConnectionResult, tunnelProperties: Option[TunnelProperties]): Unit = {
      model.resetAndEvaluateVariables(EventCollector.Throw) // after a submission, the context might have changed
      Dispatch.dispatchEvent(new XFormsSubmitDoneEvent(thisSubmission, cxr, tunnelProperties), EventCollector.Throw)
    }

    private def sendSubmitError(t: Throwable, ctx: Either[Option[ConnectionResult], Option[String]], tunnelProperties: Option[TunnelProperties]): Unit =
      sendSubmitErrorWithDefault(
        t,
        default = ctx match {
          case Left(v)  => new XFormsSubmitErrorEvent(thisSubmission, ErrorType.XXFormsInternalError, v, tunnelProperties)
          case Right(v) => new XFormsSubmitErrorEvent(thisSubmission, v, ErrorType.XXFormsInternalError, 0, tunnelProperties)
        }
      )

    private def sendSubmitErrorWithDefault(t: Throwable, default: => XFormsSubmitErrorEvent): Unit = {

      // After a submission, the context might have changed
      model.resetAndEvaluateVariables(EventCollector.Throw)

      // Try to get error event from exception and if not possible create default event
      val submitErrorEvent =
        t.narrowTo[XFormsSubmissionException] flatMap (_.submitErrorEventOpt) getOrElse default

      // Dispatch event
      submitErrorEvent.logMessage(t)

      Dispatch.dispatchEvent(submitErrorEvent, EventCollector.Throw)
    }

    private def createUriOrDocumentToSubmit(
      submissionParameters: SubmissionParameters
    )(implicit
      refContext    : RefContext,
      indentedLogger: IndentedLogger
    ): URI Either Document =
      if (requestedSerialization(submissionParameters).contains(ContentTypes.OctetStreamContentType))
        Left(
          URI.create(refContext.refNodeInfo.getStringValue)
        )
      else
        Right(
          createDocumentToSubmit(
            refContext.refNodeInfo,
            refContext.refInstanceOpt,
            submissionParameters.validate,
            submissionParameters.relevanceHandling,
            submissionParameters.xxfAnnotate,
            submissionParameters.xxfRelevantAttOpt,
            submissionParameters.tunnelProperties
          )
        )

    private def createDocumentToSubmit(
      currentNodeInfo   : om.NodeInfo,
      currentInstance   : Option[XFormsInstance],
      validate          : Boolean,
      relevanceHandling : RelevanceHandling,
      annotateWith      : Set[String],
      relevantAttOpt    : Option[QName],
      tunnelProperties  : Option[TunnelProperties]
    )(implicit
      indentedLogger    : IndentedLogger
    ): Document = {

      // Revalidate instance
      // NOTE: We need to do this before pruning so that bind/@type works correctly. XForms 1.1 seems to say that this
      // must be done after pruning, but then it is not clear how XML Schema validation would work then.
      // Also, if validate="false" or if serialization="none", then we do not revalidate. Now whether this optimization
      // is acceptable depends on whether validate="false" only means "don't check the instance's validity" or also
      // don't even recalculate. If the latter, then this also means that type annotations won't be updated, which
      // can impact serializations that use type information, for example multipart. But in that case, here we decide
      // the optimization is worth it anyway.
      if (validate)
        currentInstance foreach (_.model.doRecalculateRevalidate())

      // Get selected nodes (re-root and handle relevance)
      val documentToSubmit =
        prepareXML(
          xfcd              = containingDocument,
          ref               = currentNodeInfo,
          relevanceHandling = relevanceHandling,
          namespaceContext  = staticSubmission.namespaceMapping.mapping,
          annotateWith      = annotateWith,
          relevantAttOpt    = relevantAttOpt
        )

      // Check that there are no validation errors
      // NOTE: If the instance is read-only, it can't have MIPs at the moment, and can't fail validation/requiredness,
      // so we don't go through the process at all.
      val instanceSatisfiesValidRequired =
        currentInstance.exists(_.readonly) ||
        ! validate                         ||
        isSatisfiesValidity(documentToSubmit, relevanceHandling)

      if (! instanceSatisfiesValidRequired) {
        debug(
          "instance document or subset thereof cannot be submitted",
          List("document" -> StaticXPath.tinyTreeToString(currentNodeInfo))
        )
        throw new XFormsSubmissionException(
          submission       = thisSubmission,
          message          = "xf:submission: instance to submit does not satisfy valid and/or required model item properties.",
          description      = "checking instance validity",
          submitErrorEvent = new XFormsSubmitErrorEvent(thisSubmission, ErrorType.ValidationError, None, tunnelProperties)
        )
      }

      documentToSubmit
    }

    def handleConnectResult(
      submissionParameters  : SubmissionParameters,
      connectResult         : ConnectResult,
      initializeXPathContext: Boolean
    )(implicit
      refContext            : RefContext,
      logger                : IndentedLogger
    ): (ReplaceResult, Option[ConnectionResult]) = {

      require(submissionParameters ne null)
      require(connectResult ne null)

      try {
        withDebug("handling result") {
          connectResult.result match {
            case Success((replacer, cxr)) => (replacer.replace(thisSubmission, cxr, submissionParameters, replacer.deserialize(thisSubmission, cxr, submissionParameters)), cxr.some)
            case Failure(throwable)       => (ReplaceResult.SendError(throwable, Left(None), submissionParameters.tunnelProperties), None)
          }
        }
      } catch {
        case NonFatal(throwable) =>
          val cxrOpt = connectResult.result.toOption.map(_._2)
          (ReplaceResult.SendError(throwable, Left(cxrOpt), submissionParameters.tunnelProperties), cxrOpt)
      }
    }
  }
}