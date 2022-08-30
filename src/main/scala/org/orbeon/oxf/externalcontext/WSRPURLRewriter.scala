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
package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.util.{PathMatcher, PathUtils, URLRewriterUtils}
import org.orbeon.wsrp.WSRPSupport._

import java.net.URL
import java.util.concurrent.Callable
import java.{util => ju}


// This URL rewriter rewrites URLs using the WSRP encoding
class WSRPURLRewriter(
  retrievePathMatchers : => ju.List[PathMatcher],
  request              : ExternalContext.Request,
  wsrpEncodeResources  : Boolean
) extends URLRewriter {

  // We don't initialize the matchers right away, because when the rewriter is created, they may not be available.
  // Specifically. the rewriter is typically created along the ExternalContext and PipelineContext, before the PFC has
  // been able to place the matchers in the PipelineContext.
  private var pathMatchers: ju.List[PathMatcher] = null

  // For Java callers, use Callable
  def this(
    getPathMatchers     : Callable[ju.List[PathMatcher]],
    request             : ExternalContext.Request,
    wsrpEncodeResources : Boolean
  ) = this(
    getPathMatchers.call,
    request,
    wsrpEncodeResources
  )

  private def getPathMatchers = {
    if (pathMatchers eq null)
      pathMatchers = Option(retrievePathMatchers) getOrElse URLRewriterUtils.EMPTY_PATH_MATCHER_LIST

    pathMatchers
  }

  def rewriteRenderURL(urlString: String) =
    rewritePortletURL(urlString, URLTypeRender, null, null)

  def rewriteRenderURL(urlString: String, portletMode: String, windowState: String) =
    rewritePortletURL(urlString, URLTypeRender, portletMode, windowState)

  def rewriteActionURL(urlString: String) =
    rewritePortletURL(urlString, URLTypeBlockingAction, null, null)

  def rewriteActionURL(urlString: String, portletMode: String, windowState: String) =
    rewritePortletURL(urlString, URLTypeBlockingAction, portletMode, windowState)

  def rewriteResourceURL(urlString: String, rewriteMode: UrlRewriteMode) =
    rewriteResourceURL(urlString) // the mode is ignored

  def getNamespacePrefix = PrefixTag

  private def rewritePortletURL(urlString: String, urlType: Int, portletMode: String, windowState: String): String = {
    // Case where a protocol is specified OR it's just a fragment: the URL is left untouched
    if (PathUtils.urlHasProtocol(urlString) || urlString.startsWith("#"))
      return urlString

    // TEMP HACK to avoid multiple rewrites
    // TODO: Find out where it happens. Check XFOutputControl with image mediatype for example.
    if (urlString.indexOf("wsrp_rewrite") != -1)
      return urlString

    // Parse URL
    // TODO: Use URI instead?
    val baseURL = new URL("http", "example.org", request.getRequestPath)
    val u = new URL(baseURL, urlString)
    // Decode query string
    val parameters = PathUtils.decodeQueryStringPortlet(u.getQuery)
    // Add special path parameter
    val path =
      if (urlString.startsWith("?"))
        // This is a special case that appears to be implemented
        // in Web browsers as a convenience. Users may use it.
        request.getRequestPath
      else
        // Regular case, use parsed path
        URLRewriterUtils.getRewritingContext("wsrp", "") + u.getPath

    parameters.put(PathParameterName, Array(path))

    // Encode as "navigational state"
    val navigationalState = PathUtils.encodeQueryString(parameters.asScala)

    // Encode the URL a la WSRP
    encodeURL(urlType, navigationalState, portletMode, windowState, u.getRef, secure = false)
  }

  private def rewriteResourceURL(urlString: String): String = {
    // Always encode dynamic resources
    // NOTE: Not great to depend on XForms paths here.
    if (wsrpEncodeResources || urlString == "/xforms-server" || urlString.startsWith("/xforms-server/dynamic/")) {
      // First rewrite path to support versioned resources
      val rewrittenPath =
        URLRewriterUtils.rewriteResourceURL(
          request,
          urlString,
          getPathMatchers,
          UrlRewriteMode.AbsolutePathNoContext
        )

      // Then do the WSRP encoding
      rewritePortletURL(rewrittenPath, URLTypeResource, null, null)
    } else
      // Generate resource served by the servlet
      URLRewriterUtils.rewriteResourceURL(
        request,
        urlString,
        getPathMatchers,
        UrlRewriteMode.AbsolutePath
      )
  }
}