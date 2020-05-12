/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.cache

import net.sf.ehcache
import net.sf.ehcache.CacheManager
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.resources.URLFactory
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal


object Caches {

  import Private._

  val Logger = LoggerFactory.getLogger("org.orbeon.caches")

  def getOrElseThrow(cacheName: String): ehcache.Cache =
    cacheManager.getCache(cacheName) match {
      case cache: ehcache.Cache =>
        withMessage(cache, s"found cache configuration for `$cacheName`")
      case _ =>
        throw new OXFException(s"Cache configuration not found for `$cacheName`. Make sure `$EhcachePath` exists.")
    }

  private object Private {

    val EhcachePath = "oxf:/config/ehcache.xml"

    val cacheManager =
      withMessage(
        try new CacheManager(URLFactory.createURL(EhcachePath))
        catch {
          case NonFatal(t) =>
            throw new OXFException(s"unable to read cache manager configuration from `$EhcachePath`", t)
        },
        s"initialized cache manager from `$EhcachePath`"
      )

    def withMessage[T](t: T, message: String) = { Logger.debug(message); t }
  }
}