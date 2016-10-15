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

import java.util.Collections

import org.orbeon.dom.{Document, Node}
import org.orbeon.oxf.json.Converter
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.util.{ConnectionResult, IndentedLogger, XPath}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.action.actions.{XFormsDeleteAction, XFormsInsertAction}
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent
import org.orbeon.oxf.xforms.model.{DataModel, InstanceDataOps}
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler
import org.orbeon.oxf.xml.{TransformerUtils, XMLUtils}
import org.orbeon.saxon.om.{DocumentInfo, Item, VirtualNode}

/**
  * Handle replace="instance".
  */
class InstanceReplacer(submission: XFormsModelSubmission, containingDocument: XFormsContainingDocument)
  extends BaseReplacer(submission, containingDocument) {

  // Unwrapped document set by `deserialize()`
  private var _resultingDocumentOpt: Option[Document Either DocumentInfo] = None
  def resultingDocumentOrDocumentInfo = _resultingDocumentOpt map (_.merge) orNull

  // For CacheableSubmission
  private var wrappedDocumentInfo: Option[DocumentInfo] = None
  private var instanceCaching: Option[InstanceCaching] = None

  // CacheableSubmission: set fully wrapped resulting document info and caching info
  def setCachedResult(wrappedDocumentInfo: DocumentInfo, instanceCaching: InstanceCaching): Unit = {
    this.wrappedDocumentInfo = Option(wrappedDocumentInfo)
    this.instanceCaching     = Option(instanceCaching)
  }

  def deserialize(
    connectionResult : ConnectionResult,
    p                : XFormsModelSubmission#SubmissionParameters,
    p2               : XFormsModelSubmission#SecondPassParameters
  ): Unit = {
    // Deserialize here so it can run in parallel
    val mediatype = connectionResult.mediatypeOrDefault(ProcessorUtils.DEFAULT_CONTENT_TYPE)
    val isJSON = XMLUtils.isJSONContentType(mediatype)
    if (XMLUtils.isXMLMediatype(mediatype) || isJSON) {
      implicit val detailsLogger = getDetailsLogger(p, p2)
      _resultingDocumentOpt = Some(
        deserializeInstance(
          isReadonly       = p2.isReadonly,
          isHandleXInclude = p2.isHandleXInclude,
          isJSON           = isJSON,
          connectionResult = connectionResult
        )
      )
    } else {
      // Other media type is not allowed
      throw new XFormsSubmissionException(
        submission,
        s"""Body received with non-XML media type for `replace="instance"`: $mediatype""",
        "processing instance replacement",
        new XFormsSubmitErrorEvent(
          submission,
          XFormsSubmitErrorEvent.RESOURCE_ERROR,
          connectionResult
        )
      )
    }
  }

  private def deserializeInstance(
    isReadonly       : Boolean,
    isHandleXInclude : Boolean,
    isJSON           : Boolean,
    connectionResult : ConnectionResult)(implicit
    logger           : IndentedLogger
  ): Document Either DocumentInfo = {
    // Create resulting instance whether entire instance is replaced or not, because this:
    // 1. Wraps a Document within a DocumentInfo if needed
    // 2. Performs text nodes adjustments if needed
    try {
      ConnectionResult.withSuccessConnection(connectionResult, closeOnSuccess = true) { is ⇒

        if (! isReadonly) {
          if (logger.isDebugEnabled)
            logger.logDebug("", "deserializing to mutable instance")
          // Q: What about configuring validation? And what default to choose?

          Left(
            if (isJSON) {
              val receiver = new LocationSAXContentHandler
              Converter.jsonStringToXmlStream(connectionResult.readTextResponseBody.get, receiver)
              receiver.getDocument
            } else {
              TransformerUtils.readDom4j(is, connectionResult.url, isHandleXInclude, true)
            }
          )
        } else {
          if (logger.isDebugEnabled)
            logger.logDebug("", "deserializing to read-only instance")
          // Q: What about configuring validation? And what default to choose?
          // NOTE: isApplicationSharedHint is always false when get get here. `isApplicationSharedHint="true"` is handled above.

          Right(
            if (isJSON) {
              Converter.jsonStringToXmlDoc(connectionResult.readTextResponseBody.get)
            } else {
              TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, connectionResult.url, isHandleXInclude, true)
            }
          )
        }
      }
    } catch {
      case e: Exception ⇒
        throw new XFormsSubmissionException(
          submission,
          e,
          "xf:submission: exception while reading XML response.",
          "processing instance replacement",
          new XFormsSubmitErrorEvent(
            submission,
            XFormsSubmitErrorEvent.PARSE_ERROR,
            connectionResult
          )
        )
    }
  }

  def replace(
    connectionResult : ConnectionResult,
    p                : XFormsModelSubmission#SubmissionParameters,
    p2               : XFormsModelSubmission#SecondPassParameters
  ): Runnable = {

    // Set new instance document to replace the one submitted

    val replaceInstanceNoTargetref = submission.findReplaceInstanceNoTargetref(p.refInstance)
    if (replaceInstanceNoTargetref eq null) {

      // Replacement instance or node was specified but not found
      //
      // Not sure what's the right thing to do with 1.1, but this could be done
      // as part of the model's static analysis if the instance value is not
      // obtained through AVT, and dynamically otherwise.
      //
      // Another option would be to dispatch, at runtime, an xxforms-binding-error event. xforms-submit-error is
      // consistent with targetref, so might be better.

      throw new XFormsSubmissionException(
        submission,
        """instance attribute doesn't point to an existing instance for `replace="instance"`.""",
        "processing instance attribute",
        new XFormsSubmitErrorEvent(
          submission,
          XFormsSubmitErrorEvent.TARGET_ERROR,
          connectionResult
        )
      )
    } else {
      val destinationNodeInfo =
        submission.evaluateTargetRef(
          p.xpathContext,
          replaceInstanceNoTargetref,
          p.submissionElementContextItem
        )

      if (destinationNodeInfo eq null) {

        // Throw target-error

        // XForms 1.1: "If the processing of the targetref attribute fails,
        // then submission processing ends after dispatching the event
        // xforms-submit-error with an error-type of target-error."
        throw new XFormsSubmissionException(
          submission,
          """targetref attribute doesn't point to an element for `replace="instance"`.""",
          "processing targetref attribute",
          new XFormsSubmitErrorEvent(
            submission,
            XFormsSubmitErrorEvent.TARGET_ERROR,
            connectionResult
          )
        )
      }

      // This is the instance which is effectively going to be updated
      val instanceToUpdate = containingDocument.getInstanceForNode(destinationNodeInfo)
      if (instanceToUpdate eq null) {
        throw new XFormsSubmissionException(
          submission,
          """targetref attribute doesn't point to an element in an existing instance for `replace="instance"`.""",
          "processing targetref attribute",
          new XFormsSubmitErrorEvent(
            submission,
            XFormsSubmitErrorEvent.TARGET_ERROR,
            connectionResult
          )
        )
      }

      // Whether the destination node is the root element of an instance
      val isDestinationRootElement = instanceToUpdate.rootElement.isSameNodeInfo(destinationNodeInfo)
      if (p2.isReadonly && !isDestinationRootElement) {
        // Only support replacing the root element of an instance when using a shared instance
        throw new XFormsSubmissionException(
          submission,
          "targetref attribute must point to instance root element when using read-only instance replacement.",
          "processing targetref attribute",
          new XFormsSubmitErrorEvent(
            submission,
            XFormsSubmitErrorEvent.TARGET_ERROR,
            connectionResult
          )
        )
      }

      implicit val detailsLogger  = getDetailsLogger(p, p2)

      // Obtain root element to insert
      if (detailsLogger.isDebugEnabled)
        detailsLogger.logDebug(
          "",
          if (p2.isReadonly)
            "replacing instance with read-only instance"
          else
            "replacing instance with mutable instance",
          "instance",
          instanceToUpdate.getEffectiveId
        )

      // Perform insert/delete. This will dispatch xforms-insert/xforms-delete events.
      // "the replacement is performed by an XForms action that performs some
      // combination of node insertion and deletion operations that are
      // performed by the insert action (10.3 The insert Element) and the
      // delete action"

      // NOTE: As of 2009-03-18 decision, XForms 1.1 specifies that deferred event handling flags are set instead of
      // performing RRRR directly.
      val newDocumentInfo =
        wrappedDocumentInfo getOrElse
          XFormsInstance.createDocumentInfo(
            _resultingDocumentOpt.get,
            instanceToUpdate.instance.exposeXPathTypes
          )

      val applyDefaults = p2.applyDefaults
      if (isDestinationRootElement) {
        // Optimized insertion for instance root element replacement
        if (applyDefaults)
          newDocumentInfo match {
            case node: VirtualNode ⇒
              InstanceDataOps.setRequireDefaultValueRecursively(node.getUnderlyingNode.asInstanceOf[Node])
            case _ ⇒
          }

        instanceToUpdate.replace(
          newDocumentInfo = newDocumentInfo,
          dispatch        = true,
          instanceCaching = instanceCaching,
          isReadonly      = p2.isReadonly,
          applyDefaults   = applyDefaults
        )
      } else {
        // Generic insertion
        instanceToUpdate.markModified()
        val newDocumentRootElement: Item = DataModel.firstChildElement(newDocumentInfo)

        // Perform the insertion

        // Insert before the target node, so that the position of the inserted node
        // wrt its parent does not change after the target node is removed
        // This will also mark a structural change
        // FIXME: Replace logic should use doReplace and xxforms-replace event
        XFormsInsertAction.doInsert(
          containingDocument,
          detailsLogger,
          "before",
          Collections.singletonList(destinationNodeInfo),
          destinationNodeInfo.getParent,
          Collections.singletonList(newDocumentRootElement),
          1,
          false,
          true,
          applyDefaults
        )

        // Perform the deletion of the selected node
        XFormsDeleteAction.doDeleteOne(
          containingDocument = containingDocument,
          nodeInfo           = destinationNodeInfo,
          doDispatch         = true
        )

        // Update model instance
        // NOTE: The inserted node NodeWrapper.index might be out of date at this point because:
        // - doInsert() dispatches an event which might itself change the instance
        // - doDelete() does as well
        // Does this mean that we should check that the node is still where it should be?
      }
      submission.sendSubmitDone(connectionResult)
    }
  }
}