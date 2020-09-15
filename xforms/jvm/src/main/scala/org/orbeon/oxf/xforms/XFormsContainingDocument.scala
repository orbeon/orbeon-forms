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
package org.orbeon.oxf.xforms

import cats.syntax.option._
import org.orbeon.oxf.common.{OrbeonLocationException, Version}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{IndentedLogger, SecureUtils}
import org.orbeon.oxf.xforms.XFormsProperties.NoUpdates
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.{DumbXPathDependencies, PathMapXPathDependencies, XPathDependencies}
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.control.{Controls, XFormsControl}
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.orbeon.oxf.xforms.state.{DynamicState, XFormsState, XFormsStaticStateCache}
import org.orbeon.oxf.xforms.submission.AsynchronousSubmissionManager
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.xforms.xbl.Scope

import scala.collection.Seq
import scala.util.control.NonFatal


/**
 * Represents an XForms containing document.
 *
 * The containing document:
 *
 * - Is the container for root XForms models (including multiple instances)
 * - Contains XForms controls
 * - Handles event handlers hierarchy
 */
class XFormsContainingDocument(
  val staticState    : XFormsStaticState,
  val uuid           : String,
  val disableUpdates : Boolean,
) extends XFormsContainingDocumentSupport {

  // Members that depends from `staticState`
  val functionLibrary   : FunctionLibrary      = staticState.functionLibrary
  val staticOps         : StaticStateGlobalOps = new StaticStateGlobalOps(staticState.topLevelPart)
  val xpathDependencies : XPathDependencies    =
    if (Version.isPE && staticState.isXPathAnalysis)
      new PathMapXPathDependencies(this)
    else
      new DumbXPathDependencies

  override def partAnalysis: PartAnalysis = staticState.topLevelPart

  // Aggregate other things
  val controls: XFormsControls = new XFormsControls(this)

  private var asynchronousSubmissionManager: AsynchronousSubmissionManager = null

 // Misc state
  private var _sequence = 1L // sequence number of changes to this document
  def sequence: Long = _sequence
  def incrementSequence(): Unit =
    _sequence += 1

  private var _lastAjaxResponse: Option[SAXStore] = None // last Ajax response for retry feature
  def lastAjaxResponse: Option[SAXStore] = _lastAjaxResponse

  private var controlsStructuralChanges = Set.empty[String]

  private var _uriResolver: Option[XFormsURIResolver] = None
  def uriResolver: Option[XFormsURIResolver] = _uriResolver

  private var _response: Option[ExternalContext.Response] = None
  def response: Option[ExternalContext.Response] = _response

  private var _initializing = false
  def initializing: Boolean = _initializing

  /**
   * Whether the document is currently in a mode where it must remember differences. This is the case when:
   *
   * - the document is currently handling an update (as opposed to initialization)
   * - the property "no-updates" is false (the default)
   * - the document is
   *
   * @return true iif the document must handle differences
   */
  def isHandleDifferences: Boolean = ! initializing && supportUpdates

  def getControlByEffectiveId(effectiveId: String): XFormsControl =
    controls.getObjectByEffectiveId(effectiveId)

  def findControlByEffectiveId(effectiveId: String): Option[XFormsControl] =
    Option(getControlByEffectiveId(effectiveId))

  def isDirtySinceLastRequest: Boolean = controls.isDirtySinceLastRequest

  def initialize(
    uriResolver : Option[XFormsURIResolver],
    response    : Option[ExternalContext.Response]
  ): Unit =
    XFormsAPI.withContainingDocument(this) { // for the XForms API

      // These are cleared in `afterInitialResponse()`
      this._uriResolver  = uriResolver
      this._response     = response
      this._initializing = true

      addAllModels()

      // Group all `xforms-model-construct-done` and `xforms-ready` events within a single outermost action handler in
      // order to optimize events. Perform deferred updates only for `xforms-ready`.
      withOutermostActionHandler {
        initializeModels()
        processCompletedAsynchronousSubmissions(skipDeferredEventHandling = true, addPollEvent = true)
      }

      processDueDelayedEvents(false)
    }

  def restoreDynamicState(dynamicState: DynamicState): Unit = {

    this._sequence = dynamicState.sequence

    indentedLogger.logDebug("initialization", "restoring dynamic state for UUID", "UUID", this.uuid, "sequence", this._sequence.toString)

    // Restore request information
    restoreRequestInformation(dynamicState)
    restorePathMatchers(dynamicState)

    // Restore other encoded objects
    restoreDelayedEvents(dynamicState)
    this.pendingUploads = dynamicState.decodePendingUploads
    this._lastAjaxResponse = dynamicState.decodeLastAjaxResponse

    XFormsAPI.withContainingDocument(this) {
      Controls.withDynamicStateToRestore(dynamicState.decodeInstancesControls) {

        // Restore models state
        addAllModels()

        // Restore top-level models state, including instances
        restoreModelsState(deferRRR = false)

        // Restore controls state
        // Store serialized control state for retrieval later
        controls.createControlTree(Controls.restoringControls)

        // Once the control tree is rebuilt, restore focus if needed
        dynamicState.focusedControl foreach { focusedControl =>
          controls.setFocusedControl(controls.getCurrentControlTree.findControl(focusedControl))
        }
      }
    }
  }

  override def findObjectByEffectiveId(effectiveId: String): Option[XFormsObject] = {

    findControlByEffectiveId(effectiveId)        orElse // controls first because that's the fast way
      super.findObjectByEffectiveId(effectiveId) orElse // parent (models and this)
      (effectiveId == getEffectiveId option this)       // container id

    // TODO: "container id" check above should no longer be needed since we have a root control, right? In which case, the document would
    // no longer need to be an `XFormsObject`.
  }

  private def clearClientState(): Unit = {

    assert(! _initializing)
    assert(_response.isEmpty)
    assert(_uriResolver.isEmpty)

    clearRequestStats()
    clearTransientState()

    this.controlsStructuralChanges = Set.empty
  }

  def getControlsStructuralChanges: Set[String] =
    controlsStructuralChanges

  def addControlStructuralChange(prefixedId: String): Unit =
    this.controlsStructuralChanges += prefixedId

  // Do it here because at construction time, we don't yet have access to the static state!
  override val innerScope: Scope =
    staticState.topLevelPart.startScope

  def afterInitialResponse(): Unit = {

    getRequestStats.afterInitialResponse()

    this._uriResolver  = None // URI resolver is of no use after initialization and it may keep dangerous references (`PipelineContext`)
    this._response     = None // same as above
    this._initializing = false

    // Do this before clearing the client state
    if (! staticState.isInlineResources)
      setInitialClientScript()

    clearClientState() // client state can contain e.g. focus information, etc. set during initialization

    xpathDependencies.afterInitialResponse()
  }

  override def beforeExternalEvents(response: ExternalContext.Response, isAjaxRequest: Boolean): Unit = {

    xpathDependencies.beforeUpdateResponse()
    this._response = response.some

    if (isAjaxRequest) {
      processCompletedAsynchronousSubmissions(skipDeferredEventHandling = false, addPollEvent = false)
      processDueDelayedEvents(false)
    } else
      processDueDelayedEvents(true)
  }

  def afterExternalEvents(isAjaxRequest: Boolean): Unit = {

    if (isAjaxRequest) {
      processCompletedAsynchronousSubmissions(skipDeferredEventHandling = false, addPollEvent = true)
      processDueDelayedEvents(false)
    }

    this._response = None
  }

  def afterUpdateResponse(): Unit = {

    getRequestStats.afterUpdateResponse()

    if (!staticState.isInlineResources)
      clearInitialClientScript()

    clearClientState()
    controls.afterUpdateResponse()
    xpathDependencies.afterUpdateResponse()
  }

  def rememberLastAjaxResponse(response: SAXStore): Unit =
    _lastAjaxResponse = response.some

  def getAsynchronousSubmissionManager(create: Boolean): AsynchronousSubmissionManager = {
    if (asynchronousSubmissionManager == null && create)
      asynchronousSubmissionManager = new AsynchronousSubmissionManager(this)
    asynchronousSubmissionManager
  }

  private def processCompletedAsynchronousSubmissions(skipDeferredEventHandling: Boolean, addPollEvent: Boolean): Unit = {
    val manager = getAsynchronousSubmissionManager(false)
    if (manager != null && manager.hasPendingAsynchronousSubmissions) {

      maybeWithOutermostActionHandler(! skipDeferredEventHandling) {
        manager.processCompletedAsynchronousSubmissions()
      }

      // Remember to send a poll event if needed
      if (addPollEvent)
        manager.addClientDelayEventIfNeeded()
    }
  }

  override def initializeNestedControls(): Unit = {
    // Call-back from super class models initialization

    // This is important because if controls use binds, those must be up to date. In addition, MIP values will be up
    // to date. Finally, upon receiving xforms-ready after initialization, it is better if calculations and
    // validations are up to date.
    rebuildRecalculateRevalidateIfNeeded()

    // Initialize controls
    controls.createControlTree(None)
  }

  override def getChildrenControls(controls: XFormsControls): Seq[XFormsControl] =
    controls.getCurrentControlTree.children

  // TODO: move to trait
  private var pendingUploads = Set.empty[String]
  def getPendingUploads: Set[String] = pendingUploads

  /**
   * Register that an upload has started.
   */
  def startUpload(uploadId: String): Unit =
    pendingUploads += uploadId

  /**
   * Register that an upload has ended.
   */
  def endUpload(uploadId: String): Unit = {
    // NOTE: Don't enforce existence of upload, as this is also called if upload control becomes non-relevant, and
    // also because asynchronously if the client notifies us to end an upload after a control has become non-relevant,
    // we don't want to fail.
    pendingUploads -= uploadId
  }

  /**
   * Return the number of pending uploads.
   */
  def countPendingUploads: Int = pendingUploads.size

  /**
   * Whether an upload is pending for the given upload control.
   */
  def isUploadPendingFor(uploadControl: XFormsUploadControl): Boolean =
    pendingUploads.contains(uploadControl.getUploadUniqueId)
}

object XFormsContainingDocument {

  /**
   * Create an `XFormsContainingDocument` from an `XFormsStaticState` object.
   *
   * Used by `XFormsToXHTML` and tests.
   *
   * @param staticState     static state object
   * @param uriResolver     for loading instances during initialization (and possibly more, such as schemas and `GET` submissions upon initialization)
   * @param response        optional response for handling `replace="all"` during initialization
   * @param mustInitialize  initialize document (`false` for testing only)
   */
  def apply(
    staticState    : XFormsStaticState,
    uriResolver    : Option[XFormsURIResolver],
    response       : Option[ExternalContext.Response],
    mustInitialize : Boolean
  ): XFormsContainingDocument =
    try {
      val uuid = SecureUtils.randomHexId

      // attempt to ignore `oxf:xforms-submission`
      if (staticState.propertyMaybeAsExpression(NoUpdates).fold(_.toString != "true" , _ => true))
        LifecycleLogger.eventAssumingRequest("xforms", "new form session", List("uuid" -> uuid))

      val doc = new XFormsContainingDocument(staticState, uuid, disableUpdates = false)
      implicit val logger = doc.indentedLogger
      withDebug("initialization: creating new ContainingDocument (static state object provided).", List("uuid" -> uuid)) {

        doc.initializeRequestInformation()
        doc.initializePathMatchers()

        if (mustInitialize)
          doc.initialize(uriResolver, response)
      }
      doc
    } catch {
      case NonFatal(t) =>
        throw OrbeonLocationException.wrapException(t, XmlExtendedLocationData(null, "initializing XForms containing document".some))
    }

  /**
   * Restore an `XFormsContainingDocument` from `XFormsState` only.
   *
   * Used by `XFormsStateManager`.
   *
   * @param xformsState    XFormsState containing static and dynamic state
   * @param disableUpdates whether to disable updates (for recreating initial document upon browser back)
   */
  def apply(
    xformsState     : XFormsState,
    disableUpdates  : Boolean,
    forceEncryption : Boolean)(
    indentedLogger  : IndentedLogger
  ): XFormsContainingDocument =
    try {
      // 1. Restore the static state
      val staticState = findOrRestoreStaticState(xformsState, forceEncryption)(indentedLogger)

      // 2. Restore the dynamic state
      val dynamicState = xformsState.dynamicState getOrElse (throw new IllegalStateException)

      val doc = new XFormsContainingDocument(staticState, dynamicState.uuid, disableUpdates)
      implicit val logger = doc.indentedLogger
      withDebug("initialization: restoring containing document") {
        doc.restoreDynamicState(dynamicState)
      }
      doc
    } catch {
      case NonFatal(t) =>
        throw OrbeonLocationException.wrapException(t, XmlExtendedLocationData(null, "re-initializing XForms containing document".some))
    }

  private def findOrRestoreStaticState(
    xformsState     : XFormsState,
    forceEncryption : Boolean)(implicit
    indentedLogger  : IndentedLogger
  ): XFormsStaticState =
    xformsState.staticStateDigest match {
      case digestOpt @ Some(digest) =>
        (XFormsStaticStateCache.findDocument(digest) match {
          case Some((cachedState, _)) =>
            // Found static state in cache
            debug("found static state by digest in cache")
            cachedState
          case _ =>
            // Not found static state in cache, create static state from input
            debug("did not find static state by digest in cache")
            val restoredStaticState =
              withDebug("initialization: restoring static state") {
                XFormsStaticStateImpl.restore(
                  digest          = digestOpt,
                  encodedState    = xformsState.staticState getOrElse (throw new IllegalStateException),
                  forceEncryption = forceEncryption
                )
              }
            // Store in cache
            XFormsStaticStateCache.storeDocument(restoredStaticState)
            restoredStaticState
        }) ensuring (_.isServerStateHandling)
      case digestOpt @ None =>
        // Not digest provided, create static state from input
        debug("did not find static state by digest in cache")
        XFormsStaticStateImpl.restore(
          digest          = digestOpt,
          encodedState    = xformsState.staticState getOrElse (throw new IllegalStateException),
          forceEncryption = forceEncryption
        ) ensuring (_.isClientStateHandling)
    }
}