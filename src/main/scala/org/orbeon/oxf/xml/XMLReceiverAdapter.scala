/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.xml.sax.{Attributes, Locator}

class XMLReceiverAdapter extends XMLReceiver {
  def characters           (ch: Array[Char], start: Int, length: Int)                                : Unit = ()
  def endDocument          ()                                                                        : Unit = ()
  def endElement           (namespaceURI: String, localName: String, qName: String)                  : Unit = ()
  def endPrefixMapping     (prefix: String)                                                          : Unit = ()
  def ignorableWhitespace  (ch: Array[Char], start: Int, length: Int)                                : Unit = ()
  def processingInstruction(target: String, data: String)                                            : Unit = ()
  def setDocumentLocator   (locator: Locator)                                                        : Unit = ()
  def skippedEntity        (name: String)                                                            : Unit = ()
  def startDocument        ()                                                                        : Unit = ()
  def startElement         (namespaceURI: String, localName: String, qName: String, atts: Attributes): Unit = ()
  def startPrefixMapping   (prefix: String, uri: String)                                             : Unit = ()
  def startDTD             (name: String, publicId: String, systemId: String)                        : Unit = ()
  def endDTD               ()                                                                        : Unit = ()
  def startEntity          (name: String)                                                            : Unit = ()
  def endEntity            (name: String)                                                            : Unit = ()
  def startCDATA           ()                                                                        : Unit = ()
  def endCDATA             ()                                                                        : Unit = ()
  def comment              (ch: Array[Char], start: Int, length: Int)                                : Unit = ()
}