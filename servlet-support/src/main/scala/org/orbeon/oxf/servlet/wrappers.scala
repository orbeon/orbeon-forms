/**
 * Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.servlet

import cats.data.NonEmptyList
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util._

import java.io._
import java.{util => ju}
import scala.jdk.CollectionConverters._


// Request wrapper for forwarding a request which simulates a server-side redirect.
class ForwardServletRequestWrapper(
  request   : HttpServletRequest,
  pathQuery : String
) extends HttpServletRequestWrapper(request)
  with RequestPathQuery
  with RequestRemoveHeaders
  with RequestEmptyBody {

  import ServletRequestWrapper._

  // "Constructors" for traits
  def overriddenPathQuery = pathQuery
  override def headersToRemoveAsSet: Set[String] = HeadersToFilter

  override def getMethod = "GET"
}

trait RequestPathQuery extends HttpServletRequestWrapper {

  def overriddenPathQuery: String // will be called only once

  // 2014-09-22: Was on BaseServletRequestWrapper which was not good. See also comment in LocalRequest.
  override def getServletPath = ""

  private lazy val (pathInfo, queryString) = splitQuery(overriddenPathQuery)
  private lazy val params = combineValues[String, String, Array](queryString.toList flatMap decodeSimpleQuery).toMap

  override def getParameterMap                  = params.asJava
  override def getParameterNames                = params.keys.iterator.asJavaEnumeration
  override def getParameterValues(name: String) = params.get(name).orNull
  override def getParameter(name: String)       = params.get(name) flatMap (_.headOption) orNull

  override def getPathInfo    = pathInfo
  override def getQueryString = queryString getOrElse ""
}

trait RequestPrependHeaders extends HttpServletRequestWrapper {

  def headersToPrependAsMap: Map[String, NonEmptyList[String]]

  override def getHeaderNames: ju.Enumeration[String] =
    (headersToPrependAsMap.keysIterator ++ (super.getHeaderNames.asScala filterNot mustPrependHeader))
      .asJavaEnumeration

  override def getHeader(name: String): String =
    addedHeaderSingleOption(name) getOrElse super.getHeader(name)

  override def getHeaders(name: String): ju.Enumeration[String] =
    addedHeaderOption(name) map (_.iterator.asJavaEnumeration) getOrElse super.getHeaders(name)

  override def getDateHeader(name: String): Long =
    addedHeaderSingleOption(name) map DateUtils.parseRFC1123 getOrElse super.getDateHeader(name)

  override def getIntHeader(name: String): Int =
    addedHeaderSingleOption(name) map (_.toInt) getOrElse super.getIntHeader(name)

  private def mustPrependHeader(name: String): Boolean =
    headersToPrependAsMap.keysIterator.exists(_.equalsIgnoreCase(name))

  private def addedHeaderOption(name: String): Option[NonEmptyList[String]] =
    headersToPrependAsMap
      .collectFirst { case (key, value) if key.equalsIgnoreCase(name) => value }

  private def addedHeaderSingleOption(name: String): Option[String] =
    addedHeaderOption(name).map(_.head)
}

trait RequestRemoveHeaders extends HttpServletRequestWrapper {

  // Override this if you have a `Set` of headers to remove
  def headersToRemoveAsSet: Set[String] = Set.empty

  // Override this for more control
  // The default implementation uses `headersToRemoveAsSet` and does a case-insensitive comparison
  def mustRemoveHeader(name: String): Boolean =
    headersToRemoveAsSet.exists(_.equalsIgnoreCase(name))

  override def getHeaderNames             : ju.Enumeration[String] = (super.getHeaderNames.asScala filterNot mustRemoveHeader).asJavaEnumeration

  override def getHeader    (name: String): String                 = if (mustRemoveHeader(name)) null else super.getHeader(name)
  override def getHeaders   (name: String): ju.Enumeration[String] = if (mustRemoveHeader(name)) null else super.getHeaders(name)
  override def getDateHeader(name: String): Long                   = if (mustRemoveHeader(name)) -1   else super.getDateHeader(name)
  override def getIntHeader (name: String): Int                    = if (mustRemoveHeader(name)) -1   else super.getIntHeader(name)
}

trait RequestEmptyBody extends HttpServletRequestWrapper {

  import ServletRequestWrapper._

  private lazy val inputStream  = newEmptyInputStream
  private lazy val reader       = newEmptyReader

  override def getInputStream: InputStream = inputStream
  override def getReader: BufferedReader = reader

  override def getContentType: String = null
  override def getContentLength: Int = -1

  // TODO: filter headers content-type and content-length
}

private object ServletRequestWrapper {

  var HeadersToFilter = Set(Headers.ContentLengthLower, Headers.ContentTypeLower) // TODO: filtering as in Headers?

  def appendExtraQueryParameters(pathQuery: String, extraQueryParameters: ju.Map[String, Array[String]]) = {

    def extraQueryParametersIterator =
      for {
        (name, values) <- extraQueryParameters.asScala.iterator
        value          <- values
      } yield
        name -> value

    recombineQuery(pathQuery, extraQueryParametersIterator.toList)
  }

  def newEmptyReader      = new BufferedReader(new StringReader(""))
  def newEmptyInputStream = new ByteArrayInputStream(Array[Byte]())
}