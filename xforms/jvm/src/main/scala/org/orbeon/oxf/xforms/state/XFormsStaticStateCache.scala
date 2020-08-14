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

import org.orbeon.oxf.cache._
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.xforms.XFormsStaticState

object XFormsStaticStateCache {

  import Private._

  trait CacheTracer {
    def digestAndTemplateStatus(digestIfFound: Option[String])
    def staticStateStatus(found: Boolean, digest: String)
  }

  def storeDocument(staticState: XFormsStaticState): Unit =
    cache.add(createCacheKey(staticState.digest), System.currentTimeMillis, staticState)

  def findDocument(digest: String): Option[(XFormsStaticState, Long)] =
    cache.findValidWithValidity(createCacheKey(digest), ConstantValidity) map { case (o, validity) =>
      o.asInstanceOf[XFormsStaticState] -> ProcessorImpl.findLastModified(validity)
    }

  private object Private {

    def createCacheKey(digest: String) =
      new InternalCacheKey(ContainingDocumentKeyType, digest ensuring (_ ne null))

    val XFormsDocumentCache            = "xforms.cache.static-state"
    val XFormsDocumentCacheDefaultSize = 50
    val ConstantValidity               = 0L
    val ContainingDocumentKeyType      = XFormsDocumentCache

    val cache = ObjectCache.instance(XFormsDocumentCache, XFormsDocumentCacheDefaultSize)
  }
}
