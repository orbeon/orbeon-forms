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
import org.orbeon.oxf.xml.{FunctionSupport, OrbeonFunctionLibrary, SaxonUtils, XMLConstants}
import org.orbeon.saxon.MapFunctions._
import org.orbeon.saxon.`type`._
import org.orbeon.saxon.expr.StaticProperty.{ALLOWS_ZERO_OR_MORE, EXACTLY_ONE}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om._
import org.orbeon.saxon.value._
import org.orbeon.scaxon.Implicits._

trait MapFunction extends FunctionSupport {

//  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet =
//    addSubExpressionsToPathMap(pathMap, pathMapNodeSet)
}

trait ReturnMapFunction extends MapFunction {

  override def getItemType(th: TypeHierarchy): ItemType =
    saxonTypeForMap(th.getConfiguration)
}

//
// `map:entry($key as xs:anyAtomicType, $value as item()*) as map(*)`
//
class MapEntry extends ReturnMapFunction {

  override def evaluateItem(context: XPathContext): ObjectValue = {

    implicit val ctx = context

    val config  = context.getConfiguration
    val mapType = saxonTypeForMap(config)

    val key   = SaxonUtils.fixStringValue(itemArgument(0).asInstanceOf[AtomicValue]) // enforced by signature
    val value = itemsArgument(1)

    createValue(
      Map(key -> new SequenceExtent(value)),
      config
    )
  }
}

//
// `map:merge($maps as map(*)*) as map(*)`
//
class MapMerge extends ReturnMapFunction {

  override def evaluateItem(context: XPathContext): ObjectValue = {

    implicit val ctx = context

    val config  = context.getConfiguration
    val mapType = saxonTypeForMap(config)

    val maps = itemsArgumentOpt(0).iterator flatMap collectMapValues

    createValue(
      maps.foldLeft(Map.empty[AtomicValue, ValueRepresentation])(_ ++ _),
      config
    )
  }
}

//
// `map:get($map as map(*), $key as xs:anyAtomicType) as item()*`
//
class MapGet extends MapFunction {

  override def iterate(context: XPathContext): SequenceIterator = {

    implicit val ctx = context

    val map = itemsArgumentOpt(0).iterator flatMap collectMapValues next()
    val key = SaxonUtils.fixStringValue(itemArgument(1).asInstanceOf[AtomicValue]) // enforced by signature

    map.getOrElse(key, EmptySequence.getInstance) match {
      case v: Value    => v.iterate()
      case v: NodeInfo => SingletonIterator.makeIterator(v)
      case _           => throw new IllegalStateException
    }
  }
}

object MapFunctions {

  type UnderlyingType = Map[AtomicValue, ValueRepresentation]

  val UnderlyingClass = classOf[UnderlyingType]

  def saxonTypeForMap(config: Configuration) = new ExternalObjectType(UnderlyingClass, config)

  def collectMapValues(it: SequenceIterator)(implicit context: XPathContext): Iterator[UnderlyingType] = {

    val config  = context.getConfiguration
    val mapType = saxonTypeForMap(config)

    asScalaIterator(it) collect {
      case v: ObjectValue if v.getItemType(config.getTypeHierarchy) == mapType =>
        v.getObject.asInstanceOf[UnderlyingType]
    }
  }

  def createValue(value: UnderlyingType, config: Configuration = XPath.GlobalConfiguration): ObjectValue =
    new ObjectValue(
      value,
      saxonTypeForMap(config)
    )
}

trait MapFunctions extends OrbeonFunctionLibrary {

  Namespace(XMLConstants.XPATH_MAP_FUNCTIONS_NAMESPACE_URI) {

    Fun("entry", classOf[MapEntry], op = 0, min = 2, BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE),
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("merge", classOf[MapMerge], op = 0, min = 1,  BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, ALLOWS_ZERO_OR_MORE)
    )

    Fun("get", classOf[MapGet], op = 0, min = 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE),
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE)
    )
  }

}
