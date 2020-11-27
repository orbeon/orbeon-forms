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
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, PathMatcher}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.{DumbXPathDependencies, PartAnalysis, PathMapXPathDependencies, XPathDependencies}
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.control.{Controls, XFormsControl}
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.orbeon.oxf.xforms.state.{ControlState, InstancesControls}
import org.orbeon.oxf.xforms.submission.AsynchronousSubmissionManager
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.xforms.{DelayedEvent, DeploymentType}
import org.orbeon.xforms.runtime.XFormsObject
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.XFormsCrossPlatformSupport


import scala.collection.{Seq, immutable}


case class RequestInformation(
  deploymentType             : DeploymentType,
  requestMethod              : HttpMethod,
  requestContextPath         : String,
  requestPath                : String,
  requestHeaders             : Map[String, List[String]],
  requestParameters          : Map[String, List[String]],
  containerType              : String,
  containerNamespace         : String,
  versionedPathMatchers      : List[PathMatcher],
  isEmbedded                 : Boolean,
  isPortletContainerOrRemote : Boolean
)

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
  val functionLibrary   : FunctionLibrary      = staticState.topLevelPart.functionLibrary
  val staticOps         : StaticStateGlobalOps = new StaticStateGlobalOps(staticState.topLevelPart)
  val xpathDependencies : XPathDependencies    =
    if (CoreCrossPlatformSupport.isPE && staticState.isXPathAnalysis)
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

  def restoreDynamicState(
    sequence           : Long,
    delayedEvents      : immutable.Seq[DelayedEvent],
    pendingUploads     : Set[String],
    lastAjaxResponse   : Option[SAXStore],
    decodeControls     : InstancesControls,
    focusedControl     : Option[String],
    requestInformation : RequestInformation
  ): Unit = {

    this._sequence = sequence

    indentedLogger.logDebug("initialization", "restoring dynamic state for UUID", "UUID", this.uuid, "sequence", this._sequence.toString)

    // Restore request information
    setRequestInformation(requestInformation)

    // Restore other encoded objects
    restoreDelayedEvents(delayedEvents)
    this.pendingUploads = pendingUploads
    this._lastAjaxResponse = lastAjaxResponse

    XFormsAPI.withContainingDocument(this) {
      Controls.withDynamicStateToRestore(decodeControls) {

        // Restore models state
        addAllModels()

        // Restore top-level models state, including instances
        restoreModelsState(deferRRR = false)

        // Restore controls state
        // Store serialized control state for retrieval later
        controls.createControlTree(Controls.restoringControls)

        // Once the control tree is rebuilt, restore focus if needed
        focusedControl foreach { focusedControl =>
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

    if (! staticState.isInlineResources)
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
