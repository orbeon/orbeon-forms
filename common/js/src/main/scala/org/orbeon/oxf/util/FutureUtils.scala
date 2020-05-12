/**
  * Copyright (C) 2017 Orbeon, Inc.
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

import scala.async.Async._
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js.timers.setTimeout
import scala.util.{Failure, Success, Try}

object FutureUtils {

  def delay(delay: FiniteDuration): Future[Unit] = {
    val p = Promise[Unit]()
    setTimeout(delay) {
      p.success(())
    }
    p.future
  }

  def eventually[T](
    interval    : FiniteDuration,
    timeout     : FiniteDuration)(
    block       : => Future[T])(implicit
    execContext : ExecutionContext
  ): Future[T] =
    eventuallyAsTry(interval, timeout)(block) flatMap
      Future.fromTry

  def eventuallyAsTry[T](
    interval    : FiniteDuration,
    timeout     : FiniteDuration)(
    block       : => Future[T])(implicit
    execContext : ExecutionContext
  ): Future[Try[T]] = async {

    val startTime = System.currentTimeMillis

    var result: Try[T] = null

    var done = false
    while(! done) {
      await(block.toTry) match {
        case v @ Success(_) =>
          result = v
          done = true
        case v @ Failure(_) =>
          if ((System.currentTimeMillis - startTime).millis > timeout) {
            result = v
            done = true
          } else {
            await(delay(interval))
          }
      }
    }

    result
  }

  implicit class FutureOps[T](private val f: Future[T]) extends AnyVal {
    def toTry(implicit executor: ExecutionContext): Future[Try[T]] =
      f map Success.apply recover { case t => Failure(t) }
  }
}
