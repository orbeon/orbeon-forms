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
import org.orbeon.oxf.cache.{InternalCacheKey, ObjectCache}
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.model.InstanceCaching
import org.orbeon.oxf.xforms.model.XFormsInstance._
import org.orbeon.oxf.util.StaticXPath.{DocumentNodeInfoType, VirtualNodeType}

/**
 * Cache for shared and immutable XForms instances.
 */
object XFormsServerSharedInstancesCache extends XFormsServerSharedInstancesCacheTrait {

  import Private._

  // Try to find instance content in the cache but do not attempt to load it if not found
  def findContentOrNull(
      instance        : Instance,
      instanceCaching : InstanceCaching,
      readonly        : Boolean)(implicit
      indentedLogger  : IndentedLogger
  ): DocumentNodeInfoType =
    find(instanceCaching)(indentedLogger) map
      (wrapDocumentInfo(_, readonly, instance.exposeXPathTypes)) orNull // TODO: shouldn't need to wrap since we don't expose types on readonly instances?

  // Try to find instance content in the cache or load it
  def findContentOrLoad(
      instance        : Instance,
      instanceCaching : InstanceCaching,
      readonly        : Boolean,
      loadInstance    : InstanceLoader)(implicit
      indentedLogger  : IndentedLogger
  ): DocumentNodeInfoType = {

    // Add an entry to the cache
    def add(instanceContent: InstanceContent, timeToLive: Long): Unit = {

      debug("adding instance", instanceCaching.debugPairs)

      val cache = ObjectCache.instance(XFormsSharedInstancesCacheName, XFormsSharedInstancesCacheDefaultSize)
      val cacheKey = createCacheKey(instanceCaching)

      cache.add(cacheKey, ConstantValidity, CacheEntry(instanceContent, timeToLive))
    }

    // Load and cache new instance content
    def loadAndCache(): Option[DocumentNodeInfoType] = {
      // Note that this method is not synchronized. Scenario: if the method is synchronized, the resource URI may
      // reach an XForms page which itself needs to load a shared resource. The result would be a deadlock.
      // Without synchronization, what can happen is that two concurrent requests load the same URI at the same
      // time. In the worst case scenario, the results will be different, and the two requesting XForms instances
      // will be different. The instance that is retrieved first will be stored in the cache for a very short
      // amount of time, and the one retrieved last will win and be stored in the cache for a longer time.
      debug("loading instance into cache", instanceCaching.debugPairs)

      val instanceContent = loadInstance(instanceCaching.pathOrAbsoluteURI, instanceCaching.handleXInclude)
      // NOTE: load() must always returns a TinyTree because we don't want to put in cache a mutable document
      assert(! instanceContent.isInstanceOf[VirtualNodeType], "load() must return a TinyTree")

      add(InstanceContent(instanceContent), instanceCaching.timeToLive)
      instanceContent.some
    }

    find(instanceCaching) orElse
      loadAndCache map
      (wrapDocumentInfo(_, readonly, instance.exposeXPathTypes)) get
  }

  // Remove the given entry from the cache if present
  def remove(
    instanceSourceURI : String,
    requestBodyHash   : String,
    handleXInclude    : Boolean)(implicit
    indentedLogger    : IndentedLogger
  ): Unit = {
    debug("removing instance", List("URI" -> instanceSourceURI, "request hash" -> requestBodyHash))

    val cache = ObjectCache.instance(XFormsSharedInstancesCacheName, XFormsSharedInstancesCacheDefaultSize)
    val cacheKey = createCacheKey(instanceSourceURI, handleXInclude, Option(requestBodyHash))
    cache.remove(cacheKey)
  }

  // Empty the cache
  def removeAll(implicit indentedLogger: IndentedLogger): Unit = {
    val cache = ObjectCache.instance(XFormsSharedInstancesCacheName, XFormsSharedInstancesCacheDefaultSize)
    val count = cache.removeAll()

    debug("removed all instances", List("count" -> count.toString))
  }

  private object Private {

    val XFormsSharedInstancesCacheName        = "xforms.cache.shared-instances"
    val XFormsSharedInstancesCacheDefaultSize = 10
    val ConstantValidity                      = 0L
    val SharedInstanceKeyType                 = XFormsSharedInstancesCacheName

    case class InstanceContent(documentInfo: DocumentNodeInfoType) { require(! documentInfo.isInstanceOf[VirtualNodeType]) }
    case class CacheEntry(instanceContent: InstanceContent, timeToLive: Long, timestamp: Long = System.currentTimeMillis)

    // Find instance content in cache
    def find(instanceCaching: InstanceCaching)(implicit logger: IndentedLogger): Option[DocumentNodeInfoType] = {

      val cache = ObjectCache.instance(XFormsSharedInstancesCacheName, XFormsSharedInstancesCacheDefaultSize)
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

    // Make key also depend on handleXInclude and on request body hash if present
    def createCacheKey(instanceCaching: InstanceCaching): InternalCacheKey =
      createCacheKey(instanceCaching.pathOrAbsoluteURI, instanceCaching.handleXInclude, instanceCaching.requestBodyHash)

    def createCacheKey(sourceURI: String, handleXInclude: Boolean, requestBodyHash: Option[String]): InternalCacheKey =
      new InternalCacheKey(
        SharedInstanceKeyType,
        sourceURI + "|" + handleXInclude.toString + (requestBodyHash map ('|' + _) getOrElse "")
      )
  }
}