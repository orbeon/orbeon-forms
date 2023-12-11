/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.submission

import cats.syntax.option._
import org.orbeon.connection.ConnectionResult
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.oxf.xforms.event.events.{ErrorType, XFormsSubmitErrorEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector, XFormsEvent}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.saxon.om
import org.orbeon.xforms.{BindingErrorReason, NodeBindingErrorReason}


object TextReplacer extends Replacer {

  type DeserializeType = Option[String]

  def deserialize(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters
  ): DeserializeType =
    SubmissionUtils.readTextContent(cxr.content) match {
      case s @ Some(_) => s
      case None =>
        // Non-text/non-XML result

        // Don't store anything for now as per the spec, but we could do something better by going beyond the spec
        // NetUtils.inputStreamToAnyURI(pipelineContext, connectionResult.resultInputStream, NetUtils.SESSION_SCOPE);

        // XForms 1.1: "For a success response including a body that is both a non-XML media type (i.e. with a
        // content type not matching any of the specifiers in [RFC 3023]) and a non-text type (i.e. with a content
        // type not matching text/*), when the value of the replace attribute on element submission is "text",
        // nothing in the document is replaced and submission processing concludes after dispatching
        // xforms-submit-error with appropriate context information, including an error-type of resource-error."
        val message =
          cxr.mediatype match {
            case Some(mediatype) => s"""Mediatype is neither text nor XML for replace="text": $mediatype"""
            case None            => s"""No mediatype received for replace="text""""
          }

        throw new XFormsSubmissionException(
          submission       = submission,
          message          = message,
          description      = "reading response body",
          submitErrorEvent = new XFormsSubmitErrorEvent(
            target           = submission,
            errorType        = ErrorType.ResourceError,
            cxrOpt           = cxr.some,
            tunnelProperties = submissionParameters.tunnelProperties
          )
        )
    }

  def replace(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters,
    value               : DeserializeType
  )(implicit
    refContext          : RefContext
  ): ReplaceResult =
    value
      .flatMap(replaceText(submission, submission.containingDocument, cxr, submissionParameters, _))
      .getOrElse(ReplaceResult.SendDone(cxr, submissionParameters.tunnelProperties))

  def replaceText (
    submission          : XFormsModelSubmission,
    containingDocument  : XFormsContainingDocument,
    connectionResult    : ConnectionResult,
    submissionParameters: SubmissionParameters,
    value               : String
  )(implicit
    refContext          : RefContext
  ): Option[ReplaceResult.SendError] = {

    // XForms 1.1: "If the processing of the `targetref` attribute fails, then submission processing ends after
    // dispatching the event `xforms-submit-error` with an `error-type` of `target-error`."
    def newSubmissionException(message: String) =
      new XFormsSubmissionException(
        submission       = submission,
        message          = message,
        description      = "processing `targetref` attribute",
        submitErrorEvent = new XFormsSubmitErrorEvent(
          target           = submission,
          errorType        = ErrorType.TargetError,
          cxrOpt           = connectionResult.some,
          tunnelProperties = submissionParameters.tunnelProperties
        )
      )

    def handleSetValue(destinationNodeInfo: om.NodeInfo) = {
      // NOTE: Here we decided to use the actions logger, by compatibility with `xf:setvalue`. Anything we would
      // like to log in "submission" mode?
      def handleSetValueSuccess(oldValue: String): Unit =
        DataModel.logAndNotifyValueChange(
          source             = "submission",
          nodeInfo           = destinationNodeInfo,
          oldValue           = oldValue,
          newValue           = value,
          isCalculate        = false,
          collector          = (event: XFormsEvent) => Dispatch.dispatchEvent(event, EventCollector.Throw))(
          containingDocument = containingDocument,
          logger             = containingDocument.getIndentedLogger(XFormsActions.LoggingCategory)
        )

      def handleSetValueError(reason: NodeBindingErrorReason) =
        throw newSubmissionException(
          reason match {
            case BindingErrorReason.DisallowedNode =>
              s"""`targetref` attribute doesn't point to an element without children or to an attribute for `replace="${submissionParameters.replaceType}"`."""
            case BindingErrorReason.ReadonlyNode   =>
              s"""`targetref` attribute points to a readonly node for `replace="${submissionParameters.replaceType}"`."""
          }
        )

      try {
        DataModel.setValueIfChanged(
          nodeInfo  = destinationNodeInfo,
          newValue  = value,
          onSuccess = handleSetValueSuccess,
          onError   = handleSetValueError
        )
        None
      } catch {
        case t: XFormsSubmissionException =>
          ReplaceResult.SendError(
            t,
            Left(connectionResult.some),
            submissionParameters.tunnelProperties
          ).some
      }
    }

    // Find target location
    submission.staticSubmission.targetrefOpt match {
      case Some(targetRef) =>
        XPathCache.evaluateSingleWithContext(
          xpathContext = refContext.xpathContext,
          contextItem  = refContext.refNodeInfo,
          xpathString  = targetRef,
          reporter     = containingDocument.getRequestStats.addXPathStat
        ) match {
          case destinationNodeInfo: om.NodeInfo =>
            handleSetValue(destinationNodeInfo)
          case _ =>
            ReplaceResult.SendError(
              newSubmissionException(s"""`targetref` attribute doesn't point to a node for `replace="${submissionParameters.replaceType}"`."""),
              Left(connectionResult.some),
              submissionParameters.tunnelProperties
            ).some
        }
      case None =>
        // Use default destination
        handleSetValue(
          submission.findReplaceInstanceNoTargetref(refContext.refInstanceOpt)
            .getOrElse(throw new IllegalArgumentException).rootElement
        )
    }
  }
}