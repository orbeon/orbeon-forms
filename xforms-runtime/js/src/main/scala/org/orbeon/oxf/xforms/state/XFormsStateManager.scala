package org.orbeon.oxf.xforms.state

import java.util.concurrent.locks.Lock

import org.orbeon.oxf.xforms.XFormsContainingDocument


object XFormsStateManager extends XFormsStateLifecycle {

  def getClientEncodedStaticState(containingDocument: XFormsContainingDocument): Option[String] = None
  def getClientEncodedDynamicState(containingDocument: XFormsContainingDocument): Option[String] = None

  def afterInitialResponse(containingDocument: XFormsContainingDocument, disableDocumentCache: Boolean): Unit = ()

  def findOrRestoreDocument(parameters: RequestParameters, updates: Boolean, disableDocumentCache: Boolean): XFormsContainingDocument = ??? // TODO

  def acquireDocumentLock(uuid: String, timeout: Long): Option[Lock] = ??? // TODO

  def beforeUpdate(parameters: RequestParameters, disableDocumentCache: Boolean): XFormsContainingDocument = ??? // TODO
  def beforeUpdateResponse(containingDocument: XFormsContainingDocument, ignoreSequence: Boolean): Unit = ()
  def afterUpdateResponse(containingDocument: XFormsContainingDocument): Unit = ()
  def afterUpdate(containingDocument: XFormsContainingDocument, keepDocument: Boolean, disableDocumentCache: Boolean): Unit = ()
  def releaseDocumentLock(lock: Lock): Unit = ()
  def onAddedToCache(uuid: String): Unit = ()
  def onRemovedFromCache(uuid: String): Unit = ()
  def onEvictedFromCache(containingDocument: XFormsContainingDocument): Unit = ()
}
