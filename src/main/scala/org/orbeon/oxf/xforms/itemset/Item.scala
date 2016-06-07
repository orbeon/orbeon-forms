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
package org.orbeon.oxf.xforms.itemset

import org.orbeon.dom.QName
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.control.LHHAValue
import org.orbeon.oxf.xml.dom4j.LocationData

import scala.collection.JavaConverters._

/**
 * Represents an item (xf:item, xf:choice, or item in itemset).
 */
case class Item(
  label      : LHHAValue,
  help       : Option[LHHAValue],
  hint       : Option[LHHAValue],
  value      : String,
  attributes : List[(QName, String)])(val
  position   : Int)
extends ItemContainer {

  require(help.isEmpty || help.get.label.nonEmpty)
  require(hint.isEmpty || hint.get.label.nonEmpty)
  require(attributes ne null)

  // FIXME 2013-11-26: value is null at least in some unit tests. It shouldn't be.
  //require(value ne null)

  // FIXME 2010-08-18: label can be null in these cases:
  //
  // - xf:choice with (see XFormsUtils.getElementValue())
  //   - single-node binding that doesn't point to an acceptable item
  //   - value attribute but the evaluation context is empty
  //   - exception when dereferencing an @src attribute
  // - xf|input:xxf-type(xs:boolean)

  def jAttributes = attributes.asJava

  def classAttribute = attributes find (_._1 == XFormsConstants.CLASS_QNAME) map (_._2)

  def externalValue(encode: Boolean)   = Option(value) map (v ⇒ if (encode) position.toString else v) getOrElse ""
  def javaScriptValue(encode: Boolean) = escapeJavaScript(externalValue(encode))

  def javaScriptLabel(locationData: LocationData) =
    Option(label) map (_.javaScriptValue(locationData)) getOrElse ""

  def javaScriptHelp(locationData: LocationData) =
    help map (_.javaScriptValue(locationData))

  def javaScriptHint(locationData: LocationData) =
    hint map (_.javaScriptValue(locationData))

  // Implement equals by hand because children are not part of the case class
  // NOTE: The compiler does not generate equals for case classes that come with an equals! So can't use super to
  // reach compiler-generated case class equals. Is there a better way?
  override def equals(other: Any) = other match {
    case other: Item ⇒
      label                         == other.label         &&
      help                          == other.help          &&
      hint                          == other.hint          &&
      externalValue(encode = false) == other.externalValue(encode = false) &&
      attributes                    == other.attributes    &&
      super.equals(other)
    case _ ⇒ false
  }

  def iterateLHHA =
    Iterator(
      Option(label) map ("label" →),
      help          map ("help"  →),
      hint          map ("hint"  →)
    ).flatten
}

object Item {
  def apply(
    position   : Int,
    isMultiple : Boolean,
    attributes : List[(QName, String)],
    label      : LHHAValue,
    help       : Option[LHHAValue],
    hint       : Option[LHHAValue],
    value      : String
  ): Item =
    Item(label, help, hint, value, attributes)(position)
}