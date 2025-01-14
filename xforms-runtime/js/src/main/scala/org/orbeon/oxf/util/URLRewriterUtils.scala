package org.orbeon.oxf.util

import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.util.PathUtils.*
import org.scalajs.dom

import java.net.URI
import java.util as ju


object URLRewriterUtils {

  private val FormBuilderEditPath = "/fr/orbeon/builder/edit/"
  private val FormBuilderNewPath = "/fr/orbeon/builder/new"

  // TODO: placeholder, does it matter for Scala.js?
  def isResourcesVersioned = false

  // TODO: Analyze usage and handle rewriting modes as needed
  def rewriteResourceURL(
    request      : Request,
    urlString    : String,
    pathMatchers : ju.List[PathMatcher],
    rewriteMode  : UrlRewriteMode
  ): String = {

    // We want to rewrite for example `/xbl/etc.`
    val stringNoSlash = if (! urlString.startsWith("//")) urlString.dropStartingSlash else urlString
    val uriNoSlash    = URI.create(stringNoSlash)

    if (uriNoSlash.getScheme ne null) {
      urlString
    } else {
      if (rewriteMode == UrlRewriteMode.AbsolutePathNoContext) {
        // Only used directly by `XFormsOutputControl`
        urlString
      } else {
        val base = URI.create(dom.window.location.href)
        if (base.getScheme == "http" || base.getScheme == "https") {

          val basePath = base.resolve(".").getPath

          // Special case for Form Builder
          // https://github.com/orbeon/orbeon-forms/issues/6722
          val newBasePath =
            if (basePath.endsWith(FormBuilderEditPath))
              basePath.substring(0, basePath.length - FormBuilderEditPath.length + 1)
            else if (basePath.endsWith(FormBuilderNewPath))
              basePath.substring(0, basePath.length - FormBuilderNewPath.length + 1)
            else
              basePath

          newBasePath + "_/" + stringNoSlash

        } else {
          // Case where resources are loaded from `file:` from native app
          base.resolve(uriNoSlash).toString
        }
      }
    }
  }

  def rewriteServiceURL(
    request     : Request,
    urlString   : String,
    rewriteMode : UrlRewriteMode
  ): String =
      urlString

  // Used by `rewriteResourceURL` for `XFormsOutputControl`.
  // Q: Does anything make sense there?
  def getPathMatchers: ju.List[PathMatcher] =
    ju.Collections.emptyList[PathMatcher]
}
