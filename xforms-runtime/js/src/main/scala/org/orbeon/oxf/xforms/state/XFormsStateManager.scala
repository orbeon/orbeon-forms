package org.orbeon.oxf.xforms.state

import java.util.concurrent.locks.ReentrantLock

import org.orbeon.oxf.xforms.XFormsContainingDocument


object XFormsStateManager extends XFormsStateManagerTrait {

  private var documents: Map[String, XFormsContainingDocument] = Map.empty

  // TODO: CHECK what we should do here
  def getDocumentLock(uuid: String): Option[ReentrantLock] = Some(new ReentrantLock)
  def addDocumentToSession(uuid: String): Unit = ()

  def cacheOrStore(
    containingDocument   : XFormsContainingDocument,
    isInitialState       : Boolean, // TODO: handle `isInitialState = true`
    disableDocumentCache : Boolean  // for testing only
  ): Unit =
    documents += containingDocument.uuid -> containingDocument

  def getClientEncodedStaticState (containingDocument: XFormsContainingDocument): Option[String] = None
  def getClientEncodedDynamicState(containingDocument: XFormsContainingDocument): Option[String] = None

  def findOrRestoreDocument(
    parameters           : RequestParameters,
    disableUpdates       : Boolean,
    disableDocumentCache : Boolean
  ): Option[XFormsContainingDocument] =
    documents.get(parameters.uuid)

  def afterUpdate(
    containingDocument   : XFormsContainingDocument,
    keepDocument         : Boolean,
    disableDocumentCache : Boolean
  ): Unit = ()

  def createInitialDocumentFromStore(parameters: RequestParameters): Option[XFormsContainingDocument] =
    throw new NotImplementedError("createInitialDocumentFromStore")

  def onAddedToCache(uuid: String): Unit = ()
  def onRemovedFromCache(uuid: String): Unit = documents -= uuid
  def onEvictedFromCache(containingDocument: XFormsContainingDocument): Unit = onRemovedFromCache(containingDocument.uuid)
}
