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

object URLRewriter {
  // Works as a bitset
  // 1: whether to produce an absolute URL (starting with "http" or "https")
  // 2: whether to leave the URL as is if it is does not start with "/"
  // 4: whether to prevent insertion of a context at the start of the path
  val REWRITE_MODE_ABSOLUTE = 1
  val REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE = 2
  val REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT = 4
  val REWRITE_MODE_ABSOLUTE_PATH = 0
  val REWRITE_MODE_ABSOLUTE_NO_CONTEXT = REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT + REWRITE_MODE_ABSOLUTE
}

trait URLRewriter {
  def rewriteRenderURL(urlString: String): String
  def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String
  def rewriteActionURL(urlString: String): String
  def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String
  def rewriteResourceURL(urlString: String, rewriteMode: Int): String
  def getNamespacePrefix: String
}