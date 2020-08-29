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
package org.orbeon.oxf.xml.dom

import org.orbeon.dom.io.{SAXContentHandler, SAXReader}
import org.orbeon.oxf.xml.XMLReceiver
import org.xml.sax.{Attributes, Locator}

class LocationSAXContentHandler
  extends SAXContentHandler(
    systemIdOpt         = None,
    mergeAdjacentText   = SAXReader.MergeAdjacentText,
    stripWhitespaceText = SAXReader.StripWhitespaceText,
    ignoreComments      = SAXReader.IgnoreComments
  ) with XMLReceiver {

  private var locator: Locator = null

  override def setDocumentLocator(locator: Locator): Unit = this.locator = locator

  override def startElement(
    namespaceURI  : String,
    localName     : String,
    qualifiedName : String,
    attributes    : Attributes
  ): Unit = {
    super.startElement(namespaceURI, localName, qualifiedName, attributes)
    val locationData = LocationData.createIfPresent(locator)
    if (locationData ne null)
      elementStack.get(elementStack.size - 1).setData(locationData)
  }
}