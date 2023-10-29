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
package org.orbeon.oxf.xforms

import cats.syntax.option._
import org.orbeon.oxf.cache._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, PathUtils}
import org.orbeon.oxf.xforms.model.InstanceCaching
import org.orbeon.oxf.xforms.model.XFormsInstance._

import scala.jdk.CollectionConverters._


/**
 * Cache for shared and immutable XForms instances.
 */
object XFormsServerSharedInstancesCache extends XFormsServerSharedInstancesCacheTrait {

  import Private._

  protected def add(
    instanceCaching: InstanceCaching,
    instanceContent: InstanceContent,
    timeToLive     : Long
  )(implicit
    indentedLogger : IndentedLogger
  ): Unit = {

    debug("adding instance", instanceCaching.debugPairs)

    val cache = getCache
    val cacheKey = createCacheKey(instanceCaching)

    cache.add(cacheKey, ConstantValidity, CacheEntry(instanceContent, timeToLive))
  }

  protected def find(instanceCaching: InstanceCaching)(implicit logger: IndentedLogger): Option[DocumentNodeInfoType] = {

    val cache = getCache
    val cacheKey = createCacheKey(instanceCaching)

    def isExpired(cacheEntry: CacheEntry) =
      cacheEntry.timeToLive >= 0 && ((cacheEntry.timestamp + cacheEntry.timeToLive) < System.currentTimeMillis)

    Option(cache.findValid(cacheKey, ConstantValidity).asInstanceOf[CacheEntry]) match {
      case Some(cacheEntry) if isExpired(cacheEntry) =>
        // Remove expired entry
        debug("expiring cached instance", instanceCaching.debugPairs)
        cache.remove(cacheKey)
        None
      case Some(cacheEntry) =>
        // Instance was found
        debug("found cached instance", instanceCaching.debugPairs)
        cacheEntry.instanceContent.documentInfo.some
      case _ =>
        // Not found
        debug("cached instance not found", instanceCaching.debugPairs)
        None
    }
  }

  def remove(
    instanceSourceURI : String,
    requestBodyHash   : Option[String],
    handleXInclude    : Boolean,
    ignoreQueryString : Boolean
  )(implicit
    indentedLogger    : IndentedLogger
  ): Unit = {
    debug("removing instance", List("URI" -> instanceSourceURI, "request hash" -> requestBodyHash.toString))

    val cache = ObjectCache.instance(XFormsSharedInstancesCacheName, XFormsSharedInstancesCacheDefaultSize)

    if (ignoreQueryString) {

      // Here we remove all the entries that have a URI that matches but *with the query string removed*

      def extractUriOpt(key: InternalCacheKey) =
        key.getKey.splitTo[List]("|").headOption

      val uriNoQueryString = PathUtils.removeQueryString(instanceSourceURI)

      cache.iterateCacheKeys().asScala collect { case key: InternalCacheKey
        if extractUriOpt(key).map(PathUtils.removeQueryString).contains(uriNoQueryString) => key
      } foreach
        cache.remove
    } else {
      val cacheKey = createCacheKey(instanceSourceURI, handleXInclude, requestBodyHash)
      cache.remove(cacheKey)
    }
  }

  def removeAll(implicit indentedLogger: IndentedLogger): Unit = {

    val cache = getCache
    val count = cache.removeAll()

    debug("removed all instances", List("count" -> count.toString))
  }

  private object Private {

    val XFormsSharedInstancesCacheName        = "xforms.cache.shared-instances"
    val XFormsSharedInstancesCacheDefaultSize = 10

    val ConstantValidity                      = 0L
    val SharedInstanceKeyType                 = XFormsSharedInstancesCacheName

    case class CacheEntry(instanceContent: InstanceContent, timeToLive: Long, timestamp: Long = System.currentTimeMillis)

    def getCache: org.orbeon.oxf.cache.Cache =
      ObjectCache.instance(XFormsSharedInstancesCacheName, XFormsSharedInstancesCacheDefaultSize)

    def createCacheKey(sourceURI: String, handleXInclude: Boolean, requestBodyHash: Option[String]): InternalCacheKey =
      new InternalCacheKey(
        SharedInstanceKeyType,
        sourceURI + "|" + handleXInclude.toString + (requestBodyHash map ('|' + _) getOrElse "")
      )

    // Make key also depend on handleXInclude and on request body hash if present
    def createCacheKey(instanceCaching: InstanceCaching): InternalCacheKey =
      createCacheKey(instanceCaching.pathOrAbsoluteURI, instanceCaching.handleXInclude, instanceCaching.requestBodyHash)
  }
}