/**
 * Copyright (C) 2007 Orbeon, Inc.
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

import org.orbeon.datatypes.{ExtendedLocationData, LocationData}
import org.orbeon.dom.Element

import scala.collection.immutable

object XmlExtendedLocationData {

  // 1 Java caller
  def apply(systemID: String, line: Int, col: Int, description: String): ExtendedLocationData =
    ExtendedLocationData(systemID, line, col, Option(description), Nil)

  // 8 Scala callers
  def apply(
    locationData : LocationData, // FIXME: Some `null` callers!
    description  : Option[String]                  = None,
    params       : immutable.Seq[(String, String)] = Nil,
    element      : Option[Element]                 = None
  ): ExtendedLocationData =
    ExtendedLocationData(
      Option(locationData) map (_.file) orNull,
      Option(locationData) map (_.line) getOrElse -1,
      Option(locationData) map (_.col) getOrElse -1,
      description,
      ((element map (e => List("element" -> e.toDebugString)) getOrElse Nil) ++: params) filter (_._2 ne null)
    )

  // 14 Java callers
  def apply(
    locationData : LocationData,
    description  : String,
    element      : Element,
    params       : Array[String]
  ): ExtendedLocationData =
    apply(
      locationData,
      Option(description),
      seqToPairs(params),
      Option(element)
    )

  // 1 Java caller
  def apply(locationData: LocationData, description: String): ExtendedLocationData =
    apply(locationData, Option(description))

  // 2 Java callers
  def apply(locationData: LocationData, description: String, params: Array[String]): ExtendedLocationData =
    apply(locationData, description, null: Element, params)

  private def seqToPairs(a: Seq[String]): List[(String, String)] =
    Option(a ensuring (_.size % 2 == 0)) match {
      case Some(a) => a.grouped(2).toList collect { case Seq(k, v) => k -> v }
      case None    => Nil
    }
}
