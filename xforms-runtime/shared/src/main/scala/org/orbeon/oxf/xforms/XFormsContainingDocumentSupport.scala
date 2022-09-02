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

import java.net.URI
import java.util.concurrent.locks.Lock
import cats.Eval
import cats.syntax.option._
import org.orbeon.datatypes.{LocationData, MaximumSize}
import org.orbeon.dom.Element
import org.orbeon.oxf.cache.Cacheable
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.StaticXPath.CompiledExpression
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.analytics.{RequestStats, RequestStatsImpl}
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.event.ClientEvents._
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event._
import org.orbeon.oxf.xforms.function.xxforms.ValidationFunctionNames
import org.orbeon.oxf.xforms.model.{InstanceData, XFormsModel}
import org.orbeon.oxf.xforms.processor.ScriptBuilder
import org.orbeon.oxf.xforms.state.{LockResponse, RequestParameters, XFormsStateManager}
import org.orbeon.oxf.xforms.submission.{ConnectResult, TwoPassSubmissionParameters}
import org.orbeon.oxf.xforms.upload.{AllowedMediatypes, UploadCheckerLogic}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms._
import org.orbeon.xforms.runtime.XFormsObject
import shapeless.syntax.typeable._

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.collection.{immutable, mutable}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


object XFormsContainingDocumentSupport {

  def withDocumentAcquireLock[T](uuid: String, timeout: Long)(block: XFormsContainingDocument => T): Try[T] = {
    withLock(RequestParameters(uuid, None, None, None), timeout) {
      case Some(containingDocument) =>
        withUpdateResponse(containingDocument, ignoreSequence = true)(block(containingDocument))
      case None =>
        // This happens if the timeout expires!
        throw new IllegalStateException
    }
  }

  def withLock[T](params: RequestParameters, timeout: Long)(block: Option[XFormsContainingDocument] => T): Try[T] = {

    implicit val ec: ExternalContext = XFormsCrossPlatformSupport.externalContext

    LifecycleLogger.eventAssumingRequest("xforms", "before document lock", List("uuid" -> params.uuid))

    XFormsStateManager.acquireDocumentLock(params.uuid, timeout) match {
      case LockResponse.Success(lock) =>
        try {

          LifecycleLogger.eventAssumingRequest(
            "xforms",
            "got document lock",
            LifecycleLogger.basicRequestDetailsAssumingRequest(
              List(
                "uuid" -> params.uuid,
                "wait" -> LifecycleLogger.formatDelay(System.currentTimeMillis)
              )
            )
          )

          val containingDocument =
            XFormsStateManager.beforeUpdate(params, disableDocumentCache = false)

          var keepDocument = false
          try {
            val result = block(Some(containingDocument))
            keepDocument = true
            Success(result)
          } finally {
            XFormsStateManager.afterUpdate(containingDocument, keepDocument, disableDocumentCache = false)
          }

        } finally {
          XFormsStateManager.releaseDocumentLock(lock)
        }
      case LockResponse.Busy =>
        LifecycleLogger.eventAssumingRequest("xforms", "document lock busy (zero timeout)", List("uuid" -> params.uuid))
        Success(block(None))
      case LockResponse.Timeout =>
        LifecycleLogger.eventAssumingRequest("xforms", "document lock timeout", List("uuid" -> params.uuid))
        Success(block(None))
      case LockResponse.Failure(t) =>
        Failure(t)
    }
  }

  def withUpdateResponse[T](containingDocument: XFormsContainingDocument, ignoreSequence: Boolean)(block: => T): T = {
    XFormsStateManager.beforeUpdateResponse(containingDocument, ignoreSequence = ignoreSequence)
    val result = block
    XFormsStateManager.afterUpdateResponse(containingDocument)
    result
  }
}

abstract class XFormsContainingDocumentSupport
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
    with XFormsDocumentLifecycle[ExternalContext.Response]
    with ContainingDocumentCacheable
    with XFormsObject {

  self: XFormsContainingDocument =>
}


trait ContainingDocumentTransientState {

  self: XBLContainer with ContainingDocumentDelayedEvents =>

  import CollectionUtils._

  def getShowMaxRecoverableErrors: Int
  def findControlByEffectiveId(effectiveId: String): Option[XFormsControl]

  private class TransientState {

    var byName                   : Map[String, Any] = Map.empty[String, Any]

    var nonJavaScriptLoadsToRun  : Vector[Load] = Vector.empty
    var scriptsToRun             : Vector[Load Either ScriptInvocation] = Vector.empty

    var replaceAllEval           : Option[Eval[ConnectResult]] = None
    var gotSubmissionReplaceAll  : Boolean = false
    var gotSubmissionRedirect    : Boolean = false

    var messagesToRun            : Vector[Message] = Vector.empty
    var serverErrors             : Vector[ServerError] = Vector.empty

    var helpEffectiveControlId   : Option[String] = None
  }

  private var transientState: TransientState = new TransientState

  def setTransientState[T](name: String, value: T): Unit =
    transientState.byName += name -> value

  def getTransientState[T: ClassTag](name: String): Option[T] =
    transientState.byName.get(name) flatMap collectByErasedType[T]

  def removeTransientState(name: String): Unit =
    transientState.byName -= name

  def clearTransientState(): Unit =
    transientState = new TransientState

  def addScriptToRun(scriptInvocation: ScriptInvocation): Unit =
    transientState.scriptsToRun :+= Right(scriptInvocation)

  def getScriptsToRun: immutable.Seq[Load Either ScriptInvocation] = transientState.scriptsToRun

  def addLoadToRun(load: Load): Unit =
    findTwoPassSubmitEvent match {
      case Some(_) =>
        throw new OXFException("Unable to run a two-pass submission and `xf:load` within a same action sequence.")
      case None =>
        if (load.isJavaScript)
          transientState.scriptsToRun :+= Left(load)
        else
          transientState.nonJavaScriptLoadsToRun :+= load
    }

  def getNonJavaScriptLoadsToRun: immutable.Seq[Load] = transientState.nonJavaScriptLoadsToRun

  def getReplaceAllEval: Option[Eval[ConnectResult]] = transientState.replaceAllEval

  def setReplaceAllEval(eval: Eval[ConnectResult]): Unit =
    transientState.replaceAllEval = Option(eval)

  def setGotSubmissionReplaceAll(): Unit = {
    if (transientState.gotSubmissionReplaceAll)
      throw new OXFException("Unable to run a second submission with `replace=\"all\"` within a same action sequence.")
    transientState.gotSubmissionReplaceAll = true
  }

  def isGotSubmissionReplaceAll: Boolean = transientState.gotSubmissionReplaceAll

  def setGotSubmissionRedirect(): Unit = {
    if (transientState.gotSubmissionRedirect)
      throw new OXFException("Unable to run a second submission with `replace=\"all\"` redirection within a same action sequence.")
    transientState.gotSubmissionRedirect = true
  }

  def isGotSubmissionRedirect: Boolean = transientState.gotSubmissionRedirect

  // Add an XForms message to send to the client.
  def addMessageToRun(message: String, level: String): Unit =
    transientState.messagesToRun :+= Message(message, level)

  def getMessagesToRun: immutable.Seq[Message] = transientState.messagesToRun

  def addServerError(serverError: ServerError): Unit = {
    val maxErrors = getShowMaxRecoverableErrors
    if (maxErrors > 0 && transientState.serverErrors.size < maxErrors)
      transientState.serverErrors :+= serverError
  }

  def getServerErrors: immutable.Seq[ServerError] = transientState.serverErrors

  // Tell the client that help must be shown for the given effective control id.
  // This can be called several times, but only the last control id is remembered.
  def setClientHelpEffectiveControlId(effectiveControlId: String): Unit =
    transientState.helpEffectiveControlId = Option(effectiveControlId)

  def getClientHelpControlEffectiveId: Option[String] =
    for {
      controlId         <- transientState.helpEffectiveControlId
      xformsControl     <- findControlByEffectiveId(controlId)
      singleNodeControl <- xformsControl.narrowTo[XFormsSingleNodeControl]
      if singleNodeControl.isRelevant
    } yield
      controlId
}

trait ContainingDocumentUpload {

  def controls       : XFormsControls
  def staticState    : XFormsStaticState
  def defaultModel   : Option[XFormsModel]
  def getRequestStats: RequestStats

  def getUploadChecker: UploadCheckerLogic = UploadChecker

  // NOTE: The control no longer loads `NCName` custom MIPs during refresh. So we go to the
  // bound node instead.
  // See also https://github.com/orbeon/orbeon-forms/issues/3721.
  private def customMipForControl(controlEffectiveId: String, mipName: String) =
    controls.getCurrentControlTree.findControl(controlEffectiveId) flatMap
      CollectionUtils.collectByErasedType[XFormsSingleNodeControl]    flatMap
      (_.boundNodeOpt) flatMap (InstanceData.findCustomMip(_, mipName))

  private object UploadChecker extends UploadCheckerLogic {

    def findAttachmentMaxSizeValidationMipFor(controlEffectiveId: String) =
      customMipForControl(controlEffectiveId, ValidationFunctionNames.UploadMaxSize)

    def currentUploadSizeAggregate = {

      def evaluateAsLong(expr: CompiledExpression) =
        defaultModel match {
          case Some(m) =>
            val bindingContext = m.getDefaultEvaluationContext
            XPath.evaluateSingle(
              contextItems       = bindingContext.nodeset,
              contextPosition    = bindingContext.position,
              compiledExpression = expr,
              functionContext    = m.getContextStack.getFunctionContext(m.getEffectiveId, bindingContext),
              variableResolver   = m.variableResolver
            )(getRequestStats.getReporter).asInstanceOf[Long] // we statically ensure that the expression returns an `xs:integer`
          case None =>
            throw new AssertionError("can only evaluate dynamic properties if a model is present")
        }

      staticState.uploadMaxSizeAggregateExpression map evaluateAsLong map (0L max)
    }

    def uploadMaxSizeProperty          = staticState.uploadMaxSize
    def uploadMaxSizeAggregateProperty = staticState.uploadMaxSizeAggregate
  }

  def getUploadConstraintsForControl(controlEffectiveId: String): Try[(MaximumSize, AllowedMediatypes)] = {

    val allowedMediatypesMaybeRange = {

      def fromCommonConstraint =
        customMipForControl(controlEffectiveId, ValidationFunctionNames.UploadMediatypes) flatMap
          AllowedMediatypes.unapply

      def fromFormSetting =
        staticState.staticStringProperty(UploadMediatypesProperty).trimAllToOpt flatMap
          AllowedMediatypes.unapply

      fromCommonConstraint orElse
        fromFormSetting    getOrElse
        AllowedMediatypes.AllowedAnyMediatype
    }

    Success(UploadChecker.uploadMaxSizeForControl(controlEffectiveId) -> allowedMediatypesMaybeRange)
  }
}

trait ContainingDocumentMisc {

  self: XBLContainer =>

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

  self: XBLContainer =>

  private var eventStack: List[XFormsEvent] = Nil

  def startHandleEvent(event: XFormsEvent): Unit                = eventStack ::= event
  def endHandleEvent()                    : Unit                = eventStack = eventStack.tail
  def currentEventOpt                     : Option[XFormsEvent] = eventStack.headOption

  def withOutermostActionHandler[T](block: => T): T = {
    startOutermostActionHandler()
    val r = block
    endOutermostActionHandler()
    r
  }

  def maybeWithOutermostActionHandler[T](cond: Boolean)(block: => T): T = {
    if (cond)
      startOutermostActionHandler()
    val r = block
    if (cond)
      endOutermostActionHandler()
    r
  }

  def startOutermostActionHandler(): Unit = // Q: What about relevance?
    allModels foreach (_.startOutermostActionHandler())

  def endOutermostActionHandler(): Unit =
    if (isRelevant)
      synchronizeAndRefresh()
}

trait ContainingDocumentProperties {

  def staticState: XFormsStaticState
  def defaultModel: Option[XFormsModel]
  def getRequestStats: RequestStats

  def disableUpdates: Boolean
  def initializing: Boolean

  // Whether the document supports updates
  private lazy val _supportUpdates = ! (disableUpdates || isNoUpdates)
  def supportUpdates = _supportUpdates

  // Used by the `property()` function
  // Returns: `Int | Boolean | String`
  def getProperty(propertyName: String): Any =
    propertyName match {
      case ReadonlyAppearanceProperty => if (staticReadonly) ReadonlyAppearanceStaticValue else ReadonlyAppearanceDynamicValue
      case NoscriptProperty           => false
      case EncryptItemValuesProperty  => encodeItemValues
      case OrderProperty              => lhhacOrder // `(List[String], List[String])` FIXME
      case _                          => staticState.propertyMaybeAsExpression(propertyName).left.get // `Int | Boolean | String`
    }

  private object Memo {
    private val cache = mutable.Map.empty[String, Any]

    private def memo[T](name: String, get: => T) =
      cache.getOrElseUpdate(name, get).asInstanceOf[T]

    def staticStringProperty (name: String) = memo(name, staticState.staticStringProperty(name))
    def staticBooleanProperty(name: String) = memo(name, staticState.staticBooleanProperty(name))
    def staticIntProperty    (name: String) = memo(name, staticState.staticIntProperty(name))

    def staticBooleanProperty[T](name: String, pred: T => Boolean) =
      memo(name, pred(staticState.staticProperty(name).asInstanceOf[T]))

    def dynamicProperty[T](name: String, convert: String => T) =
      memo[T](
        name,
        convert(
          staticState.propertyMaybeAsExpression(name) match {
            case Left(value) => value.toString
            case Right(expr) => evaluateStringPropertyAVT(expr)
          }
        )
      )

    def evaluateStringPropertyAVT(expr: CompiledExpression) =
      defaultModel match {
        case Some(m) =>
          val bindingContext = m.getDefaultEvaluationContext
          XPath.evaluateAsString(
            contextItems       = bindingContext.nodeset,
            contextPosition    = bindingContext.position,
            compiledExpression = expr,
            functionContext    = m.getContextStack.getFunctionContext(m.getEffectiveId, bindingContext),
            variableResolver   = m.variableResolver
          )(getRequestStats.getReporter)
        case None =>
          throw new AssertionError("can only evaluate AVT properties if a model is present")
      }
  }

  import Memo._

  // Dynamic properties
  def staticReadonly: Boolean =
    dynamicProperty(
      ReadonlyAppearanceProperty,
      _ == ReadonlyAppearanceStaticValue
    )

  def encodeItemValues: Boolean =
    dynamicProperty(
      EncryptItemValuesProperty,
      _.toBoolean
    )

  def lhhacOrder: (List[String], List[String]) =
    dynamicProperty(
      OrderProperty,
      LHHA.getBeforeAfterOrderTokens
    )

  def staticReadonlyHint: Boolean =
    dynamicProperty(
      StaticReadonlyHintProperty,
      _.toBoolean
    )

  def staticReadonlyAlert: Boolean =
    dynamicProperty(
      StaticReadonlyAlertProperty,
      _.toBoolean
    )

  def hostLanguage: String =
    dynamicProperty(
      HostLanguage,
      identity
    )

  def isNoUpdates: Boolean =
    dynamicProperty(
      NoUpdates,
      _.toBoolean
    )

  // Static properties
  def isOptimizeGetAllSubmission            = staticBooleanProperty(OptimizeGetAllProperty)
  def isExposeXPathTypes                    = staticBooleanProperty(ExposeXpathTypesProperty)
  def isSessionHeartbeat                    = staticBooleanProperty(SessionHeartbeatProperty)
  def isXForms11Switch                      = staticBooleanProperty(Xforms11SwitchProperty)
  def isClientStateHandling                 = staticBooleanProperty[String](StateHandlingProperty, _ == StateHandlingClientValue)
  def isReadonlyAppearanceStaticSelectFull  = staticBooleanProperty[String](ReadonlyAppearanceStaticSelectProperty, _ == "full")
  def isReadonlyAppearanceStaticSelect1Full = staticBooleanProperty[String](ReadonlyAppearanceStaticSelect1Property, _ ==  "full")

  def getLabelElementName                   = staticStringProperty(LabelElementNameProperty)
  def getHintElementName                    = staticStringProperty(HintElementNameProperty)
  def getHelpElementName                    = staticStringProperty(HelpElementNameProperty)
  def getAlertElementName                   = staticStringProperty(AlertElementNameProperty)
  def getLabelAppearances                   = staticStringProperty(LabelAppearanceProperty).tokenizeToSet
  def getHintAppearances                    = staticStringProperty(HintAppearanceProperty).tokenizeToSet
  def getHelpAppearance                     = staticStringProperty(HelpAppearanceProperty)
  def getDateFormatInput                    = staticStringProperty(DateFormatInputProperty)

  def getShowMaxRecoverableErrors           = staticIntProperty(ShowRecoverableErrorsProperty)
  def getSubmissionPollDelay                = staticIntProperty(AsyncSubmissionPollDelay)
  def getAjaxFullUpdateThreshold            = staticIntProperty(AjaxUpdateFullThreshold)

  def isLocalSubmissionForward =
    staticBooleanProperty(LocalSubmissionForwardProperty) &&
    staticBooleanProperty(OptimizeLocalSubmissionReplaceAllProperty)

  import ContainingDocumentProperties._

  def getTypeOutputFormat(typeName: String) =
    SupportedTypesForOutputFormat(typeName) option
      staticStringProperty(TypeOutputFormatPropertyPrefix + typeName)

  def getTypeInputFormat(typeName: String) = {
    require(SupportedTypesForInputFormat(typeName))
    staticStringProperty(TypeInputFormatPropertyPrefix + typeName)
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
        Loggers.logger,
        Loggers.logger.isDebugEnabled && XFormsGlobalProperties.getDebugLogging.contains(loggingCategory),
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

  def staticState : XFormsStaticState

  private var _requestInformation: RequestInformation = null

  def getDeploymentType        = _requestInformation.deploymentType
  def getRequestMethod         = _requestInformation.requestMethod
  def getRequestContextPath    = _requestInformation.requestContextPath
  def getRequestPath           = _requestInformation.requestPath
  def getRequestHeaders        = _requestInformation.requestHeaders
  def getRequestParameters     = _requestInformation.requestParameters
  def getContainerType         = _requestInformation.containerType
  def getContainerNamespace    = _requestInformation.containerNamespace // always "" for servlets.
  def getVersionedPathMatchers = _requestInformation.versionedPathMatchers

  def headersGetter: String => Option[List[String]] = getRequestHeaders.get

  def isPortletContainer       = _requestInformation.containerType == "portlet"
  def embeddingTypeFromHeaders = _requestInformation.embeddingType

  def isEmbeddedFromUrlParam: Boolean =
    _requestInformation.requestParameters.get(ExternalContext.EmbeddableParam) map (_.head) contains true.toString

  def isEmbeddedFromHeaderOrUrlParam: Boolean = embeddingTypeFromHeaders.isDefined || isEmbeddedFromUrlParam

  def isServeInlineResources   = staticState.isInlineResources || _requestInformation.forceInlineResources

  // Was `protected[xforms]` but we need to call from offline. Find better solution.
  def setRequestInformation(requestInformation: RequestInformation): Unit =
    this._requestInformation = requestInformation

  def namespaceId(id: CharSequence): String =
    if (id == null)
      null
    else
      getContainerNamespace + id

  def deNamespaceId(id: String): String = {
    if (id == null)
      return null
    val containerNamespace = getContainerNamespace
    if (containerNamespace.nonEmpty && id.startsWith(containerNamespace))
      id.substring(containerNamespace.length)
    else
      id
  }

  def getNamespacedFormId: String =
    namespaceId("xforms-form")

  /**
   * Resolve a URI string against an element, taking into account ancestor xml:base attributes for
   * the resolution, and using the document's request URL as a base.
   *
   * @param element            element used to start resolution (if null, no resolution takes place)
   * @param uri                URI to resolve
   * @return resolved URI
   */
  def resolveXMLBase(element: Element, uri: String): URI =
    try
      element.resolveXMLBase(Option(getRequestPath), uri)
    catch {
      case e: IllegalArgumentException =>
        throw new ValidationException(
          s"Error while resolving URI: `$uri`",
          e,
          if (element ne null)
            element.getData.asInstanceOf[LocationData]
          else
            null
        )
    }
}

trait ContainingDocumentDelayedEvents {

  self: XBLContainer with ContainingDocumentEvent =>

  private val _delayedEvents = mutable.ListBuffer[DelayedEvent]()

  def addTwoPassSubmitEvent(p: TwoPassSubmissionParameters): Unit =
    _delayedEvents += DelayedEvent(
      eventName              = XFormsEvents.XXFORMS_SUBMIT,
      targetEffectiveId      = p.submissionEffectiveId,
      bubbles                = true,
      cancelable             = false,
      time                   = None,
      showProgress           = p.showProgress,
      browserTarget          = p.target,
      isResponseResourceType = p.isResponseResourceType
    )

  def findTwoPassSubmitEvent: Option[DelayedEvent] =
    _delayedEvents find (_.eventName == XFormsEvents.XXFORMS_SUBMIT)

  // This excludes events where `time == None`, which means it doesn't return the `xxforms-submit` event.
  def findEarliestPendingDelayedEvent: Option[DelayedEvent] =
    _delayedEvents.filter(_.time.nonEmpty).sortBy(_.time).headOption

  // Schedule an event for delayed execution, following `xf:dispatch/@delay` semantics
  def addDelayedEvent(
    eventName         : String,
    targetEffectiveId : String,
    bubbles           : Boolean,
    cancelable        : Boolean,
    time              : Long,
    showProgress      : Boolean,
    allowDuplicates   : Boolean // 2020-07-24: used by `xf:dispatch` and `false` for `xxforms-poll`
  ): Unit = {

    // For `xxforms-poll`, we *could* attempt to preserve the earlier time. But currently,
    // `addClientDelayEventIfNeeded()` for async submissions always uses a newer time at
    // each call.
    def isDuplicate(e: DelayedEvent) =
      e.eventName == eventName && e.targetEffectiveId == targetEffectiveId

    if (allowDuplicates || ! (_delayedEvents exists isDuplicate))
      _delayedEvents += DelayedEvent(
        eventName              = eventName,
        targetEffectiveId      = targetEffectiveId,
        bubbles                = bubbles,
        cancelable             = cancelable,
        time                   = time.some,
        showProgress           = showProgress,
        browserTarget          = None,
        isResponseResourceType = false
      )
  }

  def delayedEvents: List[DelayedEvent] =
    _delayedEvents.toList

  def processDueDelayedEvents(onlyEventsWithNoTime: Boolean): Unit = {

    @tailrec
    def processRemainingBatchesRecursively(): Unit = {

      // Get a fresh time for every batch because processing a batch can take time
      val currentTime = System.currentTimeMillis()

      val (dueEvents, futureEvents) =
        if (onlyEventsWithNoTime)
          delayedEvents partition (_.time.isEmpty)
        else
          delayedEvents partition (_.time exists (_ <= currentTime))

      if (dueEvents.nonEmpty) {

        _delayedEvents.clear()
        _delayedEvents ++= futureEvents

        dueEvents foreach { dueEvent =>

          withOutermostActionHandler {
            self.findObjectByEffectiveId(dueEvent.targetEffectiveId) match {
              case Some(eventTarget: XFormsEventTarget) =>
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
              case _ =>
                implicit val logger = self.containingDocument.getIndentedLogger(LOGGING_CATEGORY)
                debug(
                  "ignoring delayed event with invalid target id",
                  List(
                    "target id"  -> dueEvent.targetEffectiveId,
                    "event name" -> dueEvent.eventName
                  )
                )
            }
          }
        }

        // Try again in case there are new events available
        processRemainingBatchesRecursively()
      }
    }

    processRemainingBatchesRecursively()
  }

  protected def restoreDelayedEvents(delayedEvents: immutable.Seq[DelayedEvent]): Unit =
    _delayedEvents ++= delayedEvents // Q: Why `++=` and not just `=`?
}

trait ContainingDocumentClientState {

  self: XFormsContainingDocument =>

  private var _initialClientScript: Option[String] = None

  def initialClientScript: Option[String] =
    _initialClientScript

  def setInitialClientScript(): Unit = {

    implicit val externalContext: ExternalContext = XFormsCrossPlatformSupport.externalContext

    val response = externalContext.getResponse

    val scripts =
      ScriptBuilder.findOtherScriptInvocations(this).toList :::
      List(
        ScriptBuilder.buildJavaScriptInitialData(
          containingDocument   = this,
          rewriteResource      = response.rewriteResourceURL(_: String, UrlRewriteMode.AbsolutePathOrRelative),
          rewriteAction        = response.rewriteActionURL,
          controlsToInitialize = controls.getCurrentControlTree.rootOpt map (ScriptBuilder.gatherJavaScriptInitializations(_, includeValue = true)) getOrElse Nil,
          versionedResources   = URLRewriterUtils.isResourcesVersioned,
          heartbeatDelay       = XFormsStateManager.getHeartbeatDelay(this, externalContext)
        )
      )

    _initialClientScript = Some(scripts.fold("")(_ + _))
  }

  def clearInitialClientScript(): Unit =
    _initialClientScript = None
}

trait ContainingDocumentCacheable extends Cacheable {

  self: XFormsContainingDocument =>

  def added(): Unit =
    XFormsStateManager.onAddedToCache(uuid)

  // WARNING: This can be called while another threads owns this document lock
  def removed(): Unit =
    XFormsStateManager.onRemovedFromCache(uuid)

  // Return lock or `null` in case session just expired
  def getEvictionLock: Lock = XFormsStateManager.getDocumentLock(uuid).orNull

  // Called when cache expires this document from the document cache.
  // WARNING: This could have been called while another threads owns this document lock, but the cache now obtains
  // the lock on the document first and will not evict us if we have the lock. This means that this will be called
  // only if no thread is dealing with this document.
  def evicted(): Unit =
    XFormsStateManager.onEvictedFromCache(self)
}
