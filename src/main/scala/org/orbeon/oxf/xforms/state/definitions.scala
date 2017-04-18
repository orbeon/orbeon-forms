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

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.xforms.XFormsContainingDocument

// Encoded combination of static an dynamic state that fully represents an XForms document's current state
case class XFormsState(staticStateDigest: Option[String], staticState: Option[String], dynamicState: Option[DynamicState]) {
  override def toString = s"XFormsState($staticState, $dynamicState)"
}

case class RequestParameters(
  uuid                         : String,
  sequenceOpt                  : Option[Long],
  encodedClientStaticStateOpt  : Option[String],
  encodedClientDynamicStateOpt : Option[String]
) {
  require(
    (encodedClientStaticStateOpt.isDefined && encodedClientDynamicStateOpt.isDefined) ||
      (encodedClientStaticStateOpt.isEmpty && encodedClientDynamicStateOpt.isEmpty)
  )
}

trait XFormsStateStore {

  def storeDocumentState(containingDocument: XFormsContainingDocument, session: ExternalContext.Session, isInitialState: Boolean): Unit
  def findState(session: ExternalContext.Session, documentUUID: String, isInitialState: Boolean): Option[XFormsState]

  def getMaxSize     : Long
  def getCurrentSize : Long
}

trait XFormsStateLifecycle {

  def getClientEncodedStaticState(containingDocument: XFormsContainingDocument): String
  def getClientEncodedDynamicState(containingDocument: XFormsContainingDocument): Option[String]
  def afterInitialResponse(containingDocument: XFormsContainingDocument, template: AnnotatedTemplate): Unit

  def findOrRestoreDocument(parameters: RequestParameters, isInitialState: Boolean, updates: Boolean): XFormsContainingDocument

  def acquireDocumentLock(uuid: String, timeout: Long): Option[Lock]
  def beforeUpdate(parameters: RequestParameters): XFormsContainingDocument
  def beforeUpdateResponse(containingDocument: XFormsContainingDocument, ignoreSequence: Boolean): Unit
  def afterUpdateResponse(containingDocument: XFormsContainingDocument): Unit
  def afterUpdate(containingDocument: XFormsContainingDocument, keepDocument: Boolean): Unit
  def releaseDocumentLock(lock: Lock): Unit

  def onAddedToCache(uuid: String): Unit
  def onRemovedFromCache(uuid: String): Unit
  def onEvictedFromCache(containingDocument: XFormsContainingDocument): Unit

  def getClientEncodedDynamicStateJava(containingDocument: XFormsContainingDocument) =
    getClientEncodedDynamicState(containingDocument).orNull
}