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
package org.orbeon.oxf.util


sealed trait ByteEncoding
object ByteEncoding {
  case object Base64 extends ByteEncoding
  case object Hex    extends ByteEncoding

  def fromString(s: String): ByteEncoding = s match {
    case "base64" => Base64
    case "hex"    => Hex
    case other    => throw new IllegalArgumentException(s"Invalid digest encoding (must be one of `base64` or `hex`): `$other`")
  }
}

trait SecureUtilsTrait {
  def randomHexId: String
}
