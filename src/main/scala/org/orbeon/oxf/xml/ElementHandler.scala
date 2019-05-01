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


// Base class for all element handlers.
abstract class ElementHandler[HandlerContext](
  val uri            : String,
  val localname      : String,
  val qName          : String,
  val attributes     : Attributes,
  val handlerContext : HandlerContext
) {

  // Use of `attributes`:
  //
  // `handleAccessibilityAttributes`: `navindex`, `tabindex`, `accesskey`, `role` -> should be static
  // `xh:body` / `xh:head`: `startElement()`
  // `HandlerContext.getEffectiveId(attributes())` / `HandlerContext.getEffectiveId(attributes())`
  // getting `class` in `appendControlUserClasses`
  // `xxf:dialog`: `close`, `draggable`, `visible` -> get from static control!
  // `getInitialClasses`: `incremental`, `mediatype` -> should come from static
  // `xxf:dialog`, `XFormsLHHAHandler`: `start()`: copies all attributes in XHTML namespace
  // `XHTMLElementHandler`: AVTs
  //
  // Not all handlers need all attributes. Some information is known statically for example from
  // `ElementAnalysis` In the future, we should not have to copy all the `Attributes` all the time.

  // Override this to detect that the element has started.
  def start(): Unit = ()

  // Override this to detect that the element has ended.
  def end(): Unit = ()

  // Whether the body of the handled element may be repeated.
  def isRepeating: Boolean

  // Whether the body of the handled element must be processed.
  def isForwarding: Boolean
}

// Handler that doesn't do anything besides letting its content be processed.
final class TransparentHandler[HandlerContext](uri: String, localname: String, qName: String, attributes: Attributes, handlerContext: HandlerContext)
  extends ElementHandler[HandlerContext](uri, localname, qName, attributes, handlerContext) {

  def isRepeating  = false
  def isForwarding = true
}

// Handler that simply swallows its content and does nothing.
final class NullHandler[HandlerContext](uri: String, localname: String, qName: String, attributes: Attributes, handlerContext: HandlerContext)
  extends ElementHandler[HandlerContext](uri, localname, qName, attributes, handlerContext) {

  def isRepeating  = false
  def isForwarding = false
}