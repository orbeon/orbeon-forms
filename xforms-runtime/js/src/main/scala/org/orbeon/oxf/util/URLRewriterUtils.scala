package org.orbeon.oxf.util

import java.{util => ju}

import org.orbeon.oxf.externalcontext.ExternalContext.Request


object URLRewriterUtils {
  // TODO: placeholder, does it matter for Scala.js?
  def isResourcesVersioned = false

  def rewriteResourceURL(
    request      : Request,
    urlString    : String,
    pathMatchers : ju.List[PathMatcher],
    rewriteMode  : Int
  ): String = ???

  def getPathMatchers: ju.List[PathMatcher] = ???
}
