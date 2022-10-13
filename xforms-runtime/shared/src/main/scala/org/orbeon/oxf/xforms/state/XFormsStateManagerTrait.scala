package org.orbeon.oxf.xforms.state

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.{Lock, ReentrantLock}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.SessionExpiredException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.{Loggers, XFormsContainingDocument}


trait XFormsStateManagerTrait extends XFormsStateLifecycle {

  val LogType = "state manager"
  val Logger  = Loggers.getIndentedLogger("state")

  // Require implementation
  def getDocumentLock(uuid: String): Option[ReentrantLock]
  def addDocumentToSession(uuid: String): Unit

  def cacheOrStore(
    containingDocument   : XFormsContainingDocument,
    isInitialState       : Boolean,
    disableDocumentCache : Boolean // for testing only
  ): Unit

  // Return the locked document lock. Must be called before `beforeUpdate()`.
  def acquireDocumentLock(uuid: String, timeout: Long): LockResponse =
    // Check that the session is associated with the requested UUID. This enforces the rule that an incoming request
    // for a given UUID must belong to the same session that created the document. If the session expires, the
    // key goes away as well, and the key won't be present. If we don't do this check, the XForms server might
    // handle requests for a given UUID within a separate session, therefore providing access to other sessions,
    // which is not desirable. Further, we now have a lock stored in the session.
    getDocumentLock(uuid ensuring (_ ne null)) match {
      case Some(lock) =>
        try {
          if (lock.tryLock(timeout, TimeUnit.MILLISECONDS))
            LockResponse.Success(lock)
          else if (timeout == 0L)
            LockResponse.Busy
          else
            LockResponse.Timeout
        } catch {
          case e: InterruptedException =>
            LockResponse.Failure(e)
        }
      case None =>
        LockResponse.Failure(SessionExpiredException("Unknown form document requested."))
    }

  // Release the given document lock. Must be called after afterUpdate() in a finally block.
  def releaseDocumentLock(lock: Lock): Unit =
    lock.unlock()

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

  // Cache the document and/or store its current state.
  def afterUpdateResponse(containingDocument: XFormsContainingDocument): Unit =
    containingDocument.afterUpdateResponse()

  /**
    * Called before an incoming update.
    *
    * If found in cache, document is removed from cache.
    *
    * @return document, either from cache or from state information
    */
  def beforeUpdate(parameters: RequestParameters, disableDocumentCache: Boolean): Option[XFormsContainingDocument] =
    findOrRestoreDocument(
      parameters           = parameters,
      disableUpdates       = false,
      disableDocumentCache = disableDocumentCache
    )

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

  // Ideally we wouldn't want to force session creation, but it's hard to implement the more elaborate expiration
  // strategy without session.
  protected val ForceSessionCreation = true
}
