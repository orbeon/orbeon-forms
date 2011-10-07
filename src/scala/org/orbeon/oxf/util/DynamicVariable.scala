/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2011, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */
package org.orbeon.oxf.util

/*
 * Variant on the Scala DynamicVariable which removes the ThreadLocal value after usage.
 *
 * This is important when the thread is handled by a third-party, such as a servlet container.
 */
class DynamicVariable[T] {

    protected val threadLocal = new InheritableThreadLocal[Option[T]] {
        override def initialValue = None
    }

    def value = threadLocal.get match {
        case some @ Some(value) ⇒ some
        case None ⇒
            threadLocal.remove() // because get above creates the ThreadLocal if missing
            None
    }

    def withValue[S](value: T)(thunk: => S): S = {

        val oldValue = threadLocal.get
        threadLocal set Some(value)

        try
            thunk
        finally
            oldValue match {
                case some @ Some(_) ⇒ threadLocal set some
                case None ⇒ threadLocal.remove()
            }
    }

    override def toString: String = "DynamicVariable(" + value + ")"
}
