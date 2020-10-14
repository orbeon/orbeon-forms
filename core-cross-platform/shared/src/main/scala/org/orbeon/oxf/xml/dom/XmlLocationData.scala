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

import javax.xml.transform.SourceLocator
import org.orbeon.datatypes.{BasicLocationData, LocationData}
import org.orbeon.oxf.util.StringUtils._
import org.xml.sax.{Locator, SAXParseException}

object XmlLocationData {

  // 62 usages by processors and XML stuff
  def apply(locator: Locator): LocationData =
    BasicLocationData(locator.getSystemId, locator.getLineNumber, locator.getColumnNumber)

  // 1 usage in XSLT transformer
  def apply(sourceLocator: SourceLocator): LocationData =
    BasicLocationData(sourceLocator.getSystemId, sourceLocator.getLineNumber, sourceLocator.getColumnNumber)

  // 7 usages by processors and XML stuff
  def apply(exception: SAXParseException): LocationData =
    BasicLocationData(exception.getSystemId, exception.getLineNumber, exception.getColumnNumber)

  def createIfPresent(locator: Locator): LocationData =
    if (locator ne null) {
      val file = locator.getSystemId
      val line = locator.getLineNumber
      if (file.nonAllBlank && line != -1)
        BasicLocationData(file, line, locator.getColumnNumber)
      else
        null
    } else
      null
}
