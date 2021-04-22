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
package org.orbeon.oxf.common

import org.orbeon.datatypes.LocationData

class ValidationException(val message: String, val throwable: Throwable, locationDataOrNull: LocationData)
    extends OXFException((Option(locationDataOrNull) map (_.toString + ": ") getOrElse "") + message, throwable) {

  private var _allLocationData = Option(locationDataOrNull).toList

  def this(message: String, locationData: LocationData) =
    this(message, null, locationData)

  def this(throwable: Throwable, locationData: LocationData) =
    this(throwable.getMessage, throwable, locationData)

  def addLocationData(locationData: LocationData): Unit =
    _allLocationData ::= locationData ensuring (_ ne null)

  def allLocationData   = _allLocationData
  def firstLocationData = _allLocationData.headOption.orNull

  // Q: Shouldn't getMessage return a string containing all the messages in locationDataList?
  // Q: Then, in IndentedLogger, we could skip showing the message and the throwable.getMessage(). The latter would suffice.
  override def getMessage = super.getMessage
}