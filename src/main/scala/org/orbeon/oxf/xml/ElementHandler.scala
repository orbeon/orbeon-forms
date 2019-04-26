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

import scala.xml.SAXException

// Base class for all element handlers.
abstract class ElementHandler(
  val uri            : String,
  val localname      : String,
  val qName          : String,
  val attributes     : Attributes,
  val matched        : Any,
  val handlerContext : Any
) {

  // Override this to detect that the element has started.
  @throws[SAXException]
  def start(): Unit = ()

  // Override this to detect that the element has ended.
  @throws[SAXException]
  def end(): Unit = ()

  // Whether the body of the handled element may be repeated.
  def isRepeating: Boolean

  // Whether the body of the handled element must be processed.
  def isForwarding: Boolean
}

// NOTE: This is probably un uninformative name. See also `NullHandler`.
final class NullElementHandler(uri: String, localname: String, qName: String, attributes: Attributes, matched: Any, handlerContext: Any)
  extends ElementHandler(uri, localname, qName, attributes, matched, handlerContext) {

  def isRepeating  = false
  def isForwarding = true
}

// Handler that simply swallows its content and does nothing.
class NullHandler(uri: String, localname: String, qName: String, attributes: Attributes, matched: Any, handlerContext: Any)
  extends ElementHandler(uri, localname, qName, attributes, matched, handlerContext) {

  def isRepeating  = false
  def isForwarding = false
}