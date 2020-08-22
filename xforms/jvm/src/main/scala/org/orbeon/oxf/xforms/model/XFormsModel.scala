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

import java.net.URI
import java.{util => ju}

import org.orbeon.oxf.common.{OXFException, OrbeonLocationException, ValidationException}
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriter}
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.model.{Instance, Model, Submission}
import org.orbeon.oxf.xforms.control.Controls
import org.orbeon.oxf.xforms.event._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.submission.{BaseSubmission, SubmissionUtils, XFormsModelSubmission}
import org.orbeon.oxf.xforms.xbl.{Scope, XBLContainer}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.{SequenceExtent, Value}
import org.orbeon.scaxon.Implicits._
import org.orbeon.xforms.{XFormsConstants, XFormsId}

import scala.util.control.NonFatal

object XFormsModel {
  val LoggingCategory = "model"
}

class XFormsModel(
  val container   : XBLContainer,
  var effectiveId : String, // not final because can change if model within repeat iteration
  val staticModel : Model
) extends XFormsModelRebuildRecalculateRevalidate
     with XFormsModelVariables
     with XFormsModelInstances
     with XFormsModelEventTarget {

  selfModel =>

  def updateEffectiveId(effectiveId: String): Unit =
    selfModel.effectiveId = effectiveId

  val containingDocument  = container.getContainingDocument
  val sequenceNumber: Int = containingDocument.nextModelSequenceNumber()

  implicit val indentedLogger = containingDocument.getIndentedLogger(XFormsModel.LoggingCategory)

  def getResolutionScope: Scope = container.getPartAnalysis.scopeForPrefixedId(getPrefixedId)

  private val _submissions: Map[String, XFormsModelSubmission] =
    Map(
      (
        for {
          staticSubmission <- staticModel.submissions
        } yield
          (staticSubmission.staticId, new XFormsModelSubmission(selfModel.container, staticSubmission, selfModel))
      ): _*
    )

  private val _actions: Map[String, XFormsModelAction] =
    Map(
      (
        for {
          staticEventHandler <- staticModel.eventHandlers
          parent             =
            staticEventHandler.parent match {
              case Some(sp: Submission) => _submissions(sp.staticId)
              case _                    => selfModel
            }
        } yield
          (staticEventHandler.staticId, new XFormsModelAction(parent, staticEventHandler))
      ): _*
    )

  val modelBindsOpt: Option[XFormsModelBinds] = XFormsModelBinds(selfModel)

  def findObjectByEffectiveId(effectiveId: String): Option[XFormsObject] = {

    // If prefixes or suffixes don't match, object can't be found here
    if (
      ! (container.getFullPrefix == XFormsId.getEffectiveIdPrefix(effectiveId)) ||
        !(XFormsId.getEffectiveIdSuffix(container.getEffectiveId) == XFormsId.getEffectiveIdSuffix(effectiveId)))
      return None

    // Find by static id
    findObjectById(XFormsId.getStaticIdFromId(effectiveId), None)
  }

  def findObjectById(targetStaticId: String, contextItemOpt: Option[Item]): Option[XFormsObject] = {

    if (XFormsId.isEffectiveId(targetStaticId) || XFormsId.isAbsoluteId(targetStaticId))
      throw new OXFException(s"target id must be a static id: `$targetStaticId`")

    (targetStaticId == getId option selfModel) orElse
      findInstance(targetStaticId)             orElse
      _submissions.get(targetStaticId)         orElse
      _actions.get(targetStaticId)             orElse
      (modelBindsOpt flatMap (_.resolveBind(targetStaticId, contextItemOpt)))
  }

  // Restore the state of the model when the model object was just recreated
  def restoreState(deferRRR: Boolean): Unit = {

    // Ensure schema are loaded
    schemaValidator

    // Refresh binds, but do not recalculate (only evaluate "computed expression binds")
    // TODO: We used to not redo recalculations upon state restore. Does this cause a problem? Shouldn't
    // recalculations not depend on the number of times they run anyway?
    deferredActionContext.markStructuralChange(NoDefaultsStrategy, None)
    if (! deferRRR) {
      doRebuild()
      doRecalculateRevalidate()
    }
  }

  def performDefaultAction(event: XFormsEvent): Unit =
    event match {
      case ev: XFormsModelConstructEvent =>
        // 4.2.1 The xforms-model-construct Event
        // Bubbles: Yes / Cancelable: No / Context Info: None
        doModelConstruct(ev.rrr)
      case _: XFormsReadyEvent =>
        // This is called after xforms-ready events have been dispatched to all models
        doAfterReady()
      case _: XFormsModelConstructDoneEvent =>
        // 4.2.2 The xforms-model-construct-done Event
        // TODO: implicit lazy instance construction
      case _: XFormsRebuildEvent =>
        // 4.3.7 The xforms-rebuild Event
        // Bubbles: Yes / Cancelable: Yes / Context Info: None
        doRebuild()
      case _: XFormsModelDestructEvent =>
        containingDocument.xpathDependencies.modelDestruct(selfModel)
      case _: XFormsRecalculateEvent | _: XFormsRevalidateEvent =>
        // 4.3.5 The xforms-revalidate Event
        // 4.3.6 The xforms-recalculate Event
        // Recalculate and revalidate are unified
        // See https://github.com/orbeon/orbeon-forms/issues/1650
        doRecalculateRevalidate()
      case _: XFormsRefreshEvent =>
        // 4.3.4 The xforms-refresh Event
        doRefresh()
      case _: XFormsResetEvent =>
        // 4.3.8 The xforms-reset Event
        doReset()
      case ev: XFormsLinkExceptionEvent =>
        // 4.5.2 The xforms-link-exception Event
        // Bubbles: Yes / Cancelable: No / Context Info: The URI that failed to load (xsd:anyURI)
        // The default action for this event results in the following: Fatal error.
        ev.throwable match {
          case e: RuntimeException => throw e
          case t => throw new ValidationException(s"Received fatal error event: `${ev.name}`", t, getLocationData)
        }
      case ev: XXFormsXPathErrorEvent =>
        // Custom event for XPath errors
        // NOTE: We don't like this event very much as it is dispatched in the middle of rebuild/recalculate/revalidate,
        // and event handlers for this have to be careful. It might be better to dispatch it *after* RRR.
        XFormsError.handleNonFatalXPathError(container, ev.throwable)
      case ev: XXFormsBindingErrorEvent =>
        // Custom event for binding errors
        XFormsError.handleNonFatalSetvalueError(selfModel, ev.locationData, ev.reason)
      case ev: XXFormsActionErrorEvent =>
        XFormsError.handleNonFatalActionError(selfModel, ev.throwable)
      case _ => // NOP
    }

  private def doReset(): Unit = {
    // TODO
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
      case NonFatal(t) =>
        val schemaAttribute = modelElement.attributeValue(XFormsConstants.SCHEMA_QNAME)
        Dispatch.dispatchEvent(new XFormsLinkExceptionEvent(selfModel, schemaAttribute, t))
    }

    // 2. For each instance element, an XPath data model is constructed
    for ((staticInstance, instancePosition) <- staticModel.instances.values.zipWithIndex) {
      // Skip processing in case somebody has already set this particular instance
      // FIXME: can this ever happen?
      if (! isInstanceDefined(instancePosition)) {
        // Load instance. This might throw an exception event (and therefore a Java exception) in case of fatal problem.
        loadInitialInstance(staticInstance)
      }
    }

    deferredActionContext.markStructuralChange(NoDefaultsStrategy, None)

    // Custom event after instances are ready
    Dispatch.dispatchEvent(new XXFormsInstancesReadyEvent(selfModel))

    // 3. A rebuild, recalculate, and revalidate are then performed in sequence for this mode
    if (rrr) {
      doRebuild()
      doRecalculateRevalidate()
    }
  }
}

trait XFormsModelEventTarget
  extends XFormsEventTarget
     with ListenersTrait  {

  selfModel: XFormsModel =>

  def scope: Scope = staticModel.scope

  def getId: String = staticModel.staticId
  def getPrefixedId: String = staticModel.prefixedId
  def getEffectiveId: String = effectiveId

  def getLocationData: LocationData = staticModel.locationData

  // There is no point for events to propagate beyond the model
  // NOTE: This could change in the future once models are more integrated in the components hierarchy
  def parentEventObserver: XFormsEventTarget = null

  def performTargetAction(event: XFormsEvent): Unit = ()

  // Don't allow any external events
  def allowExternalEvent(eventName: String) = false
}

trait XFormsModelVariables {

  selfModel: XFormsModel =>

  private var topLevelVariables: ju.Map[String, ValueRepresentation] =
    new ju.LinkedHashMap[String, ValueRepresentation]
  def getTopLevelVariables: ju.Map[String, ValueRepresentation] = topLevelVariables

  private val contextStack: XFormsContextStack = new XFormsContextStack(container)
  def getContextStack: XFormsContextStack = contextStack

  def getVariable(variableName: String): SequenceIterator =
    Value.asIterator(topLevelVariables.get(variableName))

  def unsafeGetVariableAsNodeInfo(variableName: String): NodeInfo =
    getVariable(variableName).next().asInstanceOf[NodeInfo]

  def setTopLevelVariables(topLevelVariables: ju.Map[String, ValueRepresentation]): Unit =
    selfModel.topLevelVariables = topLevelVariables

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

  val variableResolver: (StructuredQName, XPathContext) => ValueRepresentation =
    (variableQName: StructuredQName, xpathContext: XPathContext) =>
      staticModel.bindsByName.get(variableQName.getLocalName) match {
        case Some(targetStaticBind) =>
          // Variable value is a bind nodeset to resolve
          BindVariableResolver.resolveClosestBind(
            modelBinds          = modelBindsOpt.get, // TODO XXX
            contextBindNodeOpt  = XFormsFunction.context.data.asInstanceOf[Option[BindNode]],
            targetStaticBind    = targetStaticBind
          ) map (new SequenceExtent(_)) getOrElse
            (throw new IllegalStateException)
        case None =>
          // Try top-level model variables
          val modelVariables = getDefaultEvaluationContext.getInScopeVariables
          // NOTE: With XPath analysis on, variable scope has been checked statically
          Option(modelVariables.get(variableQName.getLocalName)) getOrElse
            (throw new ValidationException("Undeclared variable in XPath expression: $" + variableQName.getClarkName, staticModel.locationData))
      }
}

trait XFormsModelInstances {

  selfModel: XFormsModel =>

  private val (_instanceIds: Seq[String], _instances: Array[XFormsInstance], _instancesMap: ju.Map[String, XFormsInstance]) =
    (
      staticModel.instances.values map (_.staticId),
      Array.ofDim[XFormsInstance](staticModel.instances.size),
      new ju.HashMap[String, XFormsInstance](staticModel.instances.size)
    )

  def isInstanceDefined(position: Int): Boolean =
    _instances(position) ne null

  def defaultInstanceOpt: Option[XFormsInstance] =
    _instances.find(_ ne null)

  // Iterate over all initialized instances
  def instancesIterator: Iterator[XFormsInstance] =
    _instances.iterator filter (_ ne null)

  // Return the XFormsInstance with given id, null if not found
  def getInstance(instanceStaticId: String): XFormsInstance =
    _instancesMap.get(instanceStaticId)

  // For Java callers
  def getDefaultInstance: XFormsInstance =
    defaultInstanceOpt.orNull

  def findInstance(instanceStaticId: String): Option[XFormsInstance] =
    Option(getInstance(instanceStaticId))

  // Return the XFormsInstance object containing the given node
  def findInstanceForNode(nodeInfo: NodeInfo): Option[XFormsInstance] =
    container.isRelevant flatOption { // NOTE: We shouldn't even be called if the parent control is not relevant.
      val documentInfo = nodeInfo.getDocumentRoot
      instancesIterator find (_.documentInfo.isSameNodeInfo(documentInfo))
    }

  def getInstanceForNode(nodeInfo: NodeInfo): XFormsInstance =
    findInstanceForNode(nodeInfo).orNull

  // Set an instance. The id of the instance must exist in the model.
  def indexInstance(instance: XFormsInstance): Unit = {
    val instanceId = instance.instance.staticId
    val instancePosition = _instanceIds.indexOf(instanceId)
    _instances(instancePosition) = instance
    _instancesMap.put(instanceId, instance)
  }

  // Restore all the instances serialized as children of the given container element.
  def restoreInstances(): Unit = {

    val instanceStatesIt =
      for {
        instanceStates <- Controls.restoringInstances.iterator
        instanceState  <- instanceStates
        if effectiveId == instanceState.modelEffectiveId  // NOTE: Here instance must contain document
      } yield
        instanceState

    instanceStatesIt foreach { state =>

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
        if _instancesMap.get(instance.staticId) eq null
      } yield
        instance

    missingInstancesIt foreach { instance =>
      setInlineInstance(instance)
    }
  }

  private def loadInstance(pathOrAbsoluteURI: String, handleXInclude: Boolean): DocumentInfo =
    SubmissionUtils.readTinyTree(
      model               = selfModel,
      resolvedAbsoluteUrl = new URI(
        URLRewriterUtils.rewriteServiceURL(
          NetUtils.getExternalContext.getRequest,
          pathOrAbsoluteURI,
          URLRewriter.REWRITE_MODE_ABSOLUTE
        )
      ),
      handleXInclude = handleXInclude
    )

  // Set instance and associated information if everything went well
  // NOTE: No XInclude supported to read instances with `@src` for now
  private def setInlineInstance(instance: Instance): Unit =
    indexInstance(XFormsInstance(selfModel, instance, instance.inlineContent))

  protected def loadInitialInstance(instance: Instance): Unit =
    withDebug("loading instance", List("instance id" -> instance.staticId)) {
      if (instance.useExternalContent) {
        // Load from `@src` or `@resource`
        loadInitialExternalInstanceFromCacheIfNeeded(instance)
      } else if (instance.useInlineContent) {
        // Load from inline content
        try setInlineInstance(instance)
        catch {
          case NonFatal(_) =>
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

  private def resolveInstanceURL(instance: Instance): String =
    XFormsUtils.resolveServiceURL(
      containingDocument,
      instance.element,
      instance.instanceSource.get,
      URLRewriter.REWRITE_MODE_ABSOLUTE
    )

  private def loadInitialExternalInstanceFromCacheIfNeeded(instance: Instance): Unit = {
    val instanceResource = instance.instanceSource.get
    try {
      if (instance.cache && ! ProcessorImpl.isProcessorInputScheme(instanceResource)) {
        // Instance 1) has cache hint and 2) is not input:*, so it can be cached
        // NOTE: We don't allow sharing for input:* URLs as the data will likely differ per request
        val caching = InstanceCaching.fromValues(instance.timeToLive, instance.handleXInclude, resolveInstanceURL(instance), null)
        val documentInfo = XFormsServerSharedInstancesCache.findContentOrLoad(instance, caching, instance.readonly, loadInstance)
        indexInstance(new XFormsInstance(selfModel, instance, Option(caching), documentInfo, instance.readonly, false, true))
      } else {
        // Instance cannot be cached
        // NOTE: Optimizing with include() for servlets has limitations, in particular
        // the proper split between servlet path and path info is not done.
        // TODO: Temporary. Use XFormsModelSubmission to load instances instead
        if (! NetUtils.urlHasProtocol(instanceResource) && containingDocument.isPortletContainer)
          throw new UnsupportedOperationException("<xf:instance src=\"\"> with relative path within a portlet")

        // Use full resolved resource URL
        // - absolute URL, e.g. http://example.org/instance.xml
        // - absolute path relative to server root, e.g. /orbeon/foo/bar/instance.xml
        loadNonCachedExternalInstance(instance)
      }
    } catch {
      case NonFatal(e) =>
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

  // Load an external instance using an absolute URL.
  private def loadNonCachedExternalInstance(instance: Instance): Unit = {

    val absoluteURLString = resolveInstanceURL(instance)

    val instanceDocument =
      containingDocument.uriResolver match {
        case None =>
          // Connect directly if there is no resolver or if the instance is globally cached
          // NOTE: If there is no resolver, URLs of the form input:* are not allowed
          assert(! ProcessorImpl.isProcessorInputScheme(absoluteURLString))

          if (indentedLogger.isDebugEnabled)
            indentedLogger.logDebug("load", "getting document from URI", "URI", absoluteURLString)

          val absoluteResolvedUrl = new URI(absoluteURLString)

          implicit val ec: ExternalContext = NetUtils.getExternalContext

          val headers = Connection.buildConnectionHeadersCapitalizedIfNeeded(
            url              = absoluteResolvedUrl,
            hasCredentials   = instance.credentials.isDefined,
            customHeaders    = Headers.EmptyHeaders,
            headersToForward = Connection.headersToForwardFromProperty,
            cookiesToForward = Nil,
            getHeader        = containingDocument.headersGetter
          )

          val connectionResult =
            Connection(
              method      = HttpMethod.GET,
              url         = absoluteResolvedUrl,
              credentials = instance.credentials,
              content     = None,
              headers     = headers,
              loadState   = true,
              logBody     = BaseSubmission.isLogBody
            ).connect(
              saveState = true
            )

          ConnectionResult.withSuccessConnection(connectionResult, closeOnSuccess = true) { is =>
            // TODO: Handle validating and XInclude!
            // Read result as XML
            // TODO: use submission code?
            if (! instance.readonly)
              Left(TransformerUtils.readDom4j(is, connectionResult.url, false, true))
            else
              Right(TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, connectionResult.url, false, true))
          }
        case Some(uriResolver) =>
          // Optimized case that uses the provided resolver
          if (indentedLogger.isDebugEnabled)
            indentedLogger.logDebug("load", "getting document from resolver", "URI", absoluteURLString)

          // TODO: Handle validating and handleXInclude!
          if (! instance.readonly)
            Left(uriResolver.readAsDom4j(absoluteURLString, instance.credentials.orNull))
          else
            Right(uriResolver.readAsTinyTree(XPath.GlobalConfiguration, absoluteURLString, instance.credentials.orNull))
      }

    indexInstance(
      XFormsInstance(
        model        = selfModel,
        instance     = instance,
        documentInfo = XFormsInstance.createDocumentInfo(instanceDocument, instance.exposeXPathTypes)
      )
    )
  }
}