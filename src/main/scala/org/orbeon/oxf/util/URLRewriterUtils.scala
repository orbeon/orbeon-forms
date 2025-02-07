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
package org.orbeon.oxf.util

import org.orbeon.oxf.common.Version
import org.orbeon.oxf.controller.PageFlowControllerProcessor
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriterImpl, UrlRewriteMode, UrlRewriterContext}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.servlet.OrbeonXFormsFilterImpl
import org.orbeon.oxf.util.CoreUtils.*

import java.net.URI
import java.util
import java.util.Collections
import java.util.concurrent.Callable
import java.util.regex.Pattern
import scala.jdk.CollectionConverters.*


/**
 * Utility class to rewrite URLs.
 */
object URLRewriterUtils {

  // Versioned resources configuration
  val RESOURCES_VERSIONED_PROPERTY = "oxf.resources.versioned"
  val RESOURCES_VERSIONED_DEFAULT = false

  val WSRP_ENCODE_RESOURCES_DEFAULT = false

  private val REWRITING_PLATFORM_PATHS_PROPERTY = "oxf.url-rewriting.platform-paths"
  private val REWRITING_APP_PATHS_PROPERTY = "oxf.url-rewriting.app-paths"
  private val REWRITING_APP_PREFIX_PROPERTY = "oxf.url-rewriting.app-prefix"
  private val REWRITING_CONTEXT_PROPERTY_PREFIX = "oxf.url-rewriting."
  private val REWRITING_CONTEXT_PROPERTY_SUFFIX = ".context"
  private val REWRITING_WSRP_ENCODE_RESOURCES_PROPERTY = "oxf.url-rewriting.wsrp.encode-resources"

  val RESOURCES_VERSION_NUMBER_PROPERTY = "oxf.resources.version-number"
  val REWRITING_SERVICE_BASE_URI_PROPERTY = "oxf.url-rewriting.service.base-uri"
  val REWRITING_SERVICE_BASE_URI_DEFAULT = ""
  val EMPTY_PATH_MATCHER_LIST: util.List[PathMatcher] = Collections.emptyList

  private val MATCH_ALL_PATH_MATCHER: PathMatcher = PathMatcher("/.*", null, versioned = true)

  var MATCH_ALL_PATH_MATCHERS: util.List[PathMatcher] = Collections.singletonList(URLRewriterUtils.MATCH_ALL_PATH_MATCHER)

  /**
   * Rewrite a service URL. The URL is rewritten against a base URL which is:
   *
   * - specified externally or
   * - the incoming request if not specified externally
   *
   * @param request     incoming request
   * @param urlString   URL to rewrite
   * @param rewriteMode rewrite mode
   * @return rewritten URL
   */
  def rewriteServiceURL(request: ExternalContext.Request, urlString: String, rewriteMode: UrlRewriteMode): String =
    URLRewriterImpl.rewriteServiceUrl(request, urlString, rewriteMode, getServiceBaseURI)

  def rewriteServiceURLPlain(urlRewriterCtx: UrlRewriterContext, urlString: String, rewriteMode: UrlRewriteMode): String =
    URLRewriterImpl.rewriteServiceUrlPlain(urlRewriterCtx, urlString, rewriteMode, getServiceBaseURI)

  /**
   * Return a context path as seen by the client. This might be the current request's context path, or the forwarding
   * servlet's context path. The returned path might be different for Orbeon resources vs. application resources.
   *
   * @param request        current request.
   * @param isPlatformPath whether the URL is a platform path
   * @return context path
   */
  def getClientContextPath(request: ExternalContext.Request, isPlatformPath: Boolean): String =
    // NOTE: We don't check on `javax.servlet.include.context_path`, because that attribute behaves very differently:
    // in the case of includes, it represents properties of the included servlet, not the values of the including
    // servlet.
    Option(request.getAttributesMap.get("javax.servlet.forward.context_path").asInstanceOf[String]) match {
      case Some(forwardedContextPath) if isSeparateDeployment(request) =>
        // This is the case of forwarding in separate deployment
        if (isPlatformPath)
          // Orbeon resources are forwarded
          // E.g. `/foobar/orbeon`
          forwardedContextPath + request.getContextPath
        else
          // Application resources are loaded from the original context
          // E.g. `/foobar`
          forwardedContextPath
      case _ =>
        // We were not forwarded to or we were forwarded but we are not in separate deployment
        request.getContextPath
    }

  def isSeparateDeployment(request: ExternalContext.Request): Boolean =
    "separate" == request.getAttributesMap.get(OrbeonXFormsFilterImpl.RendererDeploymentAttributeName)

  def isForwarded(request: ExternalContext.Request): Boolean =
    // NOTE: We don't check on javax.servlet.include.context_path, because that attribute behaves very differently:
    // in the case of includes, it represents properties of the included servlet, not the values of the including
    // servlet.
    request.getAttributesMap.get("javax.servlet.forward.context_path").asInstanceOf[String] != null

  /**
   * Rewrite a resource URL, possibly with version information, based on the incoming request as well as a list of
   * path matchers.
   *
   * @param request      incoming request
   * @param urlString    URL to rewrite
   * @param pathMatchers List of PathMatcher
   * @param rewriteMode  rewrite mode
   * @return rewritten URL
   */
  def rewriteResourceURL(
    request     : ExternalContext.Request,
    urlString   : String,
    pathMatchers: util.List[PathMatcher],
    rewriteMode : UrlRewriteMode
  ): String =
    if (pathMatchers != null && pathMatchers.size > 0) {
      // We need to match the URL against the matcher

      // 1. Rewrite to absolute path URI without context
      val absoluteURINoContext = URLRewriterImpl.rewriteURL(request, urlString, UrlRewriteMode.AbsolutePathNoContext)
      if (PathUtils.urlHasProtocol(absoluteURINoContext))
        return absoluteURINoContext // will be an absolute path

      val absoluteURINoContextURI = URI.create(absoluteURINoContext)

      // Obtain just the path
      val absolutePathNoContext = absoluteURINoContextURI.getPath
      if (absolutePathNoContext.startsWith("/xforms-server/"))
        // Special URL must not be rewritten as resource
        // TODO: when is this hit?
        return URLRewriterImpl.rewriteURL(request, urlString, rewriteMode)

      // 2. Determine if URL is a platform or application URL based on reserved paths
      val isPlatformURL = isPlatformPath(absolutePathNoContext)
      // TODO: get version only once for a run
      val applicationVersion = getApplicationResourceVersion
      if (! isPlatformURL && (applicationVersion.isEmpty || isSeparateDeployment(request)))
        // There is no application version OR we are in separate deployment so do usual rewrite
        return URLRewriterImpl.rewriteURL(request, urlString, rewriteMode)

      // 3. Iterate through matchers and see if we get a match
      if ((rewriteMode ne UrlRewriteMode.AbsolutePathNoPrefix) && isVersionedURL(absolutePathNoContext, pathMatchers)) {
        // 4. Found a match, perform additional rewrite at the beginning
        val versionOpt =
          if (isPlatformURL)
            Some(getOrbeonVersionForClient)
          else
            applicationVersion

        // Call full method so that we can get the proper client context path
        return URLRewriterImpl.rewriteURL(
          scheme       = request.getScheme,
          host         = request.getServerName,
          port         = request.getServerPort,
          contextPath  = request.getClientContextPath(urlString),
          requestPath  = request.getRequestPath,
          rawUrlString = versionOpt.map("/" + _).getOrElse("") + absoluteURINoContext,
          rewriteMode  = rewriteMode
        )
      }

      // No match found, perform regular rewrite
      URLRewriterImpl.rewriteURL(
        request     = request,
        urlString   = urlString,
        rewriteMode =
          if (rewriteMode eq UrlRewriteMode.AbsolutePathNoPrefix)
            UrlRewriteMode.AbsolutePathNoContext
          else
            rewriteMode
      )
    } else {
      // No Page Flow context, perform regular rewrite
      URLRewriterImpl.rewriteURL(request, urlString, rewriteMode)
    }

  /**
   * Check if the given path is a platform path (as opposed to a user application path).
   *
   * @param absolutePathNoContext path to check
   * @return true iif path is a platform path
   */
  def isPlatformPath(absolutePathNoContext: String): Boolean =
    evaluateRegexProperty(
      REWRITING_PLATFORM_PATHS_PROPERTY,
      MatchResult(_, absolutePathNoContext).matches
    )
    .getOrElse(false)

  /**
   * Check if the given path is an application path, assuming it is not already a platform path.
   *
   * @param absolutePathNoContext path to check
   * @return true iif path is a platform path
   */
  private def isNonPlatformPathAppPath(absolutePathNoContext: String) =
    evaluateRegexProperty(
      REWRITING_APP_PATHS_PROPERTY,
      MatchResult(_, absolutePathNoContext).matches
    )
    .getOrElse(false)

  private def evaluateRegexProperty[T](propertyName: String, fn: Pattern => T): Option[T] =
    Properties.instance.getPropertySet
      .getPropertyOpt(propertyName)
      .flatMap { property =>
        property
          .associatedValue(_.nonBlankStringValue.map(Pattern.compile))
          .map(fn)
      }

  /**
   * Decode an absolute path with no context, depending on whether there is an app version or not.
   *
   * @param absolutePathNoContext path
   * @param isVersioned           whether the resource is versioned or not
   * @return decoded path, or initial path if no decoding needed
   */
  def decodeResourceURI(absolutePathNoContext: String, isVersioned: Boolean): String =
    if (isVersioned) {
      // Versioned case
      if (URLRewriterUtils.getApplicationResourceVersion.isDefined) {
        // Remove version on any path
        prependAppPathIfNeeded(removeVersionPrefix(absolutePathNoContext))
      } else {
        // Try to remove version then test for platform path
        val pathWithVersionRemoved = removeVersionPrefix(absolutePathNoContext)
        if (isPlatformPath(pathWithVersionRemoved))
          // This was a versioned platform path
          pathWithVersionRemoved
        else
          // Not a versioned platform path
          // Don't remove version
          prependAppPathIfNeeded(absolutePathNoContext)
      }
    } else {
      // Non-versioned case
      prependAppPathIfNeeded(absolutePathNoContext)
    }

  private def prependAppPathIfNeeded(path: String): String =
    if (isPlatformPath(path) || isNonPlatformPathAppPath(path))
      // Path doesn't need adjustment
      path
    else
      // Adjust to make an app path
      Properties.instance.getPropertySet.getString(REWRITING_APP_PREFIX_PROPERTY, "") + path

  private def removeVersionPrefix(absolutePathNoContext: String) =
    if (absolutePathNoContext.isEmpty)
      absolutePathNoContext
    else {
      val slashIndex = absolutePathNoContext.indexOf('/', 1)
      if (slashIndex != -1)
        absolutePathNoContext.substring(slashIndex)
      else
        absolutePathNoContext
    }

  def isResourcesVersioned: Boolean =
    Version.instance.isPEFeatureEnabled(
      featureRequested = Properties.instance.getPropertySet.getBoolean(RESOURCES_VERSIONED_PROPERTY, RESOURCES_VERSIONED_DEFAULT),
      featureName      = RESOURCES_VERSIONED_PROPERTY
    )

  def getRewritingContext(rewritingStrategy: String, defaultContext: String): String =
    Properties.instance.getPropertySet.getString(REWRITING_CONTEXT_PROPERTY_PREFIX + rewritingStrategy + REWRITING_CONTEXT_PROPERTY_SUFFIX, defaultContext)

  def isWSRPEncodeResources: Boolean =
    Properties.instance.getPropertySet.getBoolean(REWRITING_WSRP_ENCODE_RESOURCES_PROPERTY, WSRP_ENCODE_RESOURCES_DEFAULT)

  def getServiceBaseURI: String =
    Properties.instance.getPropertySet.getStringOrURIAsString(REWRITING_SERVICE_BASE_URI_PROPERTY, REWRITING_SERVICE_BASE_URI_DEFAULT, allowEmpty = false)

  def getApplicationResourceVersion: Option[String] =
    Properties.instance.getPropertySet.getNonBlankString(RESOURCES_VERSION_NUMBER_PROPERTY)

  // Return the version string either in clear or encoded with HMAC depending on configuration
  def getOrbeonVersionForClient: String =
    if (isEncodeVersion)
      getHmacVersion
    else
      Version.VersionNumber

  // TODO: This property is in the `oxf.xforms` namespace. Should remove property or put in a non-XForms namespace.
  private def isEncodeVersion =
    Properties.instance.getPropertySet.getBoolean("oxf.xforms.resources.encode-version", default = true)

  private def getHmacVersion: String =
    SecureUtils.hmacStringWeakJava(Version.VersionNumber)

  def getMatchAllPathMatcher: Option[util.List[PathMatcher]] =
    isResourcesVersioned.option(MATCH_ALL_PATH_MATCHERS)

  def isVersionedURL(absolutePathNoContext: String, pathMatchers: util.List[PathMatcher]): Boolean =
    pathMatchers.asScala
      .exists(pathMatcher => MatchResult(pathMatcher.pattern, absolutePathNoContext).matches)

  def getPathMatchers: util.List[PathMatcher] = {
    val pathMatchers = PipelineContext.get.getAttribute(PageFlowControllerProcessor.PathMatchers).asInstanceOf[util.List[PathMatcher]]
    if (pathMatchers != null)
      pathMatchers
    else
      URLRewriterUtils.EMPTY_PATH_MATCHER_LIST
  }

  // Get path matchers from the pipeline context
  def getPathMatchersCallable: Callable[util.List[PathMatcher]] = new Callable[util.List[PathMatcher]]() {
    def call: util.List[PathMatcher] = URLRewriterUtils.getPathMatchers
  }
}