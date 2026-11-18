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

import org.orbeon.oxf.common.Version
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, NetUtils}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.event.events.XXFormsStateRestoredEvent
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector, XFormsEvent}
import org.orbeon.oxf.xforms.{Loggers, XFormsContainingDocument, XFormsContainingDocumentBuilder, XFormsGlobalProperties}

import java.util as ju
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import scala.jdk.CollectionConverters.*


object XFormsStateManager
  extends XFormsStateManagerTrait
    with  XFormsStateManagerUuidList {

  import Private.*

  private val ReplicationEnabled = XFormsGlobalProperties.isReplication

  if (ReplicationEnabled)
    Version.instance.requirePEFeature("State replication")

  def newIndentedLogger: IndentedLogger =
    Loggers.newIndentedLogger("state")

  def getDocumentLock(uuid: String): Option[ReentrantLock] =
    getSessionDocument(uuid).map(_.lock)

  def addDocumentToSession(uuid: String): Unit =
    Private.addDocumentToSession(uuid)

  def cacheOrStore(
    containingDocument   : XFormsContainingDocument,
    isInitialState       : Boolean,
    disableDocumentCache : Boolean // for testing only
  )(implicit
    indentedLogger       : IndentedLogger
  ): Unit =
    Private.cacheOrStore(containingDocument, isInitialState, disableDocumentCache)

  // Information about a document tied to the session.
  private case class SessionDocument(uuid: String) {
    val lock = new ReentrantLock
  }

  private def removeSessionDocument(uuid: String): Unit =
    getSession(false)
      .foreach(_.removeAttribute(getUUIDSessionKey(uuid), ExternalContext.SessionScope.Application))

  def onAddedToCache(uuid: String): Unit = addToUuidListInSession(uuid)

  /**
    * This is called indirectly when:
    *
    * - the session expires, which calls the session listener to remove the document from cache
    * - upon takeValid() when borrowing a document during an Ajax update
    *
    * Keep the UUID in the session's bounded UUID set so borrowed and passivated documents are still cleaned up from
    * `XFormsStateStore` when `sessionDestroyed` runs (#1990, #7572).
    */
  def onRemovedFromCache(uuid: String): Unit = ()

  // Keep `containingDocument.uuid` in the session's `BoundedSessionUuidSet` upon cache eviction so that when the HTTP
  // session expires or logs out (`sessionDestroyed`), `XFormsStateStore.removeDynamicState(uuid)` reclaims passivated
  // Infinispan heap and SIFS disk entries immediately (#1990, #7572), while bounding per-session UUID tracking memory
  // via `oxf.xforms.state.session-max-uuids`.
  def onEvictedFromCache(containingDocument: XFormsContainingDocument): Unit =
    if (containingDocument.staticState.isServerStateHandling)
      storeDocumentState(containingDocument, isInitialState = false)

  /**
    * Called after an update.
    *
    * @param keepDocument whether to keep the document around
    */
  def afterUpdate(
    containingDocument   : XFormsContainingDocument,
    keepDocument         : Boolean,
    disableDocumentCache : Boolean
  ): Unit = {
    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger("state")
    if (keepDocument) {
      // Re-add document to the cache
      debug("Keeping document in cache.")
      cacheOrStore(containingDocument, isInitialState = false, disableDocumentCache = disableDocumentCache)
    } else {
      // Don't re-add document to the cache
      debug("Not keeping document in cache following error.")
      // Remove all information about this document from the session and state store
      val uuid = containingDocument.uuid
      removeFromUuidListInSession(uuid)
      removeSessionDocument(uuid)
      XFormsDocumentCache.remove(uuid)
      XFormsStateStore.removeDynamicState(uuid)
    }
  }

  // Find or restore a document based on an incoming request.
  // NOTE: If found in cache, document is removed from cache.
  def findOrRestoreDocument(
    parameters           : RequestParameters,
    disableUpdates       : Boolean, // whether to disable updates (for recreating initial document upon browser back)
    disableDocumentCache : Boolean  // for testing only
  ): Option[XFormsContainingDocument] = {
    implicit val indentedLogger: IndentedLogger = newIndentedLogger
    // Try cache first unless the initial state is requested
    if (XFormsGlobalProperties.isCacheDocument && ! disableDocumentCache) {
      // Try to find the document in cache using the UUID
      // NOTE: If the document has cache.document="false", then it simply won't be found in the cache, but
      // we can't know that the property is set to false before trying.

      def newerSequenceNumberInStore(cachedDocument: XFormsContainingDocument): Boolean =
        ReplicationEnabled && (XFormsStateStore.findSequence(parameters.uuid) exists (_ > cachedDocument.sequence))

      XFormsDocumentCache.take(parameters.uuid) match {
        case Some(cachedDocument) if newerSequenceNumberInStore(cachedDocument)  =>
          debug("Document cache enabled. Document from cache has out of date sequence number. Retrieving state from store.")
          XFormsDocumentCache.remove(parameters.uuid)
          createDocumentFromStore(parameters, isInitialState = false, disableUpdates = disableUpdates)
        case some @ Some(_) =>
          // Found in cache
          debug("Document cache enabled. Returning document from cache.")
          some
        case None =>
          debug("Document cache enabled. Document not found in cache. Retrieving state from store.")
          createDocumentFromStore(parameters, isInitialState = false, disableUpdates = disableUpdates)
      }
    } else {
      debug("Document cache disabled. Retrieving state from store.")
      createDocumentFromStore(parameters, isInitialState = false, disableUpdates = disableUpdates)
    }
  }

  // Return the static state string to send to the client in the HTML page.
  def getClientEncodedStaticState(containingDocument: XFormsContainingDocument): Option[String] =
    containingDocument.staticState.isClientStateHandling option
      containingDocument.staticState.encodedState

  // Return the dynamic state string to send to the client in the HTML page.
  def getClientEncodedDynamicState(containingDocument: XFormsContainingDocument): Option[String] =
    containingDocument.staticState.isClientStateHandling option
      DynamicState.encodeDocumentToString(containingDocument, XFormsGlobalProperties.isGZIPState, isForceEncryption = true)

  def createInitialDocumentFromStore(parameters: RequestParameters): Option[XFormsContainingDocument] = {
    implicit val indentedLogger: IndentedLogger = newIndentedLogger
    createDocumentFromStore(
      parameters,
      isInitialState = true,
      disableUpdates = true
    )
  }

  private[state] def createDocumentFromStore(
    parameters     : RequestParameters,
    isInitialState : Boolean,
    disableUpdates : Boolean
  )(implicit
    indentedLogger: IndentedLogger
  ): Option[XFormsContainingDocument] = {

    val isServerState = parameters.encodedClientStaticStateOpt.isEmpty

//    implicit val externalContext = XFormsCrossPlatformSupport.externalContext

    getStateFromParamsOrStore(parameters, isInitialState) map { xformsState =>
      // Create document
      val documentFromStore =
      XFormsContainingDocumentBuilder(xformsState, disableUpdates, ! isServerState) ensuring { document =>
          (isServerState && document.staticState.isServerStateHandling) ||
            document.staticState.isClientStateHandling
        }

      // Dispatch event to root control. We should be able to dispatch an event to the document no? But this is not
      // possible right now.
      documentFromStore.controls.getCurrentControlTree.rootOpt foreach { rootContainerControl =>
        XFormsAPI.withContainingDocument(documentFromStore) {
          documentFromStore.withOutermostActionHandler {
            Dispatch.dispatchEvent(new XXFormsStateRestoredEvent(rootContainerControl, XFormsEvent.EmptyGetter), EventCollector.Throw)
          }
        }
      }

      documentFromStore
    }
  }

  def getStateFromParamsOrStore(
    parameters      : RequestParameters,
    isInitialState  : Boolean
  )(implicit
    indentedLogger  : IndentedLogger
  ): Option[XFormsState] = {

    val isServerState = parameters.encodedClientStaticStateOpt.isEmpty

    parameters.encodedClientDynamicStateOpt match {
      case None =>

        assert(isServerState)

        // State must be found by UUID in the store
        debug(
          "Getting document state from store.",
          List(
            "current cache size" -> XFormsDocumentCache.getCurrentSize.toString,
            "current store size" -> XFormsStateStore.getCurrentSize.map(_.toString).getOrElse("unknown"),
            "max store size"     -> XFormsStateStore.getMaxSize.map(_.toString).getOrElse("undefined")
          )
        )

        // 2014-11-12: This means that 1. We had a valid incoming session and 2. we obtained a lock on the document, yet
        // we didn't find it. This means that somehow state was not placed into or expired from the state store.
        // 2022-10-12: Changed to return `Option` instead of throwing.
        // See https://github.com/orbeon/orbeon-forms/issues/5402
        XFormsStateStore.findState(parameters.uuid, isInitialState)

      case Some(encodedClientDynamicState) =>
        // State comes directly with request

        assert(! isServerState)

        Some(XFormsState(None, parameters.encodedClientStaticStateOpt, Some(DynamicState(encodedClientDynamicState))))
    }
  }

  // Can also return `None` if no `ExternalContext` is found.
  // Q: Can that ever happen?
  // TODO: Pass implicit `Option[ExternalContext]`?
  protected def getSession(create: Boolean): Option[ExternalContext.Session] =
    Option(NetUtils.getExternalContext).flatMap(_.getSessionOpt(create))

  override protected def onSessionUuidOverflow(session: ExternalContext.Session, evictedUuid: String): Unit = {
    session.removeAttribute(Private.getUUIDSessionKey(evictedUuid), ExternalContext.SessionScope.Application)
    XFormsDocumentCache.remove(evictedUuid)
    XFormsStateStore.removeDynamicState(evictedUuid)
  }

  private object Private {

    private val XFormsStateManagerUuidKeyPrefix = "oxf.xforms.state.manager.uuid-key."

    def addDocumentToSession(uuid: String): Unit = {
      getSession(ForceSessionCreation).foreach { session =>
        session.setAttribute(getUUIDSessionKey(uuid), SessionDocument(uuid), ExternalContext.SessionScope.Application)
      }
      addToUuidListInSession(uuid)
    }

    def getSessionDocument(uuid: String): Option[SessionDocument] =
      getSession(false)
        .flatMap(_.getAttribute(getUUIDSessionKey(uuid), ExternalContext.SessionScope.Application))
        .collect { case value: SessionDocument => value }

    def getUUIDSessionKey(uuid: String): String =
      s"$XFormsStateManagerUuidKeyPrefix$uuid"

    def cacheOrStore(
      containingDocument   : XFormsContainingDocument,
      isInitialState       : Boolean,
      disableDocumentCache : Boolean // for testing only
    )(implicit
      indentedLogger       : IndentedLogger
    ): Unit = {

      if (XFormsGlobalProperties.isCacheDocument && ! disableDocumentCache) {
        // Cache the document
        debug("Document cache enabled. Putting document in cache.")
        XFormsDocumentCache.put(containingDocument)
        if ((isInitialState || ReplicationEnabled) && containingDocument.staticState.isServerStateHandling) {
          // Also store document state (used by browser soft reload, browser back, `<xf:reset>` and replication)
          debug("Storing initial document state.")
          storeDocumentState(containingDocument, isInitialState)
        }
      } else if (containingDocument.staticState.isServerStateHandling) {
        // Directly store the document state
        debug("Document cache disabled. Storing initial document state.")
        storeDocumentState(containingDocument, isInitialState)
      }

      implicit val ec: ExternalContext = CoreCrossPlatformSupport.externalContext

      LifecycleLogger.eventAssumingRequest(
        "xforms",
        "after cacheOrStore",
        List(
          "document cache current size" -> XFormsDocumentCache.getCurrentSize.toString,
          "document cache max size"     -> XFormsDocumentCache.getMaxSize.toString
        )
      )
    }

    def storeDocumentState(containingDocument: XFormsContainingDocument, isInitialState: Boolean): Unit = {
      require(containingDocument.staticState.isServerStateHandling)
      XFormsStateStore.storeDocumentState(
        containingDocument,
        isInitialState
      )
    }
  }
}

final class BoundedSessionUuidSet(val maxEntries: Int) extends Serializable {

  private val effectiveMax = math.max(1, maxEntries)
  private val entries      = new ju.LinkedHashMap[String, java.lang.Boolean](16, 0.75f, true)

  def add(uuid: String): Option[String] =
    synchronized {
      entries.put(uuid, java.lang.Boolean.TRUE)
      if (entries.size() > effectiveMax) {
        val it     = entries.keySet().iterator()
        val eldest = it.next()
        it.remove()
        Some(eldest)
      } else {
        None
      }
    }

  def remove(uuid: String): Boolean =
    synchronized {
      entries.remove(uuid) ne null
    }

  def contains(uuid: String): Boolean =
    synchronized {
      entries.containsKey(uuid)
    }

  def size: Int =
    synchronized {
      entries.size()
    }

  def snapshot: List[String] =
    synchronized {
      entries.keySet().iterator().asScala.toList
    }
}

// Handle the bounded LRU UUID set in the session.
trait XFormsStateManagerUuidList {

  protected def getSession(create: Boolean): Option[ExternalContext.Session]
  protected val ForceSessionCreation: Boolean
  protected def onSessionUuidOverflow(session: ExternalContext.Session, evictedUuid: String): Unit = ()

  private val XFormsStateManagerUUIDListKey   = "oxf.xforms.state.manager.uuid-list-key"
  private val activeSessionsCounter           = new AtomicInteger(0)

  def activeSessionsCount: Int = math.max(0, activeSessionsCounter.get())

  // This must be called once exactly when the session is created
  def sessionCreated(session: ExternalContext.Session): Unit =
    session.getAttribute(XFormsStateManagerUUIDListKey).getOrElse {
      activeSessionsCounter.incrementAndGet()
      session.setAttribute(XFormsStateManagerUUIDListKey, new BoundedSessionUuidSet(XFormsGlobalProperties.sessionMaxUuids))
    }

  // This must be called once exactly when the session is destroyed
  def sessionDestroyed(session: ExternalContext.Session): Unit =
    if (hasUuidListInSession(session)) {
      activeSessionsCounter.decrementAndGet()
      val uuids = getUuidListInSession(session).snapshot
      session.removeAttribute(XFormsStateManagerUUIDListKey, ExternalContext.SessionScope.Application)
      uuids.foreach { uuid =>
        XFormsDocumentCache.remove(uuid)
        XFormsStateStore.removeDynamicState(uuid)
      }
    }

  protected def addToUuidListInSession(uuid: String): Unit =
    getSession(ForceSessionCreation)
      .foreach { session =>
        getUuidListInSession(session).add(uuid).foreach { evictedUuid =>
          if (evictedUuid != uuid)
            onSessionUuidOverflow(session, evictedUuid)
        }
      }

  protected def removeFromUuidListInSession(uuid: String): Unit =
    getSession(ForceSessionCreation) // support missing session for tests
      .map(getUuidListInSession)
      .foreach { uuidList =>
        uuidList.remove(uuid)
      }

  // The UUID list is added once upon session creation so it is expected to be found here
  private[state]
  def getUuidListInSession(session: ExternalContext.Session): BoundedSessionUuidSet =
    session
      .getAttribute(XFormsStateManagerUUIDListKey, ExternalContext.SessionScope.Application)
      .map(_.asInstanceOf[BoundedSessionUuidSet])
      .getOrElse(throw new IllegalStateException(s"`$XFormsStateManagerUUIDListKey` was not set in the session. Check your listeners."))

  private[state]
  def hasUuidListInSession(session: ExternalContext.Session): Boolean =
    session.getAttribute(XFormsStateManagerUUIDListKey, ExternalContext.SessionScope.Application).nonEmpty
}