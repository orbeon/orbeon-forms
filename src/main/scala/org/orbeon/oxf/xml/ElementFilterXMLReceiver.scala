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
package org.orbeon.oxf.xml

import org.xml.sax.Attributes


class ElementFilterXMLReceiver(
  xmlReceiver : XMLReceiver,
  keep        : (Int, String, String, Attributes) => Boolean
) extends SimpleForwardingXMLReceiver(xmlReceiver) {

  private var level: Int = 0
  private var filterLevel: Int = -1

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
    if (filterLevel == -1) {
      if (keep(level, uri, localname, attributes))
        super.startElement(uri, localname, qName, attributes)
      else
        filterLevel = level
    }
    level += 1
  }

  override def endElement(uri: String, localname: String, qName: String): Unit = {
    level -= 1
    if (filterLevel == level)
      filterLevel = -1
    else if (filterLevel == -1)
      super.endElement(uri, localname, qName)
  }

  override def startPrefixMapping(s: String, s1: String)                        : Unit = if (filterLevel == -1) super.startPrefixMapping(s, s1)
  override def endPrefixMapping(s: String)                                      : Unit = if (filterLevel == -1) super.endPrefixMapping(s)
  override def ignorableWhitespace(chars: Array[Char], start: Int, length: Int) : Unit = if (filterLevel == -1) super.ignorableWhitespace(chars, start, length)
  override def characters(chars: Array[Char], start: Int, length: Int)          : Unit = if (filterLevel == -1) super.characters(chars, start, length)
  override def skippedEntity(s: String)                                         : Unit = if (filterLevel == -1) super.skippedEntity(s)
  override def processingInstruction(s: String, s1: String)                     : Unit = if (filterLevel == -1) super.processingInstruction(s, s1)
}