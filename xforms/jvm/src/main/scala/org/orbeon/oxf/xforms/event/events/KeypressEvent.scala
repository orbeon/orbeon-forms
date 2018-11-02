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
package org.orbeon.oxf.xforms.event.events

import org.apache.commons.lang3.StringUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.{EventHandler, XFormsEvent, XFormsEventTarget}

class KeypressEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(KEYPRESS, target, KeypressEvent.filter(properties), bubbles = true, cancelable = false) {

  // NOTE: Not sure if should be cancelable, this seems to indicate that "special keys" should not
  // be cancelable: http://www.quirksmode.org/dom/events/keys.html
  // NOTE: For now, not an XFormsUIEvent because can also be targeted at XFormsContainingDocument

  import KeypressEvent._

  override def matches(handler: EventHandler): Boolean = {
    val handlerKeyModifiers = handler.getKeyModifiers
    val handlerKeyText = handler.getKeyText

    // NOTE: We check on an exact match for modifiers, should be smarter
    (handlerKeyModifiers == null || keyModifiers == handlerKeyModifiers) && (handlerKeyText == null || keyText == handlerKeyText)
  }

  def keyModifiers = property[String](ModifiersProperty).orNull
  def keyText      = property[String](TextProperty).orNull
}

object KeypressEvent {

  // Filter incoming properties
  private def filter(properties: PropertyGetter) = new PropertyGetter {
    def isDefinedAt(name: String) = properties.isDefinedAt(name)
    def apply(name: String) = name match {
      case ModifiersProperty ⇒ properties(name) collect { case value: String if isNotBlank(value) ⇒ value.trimAllToEmpty }
      case TextProperty      ⇒ properties(name) collect { case value: String if isNotEmpty(value) ⇒ value } // allow for e.g. " "
      case _                 ⇒ properties(name)
    }
  }

  val ModifiersProperty = XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME.localName
  val TextProperty      = XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME.localName

  val StandardProperties = Map(KEYPRESS → Seq(ModifiersProperty, TextProperty))
}