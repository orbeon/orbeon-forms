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

import analysis.model.Instance
import org.orbeon.oxf.cache.InternalCacheKey
import org.orbeon.oxf.cache.ObjectCache
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.DebugLogger._
import org.orbeon.saxon.om.{VirtualNode, DocumentInfo}
import XFormsInstance._

/**
 * Cache for shared and immutable XForms instances.
 */
object XFormsServerSharedInstancesCache {

    private val XFormsSharedInstancesCacheName = "xforms.cache.shared-instances"
    private val XFormsSharedInstancesCacheDefaultSize = 10
    private val ConstantValidity = 0L
    private val SharedInstanceKeyType = XFormsSharedInstancesCacheName

    // Equivalent to loadContent: (String, Boolean) ⇒ DocumentInfo
    trait Loader {
        def load(instanceSourceURI: String, handleXInclude: Boolean): DocumentInfo
    }

    private case class InstanceContent(documentInfo: DocumentInfo) { require(! documentInfo.isInstanceOf[VirtualNode]) }
    private case class CacheEntry(instanceContent: InstanceContent, timeToLive: Long, timestamp: Long = System.currentTimeMillis)

    // Try to find instance content in the cache but do not attempt to load it if not found
    def findContentOrNull(
            indentedLogger: IndentedLogger,
            instance: Instance,
            instanceCaching: InstanceCaching,
            readonly: Boolean) =
        find(instanceCaching)(indentedLogger) map (wrapDocumentInfo(_, readonly, instance.isExposeXPathTypes)) orNull

    // Try to find instance content in the cache or load it
    def findContentOrLoad(
            indentedLogger: IndentedLogger,
            instance: Instance,
            instanceCaching: InstanceCaching,
            readonly: Boolean,
            loader: Loader) = {

        implicit val logger = indentedLogger

        // Add an entry to the cache
        def add(instanceContent: InstanceContent, timeToLive: Long) = {

            debug("adding instance", instanceCaching.debugPairs)

            val cache = ObjectCache.instance(XFormsSharedInstancesCacheName, XFormsSharedInstancesCacheDefaultSize)
            val cacheKey = createCacheKey(instanceCaching)

            cache.add(cacheKey, ConstantValidity, CacheEntry(instanceContent, timeToLive))
        }

        // Load and cache new instance content
        def loadAndCache() = {
            // Note that this method is not synchronized. Scenario: if the method is synchronized, the resource URI may
            // reach an XForms page which itself needs to load a shared resource. The result would be a deadlock.
            // Without synchronization, what can happen is that two concurrent requests load the same URI at the same
            // time. In the worst case scenario, the results will be different, and the two requesting XForms instances
            // will be different. The instance that is retrieved first will be stored in the cache for a very short
            // amount of time, and the one retrieved last will win and be stored in the cache for a longer time.
            debug("loading instance into cache", instanceCaching.debugPairs)

            val instanceContent = loader.load(instanceCaching.sourceURI, instanceCaching.handleXInclude)
            // NOTE: load() must always returns a TinyTree because we don't want to put in cache a mutable document
            assert(! instanceContent.isInstanceOf[VirtualNode], "load() must return a TinyTree")

            add(InstanceContent(instanceContent), instanceCaching.timeToLive)
            Some(instanceContent)
        }

        find(instanceCaching) orElse loadAndCache map (wrapDocumentInfo(_, readonly, instance.isExposeXPathTypes)) get
    }

    // Remove the given entry from the cache if present
    def remove(indentedLogger: IndentedLogger, instanceSourceURI: String, requestBodyHash: String, handleXInclude: Boolean): Unit = {
        implicit val logger = indentedLogger
        debug("removing instance", Seq("URI" → instanceSourceURI, "request hash" → requestBodyHash))

        val cache = ObjectCache.instance(XFormsSharedInstancesCacheName, XFormsSharedInstancesCacheDefaultSize)
        val cacheKey = createCacheKey(instanceSourceURI, handleXInclude, requestBodyHash)
        cache.remove(cacheKey)
    }

    // Empty the cache
    def removeAll(indentedLogger: IndentedLogger): Unit = {
        val cache = ObjectCache.instance(XFormsSharedInstancesCacheName, XFormsSharedInstancesCacheDefaultSize)
        val count = cache.removeAll()

        implicit val logger = indentedLogger
        debug("removed all instances", Seq("count" → count.toString))
    }

    // Find instance content in cache
    private def find(instanceCaching: InstanceCaching)(implicit logger: IndentedLogger) = {

        val cache = ObjectCache.instance(XFormsSharedInstancesCacheName, XFormsSharedInstancesCacheDefaultSize)
        val cacheKey = createCacheKey(instanceCaching)

        def isExpired(cacheEntry: CacheEntry) =
            cacheEntry.timeToLive >= 0 && ((cacheEntry.timestamp + cacheEntry.timeToLive) < System.currentTimeMillis)

        Option(cache.findValid(cacheKey, ConstantValidity).asInstanceOf[CacheEntry]) match {
            case Some(cacheEntry) if isExpired(cacheEntry) ⇒
                // Remove expired entry
                debug("expiring cached instance", instanceCaching.debugPairs)
                cache.remove(cacheKey)
                None
            case Some(cacheEntry) ⇒
                // Instance was found
                debug("found cached instance", instanceCaching.debugPairs)
                Some(cacheEntry.instanceContent.documentInfo)
            case _ ⇒
                // Not found
                debug("cached instance not found", instanceCaching.debugPairs)
                None
        }
    }

    // Make key also depend on handleXInclude and on request body hash if present
    private def createCacheKey(instanceCaching: InstanceCaching): InternalCacheKey =
        createCacheKey(instanceCaching.sourceURI, instanceCaching.handleXInclude, instanceCaching.requestBodyHash)

    private def createCacheKey(sourceURI: String, handleXInclude: Boolean, requestBodyHash: String): InternalCacheKey =
        new InternalCacheKey(SharedInstanceKeyType,
            sourceURI + "|" + handleXInclude.toString + (Option(requestBodyHash) map ('|' + _) getOrElse ""))
}