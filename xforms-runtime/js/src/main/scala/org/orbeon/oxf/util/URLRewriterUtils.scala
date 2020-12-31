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
  ): String = {
    println(s"xxx URLRewriterUtils.rewriteResourceURL called for $urlString")
    ???
  }

  def rewriteServiceURL(
    request     : Request,
    urlString   : String,
    rewriteMode : Int
  ): String = {
    println(s"xxx URLRewriterUtils.rewriteServiceURL called for $urlString")
    if (PathUtils.urlHasProtocol(urlString))
      urlString
    else
      ???
  }

  def getPathMatchers: ju.List[PathMatcher] = {
    println(s"xxx URLRewriterUtils.getPathMatchers called")
    ???
  }
}
