package org.orbeon.oxf.xml

import org.orbeon.saxon.model.BuiltInAtomicType
import org.orbeon.saxon.om._
import org.orbeon.saxon.trans.XPathException
import org.orbeon.saxon.value._

import java.math.{BigDecimal, BigInteger}


object SaxonUtilsDependsOnXPath extends SaxonUtilsDependsOnXPathTrait {

  val anyToItem: Any => Item = convertToItem

  val anyToItemIfNeeded: Any => Item = {
    case i: Item => i
    case a       => anyToItem(a)
  }

  // Custom conversion for now XXX FIXME: we only care about types we use
  private def convertToItem(value: Any): Item =
    value match {
      case v: Boolean     => BooleanValue.get(v)
      case v: Byte        => new Int64Value(v.toLong, BuiltInAtomicType.BYTE, false)
      case v: Float       => new FloatValue(v)
      case v: Double      => new DoubleValue(v)
      case v: Integer     => new Int64Value(v.toLong, BuiltInAtomicType.INT, false)
      case v: Long        => new Int64Value(v, BuiltInAtomicType.LONG, false)
      case v: Short       => new Int64Value(v.toLong, BuiltInAtomicType.SHORT, false)
      case v: String      => StringValue.makeStringValue(v)
      case v: BigDecimal  => new BigDecimalValue(v)
      case v: BigInteger  => new BigIntegerValue(v)
      case v: Array[Byte] => new HexBinaryValue(v)
      case _              => throw new XPathException("Java object cannot be converted to an XQuery value")
    }
}
