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

import org.orbeon.oxf.util.CoreUtils._

import scala.collection.generic.CanBuildFrom
import scala.collection.{TraversableLike, mutable}
import scala.language.{implicitConversions, reflectiveCalls}
import scala.reflect.ClassTag

object CollectionUtils {

  // Combine the second values of each tuple that have the same name
  // The caller can specify the type of the resulting values, e.g.:
  // - combineValues[String, AnyRef, Array]
  // - combineValues[String, String, List]
  def combineValues[Key, U, T[_]](parameters: Seq[(Key, U)])(implicit cbf: CanBuildFrom[Nothing, U, T[U]]): Seq[(Key, T[U])] = {
    val result = mutable.LinkedHashMap[Key, mutable.Builder[U, T[U]]]()

    for ((name, value) ← parameters)
      result.getOrElseUpdate(name, cbf()) += value

    result map { case (k, v) ⇒ k → v.result } toList
  }

  // Extensions on Iterator[T]
  implicit class IteratorWrapper[T](val i: Iterator[T]) extends AnyVal {
    def nextOption(): Option[T] = i.hasNext option i.next()
    def lastOption(): Option[T] = {
      var n = nextOption()
      while (n.isDefined) {
        val nextN = nextOption()
        if (nextN.isEmpty)
          return n
        n = nextN
      }
      None
    }
  }

  // Extensions on Iterator object
  object IteratorExt {
    def iterateWhile[T](cond: ⇒ Boolean, elem: ⇒ T): Iterator[T] =
      iterateWhileDefined(cond option elem)

    def iterateWhileDefined[T](elemOpt: ⇒ Option[T]): Iterator[T] =
      Iterator.continually(elemOpt).takeWhile(_.isDefined).flatten
  }
  implicit def fromIteratorExt(i: Iterator.type): IteratorExt.type = IteratorExt

  // WARNING: Remember that type erasure takes place! collectByErasedType[T[U1]] will work even if the underlying type was T[U2]!
  // NOTE: `case t: T` works with `ClassTag` only since Scala 2.10.
  def collectByErasedType[T: ClassTag](value: AnyRef): Option[T] = Option(value) collect { case t: T ⇒ t }

  implicit class TraversableLikeOps[A, Repr](val t: TraversableLike[A, Repr]) extends AnyVal {

    def groupByKeepOrder[K](f: A ⇒ K)(implicit cbf: CanBuildFrom[Nothing, A, Repr]): List[(K, Repr)] = {
      val m = mutable.LinkedHashMap.empty[K, mutable.Builder[A, Repr]]
      for (elem ← t) {
        val key = f(elem)
        val bldr = m.getOrElseUpdate(key, cbf())
        bldr += elem
      }
      val b = List.newBuilder[(K, Repr)]
      for ((k, v) ← m)
        b += ((k, v.result()))

      b.result()
    }

    def keepDistinctBy[K, U](key: A ⇒ K): List[A] = {
      val result = mutable.ListBuffer[A]()
      val seen   = mutable.Set[K]()

      for (x ← t) {
        val k = key(x)
        if (! seen(k)) {
          result += x
          seen += k
        }
      }

      result.to[List]
    }

    // Return duplicate values in the order in which they appear
    // A duplicate value is returned only once
    def findDuplicates: List[A] = {
      val result = mutable.LinkedHashSet[A]()
      val seen   = mutable.HashSet[A]()
      for (x ← t) {
        if (seen(x))
          result += x
        else
          seen += x
      }
      result.to[List]
    }
  }

  implicit class anyToCollectable[A](val a: A) extends AnyVal {
    def collect[B](pf: PartialFunction[A, B]): Option[B] =
      pf.isDefinedAt(a) option pf(a)
  }

  implicit class IntArrayOps(val a: Array[Int]) extends AnyVal {
    def codePointsToString = new String(a, 0, a.length)
  }

  implicit class IntIteratorOps(val i: Iterator[Int]) extends AnyVal {
    def codePointsToString = {
      val a = i.to[Array]
      new String(a, 0, a.length)
    }
  }

  sealed trait InsertPosition
  case object InsertBefore extends InsertPosition
  case object InsertAfter  extends InsertPosition

  implicit class VectorOps[T](val values: Vector[T]) extends AnyVal {

    def insertAt(index: Int, value: T, position: InsertPosition): Vector[T] =
      position match {
        case InsertBefore ⇒ (values.take(index) :+ value) ++ values.drop(index)
        case InsertAfter  ⇒ (values.take(index + 1) :+ value) ++ values.drop(index + 1)
      }

    def insertAt(index: Int, newValues: Traversable[T], position: InsertPosition): Vector[T] =
    position match {
      case InsertBefore ⇒ values.take(index) ++ newValues ++ values.drop(index)
      case InsertAfter  ⇒ values.take(index + 1) ++ newValues ++ values.drop(index + 1)
    }

    def removeAt(index: Int): Vector[T] = {
      values.take(index) ++ values.drop(index + 1)
    }
  }
}