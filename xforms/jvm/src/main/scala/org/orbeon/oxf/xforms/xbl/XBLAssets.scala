/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.AssetPath

import scala.collection.compat._
import scala.collection.mutable

object XBLAssets {

  sealed trait HeadElement
  case   class ReferenceElement(src: String) extends HeadElement
  case   class InlineElement(text: String)   extends HeadElement

  object HeadElement {
    def apply(e: Element): HeadElement = {

      val href    = e.attributeValue("href")
      val src     = e.attributeValue("src")
      val resType = e.attributeValue("type")
      val rel     = e.attributeValueOpt("rel") getOrElse "" toLowerCase

      e.getName match {
        case "link" if (href ne null) && ((resType eq null) || resType == "text/css") && rel == "stylesheet" =>
          ReferenceElement(href)
        case "style" if (src ne null) && ((resType eq null) || resType == "text/css") =>
          ReferenceElement(src)
        case "script" if (src ne null) && ((resType eq null) || resType == "text/javascript") =>
          ReferenceElement(src)
        case "style" if src eq null  =>
          InlineElement(e.getStringValue)
        case "script" if src eq null =>
          InlineElement(e.getStringValue)
        case _ =>
          throw new IllegalArgumentException(
            s"Invalid element passed to HeadElement(): ${e.toDebugString}"
          )
      }
    }
  }

  // All elements ordered in a consistent way: first by CSS name, then in the order in which they appear for that
  // given CSS name, removing duplicates
  //
  // NOTE: We used to attempt to sort by binding QName, when all bindings were direct. The code was actually incorrect
  // and "sorted" by <xbl:binding> instead (so no sorting). Now we wort by CSS name instead.
  def orderedHeadElements(
    bindings        : Iterable[AbstractBinding],
    getHeadElements : AbstractBinding => Seq[HeadElement]
  ): List[HeadElement] =
    (bindings.to(List) sortBy (_.commonBinding.cssName)).iterator.flatMap(getHeadElements).to(mutable.LinkedHashSet).to(List)

  // Output baseline, remaining, and inline resources
  def outputResources(
    outputElement : (Option[String], Option[String], Option[String]) => Unit,
    builtin       : List[AssetPath],
    headElements  : Iterable[HeadElement],
    xblBaseline   : Iterable[String],
    minimal       : Boolean
  ): Unit = {

    // For now, actual builtin resources always include the baseline builtin resources
    val builtinBaseline: mutable.LinkedHashSet[String] = builtin.iterator.map(_.assetPath(minimal)).to(mutable.LinkedHashSet)
    val allBaseline = builtinBaseline ++ xblBaseline

    // Output baseline resources with a CSS class
    allBaseline foreach (s => outputElement(Some(s), Some("xforms-baseline"), None))

    // This is in the order defined by XBLBindings.orderedHeadElements
    val xbl = headElements

    val builtinUsed: mutable.LinkedHashSet[String] = builtin.iterator.map(_.assetPath(minimal)).to(mutable.LinkedHashSet)
    val xblUsed: List[String] = xbl.iterator.collect({ case e: ReferenceElement => e.src }).to(List)

    // Output remaining resources if any, with no CSS class
    builtinUsed ++ xblUsed -- allBaseline foreach (s => outputElement(Some(s), None, None))

    // Output inline XBL resources
    xbl collect { case e: InlineElement => outputElement(None, None, Option(e.text)) }
  }
}
