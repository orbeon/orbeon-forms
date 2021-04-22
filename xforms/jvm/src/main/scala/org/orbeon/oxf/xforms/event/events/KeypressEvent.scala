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
import org.orbeon.oxf.xforms.analysis.EventHandler
import org.orbeon.oxf.xforms.analysis.EventHandler.parseKeyModifiers
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget}
import org.orbeon.xforms.EventNames._

abstract class KeyboardEvent(name: String, target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(name, target, KeyboardEvent.filter(properties), bubbles = true, cancelable = false) {

  // NOTE: Not sure if should be cancelable, this seems to indicate that "special keys" should not
  // be cancelable: http://www.quirksmode.org/dom/events/keys.html
  // NOTE: For now, not an XFormsUIEvent because can also be targeted at XFormsContainingDocument

  override def matches(handler: EventHandler): Boolean =
    (handler.keyText.isEmpty || handler.keyText == property[String](KeyTextPropertyName)) && {
      handler.keyModifiers == keyModifiersSet
    }

  private lazy val keyModifiersSet = parseKeyModifiers(property[String](KeyModifiersPropertyName))

  def keyModifiers: String = property[String](KeyModifiersPropertyName).orNull
  def keyText: String = property[String](KeyTextPropertyName).orNull
}

class KeypressEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends KeyboardEvent(KeyPress, target, properties)

class KeydownEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends KeyboardEvent(KeyDown, target, properties)

class KeyupEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends KeyboardEvent(KeyUp, target, properties)

object KeyboardEvent {

  private val Properties = List(KeyModifiersPropertyName, KeyTextPropertyName)

  // Filter incoming properties
  private def filter(properties: PropertyGetter): PropertyGetter = new PropertyGetter {
    def isDefinedAt(name: String) = properties.isDefinedAt(name)
    def apply(name: String) = name match {
      case KeyModifiersPropertyName => properties(name) collect { case value: String if isNotBlank(value) => value.trimAllToEmpty }
      case KeyTextPropertyName      => properties(name) collect { case value: String if isNotEmpty(value) => value } // allow for e.g. " "
      case _                        => properties(name)
    }
  }

  val StandardProperties: Map[String, List[String]] = KeyboardEvents map (_ -> Properties) toMap
}
