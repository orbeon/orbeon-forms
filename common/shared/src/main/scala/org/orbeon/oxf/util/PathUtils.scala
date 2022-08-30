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

import org.orbeon.oxf.util.StringUtils._

import java.util.regex.Pattern
import java.{util, lang => jl}
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

    def encode(implicit ed: UrlEncoderDecoder): String = ed.encode(s)
    def decode(implicit ed: UrlEncoderDecoder): String = ed.decode(s)
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
    pathQuery + (if (params.toIterator.isEmpty) "" else (if (pathQuery.contains("?")) "&" else "?") + encodeSimpleQuery(params))

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
    parameters.toIterator map { case (name, value) => ed.encode(name) + '=' + ed.encode(value) } mkString separator

  // Find a path extension
  def findExtension(path: String): Option[String] =
    path.lastIndexOf(".") match {
      case -1    => None
      case index => Some(path.substring(index + 1))
    }

  def maybeReplaceExtension(path: String, ext: String): Option[String] =
    (path.lastIndexOf("."), ext.trimAllToOpt) match {
      case (-1, _)                   => None
      case (index, None)             => Some(path.substring(0, index))
      case (index, Some(trimmedExt)) => Some(path.substring(0, index + 1) + trimmedExt)
    }

  def filenameFromPath(path: String): String =
    path.splitTo[List]("""\/""").lastOption getOrElse ""

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

  /**
   * Check whether a URL starts with a protocol.
   *
   * We consider that a protocol consists only of ASCII letters and must be at least two
   * characters long, to avoid confusion with Windows drive letters.
   */
  def urlHasProtocol(urlString: String): Boolean = getProtocol(urlString) ne null

  def getProtocol(urlString: String): String = {
    val colonIndex = urlString.indexOf(":")

    // Require at least two characters in a protocol
    if (colonIndex < 2)
      return null

    // Check that there is a protocol made only of letters
    for (i <- 0 until colonIndex) {
      val c = urlString.charAt(i)
      if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z'))
        return null
    }
    urlString.substring(0, colonIndex)
  }

  private val PatternNoAmp = {
    val token = "[^=&]"
    Pattern.compile("(" + token + "+)=(" + token + "*)(?:&|(?<!&)\\z)")
  }

  private val PatternAmp = {
    val token = "[^=&]+"
    Pattern.compile("(" + token + ")=(" + token + ")?(?:&amp;|&|(?<!&amp;|&)\\z)")
  }

  def encodeQueryString[T](parameters: Iterable[(String, Array[T])])(implicit ed: UrlEncoderDecoder): String = {
    val sb    = new jl.StringBuilder(100)
    var first = true
    for {
      (key, values) <- parameters
      currentValue  <- values
      if currentValue.isInstanceOf[String]
    } locally {
      if (! first)
        sb.append('&')
      sb.append(ed.encode(key))
      sb.append('=')
      sb.append(ed.encode(currentValue.asInstanceOf[String]))
      first = false
    }
    sb.toString
  }

  /**
   * @param queryString a query string of the form n1=v1&n2=v2&... to decode.  May be null.
   * @return a Map of String[] indexed by name, an empty Map if the query string was null
   */
  def decodeQueryString(queryString: CharSequence)(implicit ed: UrlEncoderDecoder): util.Map[String, Array[String]] = {
    val result = new util.LinkedHashMap[String, Array[String]]
    if (queryString != null) {
      val matcher    = PatternNoAmp.matcher(queryString)
      var matcherEnd = 0
      while (matcher.find()) {
        matcherEnd = matcher.end
        val name  = ed.decode(matcher.group(1))
        val value = ed.decode(matcher.group(2))
        addValueToStringArrayMap(result, name, value)
      }
      if (queryString.length != matcherEnd) // There was garbage at the end of the query.
        throw new IllegalArgumentException(queryString.toString)
    }
    result
  }

  // This is a modified copy of decodeQueryString() above. Not sure why we need 2 versions! Try to avoid duplication!
  def decodeQueryStringPortlet(queryString: CharSequence)(implicit ed: UrlEncoderDecoder): util.Map[String, Array[String]] = {
    val result = new util.LinkedHashMap[String, Array[String]]
    if (queryString != null) {
      val matcher    = PatternAmp.matcher(queryString)
      var matcherEnd = 0
      while (matcher.find()) {
        matcherEnd = matcher.end
        var name   = ed.decode(matcher.group(1))
        val group2 = matcher.group(2)
        val value  = if (group2 != null) ed.decode(group2) else ""
        // Handle the case where the source contains &amp;amp; because of double escaping which does occur in
        // full Ajax updates!
        if (name.startsWith("amp;"))
          name = name.substring("amp;".length)
        // NOTE: Replace spaces with '+'. This is an artifact of the fact that URLEncoder/URLDecoder
        // are not fully reversible.
        addValueToStringArrayMap(result, name, value.replace(' ', '+'))
      }
      if (queryString.length != matcherEnd) // There was garbage at the end of the query.
        throw new IllegalArgumentException(queryString.toString)
    }
    result
  }

  def addValueToStringArrayMap(map: util.Map[String, Array[String]], name: String, value: String): Unit =
    map.put(name, map.get(name) :+ value)
}
