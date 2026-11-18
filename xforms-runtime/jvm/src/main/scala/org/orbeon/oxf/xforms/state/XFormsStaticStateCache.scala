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

import org.orbeon.oxf.cache.*
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.xforms.XFormsStaticState

import java.util.concurrent.ConcurrentHashMap
import scala.util.Try

object XFormsStaticStateCache {

  import Private.*

  trait CacheTracer {
    def digestAndTemplateStatus(digestIfFound: Option[String]): Unit
    def staticStateStatus(read: Boolean, found: Boolean, digest: String): Unit
  }

  def storeDocument(staticState: XFormsStaticState): Unit = {
    if (! (staticState.singleUseStaticState && staticState.isInlineResources)) { // https://github.com/orbeon/orbeon-forms/issues/1730
      Try(Option(CoreCrossPlatformSupport.externalContext).flatMap(ec => Option(ec.getRequest)).map(_.getRequestPath))
        .toOption
        .flatten
        .filter(_.nonEmpty)
        .foreach(path => digestToSamplePath.putIfAbsent(staticState.digest, path))
      if (staticState.isServerStateHandling)
        Try(XFormsStateStore.storeStaticState(staticState, refresh = false))
      cache.add(createCacheKey(staticState.digest), System.currentTimeMillis, staticState)
    }
  }

  def findDocument(digest: String): Option[(XFormsStaticState, Long)] =
    cache.findValidWithValidity(createCacheKey(digest), ConstantValidity) map { case (o, validity) =>
      o.asInstanceOf[XFormsStaticState] -> ProcessorImpl.findLastModified(validity)
    }

  def removeDocument(digest: String): Unit = {
    digestToSamplePath.remove(digest)
    cache.remove(createCacheKey(digest))
  }

  def sampleRequestPathOpt(digest: String): Option[String] =
    Option(digestToSamplePath.get(digest))

  def getCacheForDiagnostics: Cache = cache

  private object Private {

    def createCacheKey(digest: String) =
      new InternalCacheKey(ContainingDocumentKeyType, digest ensuring (_ ne null))

    val XFormsDocumentCache            = "xforms.cache.static-states"
    val XFormsDocumentCacheDefaultSize = 200
    val ConstantValidity               = 0L
    val ContainingDocumentKeyType      = XFormsDocumentCache

    val cache              = ObjectCache.instance(XFormsDocumentCache, XFormsDocumentCacheDefaultSize)
    val digestToSamplePath = new ConcurrentHashMap[String, String]()

    cache match {
      case mc: MemoryCacheImpl =>
        mc.setEvictionListener {
          case ss: XFormsStaticState if ss.isServerStateHandling =>
            Try(XFormsStateStore.storeStaticState(ss, refresh = false))
          case _ =>
        }
      case _ =>
    }
  }
}
