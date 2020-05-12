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

import org.orbeon.errorified.Exceptions

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

  private val RootThrowablePF: PartialFunction[Throwable, Try[Nothing]] = {
    case NonFatal(t) => Failure(Exceptions.getRootThrowable(t))
  }

  implicit class TryOps[U](private val t: Try[U]) extends AnyVal {

    def onFailure(f: PartialFunction[Throwable, Any]): Try[U] =
      t recoverWith new OnFailurePF(f)

    def rootFailure: Try[U] = {
      if (t.isFailure)
        t recoverWith RootThrowablePF
      else
        t
    }

    def doEitherWay(f: => Any): Try[U] =
      try t match {
        case result @ Success(_) => f; result
        case result @ Failure(_) => f; result
      } catch {
        case NonFatal(e) => Failure(e)
      }

    def iterator: Iterator[U] = t.toOption.iterator
  }

}