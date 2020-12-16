package org.orbeon.oxf.xml

import org.orbeon.saxon.functions.SystemFunction
import org.orbeon.saxon.om
import org.orbeon.saxon.om.Chain
import org.orbeon.saxon.value.{BooleanValue, DateTimeValue, EmptySequence, Int64Value, StringValue}

import java.time.Instant
import scala.jdk.CollectionConverters._


trait FunctionSupport2 extends SystemFunction {

  import FunctionSupport2._

  def decodeSaxonArg[T : Decode](s: om.Sequence): T =
    implicitly[Decode[T]].apply(s)

  def encodeSaxonArg[T : Encode](v: T): om.Sequence =
    implicitly[Encode[T]].apply(v)
}

object FunctionSupport2 {

  type Decode[T] = om.Sequence => T
  type Encode[T] = T => om.Sequence

  implicit val IntDecode: Decode[Int] = (s: om.Sequence) =>
    s.iterate().next() match {
      case v: Int64Value => v.longValue.toInt // WARNING: value truncation is possible
      case _             => throw new IllegalArgumentException
    }

  implicit val StringDecode: Decode[String] = (s: om.Sequence) =>
    s.iterate().next() match {
      case v: StringValue => v.getStringValue
      case _              => throw new IllegalArgumentException
    }

  implicit val BooleanDecode: Decode[Boolean] = (s: om.Sequence) =>
    s.iterate().next() match {
      case v: BooleanValue => v.value
      case _               => throw new IllegalArgumentException
    }

  implicit val ItemDecode: Decode[om.Item] = (s: om.Sequence) =>
    s.iterate().next() match {
      case v: om.Item => v
      case _          => throw new IllegalArgumentException
    }

  implicit val InstantDecode: Decode[Instant] = (s: om.Sequence) =>
    s.iterate().next() match {
      case v: DateTimeValue => v.toJavaInstant
      case _                => throw new IllegalArgumentException
    }

  implicit def OptionDecode[U : Decode]: Decode[Option[U]] = (s: om.Sequence) =>
    s.iterate().next() match {
      case null  => None
      case v     => Some(implicitly[Decode[U]].apply(v))
    }

  implicit val IntEncode     : Encode[Int]     = (v: Int)     => om.One.integer(v.toLong)
  implicit val StringEncode  : Encode[String]  = (v: String)  => om.One.string(v)
  implicit val BooleanEncode : Encode[Boolean] = (v: Boolean) => om.One.bool(v)
  implicit val ItemEncode    : Encode[om.Item] = (v: om.Item) => new om.One(v)
  implicit val InstantEncode : Encode[Instant] = (v: Instant) => new om.One(DateTimeValue.fromJavaInstant(v))

  implicit def OptionEncode[U : Encode]: Encode[Option[U]] = (v: Option[U]) =>
    v match {
      case Some(i) => implicitly[Encode[U]].apply(i)
      case None    => EmptySequence
    }

  implicit def IterableEncode[U : Encode]: Encode[Iterable[U]] = (v: Iterable[U]) => {

//    // TODO: Move to `SaxonUtils` or `Implicits` (or a new place).
//    class IteratorFromSequence(s: om.Sequence) extends Iterator[om.Item] {
//
//      private val seqIt = s.iterate()
//      private var n = seqIt.next()
//
//      def hasNext: Boolean = n ne null
//      def next(): om.Item = {
//        val r = n
//        n = seqIt.next()
//        r
//      }
//    }
//
//    class SequenceFromIterator(it: Iterator[om.Item]) extends om.Sequence {
//
//      def head: Item = ???
//
//      def iterate(): SequenceIterator = new SequenceIterator {
//        def next(): Item = ???
//      }
//    }

    // Dealing directly with iterators would probably be more efficient
//    new SequenceFromIterator(v.iterator map implicitly[Encode[U]].apply flatMap (new IteratorFromSequence(_)))


  new Chain(v.toList map implicitly[Encode[U]].apply map (_.materialize) asJava)
  }
}