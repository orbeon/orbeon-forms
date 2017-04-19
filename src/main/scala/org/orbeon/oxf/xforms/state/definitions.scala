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

import java.util.concurrent.locks.Lock

import org.orbeon.dom.Document
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.xforms.XFormsContainingDocument

// Encoded combination of static an dynamic state that fully represents an XForms document's current state
case class XFormsState(staticStateDigest: Option[String], staticState: String, dynamicState: DynamicState) {
  override def toString = "XFormsState(" + staticState + "," + dynamicState + ")"
}

case class RequestParameters(
  getUUID: String,
  getEncodedClientStaticState: String,
  getEncodedClientDynamicState: String
)

trait XFormsStateStore {

  def storeDocumentState(containingDocument: XFormsContainingDocument, session: ExternalContext.Session, isInitialState: Boolean): Unit
  def findState(session: ExternalContext.Session, documentUUID: String, isInitialState: Boolean): XFormsState

  def getMaxSize     : Long
  def getCurrentSize : Long
}

trait XFormsStateLifecycle {

  def getClientEncodedStaticState(containingDocument: XFormsContainingDocument): String
  def getClientEncodedDynamicState(containingDocument: XFormsContainingDocument): String
  def afterInitialResponse(containingDocument: XFormsContainingDocument, template: AnnotatedTemplate): Unit

  def extractParameters(request: Document, isInitialState: Boolean): RequestParameters

  def findOrRestoreDocument(parameters: RequestParameters, isInitialState: Boolean, updates: Boolean): XFormsContainingDocument

  def acquireDocumentLock(uuid: String, timeout: Long): Lock
  def beforeUpdate(parameters: RequestParameters): XFormsContainingDocument
  def beforeUpdateResponse(containingDocument: XFormsContainingDocument, ignoreSequence: Boolean): Unit
  def afterUpdateResponse(containingDocument: XFormsContainingDocument): Unit
  def afterUpdate(containingDocument: XFormsContainingDocument, keepDocument: Boolean): Unit
  def releaseDocumentLock(lock: Lock): Unit

  def onAddedToCache(uuid: String): Unit
  def onRemovedFromCache(uuid: String): Unit
  def onEvictedFromCache(containingDocument: XFormsContainingDocument): Unit
}