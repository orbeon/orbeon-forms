/**
 * Copyright (C) 2016 Orbeon, Inc.
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

object CoreUtils {

  implicit class PipeOps[A](val a: A) extends AnyVal {
    // Pipe operator
    def pipe[B] (f: A ⇒ B) = f(a)
    def |>  [B] (f: A ⇒ B) = pipe(f)
    // Kestrel / K Combinator (known as tap in Ruby/Underscore)
    def kestrel[B](f: A ⇒ B): A = { f(a); a }
    def |!>    [B](f: A ⇒ B): A = kestrel(f)
  }

  implicit class OptionOps[A](val a: Option[A]) extends AnyVal {
    // Kestrel / K Combinator (known as tap in Ruby/Underscore)
    def |!>[B](f: A ⇒ B): Option[A] = { a foreach f; a }
  }

  // Extensions on Boolean
  implicit class BooleanWrapper(val b: Boolean) extends AnyVal {
    def option[A](a: ⇒ A)   = if (b) Option(a)   else None
    def string(s: String)   = if (b) s           else ""
    def list[A](a: ⇒ A)     = if (b) List(a)     else Nil
    def set[A](a: ⇒ A)      = if (b) Set(a)      else Set.empty[A]
    def iterator[A](a: ⇒ A) = if (b) Iterator(a) else Iterator.empty
  }

}