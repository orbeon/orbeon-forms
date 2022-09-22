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
import org.orbeon.datatypes.LocationData
import org.orbeon.dom.{Document, QName}
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.event._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xforms.submission.XFormsModelSubmissionSupport.{isSatisfiesValidity, prepareXML, requestedSerialization}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsError, XFormsGlobalProperties}
import org.orbeon.saxon.om
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{RelevanceHandling, XFormsId}
import shapeless.syntax.typeable._

import java.net.URI
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


object XFormsModelSubmission {

  val LOGGING_CATEGORY = "submission"
  val logger: Logger = LoggerFactory.createLogger(classOf[XFormsModelSubmission])

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
  val submissions =
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

  def performTargetAction(event: XFormsEvent): Unit = ()

  def performDefaultAction(event: XFormsEvent): Unit =
    event match {
      case e: XFormsSubmitEvent                       => doSubmit(e)
      case e: XXFormsActionErrorEvent                 => XFormsError.handleNonFatalActionError(thisSubmission, e.throwable)
      case e if e.name == XFormsEvents.XXFORMS_SUBMIT => doSubmit(e)
      case _ =>
    }

  private[submission]
  def processAsyncSubmissionResponse(submissionResult: ConnectResult)(implicit logger: IndentedLogger): Unit = {

    val p  = SubmissionParameters(None)(thisSubmission)
    val p2 = SecondPassParameters(p)(thisSubmission)

    (processReplaceResultAndCloseConnection _).tupled(
      handleConnectResult(
        p                      = p,
        p2                     = p2,
        connectResult          = submissionResult,
        initializeXPathContext = false
      )
    )
  }

  def getReplacer(cxr: ConnectionResult, p: SubmissionParameters)(implicit logger: IndentedLogger): Replacer = {
    // NOTE: This can be called from other threads so it must NOT modify the XFCD or submission
    // Handle response
    if (cxr.dontHandleResponse) {
      // Always return a replacer even if it does nothing, this way we don't have to deal with null
      new NoneReplacer(thisSubmission)
    } else if (StatusCode.isSuccessCode(cxr.statusCode)) {
      // Successful response
      if (cxr.hasContent) {
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
            s"xf:submission: invalid replace attribute: `${p.replaceType}`",
            "processing instance replacement",
            null,
            new XFormsSubmitErrorEvent(
              target    = thisSubmission,
              errorType = ErrorType.XXFormsInternalError,
              cxrOpt    = cxr.some
            )
          )
      } else {
        // There is no body, notify that processing is terminated
        if (p.replaceType == ReplaceType.Instance || p.replaceType == ReplaceType.Text) {
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
        new NoneReplacer(thisSubmission)
      }
    } else if (StatusCode.isRedirectCode(cxr.statusCode)) {
      // Got a redirect
      // Currently we don't know how to handle a redirect for replace != "all"
      if (p.replaceType != ReplaceType.All)
        throw new XFormsSubmissionException(
          thisSubmission,
          // Scala 2.13: just use `"` and `\"` (use of `"""` due to a bug in Scala 2.12!)
          s"""xf:submission for submission id: `$getId`, redirect code received with replace="${p.replaceType.entryName}"""",
          "processing submission response",
          null,
          new XFormsSubmitErrorEvent(
            target    = thisSubmission,
            errorType = ErrorType.ResourceError,
            cxrOpt    = cxr.some
          )
        )
      new RedirectReplacer(containingDocument)
    } else {
      // Error code received
      if (p.replaceType == ReplaceType.All && cxr.hasContent) {
        // For `replace="all"`, if we received content, which might be an error page, we still want to serve it
        new AllReplacer(thisSubmission, containingDocument)
      } else
        throw new XFormsSubmissionException(
          thisSubmission,
          s"xf:submission for submission id: `$getId`, error code received when submitting instance: ${cxr.statusCode}",
          "processing submission response",
          null,
          new XFormsSubmitErrorEvent(
            target    = thisSubmission,
            errorType = ErrorType.ResourceError,
            cxrOpt    = cxr.some
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

  def getDetailsLogger(p: SubmissionParameters, p2: SecondPassParameters): IndentedLogger =
    XFormsModelSubmission.getNewLogger(p, p2, getIndentedLogger, XFormsModelSubmission.isLogDetails)

  def getTimingLogger(p: SubmissionParameters, p2: SecondPassParameters): IndentedLogger = {
    val indentedLogger = getIndentedLogger
    XFormsModelSubmission.getNewLogger(p, p2, indentedLogger, indentedLogger.debugEnabled)
  }

  def allowExternalEvent(eventName: String): Boolean =
    XFormsModelSubmission.AllowedExternalEvents.contains(eventName)

  private object Private {

    def doSubmit(event: XFormsEvent): Unit = {

      implicit val indentedLogger: IndentedLogger = getIndentedLogger

      // Create the big bag of initial runtime parameters, which can throw
      val p =
        try
          SubmissionParameters(event.name.some)(thisSubmission)
        catch {
          case t: Throwable =>
            sendSubmitError(t, Right(None))
            return
        }

      var resolvedActionOrResourceOpt: Option[String] = None

      val replaceResultOpt: Option[(ReplaceResult, Option[ConnectionResult])] =
        withDebug(
          if (p.isDeferredSubmissionFirstPass)
            "submission first pass"
          else if (p.isDeferredSubmissionSecondPass)
            "submission second pass"
          else
            "submission",
          List("id" -> getEffectiveId)
        ) {
          try {
            // If a submission requiring a second pass was already set, then we ignore a subsequent submission but
            // issue a warning
            val twoPassParams = containingDocument.findTwoPassSubmitEvent
            if (p.isDeferredSubmission && twoPassParams.isDefined) {
              warn(
                "another submission requiring a second pass already exists",
                List(
                  "existing submission" -> twoPassParams.get.targetEffectiveId,
                  "new submission"      -> getEffectiveId
                )
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
                  target    = thisSubmission,
                  errorType = ErrorType.XXFormsPendingUploads,
                  cxrOpt    = None
                )
              )

            /* ***** Update data model ****************************************************************************** */

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
              if (p.validate || p.relevanceHandling != RelevanceHandling.Keep || p.xxfCalculate)
                modelForInstance.doRebuild()

              // Recalculate impacts relevance and calculated values
              if (p.relevanceHandling != RelevanceHandling.Keep || p.xxfCalculate)
                modelForInstance.doRecalculateRevalidate()
            }

            /* ***** Handle deferred submission ********************************************************************* */

            // Deferred submission: end of the first pass
            if (p.isDeferredSubmissionFirstPass) {
              // Create (but abandon) document to submit here because in case of error, an Ajax response will still be produced
              if (p.serialize)
                createUriOrDocumentToSubmit(p)
              containingDocument.addTwoPassSubmitEvent(TwoPassSubmissionParameters(getEffectiveId, p))
              return
            }

            /* ***** Submission second pass ************************************************************************* */

            // Compute parameters only needed during second pass
            val p2 = SecondPassParameters(p)(thisSubmission)
            resolvedActionOrResourceOpt = p2.actionOrResource.some // in case of exception

            /* ***** Serialization ********************************************************************************** */

            requestedSerialization(p.serializationOpt, p.xformsMethod, p.httpMethod) match {
              case None =>
                throw new XFormsSubmissionException(
                  thisSubmission,
                  s"xf:submission: invalid submission method requested: `${p.xformsMethod}`",
                  "serializing instance",
                  null,
                  null
                )
              case Some(requestedSerialization) =>
                val uriOrDocumentToSubmitOpt =
                  if (p.serialize) {
                    // Check if a submission requires file upload information

                    // Annotate before re-rooting/pruning
                    if (requestedSerialization.startsWith("multipart/") && p.refContext.refInstanceOpt.isDefined)
                      SubmissionUtils.annotateBoundRelevantUploadControls(containingDocument, p.refContext.refInstanceOpt.get)

                    createUriOrDocumentToSubmit(p).some
                  } else {
                    // Don't recreate document
                    None
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
              val sp = SerializationParameters(thisSubmission, p, p2, requestedSerialization, uriOrDocumentToSubmitOpt, overriddenSerializedData)

              /* ***** Submission connection ************************************************************************** */

              // Result information
              val connectResultOpt =
                submissions find (_.isMatch(p, p2, sp)) flatMap { submission =>
                  withDebug("connecting", List("type" -> submission.getType)) {
                    submission.connect(p, p2, sp)
                  }
                }

              /* ***** Submission result processing ******************************************************************* */

              // `None` in case the submission is running asynchronously, AND when ???
              connectResultOpt map { connectResult =>
                handleConnectResult(
                  p                      = p,
                  p2                     = p2,
                  connectResult          = connectResult,
                  initializeXPathContext = true // function context might have changed
                )
              }
            }
          } catch {
            case NonFatal(throwable) =>
              /* ***** Handle errors ********************************************************************************** */
              (
                if (p.isDeferredSubmissionSecondPass && containingDocument.isLocalSubmissionForward)
                  ReplaceResult.Throw( // no purpose to dispatch an event so we just propagate the exception
                    new XFormsSubmissionException(
                      thisSubmission,
                      "Error while processing xf:submission",
                      "processing submission",
                      throwable,
                      null
                    )
                  )
                else
                  ReplaceResult.SendError(throwable, Right(resolvedActionOrResourceOpt)),
                None
              ).some
          }
        } // withDebug

      // Execute post-submission code if any
      // We do this outside the above catch block so that if a problem occurs during dispatching `xforms-submit-done`
      // or `xforms-submit-error` we don't dispatch `xforms-submit-error` (which would be illegal).
      // This will also close the connection result if needed.
      replaceResultOpt foreach (processReplaceResultAndCloseConnection _).tupled
    }

    def processReplaceResultAndCloseConnection(
      replaceResult : ReplaceResult,
      cxrOpt        : Option[ConnectionResult]
    ): Unit =
      try {
        replaceResult match {
          case ReplaceResult.None              => ()
          case ReplaceResult.SendDone (cxr)    => sendSubmitDone(cxr)
          case ReplaceResult.SendError(t, ctx) => sendSubmitError(t, ctx)
          case ReplaceResult.Throw    (t)      => throw t
        }
      } finally {
        // https://github.com/orbeon/orbeon-forms/issues/5224
        cxrOpt foreach (_.close())
      }

    def sendSubmitDone(cxr: ConnectionResult): Unit = {
      model.resetAndEvaluateVariables() // after a submission, the context might have changed
      Dispatch.dispatchEvent(new XFormsSubmitDoneEvent(thisSubmission, cxr))
    }

    def sendSubmitError(t: Throwable, ctx: Either[Option[ConnectionResult], Option[String]]): Unit =
      sendSubmitErrorWithDefault(
        t,
        ctx match {
          case Left(v)  => new XFormsSubmitErrorEvent(thisSubmission, ErrorType.XXFormsInternalError, v)
          case Right(v) => new XFormsSubmitErrorEvent(thisSubmission, v, ErrorType.XXFormsInternalError, 0)
        }
      )

    def sendSubmitErrorWithDefault(t: Throwable, default: => XFormsSubmitErrorEvent): Unit = {

      // After a submission, the context might have changed
      model.resetAndEvaluateVariables()

      // Try to get error event from exception and if not possible create default event
      val submitErrorEvent =
        t.narrowTo[XFormsSubmissionException] flatMap (_.submitErrorEventOpt) getOrElse default

      // Dispatch event
      submitErrorEvent.logMessage(t)
      Dispatch.dispatchEvent(submitErrorEvent)
    }

    def createUriOrDocumentToSubmit(
      p: SubmissionParameters)(implicit
      indentedLogger    : IndentedLogger
    ): URI Either Document =
      if (requestedSerialization(p.serializationOpt, p.xformsMethod, p.httpMethod).contains(ContentTypes.OctetStreamContentType))
        Left(
          URI.create(p.refContext.refNodeInfo.getStringValue) // xxx TODO: if error, is it wrapped?
        )
      else
        Right(
          createDocumentToSubmit(
            p.refContext.refNodeInfo,
            p.refContext.refInstanceOpt,
            p.validate,
            p.relevanceHandling,
            p.xxfAnnotate,
            p.xxfRelevantAttOpt
          )
        )

    def createDocumentToSubmit(
      currentNodeInfo   : om.NodeInfo,
      currentInstance   : Option[XFormsInstance],
      validate          : Boolean,
      relevanceHandling : RelevanceHandling,
      annotateWith      : Set[String],
      relevantAttOpt    : Option[QName])(implicit
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
          submitErrorEvent = new XFormsSubmitErrorEvent(thisSubmission, ErrorType.ValidationError, None)
        )
      }

      documentToSubmit
    }

    def handleConnectResult(
      p                     : SubmissionParameters,
      p2                    : SecondPassParameters,
      connectResult         : ConnectResult,
      initializeXPathContext: Boolean)(implicit
      logger                : IndentedLogger
    ): (ReplaceResult, Option[ConnectionResult]) = {

      require(p ne null)
      require(p2 ne null)
      require(connectResult ne null)

      try {
        withDebug("handling result") {

          val updatedP =
            if (initializeXPathContext)
              SubmissionParameters.withUpdatedRefContext(p)(thisSubmission)
            else
                p

          connectResult.result match {
            case Success((replacer, cxr)) => (replacer.replace(cxr, updatedP, p2),             cxr.some)
            case Failure(throwable)       => (ReplaceResult.SendError(throwable, Left(None)),  None)
          }
        }
      } catch {
        case NonFatal(throwable) =>
          val cxrOpt = connectResult.result.toOption.map(_._2)
          (ReplaceResult.SendError(throwable, Left(cxrOpt)), cxrOpt)
      }
    }
  }
}