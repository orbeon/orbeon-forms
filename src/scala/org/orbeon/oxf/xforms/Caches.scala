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
import org.orbeon.oxf.resources.URLFactory
import processor.XFormsServer
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.common.OXFException

/**
 * All Ehcache-based caches.
 */
object Caches {

    lazy val stateCache = getCache("xforms.state")
    lazy val resourcesCache = getCache("xforms.resources")
    lazy val xblCache = getCache("xforms.xbl")

    private val ehcachePath = "oxf:/config/ehcache.xml"

    private lazy val cacheManager =
        try {
            // Read configuration from XML file in resources
            val manager = new CacheManager(URLFactory.createURL(ehcachePath))
            withMessage(manager, "initialized cache manager from " + ehcachePath)
        } catch {
            case _ =>
                throw new OXFException("unable to read cache manager configuration from " + ehcachePath)
        }

    private def getCache(cacheName: String) =
        cacheManager.getCache(cacheName) match {
            case cache: Cache =>
                withMessage(cache, "found cache configuration for " + cacheName)
            case _ =>
                throw new OXFException("Cache configuration not found for " + cacheName + ". Make sure an ehcache.xml file is in place.")
        }

    private val indentedLogger = {
        val LOGGING_CATEGORY = "caches"
        val logger = LoggerFactory.createLogger(Caches.getClass)
        XFormsContainingDocument.getIndentedLogger(logger, XFormsServer.getLogger, LOGGING_CATEGORY)
    }

    private def withMessage[T](t: T, message: String) = { indentedLogger.logDebug("", message); t }
}