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

import org.orbeon.xforms.HeadElement

import scala.collection.mutable
import scala.collection.compat._


case class XBLAssets(cssName: Option[String], scripts: Seq[HeadElement], styles: Seq[HeadElement])

object XBLAssets {

  // All elements ordered in a consistent way: first by CSS name, then in the order in which they appear for that
  // given CSS name, removing duplicates
  //
  // NOTE: We used to attempt to sort by binding `QName`, when all bindings were direct. The code was actually incorrect
  // and "sorted" by `<xbl:binding>` instead (so no sorting). Now we sort by CSS name instead.
  def orderedHeadElements(
    bindings        : Iterable[XBLAssets],
    getHeadElements : XBLAssets => Seq[HeadElement]
  ): List[HeadElement] =
    (bindings.toList sortBy (_.cssName)).iterator.flatMap(getHeadElements).to(mutable.LinkedHashSet).to(List)
}
