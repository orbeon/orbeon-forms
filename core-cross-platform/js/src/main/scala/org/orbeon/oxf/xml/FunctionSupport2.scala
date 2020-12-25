package org.orbeon.oxf.xml

import org.orbeon.saxon.functions.SystemFunction
import org.orbeon.saxon.om
import org.orbeon.saxon.om.Chain
import org.orbeon.saxon.value.{AtomicValue, BooleanValue, DateTimeValue, EmptySequence, Int64Value, StringValue}
import org.orbeon.scaxon.Implicits

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

  trait Decode[T] {
    def apply(v: om.Sequence): T
  }

  trait Encode[T] {
    def apply(t: T): om.Sequence
  }

  implicit val UnitDecode: Decode[Unit] = (_: om.Sequence) => ()

  implicit val IntDecode: Decode[Int] = (s: om.Sequence) =>
    s.iterate().next() match {
      case v: Int64Value => v.longValue.toInt // WARNING: value truncation is possible
      case _             => throw new IllegalArgumentException
    }

  implicit val LongDecode: Decode[Long] = (s: om.Sequence) =>
    s.iterate().next() match {
      case v: Int64Value => v.longValue
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

  implicit val NodeInfoDecode: Decode[om.NodeInfo] = (s: om.Sequence) =>
    s.iterate().next() match {
      case v: om.NodeInfo => v
      case _              => throw new IllegalArgumentException
    }

  implicit val AtomicValueDecode: Decode[AtomicValue] = (s: om.Sequence) =>
    s.iterate().next() match {
      case v: AtomicValue => v
      case _              => throw new IllegalArgumentException
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

  implicit def IterableDecode[U : Decode]: Decode[Iterable[U]] = (s: om.Sequence) =>
    Implicits.asScalaIterator(s.iterate()).map(implicitly[Decode[U]].apply).toList

  implicit val UnitEncode        : Encode[Unit]        = (_: Unit)        => EmptySequence
  implicit val IntEncode         : Encode[Int]         = (v: Int)         => om.One.integer(v.toLong)
  implicit val LongEncode        : Encode[Long]        = (v: Long)        => om.One.integer(v)
  implicit val StringEncode      : Encode[String]      = (v: String)      => om.One.string(v)
  implicit val BooleanEncode     : Encode[Boolean]     = (v: Boolean)     => om.One.bool(v)
  implicit val ItemEncode        : Encode[om.Item]     = (v: om.Item)     => new om.One(v)
  implicit val NodeInfoEncode    : Encode[om.NodeInfo] = (v: om.NodeInfo) => new om.One(v)
  implicit val AtomicValueEncode : Encode[AtomicValue] = (v: AtomicValue) => new om.One(v)
  implicit val InstantEncode     : Encode[Instant]     = (v: Instant)     => new om.One(DateTimeValue.fromJavaInstant(v))

  implicit def OptionEncode[U : Encode]: Encode[Option[U]] = {
    case Some(i) => implicitly[Encode[U]].apply(i)
    case None => EmptySequence
  }

  // We want to support not only `Iterable` but `List`, `Vector`, etc. We first tried:
  //
  //   implicit final def IterableEncode[T <: Iterable[U], U : Encode]: Encode[T]
  //
  // But this caused a "diverging implicit expansion" error. Instead, the following works.
  //
  implicit final def IterableEncode[T[_], U : Encode](implicit ev: T[U] => Iterable[U]): Encode[T[U]] = (v: T[U]) => {
    // Dealing directly with iterators would probably be more efficient
    new Chain(v.toList map implicitly[Encode[U]].apply map (_.materialize) asJava)
  }
}