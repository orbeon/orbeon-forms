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

import java.io._
import java.util.{Enumeration ⇒ JEnumeration, Map ⇒ JMap}
import javax.servlet.ServletInputStream
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper}

import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.CollectionUtils._

import scala.collection.JavaConverters._

// Request wrapper for forwarding a request which simulates a server-side redirect.
class ForwardServletRequestWrapper(
  request   : HttpServletRequest,
  pathQuery : String
) extends BaseServletRequestWrapper(request)
  with RequestPathQuery
  with RequestRemoveHeaders
  with RequestEmptyBody {

  import BaseServletRequestWrapper._

  // "Constructors" for traits
  def overriddenPathQuery = pathQuery
  def headersToRemove     = HeadersToFilter

  override def getMethod = "GET"
}

// See also: LocalRequest, which does much of this based on ExternalContext
abstract class BaseServletRequestWrapper(val request: HttpServletRequest)
  extends HttpServletRequestWrapper(request) {

  // Clean-up API by adding type parameters
  override def getHeaders(name: String) = super.getHeaders(name).asInstanceOf[JEnumeration[String]]
  override def getHeaderNames           = super.getHeaderNames.asInstanceOf[JEnumeration[String]]
}

trait RequestPathQuery extends BaseServletRequestWrapper {

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

trait RequestPrependHeaders extends BaseServletRequestWrapper {

  def headersToPrepend: Map[String, Array[String]]

  override def getHeaderNames =
    (headersToPrepend.keysIterator ++ (super.getHeaderNames.asScala filterNot headersToPrepend.keySet))
      .asJavaEnumeration

  override def getHeader(name: String) =
    addedHeaderOption(name) getOrElse super.getHeader(name)

  override def getHeaders(name: String) =
    headersToPrepend.get(name) map (_.iterator.asJavaEnumeration) getOrElse super.getHeaders(name)

  override def getDateHeader(name: String) =
    addedHeaderOption(name) map DateUtils.parseRFC1123 getOrElse super.getDateHeader(name)

  override def getIntHeader(name: String) =
    addedHeaderOption(name) map (_.toInt) getOrElse super.getIntHeader(name)

  private def addedHeaderOption(name: String) =
    headersToPrepend.get(name) filter (_.nonEmpty) map (_(0))
}

trait RequestRemoveHeaders extends BaseServletRequestWrapper {

  def headersToRemove: String ⇒ Boolean

  override def getHeaderNames = (super.getHeaderNames.asScala filterNot headersToRemove).asJavaEnumeration

  override def getHeader(name: String)     = if (headersToRemove(name)) null else super.getHeader(name)
  override def getHeaders(name: String)    = if (headersToRemove(name)) null else super.getHeaders(name)
  override def getDateHeader(name: String) = if (headersToRemove(name)) -1   else super.getDateHeader(name)
  override def getIntHeader(name: String)  = if (headersToRemove(name)) -1   else super.getIntHeader(name)
}

trait RequestEmptyBody extends BaseServletRequestWrapper {

  import BaseServletRequestWrapper._

  private lazy val inputStream  = newEmptyServletInputStream
  private lazy val reader       = newEmptyReader

  override def getInputStream   = inputStream
  override def getReader        = reader

  override def getContentType   = null
  override def getContentLength = -1

  // TODO: filter headers content-type and content-length
}

object BaseServletRequestWrapper {

  var HeadersToFilter= Set(Headers.ContentLengthLower, Headers.ContentTypeLower) // TODO: filtering as in Headers?

  def appendExtraQueryParameters(pathQuery: String, extraQueryParameters: JMap[String, Array[String]]) = {

    def extraQueryParametersIterator =
      for {
        (name, values) ← extraQueryParameters.asScala.iterator
        value          ← values
      } yield
        name → value

    recombineQuery(pathQuery, extraQueryParametersIterator.toList)
  }

  def newEmptyInputStream = new ByteArrayInputStream(Array[Byte]())
  def newEmptyReader      = new BufferedReader(new StringReader(""))

  def newEmptyServletInputStream = {
    val is = newEmptyInputStream
    new ServletInputStream {
      def read() = is.read()
    }
  }
}