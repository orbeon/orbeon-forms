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
package org.orbeon.oxf.xforms.processor

import org.orbeon.dom.io.XMLWriter
import org.orbeon.dom.{Document, Element}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.controller.PageFlowControllerProcessor
import org.orbeon.oxf.http.{SessionExpiredException, StatusCode}
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInputOutputInfo, ProcessorOutput}
import org.orbeon.oxf.servlet.OrbeonXFormsFilterImpl
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.event.events.{KeyboardEvent, XXFormsDndEvent, XXFormsLoadEvent, XXFormsUploadDoneEvent}
import org.orbeon.oxf.xforms.event.{ClientEvents, XFormsServer}
import org.orbeon.oxf.xforms.state.RequestParameters
import org.orbeon.oxf.xml._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms._
import org.orbeon.xforms.rpc.{WireAjaxEvent, WireAjaxEventWithTarget, WireAjaxEventWithoutTarget}

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal


/**
  * The XForms Server processor handles client requests, including events, and either returns an XML
  * response, or returns a response through the ExternalContext.
  */
object XFormsServerProcessor {

  private val InputRequest = "request"

  import Private._

  val logger = Loggers.logger

  def extractParameters(request: Document, isInitialState: Boolean): RequestParameters = {

    val uuid = getRequestUUID(request) ensuring (_ ne null)

    val sequenceElement =
      request.getRootElement.elementOpt(XXFORMS_SEQUENCE_QNAME) getOrElse
        (throw new IllegalArgumentException)

    val sequenceOpt =
      sequenceElement.getTextTrim.trimAllToOpt map (_.toLong)

    val submissionIdOpt =
      request.getRootElement.elementOpt(XXFORMS_SUBMISSION_ID_QNAME) flatMap
        (_.getTextTrim.trimAllToOpt)

    val encodedStaticStateOpt =
      request.getRootElement.elementOpt(XXFORMS_STATIC_STATE_QNAME) flatMap
        (_.getTextTrim.trimAllToOpt)

    val qName =
      if (isInitialState)
        XXFORMS_INITIAL_DYNAMIC_STATE_QNAME
    else
        XXFORMS_DYNAMIC_STATE_QNAME

    val encodedDynamicStateOpt =
      request.getRootElement.elementOpt(qName) flatMap
        (_.getTextTrim.trimAllToOpt)

    RequestParameters(uuid, sequenceOpt, submissionIdOpt, encodedStaticStateOpt, encodedDynamicStateOpt)
  }

  def extractWireEvents(actionElement: Element): List[WireAjaxEvent] =
    actionElement.elements(XXFORMS_EVENT_QNAME).toList map extractWireEvent

  private object Private {

    // Only a few events specify custom properties that can be set by the client
    val AllStandardProperties =
      XXFormsDndEvent.StandardProperties        ++
      KeyboardEvent.StandardProperties          ++
      XXFormsUploadDoneEvent.StandardProperties ++
      XXFormsLoadEvent.StandardProperties

    def extractWireEvent(eventElem: Element): WireAjaxEvent = {

      val eventName   = eventElem.attributeValue("name")
      val targetIdOpt = eventElem.attributeValueOpt("source-control-id")
      val properties  = eventElem.elements(XXFORMS_PROPERTY_QNAME) map { e => (e.attributeValue("name"), e.getText) } toMap

      def standardPropertiesIt =
        for {
          attributeNames <- AllStandardProperties.get(eventName).iterator
          attributeName  <- attributeNames
          attributeValue <- eventElem.attributeValueOpt(attributeName)
        } yield
          attributeName -> attributeValue

      val allProperties = properties ++ standardPropertiesIt

      targetIdOpt match {
        case Some(targetId) =>
          WireAjaxEventWithTarget(
            eventName,
            targetId,
            allProperties
          )
        case None =>
          WireAjaxEventWithoutTarget(
            eventName,
            allProperties
          )
      }
    }

    def getRequestUUID(request: Document): String = {
      val uuidElement =
        request.getRootElement.elementOpt(XFormsNames.XXFORMS_UUID_QNAME) getOrElse
          (throw new IllegalArgumentException)
      uuidElement.getTextTrim.trimAllToNull
    }
  }
}

class XFormsServerProcessor extends ProcessorImpl {

  self =>

  import XFormsServerProcessor._

  addInputInfo(new ProcessorInputOutputInfo(XFormsServerProcessor.InputRequest))

  // Case where an XML response must be generated.
  override def createOutput(outputName: String): ProcessorOutput = {
    val output = new ProcessorOutputImpl(self, outputName) {
      override def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
        try {
          doIt(pipelineContext, xmlReceiverOpt = Some(xmlReceiver))
        } catch {
          case e: SessionExpiredException =>
            implicit val externalContext = XFormsCrossPlatformSupport.externalContext
            LifecycleLogger.eventAssumingRequest("xforms", e.message, Nil)
            // Don't log whole exception
            Loggers.logger.info(e.message)
            ClientEvents.errorDocument(e.message, e.code)(xmlReceiver)
          case NonFatal(t) =>
            Loggers.logger.error(OrbeonFormatter.format(t))
            ClientEvents.errorDocument(OrbeonFormatter.message(t), StatusCode.InternalServerError)(xmlReceiver)
        }
      }
    }
    addOutput(outputName, output)
    output
  }

  // Case where the response is generated through the ExternalContext (submission with `replace="all"`).
  override def start(pipelineContext: PipelineContext): Unit =
    doIt(pipelineContext, xmlReceiverOpt = None)

  private def doIt(pipelineContext: PipelineContext, xmlReceiverOpt: Option[XMLReceiver]): Unit = {

    // Use request input provided by client
    val requestDocument = readInputAsOrbeonDom(pipelineContext, XFormsServerProcessor.InputRequest)
    implicit val externalContext = XFormsCrossPlatformSupport.externalContext

    // It's not possible to handle a form update without an existing session. We depend on this to check the UUID,
    // to get the lock, and (except for client state) to retrieve form state.
    //
    // NOTE: We should test this at the beginning of this method, but calling readInputAsOrbeonDom() in unit tests
    // can cause the side-effect to create the session, so doing so without changing some tests doesn't work.
    externalContext.getSessionOpt(false) getOrElse
      (throw SessionExpiredException("Session has expired. Unable to process incoming request."))

    // Logger used for heartbeat and request/response
    implicit val indentedLogger = Loggers.newIndentedLogger("server")

    val logRequestResponse = Loggers.isDebugEnabled("server-body")

    if (logRequestResponse)
      debug("ajax request", List("body" -> requestDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat)))

    val parameters      = extractParameters(requestDocument, isInitialState = false)
    val actionElement   = requestDocument.getRootElement.elementOpt(XXFORMS_ACTION_QNAME)
    val extractedEvents = actionElement map extractWireEvents getOrElse Nil

    // State to set before running events
    def beforeProcessRequest(containingDocument: XFormsContainingDocument): Unit = {
      // Set URL rewriter resource path information based on information in static state
      if (containingDocument.getVersionedPathMatchers.nonEmpty) {
        // Don't override existing matchers if any (e.g. case of `oxf:xforms-to-xhtml` and `oxf:xforms-submission`
        // processor running in same pipeline)
        pipelineContext.setAttribute(PageFlowControllerProcessor.PathMatchers, containingDocument.getVersionedPathMatchers.asJava)
      }

      // Set deployment mode into request (useful for epilogue)
      externalContext.getRequest.getAttributesMap
        .put(OrbeonXFormsFilterImpl.RendererDeploymentAttributeName, containingDocument.getDeploymentType.entryName)
    }

    XFormsServer.processEvents(
      logRequestResponse      = logRequestResponse,
      requestParameters       = parameters,
      requestParametersForAll = extractParameters(requestDocument, isInitialState = true),
      extractedEvents         = extractedEvents,
      xmlReceiverOpt          = xmlReceiverOpt,
      responseForReplaceAll   = PipelineResponse.getResponse(xmlReceiverOpt, externalContext),
      beforeProcessRequest    = beforeProcessRequest,
      extractWireEvents       = s => extractWireEvents(EncodeDecode.decodeXML(s, forceEncryption = true).getRootElement),
      trustEvents             = false
    )
  }
}
