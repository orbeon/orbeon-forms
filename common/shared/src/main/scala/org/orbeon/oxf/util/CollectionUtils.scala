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

import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry}
import org.orbeon.oxf.util.CoreUtils._

import scala.collection.compat._
import scala.collection.{AbstractIterator, mutable}
import scala.language.{implicitConversions, reflectiveCalls}
import scala.reflect.ClassTag

object CollectionUtils {

  // Combine the second values of each tuple that have the same name
  // The caller can specify the type of the resulting values, e.g.:
  // - combineValues[String, AnyRef, Array]
  // - combineValues[String, String, List]
  def combineValues[Key, U, T[_]](parameters: Iterable[(Key, U)])(implicit cbf: Factory[U, T[U]]): List[(Key, T[U])] = {
    val result = mutable.LinkedHashMap[Key, mutable.Builder[U, T[U]]]()

    for ((name, value) <- parameters)
      result.getOrElseUpdate(name, cbf.newBuilder) += value

    result map { case (k, v) => k -> v.result } toList
  }

  // Extensions on Iterator[T]
  implicit class IteratorWrapper[T](private val i: Iterator[T]) extends AnyVal {
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
    def iterateFrom[T](start: T, gen: T => Option[T]): Iterator[T] = {
      var next: Option[T] = Some(start)
      iterateWhileDefined {
        val result = next
        next = next.flatMap(gen)
        result
      }
    }
    def iterateWhile[T](cond: => Boolean, elem: => T): Iterator[T] =
      iterateWhileDefined(cond option elem)

    def iterateWhileDefined[T](elemOpt: => Option[T]): Iterator[T] =
      Iterator.continually(elemOpt).takeWhile(_.isDefined).flatten

    def iterateOpt[T <: AnyRef](start: T)(f: T => Option[T]): Iterator[T] = new AbstractIterator[T] {

      private[this] var _next: Option[T] = Some(start)

      def hasNext: Boolean = _next.isDefined

      def next(): T =
        _next match {
          case Some(result) =>
            // Advance on `next()` for simplicity
            _next = _next flatMap f
            result
          case None =>
            throw new NoSuchElementException("next on empty iterator")
        }
    }
  }
  implicit def fromIteratorExt(i: Iterator.type): IteratorExt.type = IteratorExt

  // WARNING: Remember that type erasure takes place! collectByErasedType[T[U1]] will work even if the underlying type was T[U2]!
  // NOTE: `case t: T` works with `ClassTag` only since Scala 2.10.
  def collectByErasedType[T: ClassTag](value: Any): Option[T] = Option(value) collect { case t: T => t }

  // 2020-09-02: For 2.13 compat, make these only operations on `List` as we are getting errors when
  // trying to make this apply to any collection type. Once we migrate to 2.13, we can try to make
  // this general again.
  implicit class ListOps[A](private val t: List[A]) extends AnyVal {

    def groupByKeepOrder[K](f: A => K): List[(K, List[A])] = {
      val m = mutable.LinkedHashMap.empty[K, mutable.Builder[A, List[A]]]
      for (elem <- t) {
        val key = f(elem)
        val bldr = m.getOrElseUpdate(key, List.newBuilder[A])
        bldr += elem
      }
      val b = List.newBuilder[(K, List[A])]
      for ((k, v) <- m)
        b += ((k, v.result()))

      b.result()
    }

    def keepDistinctBy[K, U](key: A => K): List[A] = {
      val result = mutable.ListBuffer[A]()
      val seen   = mutable.Set[K]()

      for (x <- t) {
        val k = key(x)
        if (! seen(k)) {
          result += x
          seen += k
        }
      }

      result.to(List)
    }

    // Return duplicate values in the order in which they appear
    // A duplicate value is returned only once
    def findDuplicates: List[A] = {
      val result = mutable.LinkedHashSet[A]()
      val seen   = mutable.HashSet[A]()
      for (x <- t) {
        if (seen(x))
          result += x
        else
          seen += x
      }
      result.to(List)
    }
  }

  implicit class IntArrayOps(private val a: Array[Int]) extends AnyVal {
    def codePointsToString = new String(a, 0, a.length)
  }

  implicit class IntIteratorOps(private val i: Iterator[Int]) extends AnyVal {
    def codePointsToString: String = {
      val a = i.to(Array)
      new String(a, 0, a.length)
    }
  }

  sealed trait InsertPosition extends EnumEntry with Lowercase
  object InsertPosition extends Enum[InsertPosition] {

    val values = findValues

    case object Before extends InsertPosition
    case object After  extends InsertPosition
  }

  implicit class VectorOps[T](private val values: Vector[T]) extends AnyVal {

    def insertAt(index: Int, value: T, position: InsertPosition): Vector[T] =
      position match {
        case InsertPosition.Before => (values.take(index)     :+ value) ++ values.drop(index)
        case InsertPosition.After  => (values.take(index + 1) :+ value) ++ values.drop(index + 1)
      }

    def insertAt(index: Int, newValues: Iterable[T], position: InsertPosition): Vector[T] =
      position match {
        case InsertPosition.Before => values.take(index)     ++ newValues ++ values.drop(index)
        case InsertPosition.After  => values.take(index + 1) ++ newValues ++ values.drop(index + 1)
      }

    def removeAt(index: Int): Vector[T] =
      values.take(index) ++ values.drop(index + 1)
  }
}