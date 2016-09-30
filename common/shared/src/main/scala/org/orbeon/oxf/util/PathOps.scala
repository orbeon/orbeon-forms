/**
 * Copyright (C) 2016 Orbeon, Inc.
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

import java.net.URLDecoder.{decode ⇒ decodeURL}
import java.net.URLEncoder.{encode ⇒ encodeURL}


trait PathOps {

  def dropTrailingSlash(s: String)   = if (s.isEmpty || s.last != '/') s else s.init
  def dropStartingSlash(s: String)   = if (s.isEmpty || s.head != '/') s else s.tail
  def appendStartingSlash(s: String) = if (s.nonEmpty && s.head == '/') s else '/' + s

  // Split out a URL's query part
  def splitQuery(url: String): (String, Option[String]) = {
    val index = url.indexOf('?')
    if (index == -1)
      (url, None)
    else
      (url.substring(0, index), Some(url.substring(index + 1)))
  }

  def splitQueryDecodeParams(url: String): (String, List[(String, String)]) = {
    val index = url.indexOf('?')
    if (index == -1)
      (url, Nil)
    else
      (url.substring(0, index), decodeSimpleQuery(url.substring(index + 1)))
  }

  // Recombine a path/query and parameters into a resulting URL
  def recombineQuery(pathQuery: String, params: Seq[(String, String)]) =
    pathQuery + (if (params.isEmpty) "" else (if (pathQuery.contains("?")) "&" else "?") + encodeSimpleQuery(params))

  // Decode a query string into a list of pairs
  // We assume that there are no spaces in the input query
  def decodeSimpleQuery(query: String): List[(String, String)] =
    for {
      nameValue      ← query.split('&').toList
      if nameValue.nonEmpty
      nameValueArray = nameValue.split('=')
      if nameValueArray.size >= 1
      encodedName    = nameValueArray(0)
      if encodedName.nonEmpty
      decodedName    = decodeURL(encodedName, "utf-8")
      decodedValue   = decodeURL(nameValueArray.lift(1) getOrElse "", "utf-8")
    } yield
      decodedName → decodedValue

  // Get the first query parameter value for the given name
  def getFirstQueryParameter(url: String, name: String) = {
    val (_, params) = splitQueryDecodeParams(url)

    params collectFirst { case (`name`, v) ⇒ v }
  }

  // Encode a sequence of pairs to a query string
  def encodeSimpleQuery(parameters: Seq[(String, String)]): String =
    parameters map { case (name, value) ⇒ encodeURL(name, "utf-8") + '=' + encodeURL(value, "utf-8") } mkString "&"

  // Find a path extension
  def findExtension(path: String): Option[String] =
    path.lastIndexOf(".") match {
      case -1    ⇒ None
      case index ⇒ Some(path.substring(index + 1))
    }
}

object PathUtils extends PathOps