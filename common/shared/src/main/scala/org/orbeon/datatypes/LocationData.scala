/**
 * Copyright (C) 2016 Orbeon, Inc.
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

import org.orbeon.oxf.common.ValidationException

import java.{lang => jl}
import scala.collection.compat._
import scala.collection.{Iterator, immutable}


trait LocationData {
  val file : String
  val line : Int
  val col  : Int
}

object LocationData {

  def asString(ld: LocationData): String = {
    val sb = new jl.StringBuilder

    val hasLine =
      if (ld.line > 0) {
        sb.append("line ")
        sb.append(ld.line.toString)
        true
      } else {
        false
      }

    val hasColumn =
      if (ld.col > 0) {
        if (hasLine)
          sb.append(", ")
        sb.append("column ")
        sb.append(ld.col.toString)
        true
      } else {
        false
      }

    if (ld.file ne null) {
      if (hasLine || hasColumn)
        sb.append(" of ")

      sb.append(ld.file)
    }

    sb.toString
  }

}

case class BasicLocationData(file: String, line: Int, col: Int) extends LocationData {
  override def toString: String =
    LocationData.asString(this)
}

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

  override def toString: String = {

    def parametersString =
      params collect { case (k, v) if v ne null => s"$k='$v'" } mkString ", "

    LocationData.asString(this) + (
      if (description.isDefined || params.nonEmpty)
        " (" + (description getOrElse "") + (if (params.nonEmpty) (": " + parametersString) else "") + ")"
      else
        ""
    )
  }
}

object ExtendedLocationData {

  def iterateParamNameValues(throwable: Throwable, paramName: String): Iterator[String] =
    for {
      ld <- throwable match {
        case e: ValidationException => e.allLocationData.iterator
        case _                      => Iterator.empty
      }
      (name, value) <- ld match {
        case eld: ExtendedLocationData => eld.params.iterator
        case _                         => Iterator.empty
      }
      if name == paramName
    } yield
      value
}