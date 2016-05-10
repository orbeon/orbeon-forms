/**
 *  Copyright (C) 2011 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util

import java.io.{InputStream, OutputStream, Reader, Writer}

import org.apache.log4j.Logger
import org.orbeon.errorified.Exceptions
import org.orbeon.exception._

import scala.collection.generic.CanBuildFrom
import scala.collection.{TraversableLike, mutable}
import scala.language.{implicitConversions, reflectiveCalls}
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

abstract class Function1Adapter[-T1, +R] extends (T1 ⇒ R)

object ScalaUtils extends PathOps {

  private val CopyBufferSize = 8192

  def copyStream(in: InputStream, out: OutputStream, progress: Long ⇒ Unit = _ ⇒ ()) = {

    require(in ne null)
    require(out ne null)

    useAndClose(in) { in ⇒
      useAndClose(out) { out ⇒
        val buffer = new Array[Byte](CopyBufferSize)
        Iterator continually (in read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read ⇒
          progress(read)
          out.write(buffer, 0, read)
        }
        out.flush()
      }
    }
  }

  def copyReader(in: Reader, out: Writer, progress: Long ⇒ Unit = _ ⇒ ()) = {

    require(in ne null)
    require(out ne null)

    useAndClose(in) { in ⇒
      useAndClose(out) { out ⇒
        val buffer = new Array[Char](CopyBufferSize)
        Iterator continually (in read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read ⇒
          progress(read)
          out.write(buffer, 0, read)
        }
      }
    }
  }

  // Use a closable item and make sure an attempt to close it is done after use
  def useAndClose[T <: {def close()}, U](closable: T)(block: T ⇒ U): U =
    try block(closable)
    finally {
      if (closable ne null)
        runQuietly(closable.close())
    }

  // Run a block and swallow any exception. Use only for things like close().
  def runQuietly(block: ⇒ Unit) =
    try block
    catch {
      case NonFatal(_) ⇒ // NOP
    }

  // Run the body and log/rethrow the root cause of any Exception caught
  def withRootException[T](action: String, newException: Throwable ⇒ Exception)(body: ⇒ T)(implicit logger: Logger): T =
    try body
    catch {
      case NonFatal(t) ⇒
        logger.error("Exception when running " + action + '\n' + OrbeonFormatter.format(t))
        throw newException(Exceptions.getRootThrowable(t))
    }

  implicit class PipeOps[A](val a: A) extends AnyVal {
    // Pipe operator
    def pipe[B] (f: A ⇒ B) = f(a)
    def |>  [B] (f: A ⇒ B) = pipe(f)
    // Kestrel / K Combinator (known as tap in Ruby/Underscore)
    def kestrel[B](f: A ⇒ B): A = { f(a); a }
    def |!>    [B](f: A ⇒ B): A = kestrel(f)
  }

  implicit class OptionOps[A](val a: Option[A]) extends AnyVal {
    // Kestrel / K Combinator (known as tap in Ruby/Underscore)
    def |!>[B](f: A ⇒ B): Option[A] = { a foreach f; a }
  }

  // Convert a string of tokens to a set
  def stringToSet(s: String)               = split[Set](s)
  def stringOptionToSet(s: Option[String]) = s map stringToSet getOrElse Set.empty[String]

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

  // Extensions on Boolean
  implicit class BooleanWrapper(val b: Boolean) extends AnyVal {
    def option[A](a: ⇒ A)   = if (b) Option(a)   else None
    def list[A](a: ⇒ A)     = if (b) List(a)     else Nil
    def set[A](a: ⇒ A)      = if (b) Set(a)      else Set.empty[A]
    def iterator[A](a: ⇒ A) = if (b) Iterator(a) else Iterator.empty
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

  /*
   * Rewrite in Scala of Apache StringUtils.splitWorker (http://www.apache.org/licenses/LICENSE-2.0).
   *
   * This implementation can return any collection type for which there is a builder:
   *
   * split[List]("a b")
   * split[Array]("a b")
   * split[Set]("a b")
   * split[LinkedHashSet]("a b")
   * split("a b")
   *
   * Or:
   *
   * val result: List[String]          = split("a b")(breakOut)
   * val result: Array[String]         = split("a b")(breakOut)
   * val result: Set[String]           = split("a b")(breakOut)
   * val result: LinkedHashSet[String] = split("a b")(breakOut)
   */
  def split[T[_]](str: String, sep: String = null, max: Int = 0)(implicit cbf: CanBuildFrom[Nothing, String, T[String]]): T[String] = {

    val builder = cbf()

    if (str ne null) {
      val len = str.length
      if (len != 0) {
        var sizePlus1 = 1
        var i = 0
        var start = 0
        var doMatch = false

        val test: Char ⇒ Boolean =
          if (sep eq null)
            Character.isWhitespace
          else if (sep.length == 1) {
            val sepChar = sep.charAt(0)
            sepChar ==
          } else
            sep.indexOf(_) >= 0

        while (i < len) {
          if (test(str.charAt(i))) {
            if (doMatch) {
              if (sizePlus1 == max)
                i = len

              sizePlus1 += 1

              builder += str.substring(start, i)
              doMatch = false
            }
            i += 1
            start = i
          } else {
            doMatch = true
            i += 1
          }
        }

        if (doMatch)
          builder += str.substring(start, i)
      }
    }
    builder.result()
  }

  //@XPathFunction
  def truncateWithEllipsis(s: String, maxLength: Int, tolerance: Int): String =
    if (s.length <= maxLength + tolerance) {
      s
    } else {
      val after = s.substring(maxLength, (maxLength + tolerance + 1) min s.length)
      val afterSpaceIndex = after lastIndexOf " "
      if (afterSpaceIndex != -1)
        s.substring(0, maxLength) + after.substring(0, afterSpaceIndex) + '…'
      else
        s.substring(0, maxLength) + '…'
    }

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

  private class OnFailurePF[U](f: PartialFunction[Throwable, Any]) extends PartialFunction[Throwable, Try[U]] {
    def isDefinedAt(x: Throwable) = f.isDefinedAt(x)
    def apply(v1: Throwable) = {
      f.apply(v1)
      Failure(v1)
    }
  }

  private val RootThrowablePF: PartialFunction[Throwable, Try[Nothing]] = {
    case NonFatal(t) ⇒ Failure(Exceptions.getRootThrowable(t))
  }

  // Operations on Try
  implicit class TryOps[U](val t: Try[U]) extends AnyVal {

    def onFailure(f: PartialFunction[Throwable, Any]) =
      t recoverWith new OnFailurePF(f)

    def rootFailure: Try[U] = {
      if (t.isFailure)
        t recoverWith RootThrowablePF
      else
        t
    }

    def doEitherWay(f: ⇒ Any): Try[U] =
      try t match {
        case result @ Success(_) ⇒ f; result
        case result @ Failure(_) ⇒ f; result
      } catch {
        case NonFatal(e) ⇒ Failure(e)
      }

    def iterator: Iterator[U] = t.toOption.iterator
  }

  implicit class anyToCollectable[A](val a: A) extends AnyVal {
    def collect[B](pf: PartialFunction[A, B]): Option[B] =
      pf.isDefinedAt(a) option pf(a)
  }

  // Mainly for Java callers
  def trimAllToEmpty(s: String) = s.trimAllToEmpty
  def trimAllToNull(s: String)  = s.trimAllToNull
  def trimAllToOpt(s: String)   = s.trimAllToOpt

  implicit class StringOps(val s: String) extends AnyVal {

    private def isNonBreakingSpace(c: Int) =
      c == '\u00A0' || c == '\u2007' || c == '\u202F'

    private def isZeroWidthChar(c: Int) =
      c == '\u200b' || c == '\u200c' || c == '\u200d' || c == '\ufeff'

    def trimAllToEmpty = trimControlAndAllWhitespaceToEmptyCP

    def trimAllToOpt: Option[String] = {
      val trimmed = trimAllToEmpty
      trimmed.nonEmpty option trimmed
    }

    def trimAllToNull: String = {
      val trimmed = trimAllToEmpty
      if (trimmed.isEmpty) null else trimmed
    }

    def trimControlAndAllWhitespaceToEmptyCP =
      trimToEmptyCP(c ⇒ Character.isWhitespace(c) || isNonBreakingSpace(c) || isZeroWidthChar(c) || Character.isISOControl(c))

    // Trim the string according to a matching function
    // This checks for Unicode code points, unlike String.trim() or StringUtils.trim(). When matching on spaces
    // and control characters, this is not strictly necessary since they are in the BMP and cannot collide with
    // surrogates. But in the general case it is more correct to test on code points.
    def trimToEmptyCP(matches: Int ⇒ Boolean) =
      if ((s eq null) || s.isEmpty) {
        ""
      } else {

        val it = s.iterateCodePoints

        var prefix = 0

        it takeWhile matches foreach { c ⇒
          prefix += Character.charCount(c)
        }

        var suffix = 0

        it foreach { c ⇒
          if (matches(c))
            suffix += Character.charCount(c)
          else
            suffix = 0
        }

        s.substring(prefix, s.size - suffix)
      }
  }

  private class CodePointsIterator(val cs: CharSequence) extends Iterator[Int] {

    private var nextIndex = 0

    def hasNext = nextIndex < cs.length

    def next() = {
      val result = cs.codePointAt(nextIndex)
      nextIndex += Character.charCount(result)
      result
    }
  }

  implicit class CharSequenceOps(val cs: CharSequence) extends AnyVal {

    def iterateCodePoints: Iterator[Int] = new CodePointsIterator(cs)

    def codePointAt(index: Int): Int = {
      val first = cs.charAt(index)
      if (index + 1 < cs.length) {
        if (Character.isHighSurrogate(first)) {
          val second = cs.charAt(index + 1)
          if (Character.isLowSurrogate(second))
            return Character.toCodePoint(first, second)
        }
      }
      first.toInt
    }
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
}