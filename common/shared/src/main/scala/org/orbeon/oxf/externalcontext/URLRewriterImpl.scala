package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.util.StringUtils.*

import java.net.URI


trait UrlRewriterContext {
  def scheme       : String
  def method       : HttpMethod
  def serverName   : String
  def serverPort   : Int
  def requestPath  : String
  def servicePrefix: String
  def contextPath  : String
}

object UrlRewriterContext {
  def apply(request: ExternalContext.Request): UrlRewriterContext =
    new UrlRewriterContext {
      def scheme       : String     = request.getScheme
      def method       : HttpMethod = request.getMethod
      def serverName   : String     = request.getServerName
      def serverPort   : Int        = request.getServerPort
      def requestPath  : String     = request.getRequestPath
      def servicePrefix: String     = request.servicePrefix
      def contextPath  : String     = request.getContextPath
    }

  def apply(safeRequestCtx: SafeRequestContext): UrlRewriterContext =
    new UrlRewriterContext {
      def scheme       : String     = safeRequestCtx.scheme
      def method       : HttpMethod = safeRequestCtx.method
      def serverName   : String     = safeRequestCtx.serverName
      def serverPort   : Int        = safeRequestCtx.serverPort
      def requestPath  : String     = safeRequestCtx.requestPath
      def servicePrefix: String     = safeRequestCtx.servicePrefix
      def contextPath  : String     = safeRequestCtx.contextPath
    }
}

object URLRewriterImpl {

  def rewriteURL(request: ExternalContext.Request, urlString: String, rewriteMode: UrlRewriteMode): String =
    rewriteURL(
      scheme       = request.getScheme,
      host         = request.getServerName,
      port         = request.getServerPort,
      contextPath  = request.getClientContextPath(urlString),
      requestPath  = request.getRequestPath,
      rawUrlString = urlString,
      rewriteMode  = rewriteMode
    )

  def rewriteServiceUrlPlain(
    rewriterContext          : UrlRewriterContext,
    urlString                : String,
    rewriteMode              : UrlRewriteMode,
    baseURIPropertyMaybeBlank: String,
  ): String =
    rewriteServiceURLImpl(
      rewriterContext,
      urlString,
      rewriteMode,
      baseURIPropertyMaybeBlank,
      _ => rewriterContext.contextPath
    )

  def rewriteServiceUrl(
    request                  : ExternalContext.Request,
    urlString                : String,
    rewriteMode              : UrlRewriteMode,
    baseURIPropertyMaybeBlank: String,
  ): String =
    rewriteServiceURLImpl(
      UrlRewriterContext(request),
      urlString,
      rewriteMode,
      baseURIPropertyMaybeBlank,
      request.getClientContextPath
    )

  private def rewriteServiceURLImpl(
    rewriterContext          : UrlRewriterContext,
    urlString                : String,
    rewriteMode              : UrlRewriteMode,
    baseURIPropertyMaybeBlank: String,
    clientContextPath        : String => String
  ): String = {

    // NOTE: We used to assert here that the mode required an absolute URL, but as of 2016-03-17 one caller
    // wants a path.

    // Case where a protocol is specified: the URL is left untouched
    if (PathUtils.urlHasProtocol(urlString)) {
      urlString
    } else {
      baseURIPropertyMaybeBlank.trimAllToOpt match {
        case None =>
          rewriteURL(
            rewriterContext.scheme,
            rewriterContext.serverName,
            rewriterContext.serverPort,
            clientContextPath(urlString),
            rewriterContext.requestPath,
            urlString,
            rewriteMode
          )
        case Some(baseURIProperty) =>

          val baseURI =
            try
              URI.create(baseURIProperty)
            catch {
              case t: IllegalArgumentException =>
                throw new OXFException(s"Incorrect base URI property specified: `$baseURIProperty`", t)
            }

          // NOTE: Force absolute URL to be returned in this case anyway
          rewriteURL(
            if (baseURI.getScheme != null)
              baseURI.getScheme
            else
              rewriterContext.scheme,
            if (baseURI.getHost != null)
              baseURI.getHost
            else rewriterContext.serverName,
            if (baseURI.getHost != null)
              baseURI.getPort
            else
              rewriterContext.serverPort,
            baseURI.getPath,
            "",
            urlString,
            rewriteMode
          )
      }
    }
  }

  def rewriteURL(
    scheme       : String,
    host         : String,
    port         : Int,
    contextPath  : String,
    requestPath  : String,
    rawUrlString : String,
    rewriteMode  : UrlRewriteMode
  ): String = {

    // Accept human-readable URI
    val urlString = rawUrlString.encodeHRRI(processSpace = true)

    if (PathUtils.urlHasProtocol(urlString)) {
      // Case where a protocol is specified: the URL is left untouched (except for human-readable processing)
       urlString
    } else {
      val baseURLString = {

        // Prepend absolute base if needed
        val _baseURLString =
          if (rewriteMode == UrlRewriteMode.Absolute || rewriteMode == UrlRewriteMode.AbsoluteNoContext)
            scheme + "://" + host + (if (port == 80 || port == -1) "" else ":" + port)
          else
            ""

        // Append context path if needed
        if (rewriteMode != UrlRewriteMode.AbsolutePathNoContext && rewriteMode != UrlRewriteMode.AbsoluteNoContext)
          _baseURLString + contextPath
        else
          _baseURLString
      }

      // Return absolute path URI with query string and fragment identifier if needed
      if (urlString.startsWith("?")) {
        // This is a special case that appears to be implemented
        // in Web browsers as a convenience. Users may use it.
        baseURLString + requestPath + urlString
      } else if (rewriteMode == UrlRewriteMode.AbsolutePathOrRelative && ! urlString.startsWith("/") && "" != urlString) {
        // Don't change the URL if it is a relative path and we don't force absolute
        urlString
      } else {
        // Regular case, parse the URL
        val baseURIWithPath = new URI("http", "example.org", requestPath, null)
        val resolvedURI = baseURIWithPath.resolve(urlString).normalize // normalize to remove "..", etc.
        // Append path, query and fragment
        val query = resolvedURI.getRawQuery
        val fragment = resolvedURI.getRawFragment
        val tempResult =
          resolvedURI.getRawPath + (
            if (query != null)
              "?" + query
            else
              ""
          ) + (
            if (fragment != null)
              "#" + fragment
            else
              ""
          )

        // Prepend base
        baseURLString + tempResult
      }
    }
  }
}
