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

import java.net.{URL, URLDecoder, URLEncoder}
import java.util.concurrent.Callable
import java.{util => ju}

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.URLRewriter._
import org.orbeon.oxf.util.{NetUtils, PathUtils, StringConversions, URLRewriterUtils}

// This URL rewriter rewrites URLs using the WSRP encoding
class WSRPURLRewriter(
  retrievePathMatchers : => ju.List[URLRewriterUtils.PathMatcher],
  request              : ExternalContext.Request,
  wsrpEncodeResources  : Boolean
) extends URLRewriter {

  import WSRPURLRewriter._

  // We don't initialize the matchers right away, because when the rewriter is created, they may not be available.
  // Specifically. the rewriter is typically created along the ExternalContext and PipelineContext, before the PFC has
  // been able to place the matchers in the PipelineContext.
  private var pathMatchers: ju.List[URLRewriterUtils.PathMatcher] = null

  // For Java callers, use Callable
  def this(
    getPathMatchers     : Callable[ju.List[URLRewriterUtils.PathMatcher]],
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

  def rewriteResourceURL(urlString: String, rewriteMode: Int) =
    rewriteResourceURL(urlString) // the mode is ignored

  def getNamespacePrefix = PrefixTag

  private def rewritePortletURL(urlString: String, urlType: Int, portletMode: String, windowState: String): String = {
    // Case where a protocol is specified OR it's just a fragment: the URL is left untouched
    if (NetUtils.urlHasProtocol(urlString) || urlString.startsWith("#"))
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
    val parameters = NetUtils.decodeQueryStringPortlet(u.getQuery)
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
    val navigationalState = NetUtils.encodeQueryString2(parameters)

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
          REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT
        )

      // Then do the WSRP encoding
      rewritePortletURL(rewrittenPath, URLTypeResource, null, null)
    } else
      // Generate resource served by the servlet
      URLRewriterUtils.rewriteResourceURL(
        request,
        urlString,
        getPathMatchers,
        REWRITE_MODE_ABSOLUTE_PATH
      )
  }
}

object WSRPURLRewriter {

  val PathParameterName = "orbeon.path"

  private val URLTypeBlockingAction = 1
  private val URLTypeRender         = 2
  private val URLTypeResource       = 3

  val URLTypeBlockingActionString   = "blockingAction"
  val URLTypeRenderString           = "render"
  val URLTypeResourceString         = "resource"

  val BaseTag                       = "wsrp_rewrite"
  val StartTag                      = BaseTag + '?'
  val EndTag                        = '/' + BaseTag
  val PrefixTag                     = BaseTag + '_'

  val URLTypeParam                  = "wsrp-urlType"
  val ModeParam                     = "wsrp-mode"
  val WindowStateParam              = "wsrp-windowState"
  val NavigationalStateParam        = "wsrp-navigationalState"

  val BaseTagLength                 = BaseTag.length
  val StartTagLength                = StartTag.length
  val EndTagLength                  = EndTag.length
  val PrefixTagLength               = PrefixTag.length

  private val URLTypes = Map(
    URLTypeBlockingAction -> URLTypeBlockingActionString,
    URLTypeRender         -> URLTypeRenderString,
    URLTypeResource       -> URLTypeResourceString
  )

  /**
   * Encode an URL into a WSRP pattern including the string "wsrp_rewrite".
   *
   * This does not call the portlet API. Used by Portlet2URLRewriter.
   */
  def encodeURL(
    urlType           : Int,
    navigationalState : String,
    mode              : String,
    windowState       : String,
    fragmentId        : String,
    secure            : Boolean
  ): String = {

    val sb = new StringBuilder(StartTag)

    sb.append(URLTypeParam)
    sb.append('=')

    val urlTypeString = URLTypes.getOrElse(urlType, throw new IllegalArgumentException)

    sb.append(urlTypeString)

    // Encode mode
    if (mode ne null) {
      sb.append('&')
      sb.append(ModeParam)
      sb.append('=')
      sb.append(mode)
    }

    // Encode window state
    if (windowState ne null) {
      sb.append('&')
      sb.append(WindowStateParam)
      sb.append('=')
      sb.append(windowState)
    }

    // Encode navigational state
    if (navigationalState ne null) {
      sb.append('&')
      sb.append(NavigationalStateParam)
      sb.append('=')
      sb.append(URLEncoder.encode(navigationalState, CharsetNames.Utf8))
    }
    sb.append(EndTag)

    sb.toString
  }

  type CreateResourceURL = String => String
  type CreatePortletURL  = (Option[String], Option[String], ju.Map[String, Array[String]]) => String

  def decodeURL(
    encodedURL        : String,
    createResourceURL : CreateResourceURL,
    createActionURL   : CreatePortletURL,
    createRenderURL   : CreatePortletURL
  ): String = {

    import StringConversions.getFirstValueFromStringArray

    def removeAmpIfNeeded(s: String) =
      if (s.startsWith("amp;")) s.substring("amp;".length) else s

    val wsrpParameters = NetUtils.decodeQueryStringPortlet(encodedURL)

    val urlType = {
      val urlType = getFirstValueFromStringArray(wsrpParameters.get(URLTypeParam))

      if (urlType eq null)
        throw new IllegalArgumentException(s"Missing URL type for WSRP encoded URL $encodedURL")

      if (! URLTypes.values.toSet(urlType))
        throw new IllegalArgumentException(s"Invalid URL type $urlType for WSRP encoded URL $encodedURL")

      urlType
    }

    val navigationParameters = {
      val navigationalStateValue = getFirstValueFromStringArray(wsrpParameters.get(NavigationalStateParam))
      if (navigationalStateValue ne null)
        NetUtils.decodeQueryStringPortlet(URLDecoder.decode(removeAmpIfNeeded(navigationalStateValue), CharsetNames.Utf8))
      else
        ju.Collections.emptyMap[String, Array[String]]
    }

    if (urlType == URLTypeResourceString) {
      val resourcePath = navigationParameters.get(PathParameterName)(0)
      navigationParameters.remove(PathParameterName)
      val resourceQuery = NetUtils.encodeQueryString2(navigationParameters)
      val resourceId = PathUtils.appendQueryString(resourcePath, resourceQuery)

      createResourceURL(resourceId)
    } else {
      val portletMode =
        Option(getFirstValueFromStringArray(wsrpParameters.get(ModeParam))) map removeAmpIfNeeded

      val windowState =
        Option(getFirstValueFromStringArray(wsrpParameters.get(WindowStateParam))) map removeAmpIfNeeded

      if (urlType == URLTypeBlockingActionString)
        createActionURL(portletMode, windowState, navigationParameters)
      else
        createRenderURL(portletMode, windowState, navigationParameters)
    }
  }
}