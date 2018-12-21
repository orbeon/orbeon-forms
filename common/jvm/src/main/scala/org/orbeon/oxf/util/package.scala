/**
  * Copyright (C) 2018 Orbeon, Inc.
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
package org.orbeon.oxf

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.util.PathUtils.UrlEncoderDecoder

package object util {
  implicit object JvmUrlEncoderDecoder extends UrlEncoderDecoder {
    def encode(s: String): String = java.net.URLEncoder.encode(s, CharsetNames.Utf8)
    def decode(s: String): String = java.net.URLDecoder.decode(s, CharsetNames.Utf8)
  }
}