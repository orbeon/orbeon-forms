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


case class Pagination(
  pageNumber: Int, // 1-based page number
  pageSize  : Int
) {

  def toXML: NodeInfo =
    Form.elem("pagination", value ="", "page-number" -> pageNumber.toString, "page-size" -> pageSize.toString)

  // 0-based start and end indexes
  val startIndex: Int = (pageNumber - 1) * pageSize
  val endIndex  : Int = startIndex + pageSize
}

object Pagination {
  def apply(xml: NodeInfo): Pagination = {

    val pageNumberOpt = xml.attValueOpt("page-number").map(_.toInt)
    val pageSizeOpt   = xml.attValueOpt("page-size")  .map(_.toInt)

    if (pageNumberOpt.exists(_ < 1) || pageSizeOpt.exists(_ < 1)) {
      throw new IllegalArgumentException("Page number/size must be at least 1")
    }

    // Use same defaults as in search API
    Pagination(
      pageNumber = pageNumberOpt.getOrElse(1),
      pageSize   = pageSizeOpt  .getOrElse(10)
    )
  }
}
