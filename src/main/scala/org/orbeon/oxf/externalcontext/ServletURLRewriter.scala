/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.util.URLRewriterUtils

class ServletURLRewriter(private val request: ExternalContext.Request) extends URLRewriter {

  // Lazy as `ExternalContext` might be created before PFC runs and sets matchers
  private lazy val pathMatchers = URLRewriterUtils.getPathMatchers

  def rewriteActionURL(urlString: String): String =
    URLRewriterUtils.rewriteURL(request, urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

  def rewriteRenderURL(urlString: String): String =
    URLRewriterUtils.rewriteURL(request, urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

  def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String =
    URLRewriterUtils.rewriteURL(request, urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

  def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String =
    URLRewriterUtils.rewriteURL(request, urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

  def rewriteResourceURL(urlString: String, rewriteMode: Int): String =
    URLRewriterUtils.rewriteResourceURL(
      request,
      urlString,
      pathMatchers,
      rewriteMode
    )

  def getNamespacePrefix: String = ""
}