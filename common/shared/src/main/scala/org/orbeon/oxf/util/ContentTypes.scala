/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.util

import org.orbeon.oxf.util.StringUtils._

object ContentTypes {

  val XmlContentType            = "application/xml"
  val JsonContentType           = "application/json"
  val HtmlContentType           = "text/html"
  val XhtmlContentType          = "application/xhtml+xml"
  val JavaScriptContentType     = "application/javascript"
  val CssContentType            = "text/css"
  val SoapContentType           = "application/soap+xml"
  val OctetStreamContentType    = "application/octet-stream"
  val UnknownContentType        = "content/unknown"

  val CharsetParameter          = "charset"
  val ActionParameter           = "action"

  val CssContentTypeWithCharset        = s"$CssContentType; $CharsetParameter=UTF-8"
  val JavaScriptContentTypeWithCharset = s"$JavaScriptContentType; $CharsetParameter=UTF-8"

  private val XmlTextContentType    = "text/xml"
  private val TextContentTypePrefix = "text/"
  private val XmlContentTypeSuffix  = "+xml"
  private val JsonContentTypeSuffix = "+json"

  def isXMLMediatype(mediatype: String): Boolean =
    mediatype.trimAllToOpt exists { trimmed =>
      trimmed == XmlTextContentType || trimmed == XmlContentType || trimmed.endsWith(XmlContentTypeSuffix)
    }

  def isXMLContentType(contentTypeOrNull: String): Boolean =
    getContentTypeMediaType(contentTypeOrNull) exists isXMLMediatype

  def isTextContentType(contentTypeOrNull: String): Boolean =
    contentTypeOrNull.trimAllToOpt exists { trimmed =>
      trimmed.startsWith(TextContentTypePrefix)
    }

  def isJSONMediatype(mediatype: String): Boolean =
    mediatype.trimAllToOpt exists { trimmed =>
      trimmed == JsonContentType || trimmed.endsWith(JsonContentTypeSuffix)
    }

  //@XPathFunction
  def isJSONContentType(contentTypeOrNull: String): Boolean =
    getContentTypeMediaType(contentTypeOrNull) exists isJSONMediatype

  def isTextOrJSONContentType(contentTypeOrNull: String): Boolean =
    isTextContentType(contentTypeOrNull) || isJSONContentType(contentTypeOrNull)

  def getContentTypeMediaType(contentTypeOrNull: String): Option[String] = {

    if (contentTypeOrNull eq null)
      return None

    val semicolonIndex = contentTypeOrNull.indexOf(";")
    if (semicolonIndex < 0)
      return contentTypeOrNull.trimAllToOpt

    contentTypeOrNull.substring(0, semicolonIndex).trimAllToOpt filterNot (_.equalsIgnoreCase(UnknownContentType))
  }

  def getContentTypeCharset(contentTypeOrNull: String): Option[String] =
      getContentTypeParameters(contentTypeOrNull).get(CharsetParameter)

  def getContentTypeParameters(contentTypeOrNull: String): Map[String, String] = {

    if (contentTypeOrNull eq null)
      return Map.empty

    val parametersIt = contentTypeOrNull.splitTo[Iterator](";").drop(1)

    if (! parametersIt.hasNext)
      return Map.empty // no parameters part

    val result =
      for {
        nameValue <- parametersIt
        equalIndex = nameValue.indexOf('=')
        if equalIndex >= 0
        name  = nameValue.substring(0, equalIndex).trimAllToEmpty
        value = nameValue.substring(equalIndex + 1).trimAllToEmpty
        if name.nonEmpty
      } yield
        name -> value

    result.toMap
  }

  // For Java callers
  def getContentTypeCharsetOrNull(contentTypeOrNull: String)  : String = getContentTypeCharset(contentTypeOrNull).orNull
  def getContentTypeMediaTypeOrNull(contentTypeOrNull: String): String = getContentTypeMediaType(contentTypeOrNull).orNull
}
