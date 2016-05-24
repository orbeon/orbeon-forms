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
package org.orbeon.oxf.xml.dom4j

import javax.xml.transform.SourceLocator

import org.apache.commons.lang3.StringUtils
import org.xml.sax.{Locator, SAXParseException}

object LocationData {
  def createIfPresent(locator: Locator): LocationData =
    if (locator ne null) {
      val filename = locator.getSystemId
      val line = locator.getLineNumber
      if (StringUtils.isNotBlank(filename) && line != -1)
        LocationData(filename, line, locator.getColumnNumber)
      else
        null
    } else
      null
}

case class LocationData(systemID: String, line: Int, col: Int) {

  def this(locator: Locator) =
    this(locator.getSystemId, locator.getLineNumber, locator.getColumnNumber)

  def this(sourceLocator: SourceLocator) =
    this(sourceLocator.getSystemId,sourceLocator.getLineNumber, sourceLocator.getColumnNumber)

  def this(exception: SAXParseException) =
    this(exception.getSystemId, exception.getLineNumber, exception.getColumnNumber)

  // TODO: get rid of those once callers are changed
  def getSystemID = systemID
  def getLine = line
  def getCol = col

  override def toString = {
    val sb = new java.lang.StringBuilder

    val hasLine =
      if (line > 0) {
        sb.append("line ")
        sb.append(line.toString)
        true
      } else {
        false
      }

    val hasColumn =
      if (col > 0) {
        if (hasLine)
          sb.append(", ")
        sb.append("column ")
        sb.append(col.toString)
        true
      } else {
        false
      }

    if (systemID ne null) {
      if (hasLine || hasColumn)
        sb.append(" of ")

      sb.append(systemID)
    }

    sb.toString
  }
}