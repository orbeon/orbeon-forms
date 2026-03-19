package org.orbeon.oxf.util

import org.orbeon.oxf.util.CollectionUtils.*


trait ExceptionsTrait {
  def findNestedThrowable(t: Throwable): Option[Throwable]
}

object Exceptions extends ExceptionsPlatform with ExceptionsTrait {

  def findRootThrowable(t: Throwable): Option[Throwable] =
    causesIterator(t).lastOption()

  def getRootThrowable(t: Throwable): Throwable =
    findRootThrowable(t).orNull

  def causesIterator(t: Throwable): Iterator[Throwable] =
    Iterator.iterateOpt(t)(findNestedThrowable)

  def isConnectionInterruption(t: Throwable): Boolean =
    findRootThrowable(t) match {
      case Some(_: java.net.SocketException) => true
      case Some(e: java.io.IOException) if e.getMessage != null =>
        e.getMessage.contains("Broken pipe") ||
        e.getMessage.contains("Connection reset") ||
        e.getMessage.contains("An established connection was aborted")
      case _ => false
    }

  // For Java callers
  def getNestedThrowableOrNull(t: Throwable): Throwable =
    findNestedThrowable(t).orNull
}
