/**
 * Copyright (C) 2018 Orbeon, Inc.
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
package org.orbeon.io

import enumeratum.EnumEntry.Lowercase
import enumeratum._

sealed trait UriScheme extends EnumEntry with Lowercase

object UriScheme extends Enum[UriScheme] {

  val values = findValues

  case object Http   extends UriScheme
  case object Https  extends UriScheme
  case object File   extends UriScheme
  case object Data   extends UriScheme
  case object Mailto extends UriScheme
  case object Oxf    extends UriScheme
  case object Echo   extends UriScheme

  val SchemesWithHeaders: Set[UriScheme] = Set(Http, Https, Echo)
}
