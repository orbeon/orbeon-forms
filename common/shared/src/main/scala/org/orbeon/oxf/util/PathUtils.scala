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

import StringUtils._

import scala.collection.compat.IterableOnce

object PathUtils {

  trait UrlEncoderDecoder {
    def encode(s: String): String
    def decode(s: String): String
  }

  implicit class PathOps(private val s: String) extends AnyVal {
    def dropTrailingSlash: String = if (s.isEmpty || s.last != '/')  s else s.init
    def dropStartingSlash: String = if (s.isEmpty || s.head != '/')  s else s.tail
    def appendSlash      : String = if (s.nonEmpty && s.last == '/') s else s + '/'
    def prependSlash     : String = if (s.nonEmpty && s.head == '/') s else "/" + s
  }

  // Split out a URL's query part
  def splitQuery(url: String): (String, Option[String]) = {
    val index = url.indexOf('?')
    if (index == -1)
      (url, None)
    else
      (url.substring(0, index), Some(url.substring(index + 1)))
  }

  def splitQueryDecodeParams(url: String)(implicit ed: UrlEncoderDecoder): (String, List[(String, String)]) = {
    val index = url.indexOf('?')
    if (index == -1)
      (url, Nil)
    else
      (url.substring(0, index), decodeSimpleQuery(url.substring(index + 1)))
  }

  // Recombine a path/query and parameters into a resulting URL
  def recombineQuery(pathQuery: String, params: IterableOnce[(String, String)])(implicit ed: UrlEncoderDecoder): String =
    pathQuery + (if (params.isEmpty) "" else (if (pathQuery.contains("?")) "&" else "?") + encodeSimpleQuery(params))

  // Decode a query string into a list of pairs
  // We assume that there are no spaces in the input query
  def decodeSimpleQuery(query: String)(implicit ed: UrlEncoderDecoder): List[(String, String)] =
    for {
      nameValue      <- query.split('&').toList
      if nameValue.nonEmpty
      nameValueArray = nameValue.split('=')
      if nameValueArray.size >= 1
      encodedName    = nameValueArray(0)
      if encodedName.nonEmpty
      decodedName    = ed.decode(encodedName)
      decodedValue   = ed.decode(nameValueArray.lift(1) getOrElse "")
    } yield
      decodedName -> decodedValue

  // Get the first query parameter value for the given name
  def getFirstQueryParameter(url: String, name: String)(implicit ed: UrlEncoderDecoder): Option[String] = {
    val (_, params) = splitQueryDecodeParams(url)

    params collectFirst { case (`name`, v) => v }
  }

  // Encode a sequence of pairs to a query string
  def encodeSimpleQuery(
    parameters : IterableOnce[(String, String)],
    separator  : String = "&")(implicit
    ed         : UrlEncoderDecoder
  ): String =
    parameters map { case (name, value) => ed.encode(name) + '=' + ed.encode(value) } mkString separator

  // Find a path extension
  def findExtension(path: String): Option[String] =
    path.lastIndexOf(".") match {
      case -1    => None
      case index => Some(path.substring(index + 1))
    }

  // Append a query string to an URL. This adds a '?' or a '&' or nothing, as needed.
  // The given query string is appended without further encoding.
  def appendQueryString(pathQuery: String, queryString: String): String =
    pathQuery + (if (queryString.isAllBlank) "" else (if (pathQuery.contains("?")) "&" else "?") + queryString)

  def removeQueryString(pathQuery: String): String = {
    val questionIndex = pathQuery.indexOf('?')
    if (questionIndex == -1)
      pathQuery
    else
      pathQuery.substring(0, questionIndex)
  }

  // TODO: See "Move to PathUtils" in NetUtils.
}
