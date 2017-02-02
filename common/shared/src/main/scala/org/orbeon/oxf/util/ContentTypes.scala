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

  val XmlContentType  = "application/xml"
  val JsonContentType = "application/json"
  val HtmlContentType = "text/html"
  val SoapContentType = "application/soap+xml"

  private val XmlTextContentType    = "text/xml"
  private val XmlContentTypeSuffix  = "+xml"
  private val TextContentTypePrefix = "text/"

  // Whether the given mediatype is considered as XML.
  // TODO: Handle `text/html; charset=foobar`.
  def isXMLMediatype(mediatype: String): Boolean =
    mediatype.trimAllToOpt exists { trimmed ⇒
      trimmed == XmlTextContentType || trimmed == XmlContentType || trimmed.endsWith(XmlContentTypeSuffix)
    }

  // Return whether the given content type is considered as text.
  def isTextContentType(contentType: String): Boolean =
    contentType.trimAllToOpt exists { trimmed ⇒
      trimmed.startsWith(TextContentTypePrefix)
    }

  def isJSONContentType(contentType: String): Boolean =
    contentType.trimAllToOpt exists { trimmed ⇒
      contentType.startsWith(JsonContentType)
    }

  def isTextOrJSONContentType(contentType: String): Boolean =
    isTextContentType(contentType) || isJSONContentType(contentType)

  def getContentTypeCharset(contentType: String): Option[String] =
      getContentTypeParameters(contentType).get("charset")

  def getContentTypeParameters(contentTypeOrNull: String): Map[String, String] = {

    if (contentTypeOrNull eq null)
      return Map.empty

    val parametersIt = contentTypeOrNull.splitTo[Iterator](";").drop(1)

    if (! parametersIt.hasNext)
      return Map.empty // no parameters part

    val result =
      for {
        nameValue ← parametersIt
        equalIndex = nameValue.indexOf('=')
        if equalIndex >= 0
        name  = nameValue.substring(0, equalIndex).trimAllToEmpty
        value = nameValue.substring(equalIndex + 1).trimAllToEmpty
        if name.nonEmpty
      } yield
        name → value

    result.toMap
  }

  def getContentTypeMediaType(contentTypeOrNull: String): Option[String] = {

    if (contentTypeOrNull eq null)
      return None

    val semicolonIndex = contentTypeOrNull.indexOf(";")
    if (semicolonIndex < 0)
      return contentTypeOrNull.trimAllToOpt

    contentTypeOrNull.substring(0, semicolonIndex).trimAllToOpt filterNot (_.equalsIgnoreCase("content/unknown"))
  }

  // For Java callers
  def getContentTypeCharsetOrNull(contentType: String)        : String = getContentTypeCharset(contentType).orNull
  def getContentTypeMediaTypeOrNull(contentTypeOrNull: String): String = getContentTypeMediaType(contentTypeOrNull).orNull
}
