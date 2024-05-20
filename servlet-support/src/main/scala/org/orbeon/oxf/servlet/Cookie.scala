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

object Cookie {
  def apply(cookie: javax.servlet.http.Cookie): JavaxCookie     = new JavaxCookie(cookie)
  def apply(cookie: jakarta.servlet.http.Cookie): JakartaCookie = new JakartaCookie(cookie)
}

trait Cookie {
  // javax/jakarta.servlet.http.Cookie
  def getNativeCookie: AnyRef

  def getName: String
  def getValue: String
}

class JavaxCookie(cookie: javax.servlet.http.Cookie) extends Cookie {
  override def getNativeCookie: javax.servlet.http.Cookie = cookie

  override def getName: String = cookie.getName
  override def getValue: String = cookie.getValue
}

class JakartaCookie(cookie: jakarta.servlet.http.Cookie) extends Cookie {
  override def getNativeCookie: jakarta.servlet.http.Cookie = cookie

  override def getName: String = cookie.getName
  override def getValue: String = cookie.getValue
}
