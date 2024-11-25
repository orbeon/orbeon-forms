package org.orbeon.oxf.xforms.route

import org.orbeon.dom
import org.orbeon.dom.io.XMLWriter
import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.controller.{PageFlowControllerProcessor, XmlNativeRoute}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.SessionExpiredException
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.servlet.OrbeonXFormsFilterImpl
import org.orbeon.oxf.util.Logging.debug
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.event.XFormsServer
import org.orbeon.oxf.xforms.event.events.{KeyboardEvent, XXFormsDndEvent, XXFormsLoadEvent, XXFormsUploadDoneEvent}
import org.orbeon.oxf.xforms.processor.PipelineResponse
import org.orbeon.oxf.xforms.state.RequestParameters
import org.orbeon.oxf.xforms.{Loggers, XFormsContainingDocument}
import org.orbeon.oxf.xml.{EncodeDecode, XMLReceiver}
import org.orbeon.xforms.XFormsNames
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.rpc.{WireAjaxEvent, WireAjaxEventWithTarget, WireAjaxEventWithoutTarget}

import scala.jdk.CollectionConverters.*


object XFormsServerRoute extends XmlNativeRoute {

  import Private.*

  def process(
    matchResult   : MatchResult
  )(implicit
    pc            : PipelineContext,
    ec            : ExternalContext
  ): Unit =
    doIt(
      readRequestBodyAsDomDocument,
      Some(getResponseXmlReceiver)
    )

  def doIt(
    requestDocument     : dom.Document,
    xmlReceiverOpt      : Option[XMLReceiver],
  )(implicit
    pipelineContext     : PipelineContext,
    externalContext     : ExternalContext
  ): Unit = {

//    implicit val externalContext = XFormsCrossPlatformSupport.externalContext

    // It's not possible to handle a form update without an existing session. We depend on this to check the UUID,
    // to get the lock, and (except for client state) to retrieve form state.
    //
    // NOTE: We should test this at the beginning of this method, but calling readInputAsOrbeonDom() in unit tests
    // can cause the side effect to create the session, so doing so without changing some tests doesn't work.
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

  def extractWireEvents(actionElement: Element): List[WireAjaxEvent] =
    actionElement.elements(XXFORMS_EVENT_QNAME).toList map extractWireEvent

  def getRequestUUID(request: Document): String = {
    val uuidElement =
      request.getRootElement.elementOpt(XFormsNames.XXFORMS_UUID_QNAME) getOrElse
        (throw new IllegalArgumentException)
    uuidElement.getTextTrim.trimAllToNull
  }

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

  private object Private {

    val XmlIndentation = 2

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
  }
}
