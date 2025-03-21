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

  implicit class PipeOps[A](private val a: A) extends AnyVal {
    // Pipe operator
    def pipe[B] (f: A => B            ): B = f(a)
    def |>  [B] (f: A => B            ): B = pipe(f)
    def pipeIf  (b: Boolean, f: A => A): A = if (b) f(a) else a
    // Kestrel / K Combinator (known as tap in Ruby/Underscore)
    def kestrel[B](f: A => B): A = { f(a): Unit; a }
    def |!>    [B](f: A => B): A = kestrel(f)
  }

  implicit class OptionOps[A](private val a: Option[A]) extends AnyVal {
    // Kestrel / K Combinator (known as tap in Ruby/Underscore)
    def kestrel[B](f: A => B): Option[A] = { a foreach f; a }
    def |!>[B](f: A => B): Option[A] = kestrel(f)
  }

  // Extensions on Boolean
  implicit class BooleanOps(private val b: Boolean) extends AnyVal {
    def option[A](a: => A)             : Option[A]   = if (b) Option(a)          else None
    def flatOption[A](a: => Option[A]) : Option[A]   = if (b) a                  else None
    def string(s: => String)           : String      = if (b) s                  else ""
    def list[A](a: => A)               : List[A]     = if (b) List(a)            else Nil
    def flatList[A](a: => List[A])     : List[A]     = if (b) a                  else Nil
    def seq[A](a: => A)                : Seq[A]      = if (b) Seq(a)             else Nil
    def set[A](a: => A)                : Set[A]      = if (b) Set(a)             else Set.empty[A]
    def iterator[A](a: => A)           : Iterator[A] = if (b) Iterator.single(a) else Iterator.empty
    def lazyList[A](a: => A)           : LazyList[A] = if (b) LazyList(a)        else LazyList.empty
  }

  @inline def asUnit(body: => Any): Unit = body
}