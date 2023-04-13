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

import org.orbeon.oxf.cache.{InternalCacheKey, ObjectCache}
import org.orbeon.oxf.util.SecureUtils
import org.orbeon.oxf.xforms.XFormsContainingDocument

object XFormsDocumentCache {

  import Private._

  // Add a document to the cache using the document's UUID as cache key.
  def put(containingDocument: XFormsContainingDocument): Unit =
    cache.add(createCacheKey(containingDocument.uuid), ConstantValidity, containingDocument)

  // Find a document in the cache. If found, the document is removed from the cache.
  def take(uuid: String): Option[XFormsContainingDocument] =
    Option(cache.takeValid(createCacheKey(uuid), ConstantValidity).asInstanceOf[XFormsContainingDocument])

  def peekForTests(uuid: String): Option[XFormsContainingDocument] =
    Option(cache.findValid(createCacheKey(uuid), ConstantValidity).asInstanceOf[XFormsContainingDocument])

  // Remove a document from the cache. This does NOT cause the document state to be serialized to store.
  def remove(uuid: String): Unit =
    cache.remove(createCacheKey(uuid))

  def getCurrentSize : Int = cache.getCurrentSize
  def getMaxSize     : Int = cache.getMaxSize

  private object Private {

    val CacheName                 = "xforms.cache.documents"
    val DefaultSize               = 50
    val ConstantValidity          = 0L
    val ContainingDocumentKeyType = CacheName

    val cache = ObjectCache.instance(CacheName, DefaultSize)

    def createCacheKey(uuid: String) = {
      require(uuid.length == SecureUtils.HexShortLength)
      new InternalCacheKey(ContainingDocumentKeyType, uuid)
    }
  }
}