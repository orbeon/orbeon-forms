package org.orbeon.oxf.util

import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


// For Scala.js: temporary copy of the JVM side, without `rootFailure`
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

    def rootFailure: Try[U] = ???

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