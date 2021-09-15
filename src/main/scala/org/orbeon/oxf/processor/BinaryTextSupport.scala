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
package org.orbeon.oxf.processor

import java.io.{BufferedInputStream, InputStream, InputStreamReader}
import java.{lang => jl}

import org.orbeon.dom._
import org.orbeon.oxf.common.Defaults
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.xml._
import org.xml.sax.ContentHandler
import org.xml.sax.helpers.AttributesImpl

object BinaryTextSupport {

  val TextDocumentElementName   = "document"
  val BinaryDocumentElementName = "document"

  /**
    * Generate a "standard" Orbeon text document.
    *
    * @param is           InputStream to read from
    * @param encoding     character encoding to use, or null for default
    * @param output       output ContentHandler to write text document to
    * @param contentType  optional content type to set as attribute on the root element
    * @param lastModified optional last modified timestamp
    */
  def readText(is: InputStream, encoding: String, output: ContentHandler, contentType: String, lastModified: jl.Long, statusCode: Int): Unit = {

    val encodingOrDefault = Option(encoding) getOrElse Defaults.DefaultEncodingForServletCompatibility

    outputStartDocument(output, contentType, lastModified, statusCode, XMLConstants.XS_STRING_QNAME, TextDocumentElementName)
    SAXUtils.readerToCharacters(new InputStreamReader(is, encodingOrDefault), output)
    outputEndDocument(output, TextDocumentElementName)
  }

  /**
    * Generate a "standard" Orbeon text document.
    *
    * @param text         String to read from
    * @param output       output ContentHandler to write text document to
    * @param contentType  optional content type to set as attribute on the root element
    * @param lastModified optional last modified timestamp
    */
  def readText(text: String, output: ContentHandler, contentType: String, lastModified: jl.Long): Unit = {
    outputStartDocument(output, contentType, lastModified, -1, XMLConstants.XS_STRING_QNAME, TextDocumentElementName)
    output.characters(text.toCharArray, 0, text.length)
    outputEndDocument(output, TextDocumentElementName)
  }

  /**
    * Generate a "standard" Orbeon binary document.
    *
    * @param is           InputStream to read from
    * @param output       output ContentHandler to write binary document to
    * @param contentType  optional content type to set as attribute on the root element
    * @param lastModified optional last modified timestamp
    * @param statusCode   optional status code, or -1 is to ignore
    * @param fileName     optional filename
    */
  def readBinary(
    is           : InputStream,
    output       : ContentHandler,
    contentType  : String,
    lastModified : jl.Long,
    statusCode   : Int,
    fileName     : String,
    headers      : List[(String, String)]
  ): Unit = {
    outputStartDocument(output, contentType, lastModified, statusCode, fileName, XMLConstants.XS_BASE64BINARY_QNAME, BinaryDocumentElementName, headers)
    SAXUtils.inputStreamToBase64Characters(new BufferedInputStream(is), output)
    outputEndDocument(output, BinaryDocumentElementName)
  }

  private val HeadersToFilter = Set(
    Headers.ContentTypeLower,
    Headers.LastModifiedLower,
    "status-code",
    "filename"
  )

  private def outputStartDocument(
    output          : ContentHandler,
    contentType     : String,
    lastModified    : jl.Long,
    statusCode      : Int,
    fileName        : String,
    xsiType         : QName,
    documentElement : String,
    headers         : List[(String, String)]
  ): Unit = {

    val attributes = new AttributesImpl
    attributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA", xsiType.qualifiedName)
    if (contentType ne null)
      attributes.addAttribute("", Headers.ContentTypeLower, Headers.ContentTypeLower, "CDATA", contentType)
    if (fileName ne null)
      attributes.addAttribute("", "filename", "filename", "CDATA", fileName)
    if (lastModified ne null)
      attributes.addAttribute("", Headers.LastModifiedLower, Headers.LastModifiedLower, "CDATA", DateUtils.formatRfc1123DateTimeGmt(lastModified))
    if (statusCode > 0)
      attributes.addAttribute("", "status-code", "status-code", "CDATA", Integer.toString(statusCode))

    if (headers ne null)
      headers.iterator filterNot (nameValue => HeadersToFilter(nameValue._1)) foreach { case (name, value) =>
        require((name ne null) && (value ne null))
        attributes.addAttribute("", name, name, "CDATA", value)
      }

    // Write document
    output.startDocument()
    output.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI)
    output.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI)
    output.startElement("", documentElement, documentElement, attributes)
  }

  private def outputStartDocument(
    output          : ContentHandler,
    contentType     : String,
    lastModified    : jl.Long,
    statusCode      : Int,
    xsiType         : QName,
    documentElement : String
  ): Unit =
    outputStartDocument(output, contentType, lastModified, statusCode, null, xsiType, documentElement, null)

  private def outputEndDocument(output: ContentHandler, documentElement: String): Unit = {
    output.endElement("", documentElement, documentElement)
    output.endPrefixMapping(XMLConstants.XSD_PREFIX)
    output.endPrefixMapping(XMLConstants.XSI_PREFIX)
    output.endDocument()
  }
}