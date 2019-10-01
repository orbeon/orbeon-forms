/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.xforms

import java.{util ⇒ ju}

import org.apache.commons.lang3.StringUtils
import org.orbeon.datatypes.MaximumSize
import org.orbeon.oxf.cache.Cacheable
import org.orbeon.oxf.controller.PageFlowControllerProcessor
import org.orbeon.oxf.externalcontext.URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.servlet.OrbeonXFormsFilter
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.URLRewriterUtils.PathMatcher
import org.orbeon.oxf.util.XPath.CompiledExpression
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.analytics.{RequestStats, RequestStatsImpl}
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.event.ClientEvents._
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.{ClientEvents, XFormsEvent, XFormsEventFactory, XFormsEventTarget}
import org.orbeon.oxf.xforms.function.xxforms.{UploadMaxSizeValidation, UploadMediatypesValidation}
import org.orbeon.oxf.xforms.model.{InstanceData, XFormsModel}
import org.orbeon.oxf.xforms.processor.{ScriptBuilder, XFormsServer}
import org.orbeon.oxf.xforms.state.{DynamicState, RequestParameters, XFormsStateManager}
import org.orbeon.oxf.xforms.submission.UrlType
import org.orbeon.oxf.xforms.upload.{AllowedMediatypes, UploadCheckerLogic}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.{XMLReceiver, XMLReceiverSupport}
import org.orbeon.xforms.{Constants, rpc}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

object XFormsContainingDocumentSupport {

  def withDocumentAcquireLock[T](uuid: String, timeout: Long)(block: XFormsContainingDocument ⇒ T): T = {
    withLock(RequestParameters(uuid, None, None, None), timeout) {
      case Some(containingDocument) ⇒
        withUpdateResponse(containingDocument, ignoreSequence = true)(block(containingDocument))
      case None ⇒
        // This happens if the timeout expires!
        throw new IllegalStateException
    }
  }

  def withLock[T](params: RequestParameters, timeout: Long)(block: Option[XFormsContainingDocument] ⇒ T): T = {

    LifecycleLogger.eventAssumingRequest("xforms", "before document lock", List("uuid" → params.uuid))

    XFormsStateManager.acquireDocumentLock(params.uuid, timeout) match {
      case Some(lock) ⇒
        try {

          LifecycleLogger.eventAssumingRequest(
            "xforms",
            "got document lock",
            LifecycleLogger.basicRequestDetailsAssumingRequest(
              List(
                "uuid" → params.uuid,
                "wait" → LifecycleLogger.formatDelay(System.currentTimeMillis)
              )
            )
          )

          val containingDocument =
            XFormsStateManager.beforeUpdate(params, disableDocumentCache = false)

          var keepDocument = false
          try {
            val result = block(Some(containingDocument))
            keepDocument = true
            result
          } finally {
            XFormsStateManager.afterUpdate(containingDocument, keepDocument, disableDocumentCache = false)
          }

        } finally {
          XFormsStateManager.releaseDocumentLock(lock)
        }
      case None ⇒
        LifecycleLogger.eventAssumingRequest("xforms", "document lock timeout", List("uuid" → params.uuid))
        block(None)
    }
  }

  def withUpdateResponse[T](containingDocument: XFormsContainingDocument, ignoreSequence: Boolean)(block: ⇒ T): T = {
    XFormsStateManager.beforeUpdateResponse(containingDocument, ignoreSequence = ignoreSequence)
    val result = block
    XFormsStateManager.afterUpdateResponse(containingDocument)
    result
  }
}

case class Message(message: String, level: String)

case class Load(resource: String, target: Option[String], urlType: UrlType, isReplace: Boolean, isShowProgress: Boolean)

abstract class XFormsContainingDocumentSupport(var disableUpdates: Boolean)
  extends XBLContainer(
    _effectiveId         = Constants.DocumentId,
    prefixedId           = Constants.DocumentId,
    fullPrefix           = "",
    parentXBLContainer   = None,
    associatedControlOpt = None,
    innerScope           = null
  ) with ContainingDocumentLogging
    with ContainingDocumentTransientState
    with ContainingDocumentMisc
    with ContainingDocumentUpload
    with ContainingDocumentEvent
    with ContainingDocumentProperties
    with ContainingDocumentRequestStats
    with ContainingDocumentRequest
    with ContainingDocumentDelayedEvents
    with ContainingDocumentClientState
    with XFormsDocumentLifecycle
    with Cacheable
    with XFormsObject {

  self: XFormsContainingDocument ⇒
}


trait ContainingDocumentTransientState {

  import CollectionUtils._

  private var transientState = Map.empty[String, Any]

  def setTransientState[T](name: String, value: T): Unit =
    transientState += name → value

  def getTransientState[T: ClassTag](name: String): Option[T] =
    transientState.get(name) flatMap collectByErasedType[T]

  def removeTransientState(name: String): Unit =
    transientState -= name

  def clearTransientState(): Unit =
    transientState = Map.empty
}

trait ContainingDocumentUpload {

  def getControls    : XFormsControls
  def getStaticState : XFormsStaticState
  def defaultModel   : Option[XFormsModel]
  def getRequestStats: RequestStats

  def getUploadChecker: UploadCheckerLogic = UploadChecker

  // NOTE: The control no longer loads `NCName` custom MIPs during refresh. So we go to the
  // bound node instead.
  // See also https://github.com/orbeon/orbeon-forms/issues/3721.
  private def customMipForControl(controlEffectiveId: String, mipName: String) =
    getControls.getCurrentControlTree.findControl(controlEffectiveId) flatMap
      CollectionUtils.collectByErasedType[XFormsSingleNodeControl]    flatMap
      (_.boundNodeOpt) flatMap (InstanceData.findCustomMip(_, mipName))

  private object UploadChecker extends UploadCheckerLogic {

    def findAttachmentMaxSizeValidationMipFor(controlEffectiveId: String) =
      customMipForControl(controlEffectiveId, UploadMaxSizeValidation.PropertyName)

    def currentUploadSizeAggregate = {

      def evaluateAsLong(expr: CompiledExpression) =
        defaultModel match {
          case Some(m) ⇒
            val bindingContext = m.getDefaultEvaluationContext
            XPath.evaluateSingle(
              contextItems       = bindingContext.nodeset,
              contextPosition    = bindingContext.position,
              compiledExpression = expr,
              functionContext    = m.getContextStack.getFunctionContext(m.getEffectiveId, bindingContext),
              variableResolver   = m.variableResolver
            )(getRequestStats.getReporter).asInstanceOf[Long] // we statically ensure that the expression returns an `xs:integer`
          case None ⇒
            throw new AssertionError("can only evaluate dynamic properties if a model is present")
        }

      getStaticState.uploadMaxSizeAggregateExpression map evaluateAsLong map (0L max)
    }

    def uploadMaxSizeProperty          = getStaticState.uploadMaxSize
    def uploadMaxSizeAggregateProperty = getStaticState.uploadMaxSizeAggregate
  }

  def getUploadConstraintsForControl(controlEffectiveId: String): (MaximumSize, AllowedMediatypes) = {

    val allowedMediatypesMaybeRange = {

      def fromCommonConstraint =
        customMipForControl(controlEffectiveId, UploadMediatypesValidation.PropertyName) flatMap
          AllowedMediatypes.unapply

      def fromFormSetting =
        getStaticState.staticStringProperty(UPLOAD_MEDIATYPES_PROPERTY).trimAllToOpt flatMap
          AllowedMediatypes.unapply

      fromCommonConstraint orElse
        fromFormSetting    getOrElse
        AllowedMediatypes.AllowedAnyMediatype
    }

    UploadChecker.uploadMaxSizeForControl(controlEffectiveId) → allowedMediatypesMaybeRange
  }
}

trait ContainingDocumentMisc {

  self: XBLContainer ⇒

  protected def initializeModels(): Unit =
    initializeModels(
      List(
        XFORMS_MODEL_CONSTRUCT,
        XFORMS_MODEL_CONSTRUCT_DONE,
        XFORMS_READY
      )
    )

  // A sequence number used for dependencies
  // This doesn't need to be serialized/deserialized as it's only used internally
  private var _lastModelSequenceNumber = 0
  def nextModelSequenceNumber() = {
    _lastModelSequenceNumber += 1
    _lastModelSequenceNumber
  }
}

trait ContainingDocumentEvent {

  private var eventStack: List[XFormsEvent] = Nil

  def startHandleEvent(event: XFormsEvent): Unit                = eventStack ::= event
  def endHandleEvent()                    : Unit                = eventStack = eventStack.tail
  def currentEventOpt                     : Option[XFormsEvent] = eventStack.headOption
}

trait ContainingDocumentProperties {

  def getStaticState: XFormsStaticState
  def defaultModel: Option[XFormsModel]
  def getRequestStats: RequestStats

  def disableUpdates: Boolean
  def isInitializing: Boolean

  // Whether the document supports updates
  private lazy val _supportUpdates = ! (disableUpdates || isNoUpdates)
  def supportUpdates = _supportUpdates

  // Used by the property() function
  def getProperty(propertyName: String): Any = propertyName match {
    case READONLY_APPEARANCE_PROPERTY ⇒ if (staticReadonly) READONLY_APPEARANCE_STATIC_VALUE else READONLY_APPEARANCE_DYNAMIC_VALUE
    case NOSCRIPT_PROPERTY            ⇒ false
    case ENCRYPT_ITEM_VALUES_PROPERTY ⇒ encodeItemValues
    case ORDER_PROPERTY               ⇒ lhhacOrder
    case _                            ⇒ getStaticState.propertyMaybeAsExpression(propertyName).left.get
  }

  private object Memo {
    private val cache = collection.mutable.Map.empty[String, Any]

    private def memo[T](name: String, get: ⇒ T) =
      cache.getOrElseUpdate(name, get).asInstanceOf[T]

    def staticStringProperty (name: String) = memo(name, getStaticState.staticStringProperty(name))
    def staticBooleanProperty(name: String) = memo(name, getStaticState.staticBooleanProperty(name))
    def staticIntProperty    (name: String) = memo(name, getStaticState.staticIntProperty(name))

    def staticBooleanProperty[T](name: String, pred: T ⇒ Boolean) =
      memo(name, pred(getStaticState.staticProperty(name).asInstanceOf[T]))

    def dynamicProperty[T](name: String, convert: String ⇒ T) =
      memo[T](
        name,
        convert(
          getStaticState.propertyMaybeAsExpression(name) match {
            case Left(value) ⇒ value.toString
            case Right(expr) ⇒ evaluateStringPropertyAVT(expr)
          }
        )
      )

    def evaluateStringPropertyAVT(expr: CompiledExpression) =
      defaultModel match {
        case Some(m) ⇒
          val bindingContext = m.getDefaultEvaluationContext
          XPath.evaluateAsString(
            contextItems       = bindingContext.nodeset,
            contextPosition    = bindingContext.position,
            compiledExpression = expr,
            functionContext    = m.getContextStack.getFunctionContext(m.getEffectiveId, bindingContext),
            variableResolver   = m.variableResolver
          )(getRequestStats.getReporter)
        case None ⇒
          throw new AssertionError("can only evaluate AVT properties if a model is present")
      }
  }

  import Memo._

  // Dynamic properties
  def staticReadonly =
    dynamicProperty(
      READONLY_APPEARANCE_PROPERTY,
      _ == READONLY_APPEARANCE_STATIC_VALUE
    )

  def encodeItemValues =
    dynamicProperty(
      ENCRYPT_ITEM_VALUES_PROPERTY,
      _.toBoolean
    )

  def lhhacOrder =
    dynamicProperty(
      ORDER_PROPERTY,
      LHHA.getBeforeAfterOrderTokens
    )

  def staticReadonlyHint: Boolean =
    dynamicProperty(
      STATIC_READONLY_HINT_PROPERTY,
      _.toBoolean
    )

  def staticReadonlyAlert: Boolean =
    dynamicProperty(
      STATIC_READONLY_ALERT_PROPERTY,
      _.toBoolean
    )

  def hostLanguage =
    dynamicProperty(
      HOST_LANGUAGE,
      identity
    )

  def isNoUpdates =
    dynamicProperty(
      NO_UPDATES,
      _.toBoolean
    )

  def isNoUpdatesStatic =
    getStaticState.propertyMaybeAsExpression(NO_UPDATES) match {
      case Left(value) ⇒ value.toString == "true"
      case _ ⇒ false
    }

  // Static properties
  def isOptimizeGetAllSubmission            = staticBooleanProperty(OPTIMIZE_GET_ALL_PROPERTY)
  def isLocalSubmissionInclude              = staticBooleanProperty(LOCAL_SUBMISSION_INCLUDE_PROPERTY)
  def isLocalInstanceInclude                = staticBooleanProperty(LOCAL_INSTANCE_INCLUDE_PROPERTY)
  def isExposeXPathTypes                    = staticBooleanProperty(EXPOSE_XPATH_TYPES_PROPERTY)
  def isSessionHeartbeat                    = staticBooleanProperty(SESSION_HEARTBEAT_PROPERTY)
  def isXForms11Switch                      = staticBooleanProperty(XFORMS11_SWITCH_PROPERTY)
  def isClientStateHandling                 = staticBooleanProperty[String](STATE_HANDLING_PROPERTY, _ == STATE_HANDLING_CLIENT_VALUE)
  def isReadonlyAppearanceStaticSelectFull  = staticBooleanProperty[String](READONLY_APPEARANCE_STATIC_SELECT_PROPERTY, _ == "full")
  def isReadonlyAppearanceStaticSelect1Full = staticBooleanProperty[String](READONLY_APPEARANCE_STATIC_SELECT1_PROPERTY, _ ==  "full")

  def getLabelElementName                   = staticStringProperty(LABEL_ELEMENT_NAME_PROPERTY)
  def getHintElementName                    = staticStringProperty(HINT_ELEMENT_NAME_PROPERTY)
  def getHelpElementName                    = staticStringProperty(HELP_ELEMENT_NAME_PROPERTY)
  def getAlertElementName                   = staticStringProperty(ALERT_ELEMENT_NAME_PROPERTY)
  def getLabelAppearances                   = stringToSet(staticStringProperty(LABEL_APPEARANCE_PROPERTY))
  def getHintAppearances                    = stringToSet(staticStringProperty(HINT_APPEARANCE_PROPERTY))
  def getHelpAppearance                     = staticStringProperty(HELP_APPEARANCE_PROPERTY)
  def getDateFormatInput                    = staticStringProperty(DATE_FORMAT_INPUT_PROPERTY)

  def getShowMaxRecoverableErrors           = staticIntProperty(SHOW_RECOVERABLE_ERRORS_PROPERTY)
  def getSubmissionPollDelay                = staticIntProperty(ASYNC_SUBMISSION_POLL_DELAY)
  def getAjaxFullUpdateThreshold            = staticIntProperty(AJAX_UPDATE_FULL_THRESHOLD)

  def isLocalSubmissionForward =
    staticBooleanProperty(LOCAL_SUBMISSION_FORWARD_PROPERTY) &&
    staticBooleanProperty(OPTIMIZE_LOCAL_SUBMISSION_REPLACE_ALL_PROPERTY)

  import ContainingDocumentProperties._

  def getTypeOutputFormat(typeName: String) =
    SupportedTypesForOutputFormat(typeName) option
      staticStringProperty(TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + typeName)

  def getTypeInputFormat(typeName: String) = {
    require(SupportedTypesForInputFormat(typeName))
    staticStringProperty(TYPE_INPUT_FORMAT_PROPERTY_PREFIX + typeName)
  }
}

private object ContainingDocumentProperties {
  val SupportedTypesForOutputFormat = Set("date", "time", "dateTime", "decimal", "integer", "float", "double")
  val SupportedTypesForInputFormat  = Set("date", "time")
}

trait ContainingDocumentLogging {

  private final val indentation = new IndentedLogger.Indentation
  private final val loggersMap = mutable.HashMap[String, IndentedLogger]()

  def getIndentedLogger(loggingCategory: String): IndentedLogger =
    loggersMap.getOrElseUpdate(loggingCategory,
      new IndentedLogger(
        XFormsServer.logger,
        XFormsServer.logger.isDebugEnabled && getDebugLogging.contains(loggingCategory),
        indentation
      )
    )

  val indentedLogger = getIndentedLogger("document")
}

trait ContainingDocumentRequestStats {

  private var _requestStats = RequestStatsImpl()
  def getRequestStats = _requestStats

  def clearRequestStats(): Unit =
    _requestStats = RequestStatsImpl()
}

trait ContainingDocumentRequest {

  def getStaticState : XFormsStaticState

  private var _deploymentType             : DeploymentType = null
  private var _requestContextPath         : String = null
  private var _requestPath                : String = null
  private var _requestHeaders             : Map[String, List[String]] = null
  private var _requestParameters          : Map[String, List[String]] = null
  private var _containerType              : String = null
  private var _containerNamespace         : String = null
  private var _versionedPathMatchers      : ju.List[URLRewriterUtils.PathMatcher] = null
  private var _isEmbedded                 : Boolean = false
  private var _isPortletContainerOrRemote : Boolean = false

  def getDeploymentType        = _deploymentType
  def getRequestContextPath    = _requestContextPath
  def getRequestPath           = _requestPath
  def getRequestHeaders        = _requestHeaders
  def getRequestParameters     = _requestParameters
  def getContainerType         = _containerType
  def getContainerNamespace    = _containerNamespace // always "" for servlets.
  def getVersionedPathMatchers = _versionedPathMatchers

  def headersGetter: String ⇒ Option[List[String]] = getRequestHeaders.get

  def isPortletContainer         = _containerType == "portlet"
  def isEmbedded                 = _isEmbedded
  def isServeInlineResources     = getStaticState.isInlineResources || _isPortletContainerOrRemote

  private def isEmbeddedFromHeaders(headers: Map[String, List[String]]) =
    Headers.firstItemIgnoreCase(headers, Headers.OrbeonClient) exists Headers.EmbeddedClientValues

  private def isPortletContainerOrRemoteFromHeaders(headers: Map[String, List[String]]) =
    isPortletContainer || (Headers.firstItemIgnoreCase(headers, Headers.OrbeonClient) contains Headers.PortletClient)

  protected def initializeRequestInformation(): Unit =
    Option(NetUtils.getExternalContext.getRequest) match {
      case Some(request) ⇒
        // Remember if filter provided separate deployment information

        import OrbeonXFormsFilter._

        val rendererDeploymentType =
          request.getAttributesMap.get(RendererDeploymentAttributeName).asInstanceOf[String]

        _deploymentType =
          rendererDeploymentType match {
            case "separate"   ⇒ DeploymentType.separate
            case "integrated" ⇒ DeploymentType.integrated
            case _            ⇒ DeploymentType.standalone
          }

        // Try to get request context path
        _requestContextPath = request.getClientContextPath("/")

        // It is possible to override the base URI by setting a request attribute. This is used by OrbeonXFormsFilter.
        // NOTE: We used to have response.rewriteRenderURL() on this, but why?
        _requestPath =
          Option(request.getAttributesMap.get(RendererBaseUriAttributeName).asInstanceOf[String]) getOrElse
          request.getRequestPath

        _requestHeaders =
          request.getHeaderValuesMap.asScala mapValues (_.toList) toMap

        _isEmbedded = isEmbeddedFromHeaders(_requestHeaders)

        _requestParameters =
          request.getParameterMap.asScala mapValues StringConversions.objectArrayToStringArray mapValues (_.toList) toMap

        _containerType = request.getContainerType
        _containerNamespace = StringUtils.defaultIfEmpty(request.getContainerNamespace, "")
        _isPortletContainerOrRemote = isPortletContainerOrRemoteFromHeaders(_requestHeaders)
      case None ⇒
        // Special case when we run outside the context of a request
        _deploymentType = DeploymentType.standalone
        _requestContextPath = ""
        _requestPath = "/"
        _requestHeaders = Map.empty
        _isEmbedded = false
        _requestParameters = Map.empty
        _containerType = "servlet"
        _containerNamespace = ""
        _isPortletContainerOrRemote = false
    }

  protected def initializePathMatchers(): Unit =
    _versionedPathMatchers =
      Option(PipelineContext.get.getAttribute(PageFlowControllerProcessor.PathMatchers).asInstanceOf[ju.List[PathMatcher]]) getOrElse
        ju.Collections.emptyList[PathMatcher]

  protected def restoreRequestInformation(dynamicState: DynamicState): Unit =
    dynamicState.deploymentType match {
      case Some(_) ⇒
        // Normal case where information below was previously serialized
        _deploymentType             = DeploymentType.valueOf(dynamicState.decodeDeploymentTypeJava)
        _requestContextPath         = dynamicState.decodeRequestContextPathJava
        _requestPath                = dynamicState.decodeRequestPathJava
        _requestHeaders             = dynamicState.requestHeaders.toMap
        _isEmbedded                 = isEmbeddedFromHeaders(_requestHeaders)
        _requestParameters          = dynamicState.requestParameters.toMap
        _containerType              = dynamicState.decodeContainerTypeJava
        _containerNamespace         = dynamicState.decodeContainerNamespaceJava
        _isPortletContainerOrRemote = isPortletContainerOrRemoteFromHeaders(_requestHeaders)
      case None ⇒
        // Use information from the request
        // This is relied upon by oxf:xforms-submission and unit tests and shouldn't be relied on in other cases
        initializeRequestInformation()
    }

  protected def restorePathMatchers(dynamicState: DynamicState): Unit =
    _versionedPathMatchers = dynamicState.decodePathMatchersJava
}

trait ContainingDocumentDelayedEvents {

  self: XBLContainer ⇒

  private val _delayedEvents = ListBuffer[DelayedEvent]()

  // Schedule an event for delayed execution, following xf:dispatch/@delay semantics
  def addDelayedEvent(
    eventName         : String,
    targetEffectiveId : String,
    bubbles           : Boolean,
    cancelable        : Boolean,
    time              : Long,
    discardable       : Boolean,
    showProgress      : Boolean,
    allowDuplicates   : Boolean
  ): Unit = {

    def isDuplicate(e: DelayedEvent) =
      e.eventName == eventName && e.targetEffectiveId == targetEffectiveId

    if (allowDuplicates || ! (_delayedEvents exists isDuplicate))
      _delayedEvents += DelayedEvent(
        eventName,
        targetEffectiveId,
        bubbles,
        cancelable,
        time,
        discardable,
        showProgress
      )
  }

  def delayedEvents =
    _delayedEvents.toList

  def clearAllDelayedEvents() =
    _delayedEvents.clear()

  def processDueDelayedEvents(): Unit = {

    @tailrec
    def processRemainingBatchesRecursively(): Unit = {

      // Get a fresh time for every batch because processing a batch can take time
      val currentTime = System.currentTimeMillis()

      val (dueEvents, futureEvents) = delayedEvents partition (_.time <= currentTime)

      if (dueEvents.nonEmpty) {

        _delayedEvents.clear()
        _delayedEvents ++= futureEvents

        dueEvents foreach { dueEvent ⇒

          startOutermostActionHandler()

          self.getObjectByEffectiveId(dueEvent.targetEffectiveId) match {
            case eventTarget: XFormsEventTarget ⇒
              ClientEvents.processEvent(
                self.containingDocument,
                XFormsEventFactory.createEvent(
                  eventName  = dueEvent.eventName,
                  target     = eventTarget,
                  properties = EmptyGetter, // NOTE: We don't support properties for delayed events yet.
                  bubbles    = dueEvent.bubbles,
                  cancelable = dueEvent.cancelable
                )
              )
            case _ ⇒
              implicit val logger = self.containingDocument.getIndentedLogger(LOGGING_CATEGORY)
              debug(
                "ignoring delayed event with invalid target id",
                List(
                  "target id"  → dueEvent.targetEffectiveId,
                  "event name" → dueEvent.eventName
                )
              )
          }

          endOutermostActionHandler()
        }

        // Try again in case there are new events available
        processRemainingBatchesRecursively()
      }
    }

    processRemainingBatchesRecursively()
  }
}

trait ContainingDocumentClientState {

  self: XFormsContainingDocument ⇒

  private var _initialClientScript: Option[String] = None

  def initialClientScript: Option[String] =
    _initialClientScript

  def setInitialClientScript(): Unit = {

    implicit val externalContext = NetUtils.getExternalContext

    val response = externalContext.getResponse

    val scripts =
      ScriptBuilder.findOtherScriptInvocations(this).toList :::
      ScriptBuilder.findConfigurationProperties(
        this,
        URLRewriterUtils.isResourcesVersioned,
        XFormsStateManager.getHeartbeatDelay(this, NetUtils.getExternalContext)
      ).toList :::
      List(
        ScriptBuilder.buildJavaScriptInitialData(
          containingDocument   = this,
          rewriteResource      = response.rewriteResourceURL(_: String, REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE),
          controlsToInitialize = getControls.getCurrentControlTree.rootOpt map ScriptBuilder.gatherJavaScriptInitializations getOrElse Nil
        )
      )

    _initialClientScript = Some(scripts.fold("")(_ + _))
  }


  def clearInitialClientScript(): Unit =
    _initialClientScript = None
}

case class DelayedEvent(
  eventName         : String,
  targetEffectiveId : String,
  bubbles           : Boolean,
  cancelable        : Boolean,
  time              : Long,
  discardable       : Boolean, // whether the client can discard the event past the delay (see AjaxServer.js)
  showProgress      : Boolean  // whether to show the progress indicator when submitting the event
) {

  private def asEncodedDocument: String = {

    import org.orbeon.oxf.xml.Dom4j._

    XFormsUtils.encodeXML(
      <xxf:events xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
        <xxf:event
          name={eventName}
          source-control-id={targetEffectiveId}
          bubbles={bubbles.toString}
          cancelable={cancelable.toString}/>
      </xxf:events>,
      false
    )
  }

  def writeAsSAX(currentTime: Long)(implicit receiver: XMLReceiver): Unit = {

    import XMLReceiverSupport._

    element(
      localName = "server-events",
      prefix    = "xxf",
      uri       = XXFORMS_NAMESPACE_URI,
      atts      = List(
        "delay"         → (time - currentTime).toString,
        "discardable"   → discardable.toString,
        "show-progress" → showProgress.toString
      ),
      text      = asEncodedDocument
    )
  }

  def toServerEvent(currentTime: Long): rpc.ServerEvent =
    rpc.ServerEvent(
      delay        = time - currentTime,
      discardable  = true,
      showProgress = showProgress,
      event        = asEncodedDocument
    )
}
