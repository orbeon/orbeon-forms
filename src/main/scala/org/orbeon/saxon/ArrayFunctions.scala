/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.saxon

import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.{FunctionSupport, OrbeonFunctionLibrary, XMLConstants}
import org.orbeon.saxon.`type`._
import org.orbeon.saxon.expr.StaticProperty.{ALLOWS_ZERO_OR_MORE, EXACTLY_ONE}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator, SingletonIterator, ValueRepresentation}
import org.orbeon.saxon.value._
import org.orbeon.scaxon.Implicits._


trait ArrayFunction extends FunctionSupport

trait ReturnArrayFunction extends ArrayFunction {

  import ArrayFunctions._

  override def getItemType(th: TypeHierarchy): ItemType =
    saxonTypeForArray(th.getConfiguration)
}

//
// `array:size($array as array(*)) as xs:integer`
//
class ArraySize extends ArrayFunction {

  import ArrayFunctions._

  override def evaluateItem(context: XPathContext): IntegerValue = {

    implicit val ctx = context

    (itemsArgumentOpt(0).iterator flatMap collectArrayValues next()).size
  }
}

//
// `array:get($array as array(*), $position as xs:integer) as item()*`
//
class ArrayGet extends ArrayFunction {

  import ArrayFunctions._

  override def iterate(context: XPathContext): SequenceIterator = {

    implicit val ctx = context

    val vector = itemsArgumentOpt(0).iterator flatMap collectArrayValues next()
    val index  = longArgumentOpt(1).get.toInt // unlikely, but will lose precision if out of `Int` range

    vector(index - 1) match {
      case v: Value    => v.iterate()
      case v: NodeInfo => SingletonIterator.makeIterator(v)
      case _           => throw new IllegalStateException
    }
  }
}

//
// `array:put($array as array(*), $position	as xs:integer, $member as item()*) as array(*)`
//
class ArrayPut extends ArrayFunction {

  import ArrayFunctions._

  override def evaluateItem(context: XPathContext): ObjectValue = {

    implicit val ctx = context

    val vector = itemsArgumentOpt(0).iterator flatMap collectArrayValues next()
    val index  = longArgumentOpt(1).get.toInt // unlikely, but will lose precision if out of `Int` range
    val value = itemsArgument(2)

    createValue(
      vector.updated(index - 1, new SequenceExtent(value)),
      ctx.getConfiguration
    )
  }
}

//
// `array:append($array as array(*), $appendage as item()*) as array(*)`
//
class ArrayAppend extends ArrayFunction {

  import ArrayFunctions._

  override def evaluateItem(context: XPathContext): ObjectValue = {

    implicit val ctx = context

    val vector = itemsArgumentOpt(0).iterator flatMap collectArrayValues next()
    val value = itemsArgument(1)

    createValue(
      vector :+ new SequenceExtent(value),
      ctx.getConfiguration
    )
  }
}

//
// `array:join($arrays as array(*)*) as array(*)`
//
class ArrayJoin extends ReturnArrayFunction {

  import ArrayFunctions._

  override def evaluateItem(context: XPathContext): ObjectValue = {

    implicit val ctx = context

    val arrays = itemsArgumentOpt(0).iterator flatMap collectArrayValues

    createValue(
      arrays.fold(Vector.empty[ValueRepresentation])(_ ++ _),
      ctx.getConfiguration
    )
  }
}

trait ArrayFunctions extends OrbeonFunctionLibrary {

  Namespace(XMLConstants.XPATH_ARRAY_FUNCTIONS_NAMESPACE_URI) {

    Fun("size", classOf[ArraySize], op = 0, min = 1, BuiltInAtomicType.INTEGER, EXACTLY_ONE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE)
    )

    Fun("get", classOf[ArrayGet], op = 0, min = 2,  Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE),
      Arg(BuiltInAtomicType.INTEGER, EXACTLY_ONE)
    )

    Fun("put", classOf[ArrayPut], op = 0, min = 3,  BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE),
      Arg(BuiltInAtomicType.INTEGER, EXACTLY_ONE),
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("append", classOf[ArrayAppend], op = 0, min = 2,  BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE),
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("join", classOf[ArrayJoin], op = 0, min = 1,  BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, ALLOWS_ZERO_OR_MORE)
    )

    // Other candidates:
    //
    // - array:subarray
    // - array:remove
    // - array:insert-before
    // - array:head
    // - array:tail
    // - array:reverse
    // - array:sort (without function)
    // - array:flatten
  }

}

object ArrayFunctions {

  type UnderlyingType = Vector[ValueRepresentation]

  val UnderlyingClass = classOf[UnderlyingType]

  def saxonTypeForArray(config: Configuration) = new ExternalObjectType(UnderlyingClass, config)

  def collectArrayValues(it: SequenceIterator)(implicit context: XPathContext): Iterator[UnderlyingType] = {

    val config    = context.getConfiguration
    val arrayType = saxonTypeForArray(config)

    asScalaIterator(it) collect {
      case v: ObjectValue if v.getItemType(config.getTypeHierarchy) == arrayType =>
        v.getObject.asInstanceOf[UnderlyingType]
    }
  }

  def createValue(value: UnderlyingType, config: Configuration = XPath.GlobalConfiguration): ObjectValue = {
    new ObjectValue(
      value,
      saxonTypeForArray(config)
    )
  }

}