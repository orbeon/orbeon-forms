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

import cats.syntax.option._
import org.orbeon.dom.QName
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsNames


sealed trait ItemNode extends ItemContainer with ItemNodeImpl {
  val label      : LHHAValue
  val attributes : List[(QName, String)]
  val position   : Int
  def iterateLHHA: Iterator[(String, LHHAValue)]
}

object Item {

  type Value[T <: om.Item] = String Either List[T]

  case class ValueNode(
    label      : LHHAValue,
    help       : Option[LHHAValue],
    hint       : Option[LHHAValue],
    value      : Item.Value[om.Item],
    attributes : List[(QName, String)])(val
    position   : Int
  ) extends
    ItemNode with ItemLeafImpl

  case class ChoiceNode(
    label      : LHHAValue,
    attributes : List[(QName, String)])(val
    position   : Int
  ) extends
    ItemNode with ChoiceLeafImpl
}

sealed trait ItemNodeImpl {

  self: ItemNode =>

  require(attributes ne null)

  def javaScriptLabel(locationData: LocationData): String =
    label.javaScriptValue(locationData)

  def classAttribute: Option[String] =
    attributes find (_._1 == XFormsNames.CLASS_QNAME) map (_._2)
}

sealed trait ItemLeafImpl {

  self: Item.ValueNode =>

  // We allow the empty string for label value, but somehow we don't allow this for help and hint
  require(help.isEmpty  || help.exists(_.label.nonEmpty))
  require(hint.isEmpty  || hint.exists(_.label.nonEmpty))
  require(value ne null)

  // `label` can be `None` in these cases:
  //
  // - `xf:choices` with (see `XFormsUtils.getElementValue()`)
  //   - single-node binding that doesn't point to an acceptable item
  //   - value attribute but the evaluation context is empty
  //   - exception when dereferencing an @src attribute
  // - `xf|input:xxf-type(xs:boolean)`

  def externalValue(encode: Boolean): String =
    if (encode)
      position.toString
    else
      value match {
        case Left(v)  => v
        case Right(_) => position.toString
      }

  def javaScriptValue(encode: Boolean): String =
    externalValue(encode).escapeJavaScript

  def javaScriptHelp(locationData: LocationData): Option[String] =
    help map (_.javaScriptValue(locationData))

  def javaScriptHint(locationData: LocationData): Option[String] =
    hint map (_.javaScriptValue(locationData))

  def iterateLHHA: Iterator[(String, LHHAValue)] =
    Iterator(
      ("label" -> label).some,
      help  map ("help"  ->),
      hint  map ("hint"  ->)
    ).flatten

  // Implement equals by hand because children are not part of the case class
  // NOTE: The compiler does not generate equals for case classes that come with an equals! So can't use super to
  // reach compiler-generated case class equals. Is there a better way?
  override def equals(other: Any): Boolean = other match {
    case other: Item.ValueNode =>
      label                         == other.label                         &&
      help                          == other.help                          &&
      hint                          == other.hint                          &&
      externalValue(encode = false) == other.externalValue(encode = false) &&
      attributes                    == other.attributes                    &&
      super.equals(other)
    case _ => false
  }
}

sealed trait ChoiceLeafImpl {

  self: Item.ChoiceNode =>

  def iterateLHHA: Iterator[(String, LHHAValue)] =
    Iterator("label" -> label)

  override def equals(other: Any): Boolean = other match {
    case other: Item.ChoiceNode =>
      label      == other.label      &&
      attributes == other.attributes &&
      super.equals(other)
    case _ => false
  }
}
