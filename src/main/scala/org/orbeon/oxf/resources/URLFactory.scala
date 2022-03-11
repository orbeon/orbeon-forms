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
package org.orbeon.oxf.resources

import org.orbeon.oxf.resources.handler.{DataHandler, HTTPHandler, OXFHandler, SystemHandler}
import org.orbeon.oxf.util.PathUtils.{getProtocol, removeQueryString}
import org.orbeon.oxf.util.StringUtils._

import java.net._


/**
 * This factory should be used (instead of new URL(...)) to create URL objects. It:
 *
 * - supports the Orbeon "oxf:" protocol
 * - supports the Orbeon "system:" protocol
 * - directs "http:" and "https:" to our own HTTP handler
 * - removes the query string from "oxf:", "file:" and "system:" URLs
 */
object URLFactory {

  def createURL(url: URI): URL =
    createURL(null.asInstanceOf[URL], url.toString)

  def createURL(spec: String): URL =
    createURL(null.asInstanceOf[URL], spec)

  def createURL(context: String, spec: String): URL =
    createURL(context.trimAllToOpt map createURL orNull, spec)

  def createURL(context: URL, spec: String): URL = protocol(context, spec) match {
    case "http" | "https"       => new URL(context, spec, HTTP)
    case OXFHandler.Protocol    => new URL(context, removeQueryString(spec), OXF)
    case SystemHandler.PROTOCOL => new URL(context, removeQueryString(spec), System)
    case "data"                 => new URL(context, removeQueryString(spec), DataHandler)
    case "file"                 => new URL(context, removeQueryString(spec))
    case _                      => new URL(context, spec)
  }

  private def protocol(context: URL, spec: String) =
    Option(getProtocol(spec)) orElse (Option(context) map (_.getProtocol)) getOrElse (throw new IllegalArgumentException)

  private val OXF    = new OXFHandler
  private val HTTP   = new HTTPHandler
  private val System = new SystemHandler
}