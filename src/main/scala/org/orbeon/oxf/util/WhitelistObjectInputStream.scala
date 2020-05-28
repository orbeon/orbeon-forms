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
package org.orbeon.oxf.util

import java.io.{InputStream, ObjectInputStream, ObjectStreamClass}


class WhitelistObjectInputStream(is: InputStream, classNames: Set[String], prefixes: List[String]) extends ObjectInputStream(is) {

  override def resolveClass(desc: ObjectStreamClass): Class[_] = {

    val name = desc.getName

    if (! (classNames(name) || prefixes.exists(name.startsWith)))
      throw new IllegalArgumentException(s"cannot deserialize class `$name`")

    super.resolveClass(desc)
  }
}

object WhitelistObjectInputStream {

  def apply(is: InputStream, clazz: Class[_], prefixes: List[String]): WhitelistObjectInputStream =
    new WhitelistObjectInputStream(is, AllowedClasses ++ Set(clazz.getName), prefixes ::: AllowedPrefixes)

  private val AllowedPrefixes = List(
    "scala.collection.",
    "scala.Tuple",
    "["
  )

  private val AllowedClasses = Set(
    "scala.None$",
    "scala.Some",
    "scala.Option"
  )

}
