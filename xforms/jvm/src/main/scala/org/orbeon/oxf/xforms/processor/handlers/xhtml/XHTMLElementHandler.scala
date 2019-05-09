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

import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler
import org.xml.sax.{Attributes, SAXException}

/**
  * Handle xhtml:* for handling AVTs as well as rewriting @id and @for.
  */
object XHTMLElementHandler {
  val REF_ID_ATTRIBUTE_NAMES = Array("for")
}

class XHTMLElementHandler(uri: String, localname: String, qName: String, localAtts: Attributes, matched: Any, handlerContext: Any)
  extends XFormsBaseHandlerXHTML(uri, localname, qName, localAtts, matched, handlerContext, false, true) {

  @throws[SAXException]
  override def start(): Unit = {
    xformsHandlerContext.getController.getOutput.startElement(
      uri,
      localname,
      qName,
      XFormsBaseHandler.handleAVTsAndIDs(attributes, XHTMLElementHandler.REF_ID_ATTRIBUTE_NAMES, xformsHandlerContext)
    )
  }

  @throws[SAXException]
  override def end(): Unit = {
    xformsHandlerContext.getController.getOutput.endElement(uri, localname, qName)
  }
}