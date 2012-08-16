/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.exception

import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData}
import org.apache.commons.lang3.StringUtils._
import collection.JavaConverters._
import org.orbeon.errorified._

// Orbeon-specific exception formatter
object OrbeonFormatter extends Formatter {

    val Width = 120
    val MaxStackLength = 40

    override def getThrowableMessage(throwable: Throwable): Option[String] =
        throwable match {
            case ve: ValidationException ⇒ Option(ve.getSimpleMessage)
            case t ⇒ Option(t.getMessage)
        }

    override def getAllLocationData(t: Throwable): List[SourceLocation] =
        ValidationException.getAllLocationData(t).asScala.toList flatMap sourceLocation

    // Create SourceLocation from LocationData
    private def sourceLocation(locationData: LocationData) =
        if (isNotBlank(locationData.getSystemID) && !locationData.getSystemID.endsWith(".java")) {
            val (description, params) =
                locationData match {
                    case extended: ExtendedLocationData ⇒
                        (Option(extended.getDescription), arrayToTuples(extended.getParameters))
                    case _ ⇒ (None, Nil)
                }

            Some(SourceLocation(locationData.getSystemID, filterLineCol(locationData.getLine), filterLineCol(locationData.getCol), description, params))
        } else
            None

    private def arrayToTuples(a: Array[String]): List[(String, String)] = Option(a) match {
        case Some(a) ⇒ a.grouped(2) map (sub ⇒ (sub(0), sub(1))) filter (_._2 ne null) toList
        case None ⇒ Nil
    }
}
