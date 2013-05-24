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

import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
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
            case ve: ValidationException ⇒ Option(ve.message)
            case t ⇒ Option(t.getMessage)
        }

    override def getAllLocationData(t: Throwable): List[SourceLocation] =
        OrbeonLocationException.getAllLocationData(t) flatMap sourceLocation

    // Create SourceLocation from LocationData
    private def sourceLocation(locationData: LocationData): Option[SourceLocation] =
        if (isNotBlank(locationData.getSystemID)) {
            val (description, params) =
                locationData match {
                    case extended: ExtendedLocationData ⇒ (extended.description, extended.params)
                    case _                              ⇒ (None, Nil)
                }

            Some(SourceLocation(locationData.getSystemID, filterLineCol(locationData.getLine), filterLineCol(locationData.getCol), description, params))
        } else
            None
}
