/**
 * Copyright (C) 2024 Orbeon, Inc.
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

import scala.reflect.ClassTag
import scala.util.Try


object ReflectionUtils {
  def loadClassByName[T <: AnyRef : ClassTag](className: String): Option[T] = {

    def tryFromScalaObject: Try[AnyRef] = Try {
      Class.forName(className + "$").getDeclaredField("MODULE$").get(null)
    }

    def fromJavaClass: AnyRef =
      Class.forName(className).getDeclaredMethod("instance").invoke(null)

    tryFromScalaObject getOrElse fromJavaClass match {
      case instance: T => Some(instance)
      case _ =>
        throw new ClassCastException(
          s"class `$className` does not refer to a ${implicitly[ClassTag[T]].runtimeClass.getName}"
        )
    }
  }
}
