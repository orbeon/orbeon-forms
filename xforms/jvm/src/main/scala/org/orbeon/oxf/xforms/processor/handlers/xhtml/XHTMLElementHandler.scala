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
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.XMLReceiverSupport.{element, _}
import org.orbeon.oxf.xml.{XMLConstants, XMLNames, XMLReceiver}
import org.xml.sax.Attributes


object XHTMLElementHandler {

  val RefIdAttributeNames: Array[String] = Array("for")

  def outputXInclude(href: String)(implicit xmlReceiver: XMLReceiver): Unit =
    element(
      localName = "include",
      uri       = XMLNames.XIncludeURI,
      atts      = List("href" -> href, "fixup-xml-base" -> "false")
    )

  def outputHiddenField(htmlPrefix: String, name: String, value: String)(implicit xmlReceiver: XMLReceiver): Unit =
    element(
      localName = "input",
      prefix    = htmlPrefix,
      uri       = XMLConstants.XHTML_NAMESPACE_URI,
      atts      = List("type" -> "hidden", "name" -> name, "value" -> value)
    )
}

// Handle `xh:*` for handling AVTs as well as rewriting `@id` and `@for`.
class XHTMLElementHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  handlerContext : HandlerContext
) extends
  XFormsBaseHandlerXHTML(
    uri,
    localname,
    qName,
    localAtts,
    handlerContext,
    repeating  = false,
    forwarding = true
  ) {

  override def start(): Unit =
    handlerContext.controller.output.startElement(
      uri,
      localname,
      qName,
      XFormsBaseHandler.handleAVTsAndIDs(attributes, XHTMLElementHandler.RefIdAttributeNames, handlerContext)
    )

  override def end(): Unit =
    handlerContext.controller.output.endElement(uri, localname, qName)
}