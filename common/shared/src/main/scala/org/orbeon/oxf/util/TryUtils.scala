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

import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


object TryUtils {

  private class OnFailurePF[U](f: PartialFunction[Throwable, Any]) extends PartialFunction[Throwable, Try[U]] {
    def isDefinedAt(x: Throwable) = f.isDefinedAt(x)
    def apply(v1: Throwable) = {
      f.apply(v1)
      Failure(v1)
    }
  }

  implicit class TryOps[U](private val t: Try[U]) extends AnyVal {

    def onFailure(f: PartialFunction[Throwable, Any]): Try[U] =
      t recoverWith new OnFailurePF(f)

    def doEitherWay(f: => Any): Try[U] =
      try t match {
        case result @ Success(_) => f; result
        case result @ Failure(_) => f; result
      } catch {
        case NonFatal(e) => Failure(e)
      }

    def iterator: Iterator[U] = t.toOption.iterator
  }

  // Sequence a bunch of `Try`s:
  //
  // - lazily apply `f()` to the elements
  // - return a `Success` iif all applications of `f()` are successful
  // - else return the first `Failure`
  // - all subsequent elements are ignored in case of failure
  def sequenceLazily[T, U](iterable: Iterable[T])(f: T => Try[U]): Try[List[U]] = {

    val tryStream     = iterable.toStream.map(f)
    val successStream = tryStream.takeWhile(_.isSuccess).map(_.get)

    val successSize = successStream.size

    assert(successSize <= iterable.size)

    if (successSize < iterable.size)
      tryStream(successSize) match {
        case Failure(t) => Failure(t)
        case Success(_) => throw new IllegalStateException
      }
    else
      Success(successStream.toList)
  }
}