/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*


object FormRunnerSummaryOrder {

  val SummaryControlColumnsElemName = "summary-control-columns"
  val ColumnElemName                = "column"
  val ControlAttName                = "control"
  val SectionNameAttName            = "section-name"

  case class SummaryControlColumn(
    control       : String,
    sectionNameOpt: Option[String] = None
  )

  def readSummaryControlColumns(metadataRootElemOpt: Option[NodeInfo]): List[SummaryControlColumn] =
    (for {
      metadataElem <- metadataRootElemOpt.toList
      columnElem   <- metadataElem / SummaryControlColumnsElemName / ColumnElemName
      controlName  <- columnElem.attValueNonBlankOpt(ControlAttName).toList
    } yield
      SummaryControlColumn(
        control        = controlName,
        sectionNameOpt = columnElem.attValueNonBlankOpt(SectionNameAttName)
      )).distinct

  def sortBySummaryOrder[A](
    items      : Seq[A],
    customOrder: Seq[SummaryControlColumn]
  )(
    keyOf      : A => SummaryControlColumn
  ): List[A] =
    if (customOrder.isEmpty) {
      items.toList
    } else {
      val rankByKey: Map[SummaryControlColumn, Int] =
        customOrder.distinct.zipWithIndex.toMap

      items
        .zipWithIndex
        .sortBy { case (item, originalIdx) =>
          rankByKey.get(keyOf(item)) match {
            case Some(rank) => (0, rank, originalIdx)
            case None       => (1, 0,    originalIdx)
          }
        }
        .map(_._1)
        .toList
    }
}
