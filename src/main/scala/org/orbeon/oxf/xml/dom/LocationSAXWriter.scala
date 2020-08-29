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

import org.orbeon.dom.io.SAXWriter
import org.orbeon.dom.{Document, Element}
import org.xml.sax.Locator

class LocationSAXWriter extends SAXWriter {

  private var _currentLocationData: LocationData = null

  override protected def createDocumentLocator(document: Document): Option[Locator] =
    Some(
      new Locator {

        def getSystemId: String =
          if (_currentLocationData eq null)
            null
          else
            _currentLocationData.file

        def getLineNumber: Int =
          if (_currentLocationData eq null)
            -1
          else
            _currentLocationData.line

        def getColumnNumber: Int =
          if (_currentLocationData eq null)
            -1
          else
            _currentLocationData.col

        def getPublicId = null
      }
    )

  override protected def startElement(element: Element): Unit = {

    _currentLocationData =
      element.getData match {
        case locationData: LocationData => locationData
        case _ => null
      }

    super.startElement(element)
  }
}
