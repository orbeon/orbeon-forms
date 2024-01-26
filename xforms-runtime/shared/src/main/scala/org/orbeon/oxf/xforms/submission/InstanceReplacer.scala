/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.connection.ConnectionResult
import org.orbeon.dom.Node
import org.orbeon.oxf.json.Converter
import org.orbeon.oxf.util.CollectionUtils.InsertPosition
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StaticXPath.{DocumentNodeInfoType, VirtualNodeType}
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger, XPath}
import org.orbeon.oxf.xforms.action.actions.{XFormsDeleteAction, XFormsInsertAction}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.event.XFormsEvent.TunnelProperties
import org.orbeon.oxf.xforms.event.events.{ErrorType, XFormsSubmitErrorEvent}
import org.orbeon.oxf.xforms.model.XFormsInstance.InstanceDocument
import org.orbeon.oxf.xforms.model.{DataModel, InstanceCaching, InstanceDataOps, XFormsInstance}
import org.orbeon.oxf.xml.dom.LocationSAXContentHandler
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.util.control.NonFatal


class DirectInstanceReplacer(value: (DocumentNodeInfoType, InstanceCaching)) extends Replacer {

  type DeserializeType = Either[InstanceDocument, (DocumentNodeInfoType, InstanceCaching)]

  def deserialize(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters
  ): DeserializeType =
    Right(value)

  def replace(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters,
    value               : DeserializeType
  )(implicit
    refContext          : RefContext
  ): ReplaceResult =
    InstanceReplacer.replace(submission, cxr, submissionParameters, value)
}

object InstanceReplacer extends Replacer {

  type DeserializeType = Either[InstanceDocument, (DocumentNodeInfoType, InstanceCaching)]

  def deserialize(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters
  ): DeserializeType = {
    // Deserialize here so it can run in parallel
    val contentType = cxr.mediatypeOrDefault(ContentTypes.XmlContentType)
    val isJSON = ContentTypes.isJSONContentType(contentType)
    if (ContentTypes.isXMLContentType(contentType) || isJSON) {
      implicit val detailsLogger: IndentedLogger = submission.getDetailsLogger
      Left(
        deserializeInstance(
          submission       = submission,
          isReadonly       = submissionParameters.isReadonly,
          isHandleXInclude = submissionParameters.isHandleXInclude,
          isJSON           = isJSON,
          connectionResult = cxr,
          tunnelProperties = submissionParameters.tunnelProperties
        )
      )
    } else {
      // Other media type is not allowed
      throw new XFormsSubmissionException(
        submission       = submission,
        message          = s"""Body received with non-XML media type for `replace="instance"`: $contentType""",
        description      = "processing instance replacement",
        submitErrorEvent = new XFormsSubmitErrorEvent(
          submission,
          ErrorType.ResourceError,
          cxr.some,
          submissionParameters.tunnelProperties
        )
      )
    }
  }

  def replace(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters,
    value               : DeserializeType
  )(implicit
    refContext          : RefContext
  ): ReplaceResult =
    submission.findReplaceInstanceNoTargetref(refContext.refInstanceOpt) match {
      case None =>

        // Replacement instance or node was specified but not found
        //
        // Not sure what's the right thing to do with 1.1, but this could be done
        // as part of the model's static analysis if the instance value is not
        // obtained through AVT, and dynamically otherwise.
        //
        // Another option would be to dispatch, at runtime, an xxforms-binding-error event. xforms-submit-error is
        // consistent with targetref, so might be better.

        ReplaceResult.SendError(
          new XFormsSubmissionException(
            submission       = submission,
            message          = """`instance` attribute doesn't point to an existing instance for `replace="instance"`.""",
            description      = "processing `instance` attribute",
            submitErrorEvent = new XFormsSubmitErrorEvent(
              target    = submission,
              errorType = ErrorType.TargetError,
              cxrOpt    = cxr.some,
              tunnelProperties = submissionParameters.tunnelProperties
            )
          ),
          Left(cxr.some),
          submissionParameters.tunnelProperties
        )

      case Some(replaceInstanceNoTargetref) =>

        val destinationNodeInfoOpt =
          submission.evaluateTargetRef(
            refContext.xpathContext,
            replaceInstanceNoTargetref,
            refContext.submissionElementContextItem
          )

        destinationNodeInfoOpt match {
          case None =>
            // XForms 1.1: "If the processing of the `targetref` attribute fails,
            // then submission processing ends after dispatching the event
            // `xforms-submit-error` with an `error-type` of `target-error`."
            ReplaceResult.SendError(
              new XFormsSubmissionException(
                submission       = submission,
                message          = """targetref attribute doesn't point to an element for `replace="instance"`.""",
                description      = "processing targetref attribute",
                submitErrorEvent = new XFormsSubmitErrorEvent(
                  target           = submission,
                  errorType        = ErrorType.TargetError,
                  cxrOpt           = cxr.some,
                  tunnelProperties = submissionParameters.tunnelProperties
                )
              ),
              Left(cxr.some),
              submissionParameters.tunnelProperties
            )
          case Some(destinationNodeInfo) =>
            // This is the instance which is effectively going to be updated
            submission.containingDocument.instanceForNodeOpt(destinationNodeInfo) match {
              case None =>
                ReplaceResult.SendError(
                  new XFormsSubmissionException(
                    submission       = submission,
                    message          = """targetref attribute doesn't point to an element in an existing instance for `replace="instance"`.""",
                    description      = "processing targetref attribute",
                    submitErrorEvent = new XFormsSubmitErrorEvent(
                      target           = submission,
                      errorType        = ErrorType.TargetError,
                      cxrOpt           = cxr.some,
                      tunnelProperties = submissionParameters.tunnelProperties
                    )
                  ),
                  Left(cxr.some),
                  submissionParameters.tunnelProperties
                )
              case Some(instanceToUpdate) =>
                // Whether the destination node is the root element of an instance
                val isDestinationRootElement = instanceToUpdate.rootElement.isSameNodeInfo(destinationNodeInfo)
                if (submissionParameters.isReadonly && ! isDestinationRootElement) {
                  // Only support replacing the root element of an instance when using a shared instance
                  ReplaceResult.SendError(
                    new XFormsSubmissionException(
                      submission       = submission,
                      message          = "targetref attribute must point to instance root element when using read-only instance replacement.",
                      description      = "processing targetref attribute",
                      submitErrorEvent = new XFormsSubmitErrorEvent(
                        target           = submission,
                        errorType        = ErrorType.TargetError,
                        cxrOpt           = cxr.some,
                        tunnelProperties = submissionParameters.tunnelProperties
                      )
                    ),
                    Left(cxr.some),
                    submissionParameters.tunnelProperties
                  )
                } else {
                  implicit val detailsLogger: IndentedLogger = submission.getDetailsLogger

                  debug(
                    s"replacing instance with ${if (submissionParameters.isReadonly) "read-only" else "mutable"} instance",
                    List("instance"-> instanceToUpdate.getEffectiveId)
                  )

                  // Obtain root element to insert
                  val newDocumentInfo =
                    value match {
                      case Left(instanceDocument) =>
                        XFormsInstance.createDocumentInfo(
                          instanceDocument,
                          instanceToUpdate.instance.exposeXPathTypes
                        )
                      case Right((documentNodeInfo, _)) =>
                        documentNodeInfo
                    }

                  // Perform insert/delete. This will dispatch xforms-insert/xforms-delete events.
                  // "the replacement is performed by an XForms action that performs some
                  // combination of node insertion and deletion operations that are
                  // performed by the insert action (10.3 The insert Element) and the
                  // delete action"

                  // NOTE: As of 2009-03-18 decision, XForms 1.1 specifies that deferred event handling flags are set instead of
                  // performing RRRR directly.

                  val applyDefaults = submissionParameters.applyDefaults
                  if (isDestinationRootElement) {
                    // Optimized insertion for instance root element replacement
                    if (applyDefaults)
                      newDocumentInfo match {
                        case node: VirtualNodeType =>
                          InstanceDataOps.setRequireDefaultValueRecursively(node.getUnderlyingNode.asInstanceOf[Node])
                        case _ => ()
                      }

                    instanceToUpdate.replace(
                      newDocumentInfo = newDocumentInfo,
                      collector       = EventCollector.Throw,
                      dispatch        = true,
                      instanceCaching = value.toOption.map(_._2),
                      isReadonly      = submissionParameters.isReadonly,
                      applyDefaults   = applyDefaults
                    )
                  } else {
                    // Generic insertion
                    instanceToUpdate.markModified()

                    // Perform the insertion

                    // Insert before the target node, so that the position of the inserted node
                    // wrt its parent does not change after the target node is removed
                    // This will also mark a structural change
                    // FIXME: Replace logic should use `doReplace` and `xxforms-replace` event
                    XFormsInsertAction.doInsert(
                      containingDocumentOpt             = submission.containingDocument.some,
                      insertPosition                    = InsertPosition.Before,
                      insertLocation                    = Left(NonEmptyList(destinationNodeInfo, Nil) -> 1),
                      originItemsOpt                    = List(DataModel.firstChildElement(newDocumentInfo)).some,
                      doClone                           = false,
                      doDispatch                        = true,
                      requireDefaultValues              = applyDefaults,
                      searchForInstance                 = true,
                      removeInstanceDataFromClonedNodes = true,
                      structuralDependencies            = true,
                      collector                         = EventCollector.Throw
                    )

                    // Perform the deletion of the selected node
                    XFormsDeleteAction.doDeleteOne(
                      containingDocument = submission.containingDocument,
                      nodeInfo           = destinationNodeInfo,
                      doDispatch         = true,
                      collector          = EventCollector.Throw
                    )
                  }
                  ReplaceResult.SendDone(cxr, submissionParameters.tunnelProperties)
                }
            }
        }
    }

  private def deserializeInstance(
    submission       : XFormsModelSubmission,
    isReadonly       : Boolean,
    isHandleXInclude : Boolean,
    isJSON           : Boolean,
    connectionResult : ConnectionResult,
    tunnelProperties : Option[TunnelProperties]
  )(implicit
    logger           : IndentedLogger
  ): InstanceDocument =
    try {
      ConnectionResult.withSuccessConnection(connectionResult, closeOnSuccess = true) { is =>
        if (! isReadonly) {
          debug("deserializing to mutable instance")
          // Q: What about configuring validation? And what default to choose?
          Left(
            if (isJSON) {
              val receiver = new LocationSAXContentHandler
              Converter.jsonStringToXmlStream(SubmissionUtils.readTextContent(connectionResult.content).get, receiver)
              receiver.getDocument
            } else {
              XFormsCrossPlatformSupport.readOrbeonDom(is, connectionResult.url, isHandleXInclude, handleLexical = true)
            }
          )
        } else {
          debug("deserializing to read-only instance")
          // Q: What about configuring validation? And what default to choose?
          Right(
            if (isJSON)
              Converter.jsonStringToXmlDoc(SubmissionUtils.readTextContent(connectionResult.content).get)
            else
              XFormsCrossPlatformSupport.readTinyTree(XPath.GlobalConfiguration, is, connectionResult.url, isHandleXInclude, handleLexical = true)
          )
        }
      }
    } catch {
      case NonFatal(t) =>
        throw new XFormsSubmissionException(
          submission       = submission,
          message          = "xf:submission: exception while reading XML response.",
          description      = "processing instance replacement",
          throwable        = t,
          submitErrorEvent = new XFormsSubmitErrorEvent(
            submission,
            ErrorType.ParseError,
            connectionResult.some,
            tunnelProperties
          )
        )
    }
}