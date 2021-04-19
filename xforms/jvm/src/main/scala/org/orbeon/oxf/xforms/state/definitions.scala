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
import org.orbeon.oxf.xforms.XFormsContainingDocument

import scala.util.Try

// Encoded combination of static an dynamic state that fully represents an XForms document's current state
case class XFormsState(
  staticStateDigest : Option[String],
  staticState       : Option[String],
  dynamicState      : Option[DynamicState]
){
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

trait XFormsStateLifecycle {

  def getClientEncodedStaticState (containingDocument: XFormsContainingDocument): Option[String]
  def getClientEncodedDynamicState(containingDocument: XFormsContainingDocument): Option[String]

  def afterInitialResponse(
    containingDocument   : XFormsContainingDocument,
    disableDocumentCache : Boolean
  ): Unit

  def findOrRestoreDocument(
    parameters           : RequestParameters,
    updates              : Boolean,
    disableDocumentCache : Boolean
  ): XFormsContainingDocument

  def acquireDocumentLock (uuid: String, timeout: Long): Try[Option[Lock]]
  def releaseDocumentLock (lock: Lock): Unit

  def beforeUpdate        (parameters: RequestParameters, disableDocumentCache: Boolean): XFormsContainingDocument

  def beforeUpdateResponse(containingDocument: XFormsContainingDocument, ignoreSequence: Boolean): Unit
  def afterUpdateResponse (containingDocument: XFormsContainingDocument): Unit
  def afterUpdate         (containingDocument: XFormsContainingDocument, keepDocument: Boolean, disableDocumentCache: Boolean): Unit

  def onAddedToCache      (uuid: String): Unit
  def onRemovedFromCache  (uuid: String): Unit
  def onEvictedFromCache  (containingDocument: XFormsContainingDocument): Unit
}