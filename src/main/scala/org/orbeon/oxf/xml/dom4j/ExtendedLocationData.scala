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
package org.orbeon.oxf.xml.dom4j

import org.orbeon.dom.Element

import scala.collection.compat._
import scala.collection.immutable

/**
 * LocationData information with additional information.
 */
class ExtendedLocationData private (
    systemID              : String,
    line                  : Int,
    col                   : Int,
    val description       : Option[String],
    paramsAllowNullValues : immutable.Seq[(String, String)]
) extends LocationData(systemID, line, col) {

  val params = paramsAllowNullValues filter (_._2 ne null)

  // With description
  def this(systemID: String, line: Int, col: Int, description: String) =
    this(systemID, line, col, Option(description), Nil)

  // With LocationData, description, params and element
  def this(locationData: LocationData, description: Option[String] = None, params: immutable.Seq[(String, String)] = Nil, element: Option[Element] = None) =
    this(
      Option(locationData) map (_.file) orNull,
      Option(locationData) map (_.line) getOrElse -1,
      Option(locationData) map (_.col) getOrElse -1,
      description,
      (if (element.isDefined) List("element" -> (element map (_.toDebugString) get)) else Nil) ++: params
    )

  // For Java callers: with LocationData, description, element and parameters
  def this(locationData: LocationData, description: String, element: Element, params: Array[String]) =
    this(
      locationData,
      Option(description),
      ExtendedLocationData.arrayToPairs(params),
      Option(element)
    )

  // For Java callers: with LocationData and description
  def this(locationData: LocationData, description: String) =
    this(locationData, Some(description))

  // For Java callers: with LocationData, description and element
  def this(locationData: LocationData, description: String, element: Element) =
    this(locationData, Option(description), element = Option(element))

  // For Java callers: with LocationData, description and parameters
  def this(locationData: LocationData, description: String, params: Array[String]) =
    this(locationData, description, null: Element, params)

  // The element, if any, represented as a String
  def elementString = params.toMap.get("element")

  // For Java callers
  def getDescription               = description.orNull
  def getElementString             = getElementDebugString
  def getElementDebugString        = elementString.orNull
  def getParameters: Array[String] = params.iterator.flatMap(p => Array(p._1, p._2)).to(Array)

  override def toString = {

    def parametersString =
      params collect { case (k, v) if v ne null => s"$k='$v'" } mkString ", "

    super.toString + (
      if (description.isDefined || params.nonEmpty)
        " (" + (description getOrElse "") + (if (params.nonEmpty) (": " + parametersString) else "") + ")"
      else
        ""
    )
  }
}

object ExtendedLocationData {
  def arrayToPairs(a: Seq[String]) = Option(a ensuring (_.size % 2 == 0)) match {
    case Some(a) => a.grouped(2).toList collect { case Seq(k, v) => k -> v }
    case None    => Nil
  }
}