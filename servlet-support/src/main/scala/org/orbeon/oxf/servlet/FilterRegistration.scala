/**
 * Copyright (C) 2023 Orbeon, Inc.
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
package org.orbeon.oxf.servlet

import java.{util => ju}
import scala.jdk.CollectionConverters._

sealed trait DispatcherType {
  def asJavax  : javax.servlet.DispatcherType
  def asJakarta: jakarta.servlet.DispatcherType
}

object DispatcherType {
  // Use lazy vals to avoid runtime errors (either javax.* or jakarta.* will be absent)
  case object FORWARD extends DispatcherType { override lazy val asJavax = javax.servlet.DispatcherType.FORWARD; override lazy val asJakarta = jakarta.servlet.DispatcherType.FORWARD }
  case object INCLUDE extends DispatcherType { override lazy val asJavax = javax.servlet.DispatcherType.INCLUDE; override lazy val asJakarta = jakarta.servlet.DispatcherType.INCLUDE }
  case object REQUEST extends DispatcherType { override lazy val asJavax = javax.servlet.DispatcherType.REQUEST; override lazy val asJakarta = jakarta.servlet.DispatcherType.REQUEST }
  case object ASYNC   extends DispatcherType { override lazy val asJavax = javax.servlet.DispatcherType.ASYNC;   override lazy val asJakarta = jakarta.servlet.DispatcherType.ASYNC   }
  case object ERROR   extends DispatcherType { override lazy val asJavax = javax.servlet.DispatcherType.ERROR;   override lazy val asJakarta = jakarta.servlet.DispatcherType.ERROR   }
}

object FilterRegistration {
  def apply(filterRegistration: javax.servlet.FilterRegistration): FilterRegistration   = new JavaxFilterRegistration(filterRegistration)
  def apply(filterRegistration: jakarta.servlet.FilterRegistration): FilterRegistration = new JakartaFilterRegistration(filterRegistration)
}

trait FilterRegistration {
  def addMappingForUrlPatterns(dispatcherTypes: Set[DispatcherType], isMatchAfter: Boolean, urlPatterns: String*): Unit
  def setInitParameter(name: String, value: String): Boolean
}

class JavaxFilterRegistration(filterRegistration: javax.servlet.FilterRegistration) extends FilterRegistration {
  override def addMappingForUrlPatterns(dispatcherTypes: Set[DispatcherType], isMatchAfter: Boolean, urlPatterns: String*): Unit =
    filterRegistration.addMappingForUrlPatterns(ju.EnumSet.copyOf(dispatcherTypes.map(_.asJavax).asJavaCollection), isMatchAfter, urlPatterns: _*)
  override def setInitParameter(name: String, value: String): Boolean =
    filterRegistration.setInitParameter(name, value)
}

class JakartaFilterRegistration(filterRegistration: jakarta.servlet.FilterRegistration) extends FilterRegistration {
  override def addMappingForUrlPatterns(dispatcherTypes: Set[DispatcherType], isMatchAfter: Boolean, urlPatterns: String*): Unit =
    filterRegistration.addMappingForUrlPatterns(ju.EnumSet.copyOf(dispatcherTypes.map(_.asJakarta).asJavaCollection), isMatchAfter, urlPatterns: _*)
  override def setInitParameter(name: String, value: String): Boolean =
    filterRegistration.setInitParameter(name, value)
}
