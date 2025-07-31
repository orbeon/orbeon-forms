/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2011, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */
package org.orbeon.oxf.util

/*
 * Variant on the Scala `DynamicVariable` which removes the `ThreadLocal` value after usage.
 *
 * This is important when the thread is handled by a third party, such as a servlet container.
 */
class DynamicVariable[T](initial: => Option[T] = None, isInheritable: Boolean = true) {

  self =>

  protected val threadLocal: ThreadLocal[Option[T]] =
    if (isInheritable)
      new InheritableThreadLocal[Option[T]] {
        override def initialValue: Option[T] = initial
      }
    else
      new ThreadLocal[Option[T]] {
        override def initialValue: Option[T] = initial
      }

  def value: Option[T] = threadLocal.get match {
    case some @ Some(_) => some
    case None =>
      threadLocal.remove() // because `get` above creates the ThreadLocal if missing
      None
  }

  def value_=(value: T): Unit = threadLocal set Some(value)

  def clear(): Unit = threadLocal.remove()

  def withValue[S](value: T)(thunk: => S): S = {

    val oldValueOpt = self.value
    self.value = value

    try
      thunk
    finally
      oldValueOpt match {
        case Some(oldValue) => self.value = oldValue
        case None           => self.clear()
      }
  }

  override def toString: String = s"DynamicVariable($value)"
}
