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
package org.orbeon.oxf.xforms.function.map

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.map.MapFunctions.saxonTypeForMap
import org.orbeon.oxf.xml.SaxonUtils.StringValueWithEquals
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.`type`.{ExternalObjectType, ItemType, TypeHierarchy}
import org.orbeon.saxon.expr.{PathMap, XPathContext}
import org.orbeon.saxon.om._
import org.orbeon.saxon.value._
import org.orbeon.scaxon.XML

trait MapFunction extends XFormsFunction {

//  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet =
//    addSubExpressionsToPathMap(pathMap, pathMapNodeSet)
}

//
// `map:entry($key as xs:anyAtomicType, $value as item()*) as map(*)`
//
class MapEntry extends MapFunction {

  import MapFunctions._

  override def evaluateItem(context: XPathContext): ObjectValue = {

    implicit val ctx = context

    val config  = context.getConfiguration
    val mapType = saxonTypeForMap(config)

    val key   = itemArgument(0)
    val value = itemsArgument(1)

    new ObjectValue(
      Map(fixStringValue(key) → new SequenceExtent(value)),
      mapType
    )
  }

  override def getItemType(th: TypeHierarchy): ItemType =
    saxonTypeForMap(th.getConfiguration)
}

//
// `map:merge($maps as map(*)*) as map(*)`
//
class MapMerge extends MapFunction {

  import MapFunctions._

  override def evaluateItem(context: XPathContext): ObjectValue = {

    implicit val ctx = context

    val config  = context.getConfiguration
    val mapType = saxonTypeForMap(config)

    val maps = itemsArgumentOpt(0).iterator flatMap collectMapValues

    new ObjectValue(
      maps.foldLeft(Map.empty[AtomicValue, ValueRepresentation])(_ ++ _),
      mapType
    )
  }

  override def getItemType(th: TypeHierarchy): ItemType =
    saxonTypeForMap(th.getConfiguration)
}

//
// `map:get($map as map(*), $key as xs:anyAtomicType) as item()*`
//
class MapGet extends MapFunction {

  import MapFunctions._

  override def iterate(context: XPathContext): SequenceIterator = {

    implicit val ctx = context

    val map = itemsArgumentOpt(0).iterator flatMap collectMapValues next()
    val key = fixStringValue(itemArgument(1)).asInstanceOf[AtomicValue]

    map.getOrElse(key, EmptySequence.getInstance) match {
      case v: Value    ⇒ v.iterate()
      case v: NodeInfo ⇒ SingletonIterator.makeIterator(v)
      case _           ⇒ throw new IllegalStateException
    }
  }
}

private object MapFunctions {

  val UnderlyingClass = classOf[Map[AtomicValue, ValueRepresentation]]

  def saxonTypeForMap(config: Configuration) = new ExternalObjectType(UnderlyingClass, config)

  def collectMapValues(it: SequenceIterator)(implicit xpathContext: XPathContext): Iterator[Map[AtomicValue, ValueRepresentation]] = {

    val config  = xpathContext.getConfiguration
    val mapType = saxonTypeForMap(config)

    XML.asScalaIterator(it) collect {
      case v: ObjectValue if v.getItemType(config.getTypeHierarchy) == mapType ⇒
        v.getObject.asInstanceOf[Map[AtomicValue, ValueRepresentation]]
    }
  }

  def fixStringValue(item: Item): Item =
    item match {
      case v: StringValue ⇒ new StringValueWithEquals(v.getStringValueCS)
      case v              ⇒ v
    }

}