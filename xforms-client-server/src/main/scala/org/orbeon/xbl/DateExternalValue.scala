/**
  * Copyright (C) 2019 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl


//
// NOTE: This is in `xformsCommon` because it is used by:
//
// - `formRunnerJVM`
// - `xformsWeb`
//
// But there might be a better place to put this, such as an
// `formRunnerComponents` module.
//
case class DateExternalValue(
  value         : String,
  format        : String,
  excludedDates : List[String]
)
