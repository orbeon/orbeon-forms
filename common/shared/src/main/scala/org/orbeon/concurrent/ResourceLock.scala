package org.orbeon.concurrent

import java.util.concurrent.Semaphore


class ResourceLock {

  private val semaphore = new Semaphore(1)

  private val recursionFlag = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  // The idea of this method is that we want to avoid recursion and also avoid concurrent access to the `thunk`. We
  // detect recursion through a `ThreadLocal` flag, so this only works for same-thread recursion, but that is the
  // current use-case. If we are in the recursive case, we return `None`. If `allowBlocking` is `true`, we will block
  // to access the resource, otherwise we will not block and return `None`. We can also return `None` if we were
  // interrupted but that is not really expected.
  def withAcquiredResourceOrNone[T](allowBlocking: Boolean)(thunk: => T): Option[T] =
    if (recursionFlag.get()) {
      None
    } else {
      recursionFlag.remove() // because `get()` will initialize the `ThreadLocal` value

      def thunkWithFlagAndRelease(): Some[T] =
        try {
          recursionFlag.set(true)
          Some(thunk)
        } finally {
          recursionFlag.remove()
          semaphore.release()
        }

      if (allowBlocking)
        try {
          semaphore.acquire()
          thunkWithFlagAndRelease()
        } catch {
          case _: InterruptedException =>
            None
        }
      else {
        if (semaphore.tryAcquire())
          thunkWithFlagAndRelease()
        else
          None
      }
    }
}
