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
package org.orbeon.oxf.xforms.state

import java.util.concurrent.locks.{Lock, ReentrantLock}
import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}

import org.orbeon.dom.Document
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.SessionExpiredException
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.event.events.XXFormsStateRestoredEvent
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.{Loggers, XFormsConstants, XFormsContainingDocument, XFormsProperties}

import scala.collection.JavaConverters._

object XFormsStateManager extends XFormsStateLifecycle {

  import Private._

  private val ReplicationEnabled = XFormsProperties.isReplication

  if (ReplicationEnabled)
     Version.instance.requirePEFeature("State replication")

  // This must be called once exactly when the session is created
  def sessionCreated(session: ExternalContext.Session): Unit =
    session.getAttribute(XFormsStateManagerUUIDListKey) getOrElse {
      session.setAttribute(XFormsStateManagerUUIDListKey, new ConcurrentLinkedQueue[String])
    }

  // This must be called once exactly when the session is destroyed
  def sessionDestroyed(session: ExternalContext.Session): Unit =
    getOrCreateUuidListInSession(session).iterator.asScala foreach { uuid =>
      XFormsDocumentCache.remove(uuid)
      EhcacheStateStore.removeDynamicState(uuid)
    }

  val LogType = "state manager"
  val Logger  = Loggers.getIndentedLogger("state")

  // For Java callers
  def instance = XFormsStateManager

  // Information about a document tied to the session.
  case class SessionDocument(uuid: String) {
    val lock = new ReentrantLock
  }

  // Keep public and static for unit tests and submission processor (called from XSLT)
  def removeSessionDocument(uuid: String): Unit = {
    val session = NetUtils.getSession(false)
    if (session ne null) {
      session.removeAttribute(getUUIDSessionKey(uuid), ExternalContext.SessionScope.Application)
    }
  }

  def getRequestUUID(request: Document): String = {
    val uuidElement = request.getRootElement.element(XFormsConstants.XXFORMS_UUID_QNAME)
    assert(uuidElement != null)
    uuidElement.getTextTrim.trimAllToNull
  }

  def getDocumentLock(uuid: String): Option[ReentrantLock] =
    getSessionDocument(uuid) map (_.lock)

  def getDocumentLockOrNull(uuid: String) =
    getDocumentLock(uuid).orNull

  /**
    * Return the delay for the session heartbeat event.
    *
    * @return delay in ms, or -1 is not applicable
    */
  def getHeartbeatDelay(containingDocument: XFormsContainingDocument, externalContext: ExternalContext): Long =
    if (containingDocument.staticState.isClientStateHandling || ! containingDocument.isSessionHeartbeat) {
      -1L
    } else {
      // 80% of session expiration time, in ms
      externalContext.getRequest.getSession(ForceSessionCreation).getMaxInactiveInterval * 800
    }

  /**
    * Called after the initial response is sent without error.
    *
    * Implementation: cache the document and/or store its initial state.
    */
  def afterInitialResponse(
    containingDocument   : XFormsContainingDocument,
    disableDocumentCache : Boolean
  ): Unit =
    if (! containingDocument.isNoUpdates) {
      addDocumentToSession(containingDocument.uuid)
      cacheOrStore(containingDocument, isInitialState = true, disableDocumentCache = disableDocumentCache)
    }

  def onAddedToCache(uuid: String): Unit = addUuidToSession(uuid)

  /**
    * This is called indirectly when:
    *
    * - the session expires, which calls the session listener above to remove the document from cache
    * - upon takeValid()
    * - nobody else is supposed to call remove() or removeAll() on the cache
    */
  // WARNING: This can be called while another threads owns this document lock
  def onRemovedFromCache(uuid: String): Unit =
    removeUuidFromSession(uuid)

  // WARNING: This could have been called while another threads owns this document lock, but the cache now obtains
  // the lock on the document first and will not evict us if we have the lock. This means that this will be called
  // only if no thread is dealing with this document.
  // Remove session listener for cache
  def onEvictedFromCache(containingDocument: XFormsContainingDocument): Unit = {
    removeUuidFromSession(containingDocument.uuid)
    // Store document state
    if (containingDocument.staticState.isServerStateHandling)
      storeDocumentState(containingDocument, isInitialState = false)
  }

  /**
    * Return the locked document lock. Must be called bef~ore beforeUpdate().
    *
    * @param uuid incoming UUID
    * @return the document lock, already locked
    */
  def acquireDocumentLock(uuid: String, timeout: Long): Option[Lock] = {
    assert(uuid ne null)
    // Check that the session is associated with the requested UUID. This enforces the rule that an incoming request
    // for a given UUID must belong to the same session that created the document. If the session expires, the
    // key goes away as well, and the key won't be present. If we don't do this check, the XForms server might
    // handle requests for a given UUID within a separate session, therefore providing access to other sessions,
    // which is not desirable. Further, we now have a lock stored in the session.
    val lock =
      getDocumentLock(uuid) getOrElse
        (throw SessionExpiredException("Unknown form document requested."))

    // Lock document for at most the max retry delay plus an increment
    try {
      lock.tryLock(timeout, TimeUnit.MILLISECONDS) option lock
    } catch {
      case e: InterruptedException =>
        throw new OXFException(e)
    }
  }

  // Release the given document lock. Must be called after afterUpdate() in a finally block.
  def releaseDocumentLock(lock: Lock): Unit =
    lock.unlock()

  /**
    * Called before an incoming update.
    *
    * If found in cache, document is removed from cache.
    *
    * @return document, either from cache or from state information
    */
  def beforeUpdate(parameters: RequestParameters, disableDocumentCache: Boolean): XFormsContainingDocument =
    findOrRestoreDocument(
      parameters           = parameters,
      disableUpdates       = false,
      disableDocumentCache = disableDocumentCache
    )

  /**
    * Called after an update.
    *
    * @param keepDocument whether to keep the document around
    */
  def afterUpdate(
    containingDocument   : XFormsContainingDocument,
    keepDocument         : Boolean,
    disableDocumentCache : Boolean
  ): Unit =
    if (keepDocument) {
      // Re-add document to the cache
      Logger.logDebug(LogType, "Keeping document in cache.")
      cacheOrStore(containingDocument, isInitialState = false, disableDocumentCache = disableDocumentCache)
    } else {
      // Don't re-add document to the cache
      Logger.logDebug(LogType, "Not keeping document in cache following error.")
      // Remove all information about this document from the session
      val uuid = containingDocument.uuid
      removeUuidFromSession(uuid)
      removeSessionDocument(uuid)
    }

  // Find or restore a document based on an incoming request.
  // NOTE: If found in cache, document is removed from cache.
  def findOrRestoreDocument(
    parameters           : RequestParameters,
    disableUpdates       : Boolean, // whether to disable updates (for recreating initial document upon browser back)
    disableDocumentCache : Boolean  // for testing only
  ): XFormsContainingDocument =
    // Try cache first unless the initial state is requested
    if (XFormsProperties.isCacheDocument && ! disableDocumentCache) {
      // Try to find the document in cache using the UUID
      // NOTE: If the document has cache.document="false", then it simply won't be found in the cache, but
      // we can't know that the property is set to false before trying.

      def newerSequenceNumberInStore(cachedDocument: XFormsContainingDocument) =
        ReplicationEnabled && (EhcacheStateStore.findSequence(parameters.uuid) exists (_ > cachedDocument.sequence))

      XFormsDocumentCache.take(parameters.uuid) match {
        case Some(cachedDocument) if newerSequenceNumberInStore(cachedDocument)  =>
          Logger.logDebug(LogType, "Document cache enabled. Document from cache has out of date sequence number. Retrieving state from store.")
          XFormsDocumentCache.remove(parameters.uuid)
          createDocumentFromStore(parameters, isInitialState = false, disableUpdates = disableUpdates)
        case Some(cachedDocument) =>
          // Found in cache
          Logger.logDebug(LogType, "Document cache enabled. Returning document from cache.")
          cachedDocument
        case None =>
          Logger.logDebug(LogType, "Document cache enabled. Document not found in cache. Retrieving state from store.")
          createDocumentFromStore(parameters, isInitialState = false, disableUpdates = disableUpdates)
      }
    } else {
      Logger.logDebug(LogType, "Document cache disabled. Retrieving state from store.")
      createDocumentFromStore(parameters, isInitialState = false, disableUpdates = disableUpdates)
    }

  // Return the static state string to send to the client in the HTML page.
  def getClientEncodedStaticState(containingDocument: XFormsContainingDocument): Option[String] =
    containingDocument.staticState.isClientStateHandling option
      containingDocument.staticState.encodedState

  // Return the dynamic state string to send to the client in the HTML page.
  def getClientEncodedDynamicState(containingDocument: XFormsContainingDocument): Option[String] =
    containingDocument.staticState.isClientStateHandling option
      DynamicState.encodeDocumentToString(containingDocument, XFormsProperties.isGZIPState, isForceEncryption = true)

  // Update the document's change sequence.
  def beforeUpdateResponse(containingDocument: XFormsContainingDocument, ignoreSequence: Boolean): Unit = {
    if (containingDocument.isDirtySinceLastRequest) {
      Logger.logDebug(LogType, "Document is dirty. Generating new dynamic state.")
    } else {
      // The document is not dirty: no real encoding takes place here
      Logger.logDebug(LogType, "Document is not dirty. Keep existing dynamic state.")
    }
    // Tell the document to update its state
    if (! ignoreSequence)
      containingDocument.incrementSequence()
  }

  // Cache the document and/or store its current state.
  def afterUpdateResponse(containingDocument: XFormsContainingDocument): Unit =
    containingDocument.afterUpdateResponse()

  // The UUID list is added once upon session creation so it is expected to be found here
  def getOrCreateUuidListInSession(session: ExternalContext.Session): ConcurrentLinkedQueue[String] =
    session.getAttribute(XFormsStateManagerUUIDListKey, ExternalContext.SessionScope.Application) map
      (_.asInstanceOf[ConcurrentLinkedQueue[String]]) getOrElse
      (throw new IllegalStateException(s"`$XFormsStateManagerUUIDListKey` was not set in the session. Check your listeners."))

  def createDocumentFromStore(
    parameters     : RequestParameters,
    isInitialState : Boolean,
    disableUpdates : Boolean
  ): XFormsContainingDocument = {

    val isServerState = parameters.encodedClientStaticStateOpt.isEmpty

    implicit val externalContext = NetUtils.getExternalContext

    val xformsState = getStateFromParamsOrStore(parameters, isInitialState)

    // Create document
    val documentFromStore =
      XFormsContainingDocument(xformsState, disableUpdates, ! isServerState)(Logger) ensuring { document =>
        (isServerState && document.staticState.isServerStateHandling) ||
          document.staticState.isClientStateHandling
      }

    // Dispatch event to root control. We should be able to dispatch an event to the document no? But this is not
    // possible right now.
    documentFromStore.controls.getCurrentControlTree.rootOpt foreach { rootContainerControl =>
      XFormsAPI.withContainingDocument(documentFromStore) {
        documentFromStore.withOutermostActionHandler {
          Dispatch.dispatchEvent(new XXFormsStateRestoredEvent(rootContainerControl, XFormsEvent.EmptyGetter))
        }
      }
    }

    documentFromStore
  }

  def getStateFromParamsOrStore(
    parameters      : RequestParameters,
    isInitialState  : Boolean)(implicit
    externalContext : ExternalContext
  ): XFormsState = {

    val isServerState = parameters.encodedClientStaticStateOpt.isEmpty

    parameters.encodedClientDynamicStateOpt match {
      case None =>

        assert(isServerState)

        // State must be found by UUID in the store
        if (Logger.isDebugEnabled)
          Logger.logDebug(
            LogType,
            "Getting document state from store.",
            "current cache size", XFormsDocumentCache.getCurrentSize.toString,
            "current store size", EhcacheStateStore.getCurrentSize.toString,
            "max store size", EhcacheStateStore.getMaxSize.toString
          )

        val session = externalContext.getRequest.getSession(ForceSessionCreation)
        EhcacheStateStore.findState(session, parameters.uuid, isInitialState) getOrElse {
          // 2014-11-12: This means that 1. We had a valid incoming session and 2. we obtained a lock on the
          // document, yet we didn't find it. This means that somehow state was not placed into or expired from
          // the state store.
          throw SessionExpiredException("Unable to retrieve XForms engine state. Unable to process incoming request.")
        }
      case Some(encodedClientDynamicState) =>
        // State comes directly with request

        assert(! isServerState)

        XFormsState(None, parameters.encodedClientStaticStateOpt, Some(DynamicState(encodedClientDynamicState)))
    }
  }

  private object Private {

    // Ideally we wouldn't want to force session creation, but it's hard to implement the more elaborate expiration
    // strategy without session.
    val ForceSessionCreation = true

    val XFormsStateManagerUuidKeyPrefix = "oxf.xforms.state.manager.uuid-key."
    val XFormsStateManagerUUIDListKey   = "oxf.xforms.state.manager.uuid-list-key"

    def addDocumentToSession(uuid: String): Unit = {
      val session = NetUtils.getSession(ForceSessionCreation)
      session.setAttribute(getUUIDSessionKey(uuid), SessionDocument(uuid), ExternalContext.SessionScope.Application)
    }

    def getSessionDocument(uuid: String): Option[SessionDocument] =
      Option(NetUtils.getSession(false)) flatMap { session =>
        session.getAttribute(getUUIDSessionKey(uuid), ExternalContext.SessionScope.Application)
      } collect {
        case value: SessionDocument => value
      }

    def getUUIDSessionKey(uuid: String) =
      XFormsStateManagerUuidKeyPrefix + uuid

    // Tricky: if `onRemove()` is called upon session expiration, there might not be an `ExternalContext`. But it's fine,
    // because the session goes away -> all of its attributes go away so we don't have to remove them below.
    def removeUuidFromSession(uuid: String): Unit =
      Option(NetUtils.getSession(ForceSessionCreation)) map // support missing session for tests
        getOrCreateUuidListInSession                    foreach
        (_.remove(uuid))

    def cacheOrStore(
      containingDocument   : XFormsContainingDocument,
      isInitialState       : Boolean,
      disableDocumentCache : Boolean // for testing only
    ): Unit = {

      if (XFormsProperties.isCacheDocument && ! disableDocumentCache) {
        // Cache the document
        Logger.logDebug(LogType, "Document cache enabled. Putting document in cache.")
        XFormsDocumentCache.put(containingDocument)
        if ((isInitialState || ReplicationEnabled) && containingDocument.staticState.isServerStateHandling) {
          // Also store document state (used by browser soft reload, browser back, `<xf:reset>` and replication)
          Logger.logDebug(LogType, "Storing initial document state.")
          storeDocumentState(containingDocument, isInitialState)
        }
      } else if (containingDocument.staticState.isServerStateHandling) {
        // Directly store the document state
        Logger.logDebug(LogType, "Document cache disabled. Storing initial document state.")
        storeDocumentState(containingDocument, isInitialState)
      }

      LifecycleLogger.eventAssumingRequest(
        "xforms",
        "after cacheOrStore",
        List(
          "document cache current size" -> XFormsDocumentCache.getCurrentSize.toString,
          "document cache max size"     -> XFormsDocumentCache.getMaxSize.toString
        )
      )
    }

    def addUuidToSession(uuid: String): Boolean =
      getOrCreateUuidListInSession(NetUtils.getSession(ForceSessionCreation)).add(uuid)

    def storeDocumentState(containingDocument: XFormsContainingDocument, isInitialState: Boolean): Unit = {
      require(containingDocument.staticState.isServerStateHandling)
      EhcacheStateStore.storeDocumentState(
        containingDocument,
        NetUtils.getExternalContext.getRequest.getSession(ForceSessionCreation),
        isInitialState
      )
    }
  }
}