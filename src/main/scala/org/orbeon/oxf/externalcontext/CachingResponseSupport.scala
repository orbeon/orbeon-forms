/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.servlet.ServletExternalContext
import org.orbeon.oxf.util.{DateUtils, NetUtils}
import ExternalContext.Request

trait CachingResponseSupport {

  def setHeader(name: String, value: String): Unit

  protected var responseCachingDisabled = false

  private def setResponseHeaders(headers: List[(String, String)]): Unit =
    for ((key, value) <- headers)
      setHeader(key, value)

  private def setDateHeader(name: String, value: Long) =
      setHeader(name, DateUtils.formatRfc1123DateTimeGmt(value))

  def setPageCaching(lastModified: Long): Unit =
    if (responseCachingDisabled) {
      setResponseHeaders(ServletExternalContext.nocacheCacheHeaders)
    } else {
      // Get current time and adjust lastModified
      val now = System.currentTimeMillis
      var _lastModified = lastModified
      if (_lastModified <= 0)
        _lastModified = now
      // Set last-modified
      setDateHeader(Headers.LastModified, _lastModified)
      // Make sure the client does not load from cache without revalidation
      setDateHeader("Expires", now)
      setResponseHeaders(ServletExternalContext.pageCacheHeaders)
    }

  def setResourceCaching(lastModified: Long, expires: Long): Unit =
    if (responseCachingDisabled) {
      setResponseHeaders(ServletExternalContext.nocacheCacheHeaders)
    } else {
      // Get current time and adjust parameters
      val now = System.currentTimeMillis
      var _lastModified = lastModified
      var _expires = expires
      if (_lastModified <= 0) {
        _lastModified = now
        _expires = now
      } else if (_expires <= 0) {
        // Regular expiration strategy. We use the HTTP spec heuristic to calculate the "Expires" header value
        // (10% of the difference between the current time and the last modified time)
        _expires = now + (now - _lastModified) / 10
      }
      // Set caching headers
      setDateHeader(Headers.LastModified, _lastModified)
      setDateHeader("Expires", _expires)
      setResponseHeaders(ServletExternalContext.resourceCacheHeaders)
    }

  def checkIfModifiedSince(request: Request, lastModified: Long): Boolean =
    responseCachingDisabled || NetUtils.checkIfModifiedSince(request, lastModified, ServletExternalContext.Logger)
}
