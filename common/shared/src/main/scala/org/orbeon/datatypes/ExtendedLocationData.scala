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
package org.orbeon.datatypes

import scala.collection.compat._
import scala.collection.immutable


case class ExtendedLocationData private (
  file        : String,
  line        : Int,
  col         : Int,
  description : Option[String],
  params      : immutable.Seq[(String, String)]
) extends LocationData {

  // For Java callers
  def getDescription        : String        = description.orNull
  def getElementDebugString : String        = params collectFirst { case ("element", v) => v } orNull
  def getParameters         : Array[String] = params.iterator.flatMap(p => Array(p._1, p._2)).to(Array)

  override def toString: String =
    ExtendedLocationData.asString(this)
}

object ExtendedLocationData {

  def apply(
    systemID              : String,
    line                  : Int,
    col                   : Int,
    description           : Option[String],
    paramsAllowNullValues : immutable.Seq[(String, String)]
  ): ExtendedLocationData =
    new ExtendedLocationData(
      systemID,
      line,
      col,
      description,
      paramsAllowNullValues filter (_._2 ne null)
    )

  def asString(ld: ExtendedLocationData): String = {

    def parametersString =
      ld.params collect { case (k, v) if v ne null => s"$k='$v'" } mkString ", "

    LocationData.asString(ld) + (
      if (ld.description.isDefined || ld.params.nonEmpty)
        " (" + (ld.description getOrElse "") + (if (ld.params.nonEmpty) (": " + parametersString) else "") + ")"
      else
        ""
    )
  }
}