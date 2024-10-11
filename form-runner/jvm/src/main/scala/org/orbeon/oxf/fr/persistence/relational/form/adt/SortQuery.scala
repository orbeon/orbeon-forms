/**
 * Copyright (C) 2024 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.form.adt

import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.NodeInfoOps

import java.time.Instant


case class SortQuery[T](
  metadata               : Metadata[T],
  languageOpt            : Option[String],
  localRemoteOrCombinator: LocalRemoteOrCombinator,
  orderDirection         : OrderDirection
) extends FilterOrSortQuery[T] {

  def toXML: NodeInfo =
    elem("sort", value = "", "direction" -> orderDirection.string)
}

object SortQuery {
  def apply[T](xml: NodeInfo): SortQuery[T] =
    SortQuery(
      metadata                = Metadata(xml.attValue("metadata")).asInstanceOf[Metadata[T]], // TODO: fix type design/inference
      languageOpt             = xml.attValueOpt("*:lang"),
      localRemoteOrCombinator = LocalRemoteOrCombinator(xml),
      orderDirection          = OrderDirection(xml.attValue("direction"))
    )

  val defaultSortQuery: SortQuery[Instant] =
    SortQuery[Instant](
      metadata                = Metadata.LastModified,
      languageOpt             = None,
      localRemoteOrCombinator = LocalRemoteOrCombinator.Max, // Most recent between local and remote forms
      orderDirection          = OrderDirection.Descending
    )
}
