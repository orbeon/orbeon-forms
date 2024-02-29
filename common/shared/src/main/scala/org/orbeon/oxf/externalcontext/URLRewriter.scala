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


sealed trait UrlRewriteMode
object UrlRewriteMode {
  case object Absolute               extends UrlRewriteMode
  case object AbsolutePathOrRelative extends UrlRewriteMode
  case object AbsolutePathNoContext  extends UrlRewriteMode
  case object AbsolutePath           extends UrlRewriteMode
  case object AbsoluteNoContext      extends UrlRewriteMode
  case object AbsolutePathNoPrefix   extends UrlRewriteMode // no context and no version prefix
}

trait URLRewriter {
  def rewriteRenderURL  (urlString: String): String
  def rewriteRenderURL  (urlString: String, portletMode: String, windowState: String): String
  def rewriteActionURL  (urlString: String): String
  def rewriteActionURL  (urlString: String, portletMode: String, windowState: String): String
  def rewriteResourceURL(urlString: String, rewriteMode: UrlRewriteMode): String
  def getNamespacePrefix: String
}