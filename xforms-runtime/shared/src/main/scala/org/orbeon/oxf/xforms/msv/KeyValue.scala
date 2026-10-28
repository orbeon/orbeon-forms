/**
 * Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.msv

import org.orbeon.dom

/**
 * Represents multi-field keys.
 *
 * This class implements equality test and hash code based on the equalities of each item.
 */
class KeyValue(val values: Array[AnyRef], val element: dom.Element) {

  override def hashCode(): Int = {
    var code = 0
    var i = 0
    while (i < values.length) {
      if (values(i) != null)
        code ^= values(i).hashCode()
      i += 1
    }
    code
  }

  override def equals(o: Any): Boolean = o match {
    case rhs: KeyValue =>
      if (values.length != rhs.values.length) {
        false
      } else {
        var i = 0
        while (i < values.length) {
          if (values(i) != rhs.values(i))
            return false
          i += 1
        }
        true
      }
    case _ => false
  }
}
