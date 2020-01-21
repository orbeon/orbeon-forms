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
package org.orbeon.oxf.xforms.model

import java.net.{URI, URISyntaxException}
import java.{util ⇒ ju}

import org.orbeon.io.UriScheme
import org.orbeon.oxf.common.{OXFException, OrbeonLocationException, ValidationException}
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriter}
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.model.{Instance, Model, Submission}
import org.orbeon.oxf.xforms.control.Controls
import org.orbeon.oxf.xforms.event._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.submission.{BaseSubmission, SubmissionUtils, XFormsModelSubmission}
import org.orbeon.oxf.xforms.xbl.{Scope, XBLContainer}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData}
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.Value
import org.orbeon.xforms.XFormsId

import scala.util.control.NonFatal

object XFormsModel {
  val LoggingCategory = "model"
}

class XFormsModel(
  val container: XBLContainer,
  var effectiveId: String, // not final because can change if model within repeat iteration
  val staticModel: Model
) extends XFormsModelBase
     with XFormsEventTarget {

  selfModel ⇒

  private var topLevelVariables: ju.Map[String, ValueRepresentation] =
    new ju.LinkedHashMap[String, ValueRepresentation]

  private val (instanceIds: Seq[String], instances: Array[XFormsInstance], instancesMap: ju.Map[String, XFormsInstance]) =
    (
      staticModel.instances.values map (_.staticId),
      Array.ofDim[XFormsInstance](staticModel.instances.size),
      new ju.HashMap[String, XFormsInstance](staticModel.instances.size)
    )

  private val submissions: Map[String, XFormsModelSubmission] =
    Map(
      (
        for {
          staticSubmission ← staticModel.submissions
        } yield
          (staticSubmission.staticId, new XFormsModelSubmission(selfModel.container, staticSubmission, selfModel))
      ): _*
    )

  private val actions: Map[String, XFormsModelAction] =
    Map(
      (
        for {
          staticEventHandler <- staticModel.eventHandlers
          parent             =
            staticEventHandler.parent match {
              case Some(sp: Submission) ⇒ submissions(sp.staticId)
              case _                    ⇒ selfModel
            }
        } yield
          (staticEventHandler.staticId, new XFormsModelAction(parent, staticEventHandler))
      ): _*
    )

  // Create binds object
  private val _modelBindsOpt: Option[XFormsModelBinds] = XFormsModelBinds(selfModel)

  // Create context stack
  private val contextStack: XFormsContextStack = new XFormsContextStack(container)

  // Temporarily initialize the evaluation context to an empty context, so that handlers upon `xforms-model-construct` can work
  private var defaultEvaluationContext: BindingContext =
    XFormsContextStack.defaultContext(null, container, selfModel)

  def getDefaultEvaluationContext: BindingContext = defaultEvaluationContext

  // Evaluate all top-level variables
  def resetAndEvaluateVariables(): Unit = {
    // NOTE: This method is called during RRR and by submission processing. Need to do dependency handling.
    // Reset context to this model, including evaluating the model variables
    contextStack.resetBindingContext(selfModel)
    // Remember context and variables
    defaultEvaluationContext = contextStack.getCurrentBindingContext
  }

  // Return the value of the given model variable
  def getVariable(variableName: String): SequenceIterator =
    Value.asIterator(topLevelVariables.get(variableName))

  def unsafeGetVariableAsNodeInfo(variableName: String): NodeInfo =
    getVariable(variableName).next.asInstanceOf[NodeInfo]

  def updateEffectiveId(effectiveId: String): Unit =
    selfModel.effectiveId = effectiveId

  def geæPrefixedId: String = staticModel.prefixedId

  def getIndentedLogger: IndentedLogger = indentedLogger
  def getContextStack: XFormsContextStack = contextStack
  def getStaticModel: Model = staticModel

  def getObjectByEffectiveId(effectiveId: String): XFormsObject = {

    // If prefixes or suffixes don't match, object can't be found here
    if (
      ! (container.getFullPrefix == XFormsId.getEffectiveIdPrefix(effectiveId)) ||
        !(XFormsId.getEffectiveIdSuffix(container.getEffectiveId) == XFormsId.getEffectiveIdSuffix(effectiveId)))
      return null

    // Find by static id
    resolveObjectById(XFormsId.getStaticIdFromId(effectiveId), None)
  }

  /**
   * Resolve an object. This optionally depends on a source, and involves resolving whether the source is within a
   * repeat or a component.
   *
   * @param targetStaticId static id of the target
   * @param contextItemOpt context item, or null (used for bind resolution only)
   * @return object, or null if not found
   */
  def resolveObjectById(targetStaticId: String, contextItemOpt: Option[Item]): XFormsObject = {

    if (XFormsId.isEffectiveId(targetStaticId) || XFormsId.isAbsoluteId(targetStaticId))
      throw new OXFException(s"target id must be a static id: `$targetStaticId`")

    // Check this id
    if (targetStaticId == getId)
      return selfModel

    // Search instances
    val instance = instancesMap.get(targetStaticId)
    if (instance ne null)
      return instance

    // Search submissions
    val resultSubmission = submissions.get(targetStaticId)
    if (resultSubmission.isDefined)
      return resultSubmission.get

    // Search actions
    val action = actions.get(targetStaticId)
    if (action.isDefined)
      return action.get

    // Search binds
    if (_modelBindsOpt.isDefined) {
      val bindOpt = _modelBindsOpt.get.resolveBind(targetStaticId, contextItemOpt)
      if (bindOpt.isDefined)
        return bindOpt.get
    }

    null
  }

  def getDefaultInstance: XFormsInstance =
    defaultInstanceOpt.orNull

  def defaultInstanceOpt: Option[XFormsInstance] =
    instances.headOption

  // Iterate over all initialized instances
  def instancesIterator: Iterator[XFormsInstance] =
    instances.iterator filter (_ ne null)

  // Return the XFormsInstance with given id, null if not found
  def getInstance(instanceStaticId: String): XFormsInstance =
    instancesMap.get(instanceStaticId)

  def findInstance(instanceStaticId: String): Option[XFormsInstance] =
    Option(getInstance(instanceStaticId))

  // Return the XFormsInstance object containing the given node
  def getInstanceForNode(nodeInfo: NodeInfo): XFormsInstance = {
    val documentInfo = nodeInfo.getDocumentRoot
    // NOTE: We shouldn't even be called if the parent control is not relevant.
    if (container.isRelevant) {
      // NOTE: Some instances can be uninitialized during model construction so test for null.
      for (currentInstance <- instances) {
        if ((currentInstance ne null) && currentInstance.documentInfo.isSameNodeInfo(documentInfo))
          return currentInstance
      }
    }
    null
  }

  // Set an instance. The id of the instance must exist in the model.
  def indexInstance(instance: XFormsInstance): Unit = {
    val instanceId = instance.instance.staticId
    val instancePosition = instanceIds.indexOf(instanceId)
    instances(instancePosition) = instance
    instancesMap.put(instanceId, instance)
  }

  def scope: Scope = staticModel.scope

  def getId: String = staticModel.staticId
  def getPrefixedId: String = staticModel.prefixedId
  def getEffectiveId: String = effectiveId

  def getLocationData: LocationData = staticModel.locationData

  def getResolutionScope: Scope = container.getPartAnalysis.scopeForPrefixedId(getPrefixedId)

  def modelBindsOpt: Option[XFormsModelBinds] =
    _modelBindsOpt

  /**
   * Restore the state of the model when the model object was just recreated.
   */
  def restoreState(deferRRR: Boolean) {
    // Ensure schema are loaded
    schemaValidator

    // Refresh binds, but do not recalculate (only evaluate "computed expression binds")
    // TODO: We used to not redo recalculations upon state restore. Does this cause a problem? Shouldn't
    // recalculations not depend on the number of times they run anyway?
    deferredActionContext.jMarkStructuralChange()
    if (! deferRRR) {
      doRebuild()
      doRecalculateRevalidate()
    }
  }

  // Restore all the instances serialized as children of the given container element.
  def restoreInstances(): Unit = {

    val instanceStatesIt =
      for {
        instanceStates ← Controls.restoringInstances.iterator
        instanceState  ← instanceStates
        if effectiveId == instanceState.modelEffectiveId  // NOTE: Here instance must contain document
      } yield
        instanceState

    instanceStatesIt foreach { state ⇒

      XFormsInstance.restoreInstanceFromState(selfModel, state, loadInstance)
      indentedLogger.logDebug(
        "restore",
        "restoring instance from dynamic state",
        "model effective id", effectiveId,
        "instance effective id", state.effectiveId
      )
    }

    // Then get missing instances from static state if necessary
    // This can happen if the instance is not replaced, readonly and inline
    val missingInstancesIt =
      for {
        instance <- container.getPartAnalysis.getInstances(getPrefixedId).iterator
        if instancesMap.get(instance.staticId) eq null
      } yield
        instance

    missingInstancesIt foreach { instance ⇒
      setInlineInstance(instance)
    }
  }

  override def performDefaultAction(event: XFormsEvent): Unit = {
    val eventName = event.name
    if (XFormsEvents.XFORMS_MODEL_CONSTRUCT == eventName) {
      // 4.2.1 The xforms-model-construct Event
      // Bubbles: Yes / Cancelable: No / Context Info: None
      val modelConstructEvent = event.asInstanceOf[XFormsModelConstructEvent]
      doModelConstruct(modelConstructEvent.rrr)
    } else if (XFormsEvents.XXFORMS_READY == eventName) {
      // This is called after xforms-ready events have been dispatched to all models
      doAfterReady()
    } else if (XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE == eventName) {
      // 4.2.2 The xforms-model-construct-done Event
      // TODO: implicit lazy instance construction
    } else if (XFormsEvents.XFORMS_REBUILD == eventName) {
      // 4.3.7 The xforms-rebuild Event
      // Bubbles: Yes / Cancelable: Yes / Context Info: None
      doRebuild()
    } else if (XFormsEvents.XFORMS_MODEL_DESTRUCT == eventName)
      containingDocument.getXPathDependencies.modelDestruct(selfModel)
    else if (XFormsEvents.XFORMS_RECALCULATE == eventName) {
      // 4.3.6 The xforms-recalculate Event
      // Recalculate and revalidate are unified
      // See https://github.com/orbeon/orbeon-forms/issues/1650
      doRecalculateRevalidate()
    } else if (XFormsEvents.XFORMS_REVALIDATE == eventName) {
      // 4.3.5 The xforms-revalidate Event
      doRecalculateRevalidate()
    } else if (XFormsEvents.XFORMS_REFRESH == eventName) {
      // 4.3.4 The xforms-refresh Event
      doRefresh()
    } else if (XFormsEvents.XFORMS_RESET == eventName) {
      // 4.3.8 The xforms-reset Event
      doReset()
    } else if (XFormsEvents.XFORMS_LINK_EXCEPTION == eventName) {
      // 4.5.2 The xforms-link-exception Event
      // Bubbles: Yes / Cancelable: No / Context Info: The URI that failed to load (xsd:anyURI)
      // The default action for this event results in the following: Fatal error.
      val exceptionEvent = event.asInstanceOf[XFormsLinkExceptionEvent]
      val throwable = exceptionEvent.throwable
      throwable match {
        case e: RuntimeException ⇒ throw e
        case _ ⇒ throw new ValidationException(s"Received fatal error event: `$eventName`", throwable, getLocationData)
      }
    } else if (XFormsEvents.XXFORMS_XPATH_ERROR == eventName) {
      // Custom event for XPath errors
      // NOTE: We don't like this event very much as it is dispatched in the middle of rebuild/recalculate/revalidate,
      // and event handlers for this have to be careful. It might be better to dispatch it *after* RRR.
      val ev = event.asInstanceOf[XXFormsXPathErrorEvent]
      XFormsError.handleNonFatalXPathError(container, ev.throwable)
    } else if (XFormsEvents.XXFORMS_BINDING_ERROR == eventName) {
      // Custom event for binding errors
      val ev = event.asInstanceOf[XXFormsBindingErrorEvent]
      XFormsError.handleNonFatalSetvalueError(selfModel, ev.locationData, ev.reason)
    } else if (XFormsEvents.XXFORMS_ACTION_ERROR == eventName) {
      val ev = event.asInstanceOf[XXFormsActionErrorEvent]
      XFormsError.handleNonFatalActionError(selfModel, ev.throwable)
    }
  }

  private def doReset() { // TODO
    // "The instance data is reset to the tree structure and values it had immediately
    // after having processed the xforms-ready event."
    // "Then, the events xforms-rebuild, xforms-recalculate, xforms-revalidate and
    // xforms-refresh are dispatched to the model element in sequence."
    Dispatch.dispatchEvent(new XFormsRebuildEvent(selfModel))
    Dispatch.dispatchEvent(new XFormsRecalculateEvent(selfModel))
    Dispatch.dispatchEvent(new XFormsRefreshEvent(selfModel))
  }

  private def doAfterReady(): Unit = ()

  private def doModelConstruct(rrr: Boolean): Unit = {
    val modelElement = staticModel.element
    // 1. The XML Schemas, if any, are loaded
    try schemaValidator
    catch {
      case NonFatal(e) ⇒
        val schemaAttribute = modelElement.attributeValue(XFormsConstants.SCHEMA_QNAME)
        Dispatch.dispatchEvent(new XFormsLinkExceptionEvent(selfModel, schemaAttribute, e))
    }
    // 2. For each instance element, an XPath data model is constructed
    for ((staticInstance, instancePosition) <- staticModel.instances.values.zipWithIndex) {
      // Skip processing in case somebody has already set this particular instance
      // FIXME: can this ever happen?
      if (instances(instancePosition) eq null) {
        // Load instance. This might throw an exception event (and therefore a Java exception) in case of fatal problem.
        loadInitialInstance(staticInstance)
      }
    }
    deferredActionContext.jMarkStructuralChange()

    // Custom event after instances are ready
    Dispatch.dispatchEvent(new XXFormsInstancesReadyEvent(selfModel))

    // 3. A rebuild, recalculate, and revalidate are then performed in sequence for this mode
    if (rrr) {
      doRebuild()
      doRecalculateRevalidate()
    }
  }

  private def loadInitialInstance(instance: Instance): Unit =
    withDebug("loading instance", List("instance id" → instance.staticId)) {
      if (instance.useExternalContent) {
        // Load from `@src` or `@resource`
        loadInitialExternalInstanceFromCacheIfNeeded(instance)
      } else if (instance.useInlineContent) {
        // Load from inline content
        try setInlineInstance(instance)
        catch {
          case NonFatal(_) ⇒
            Dispatch.dispatchEvent(
              new XFormsLinkExceptionEvent(
                selfModel,
                null,
                new ValidationException(
                  "Error extracting or setting inline instance",
                  new ExtendedLocationData(
                    instance.locationData,
                    "processing XForms instance",
                    instance.element
                  )
                )
              )
            )
        }
      } else {
        // Everything missing
        Dispatch.dispatchEvent(
          new XFormsLinkExceptionEvent(
            selfModel,
            "",
            new ValidationException(
              s"Required @src attribute, @resource attribute, or inline content for instance: `${instance.staticId}`",
              new ExtendedLocationData(
                instance.locationData,
                "processing XForms instance",
                instance.element
              )
            )
          )
        )
      }
    }

  private def setInlineInstance(instance: Instance): Unit = {
    // Set instance and associated information if everything went well
    // NOTE: No XInclude supported to read instances with `@src` for now
    indexInstance(XFormsInstance(selfModel, instance, instance.inlineContent))
  }

  private def resolveInstanceURL(instance: Instance): String =
    XFormsUtils.resolveServiceURL(containingDocument, instance.element, instance.instanceSource.get, URLRewriter.REWRITE_MODE_ABSOLUTE)

  private def loadInitialExternalInstanceFromCacheIfNeeded(instance: Instance): Unit = {
    val instanceResource = instance.instanceSource.get
    try {
      if (instance.cache && ! ProcessorImpl.isProcessorInputScheme(instanceResource)) {
        // Instance 1) has cache hint and 2) is not input:*, so it can be cached
        // NOTE: We don't allow sharing for input:* URLs as the data will likely differ per request
        // TODO: This doesn't handle optimized submissions.
        val caching = InstanceCaching.fromValues(instance.timeToLive, instance.handleXInclude, resolveInstanceURL(instance), null)
        val documentInfo = XFormsServerSharedInstancesCache.findContentOrLoad(instance, caching, instance.readonly, loadInstance)
        indexInstance(new XFormsInstance(selfModel, instance, Option(caching), documentInfo, instance.readonly, false, true))
      } else {
        // Instance cannot be cached
        // NOTE: Optimizing with include() for servlets has limitations, in particular
        // the proper split between servlet path and path info is not done.
        // TODO: Temporary. Use XFormsModelSubmission to load instances instead
        if (!NetUtils.urlHasProtocol(instanceResource) && containingDocument.isPortletContainer)
          throw new UnsupportedOperationException("<xf:instance src=\"\"> with relative path within a portlet")

        // Use full resolved resource URL
        // - absolute URL, e.g. http://example.org/instance.xml
        // - absolute path relative to server root, e.g. /orbeon/foo/bar/instance.xml
        loadNonCachedExternalInstance(instance)
      }
    } catch {
      case NonFatal(e) ⇒
        Dispatch.dispatchEvent(
          new XFormsLinkExceptionEvent(
            selfModel,
            instanceResource,
            OrbeonLocationException.wrapException(
              e,
              new ExtendedLocationData(
                instance.locationData,
                "reading external instance",
                instance.element
              )
            )
          )
        )
    }
  }

  private def loadInstance(pathOrAbsoluteURI: String, handleXInclude: Boolean): DocumentInfo =
    SubmissionUtils.readTinyTree(
      model          = selfModel,
      resolvedURL    = URLRewriterUtils.rewriteServiceURL(
        NetUtils.getExternalContext.getRequest,
        pathOrAbsoluteURI,
        URLRewriter.REWRITE_MODE_ABSOLUTE
      ),
      handleXInclude = handleXInclude
    )

  // Load an external instance using an absolute URL.
  private def loadNonCachedExternalInstance(instance: Instance): Unit = {

    val absoluteURLString = resolveInstanceURL(instance)

    val instanceDocument =
      if (containingDocument.getURIResolver eq null) {
        // Connect directly if there is no resolver or if the instance is globally cached
        // NOTE: If there is no resolver, URLs of the form input:* are not allowed
        assert(! ProcessorImpl.isProcessorInputScheme(absoluteURLString))

        if (indentedLogger.isDebugEnabled)
          indentedLogger.logDebug("load", "getting document from URI", "URI", absoluteURLString)

        val absoluteResolvedURL =
          try new URI(absoluteURLString)
          catch {
            case e: URISyntaxException ⇒
              throw new OXFException(e)
          }

        implicit val ec: ExternalContext = NetUtils.getExternalContext

        val headers = Connection.buildConnectionHeadersCapitalizedIfNeeded(
          scheme           = UriScheme.withName(absoluteResolvedURL.getScheme),
          hasCredentials   = instance.credentials.isDefined,
          customHeaders    = Headers.EmptyHeaders,
          headersToForward = Connection.headersToForwardFromProperty,
          cookiesToForward = Nil,
          getHeader        = containingDocument.headersGetter
        )

        val connectionResult =
          Connection(
            method      = HttpMethod.GET,
            url         = absoluteResolvedURL,
            credentials = instance.credentials,
            content     = None,
            headers     = headers,
            loadState   = true,
            logBody     = BaseSubmission.isLogBody
          ).connect(
            saveState = true
          )

        ConnectionResult.withSuccessConnection(connectionResult, closeOnSuccess = true) { is ⇒
          // TODO: Handle validating and XInclude!
          // Read result as XML
          // TODO: use submission code?
          if (! instance.readonly)
            Left(TransformerUtils.readDom4j(is, connectionResult.url, false, true))
          else
            Right(TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, connectionResult.url, false, true))
        }
      } else {
        // Optimized case that uses the provided resolver
        if (indentedLogger.isDebugEnabled)
          indentedLogger.logDebug("load", "getting document from resolver", "URI", absoluteURLString)

        // TODO: Handle validating and handleXInclude!
        if (! instance.readonly)
          Left(containingDocument.getURIResolver.readAsDom4j(absoluteURLString, instance.credentials.orNull))
        else
          Right(containingDocument.getURIResolver.readAsTinyTree(XPath.GlobalConfiguration, absoluteURLString, instance.credentials.orNull))
      }

    indexInstance(
      XFormsInstance(
        model        = selfModel,
        instance     = instance,
        documentInfo = XFormsInstance.createDocumentInfo(instanceDocument, instance.exposeXPathTypes)
      )
    )
  }

  override def performTargetAction(event: XFormsEvent): Unit = ()

  def markValueChange(nodeInfo: NodeInfo, isCalculate: Boolean): Unit = {
    // Set the flags
    deferredActionContext.markValueChange(isCalculate)
    // Notify dependencies of the change
    if (nodeInfo ne null)
      containingDocument.getXPathDependencies.markValueChanged(selfModel, nodeInfo)
  }

  // NOP now that deferredActionContext is always created
  def startOutermostActionHandler(): Unit = ()


  def rebuildRecalculateRevalidateIfNeeded(): Unit = {
    // Process deferred behavior
    val currentDeferredActionContext = deferredActionContext

    // NOTE: We used to clear `deferredActionContext`, but this caused events to be dispatched in a different
    // order. So we are now leaving the flag as is, and waiting until they clear themselves.
    if (currentDeferredActionContext.rebuild)
      containingDocument.withOutermostActionHandler {
        Dispatch.dispatchEvent(new XFormsRebuildEvent(selfModel))
      }

    if (currentDeferredActionContext.recalculateRevalidate)
      containingDocument.withOutermostActionHandler {
        Dispatch.dispatchEvent(new XFormsRecalculateEvent(selfModel))
      }
  }

  override def parentEventObserver: XFormsEventTarget = {
    // There is no point for events to propagate beyond the model
    // NOTE: This could change in the future once models are more integrated in the components hierarchy
    null
  }

  def getTopLevelVariables: ju.Map[String, ValueRepresentation] = topLevelVariables

  def setTopLevelVariables(topLevelVariables: ju.Map[String, ValueRepresentation]): Unit = {
    selfModel.topLevelVariables = topLevelVariables
  }

  // Don't allow any external events
  override def allowExternalEvent(eventName: String) = false
}