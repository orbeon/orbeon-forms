/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.state

import java.io.{InputStream, ObjectInputStream, ObjectStreamClass}


class WhitelistObjectInputStream(is: InputStream, classNames: Set[String]) extends ObjectInputStream(is) {

  def this(is: InputStream, clazz: Class[_]) = this(is, Set(clazz.getName))

  override def resolveClass(desc: ObjectStreamClass): Class[_] = {

    println(s"xxx resolveClass: ${desc.getName}")

    if (! classNames(desc.getName))
      throw new IllegalArgumentException(s"cannot deserialize class `${desc.getName}`")

    super.resolveClass(desc)
  }
}
