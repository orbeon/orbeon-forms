package org.orbeon.oxf.util

import org.orbeon.oxf.util.CollectionUtils.IteratorWrapper


object Exceptions {

  def causesIterator(t: Throwable): Iterator[Throwable] =
    Iterator.iterate(t)(_.getCause).takeWhile(_ ne null)

  def getRootThrowable(t: Throwable): Option[Throwable] =
    causesIterator(t).lastOption()
}
