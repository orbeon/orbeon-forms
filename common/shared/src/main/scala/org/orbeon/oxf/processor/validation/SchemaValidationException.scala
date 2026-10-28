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
package org.orbeon.oxf.processor.validation

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.common.ValidationException


// So we can differentiate with other ValidationException, see:
// https://github.com/orbeon/orbeon-forms/issues/910
class SchemaValidationException(
  message            : String,
  throwable          : Throwable,
  locationDataOrNull : LocationData
) extends ValidationException(message, throwable, locationDataOrNull) {

  def this(message: String, locationData: LocationData) =
    this(message, null, locationData)

  def this(throwable: Throwable, locationData: LocationData) =
    this(throwable.getMessage, throwable, locationData)
}
