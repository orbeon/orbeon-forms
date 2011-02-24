/**
 * Copyright (C) 2011 Orbeon, Inc.
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

package org.orbeon.oxf.xforms;

import net.sf.ehcache._
import config.CacheConfiguration
import org.orbeon.oxf.resources.URLFactory
import processor.XFormsServer
import org.orbeon.oxf.util.LoggerFactory
import store.MemoryStoreEvictionPolicy

object Caches {

    private val ehcachePath = "oxf:/config/ehcache.xml"

    lazy val cacheManager =
        try {
            // Read configuration from XML file in resources
            val manager = new CacheManager(URLFactory.createURL(ehcachePath))
            debug("initialized cache manager from " + ehcachePath)
            manager
        } catch {
            case _ =>
                // Fallback configuration if not found
                warn("unable to read cache manager configuration from " + ehcachePath)
                new CacheManager
        }

    private val cacheName = "xforms.resources"

    lazy val resourcesCache =
        Caches.cacheManager.getCache(cacheName) match {
            // If manager already knows about our cache we are good to go
            case cache: Cache =>
                debug("found cache configuration for " + cacheName)
                cache
            // Otherwise use fallback configuration
            case _ =>
                val cache = new Cache(new CacheConfiguration(cacheName, 200)
                        memoryStoreEvictionPolicy MemoryStoreEvictionPolicy.LFU
                        overflowToDisk true
                        diskSpoolBufferSizeMB 1
                        diskStorePath "java.io.tmpdir/orbeon/cache"
                        eternal true
                        timeToLiveSeconds 0
                        timeToIdleSeconds 0
                        diskPersistent true
                        maxElementsOnDisk 0
                        diskExpiryThreadIntervalSeconds 120)

                Caches.cacheManager.addCache(cache)
                debug("used fallback cache configuration for " + cacheName)
                cache
        }

    private val LOGGING_CATEGORY = "caches"
    private val logger = LoggerFactory.createLogger(Caches.getClass)
    private val indentedLogger = XFormsContainingDocument.getIndentedLogger(logger, XFormsServer.getLogger, LOGGING_CATEGORY)

    private def debug(message: String) = indentedLogger.logDebug("", message)
    private def warn(message: String) = indentedLogger.logWarning("", message)
}