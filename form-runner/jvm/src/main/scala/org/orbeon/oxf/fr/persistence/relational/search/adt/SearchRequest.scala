/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.search.adt

import org.orbeon.oxf.externalcontext.Credentials
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.permission.Operation
import org.orbeon.oxf.fr.persistence.SearchVersion
import org.orbeon.oxf.fr.persistence.relational.Provider

import java.time.Instant


trait SearchRequestCommon {
  def provider           : Provider
  def appForm            : AppForm
  def version            : SearchVersion
  def credentials        : Option[Credentials]
  def anyOfOperations    : Option[Set[Operation]]
  def isInternalAdminUser: Boolean
}

object OrderColumn {
  def apply(s: String): OrderColumn = s.toLowerCase.trim match {
    case "created"          => Created
    case "created-by"       => CreatedBy
    case "last-modified"    => LastModified
    case "last-modified-by" => LastModifiedBy
    case "workflow-stage"   => WorkflowStage
    case controlPath        => ControlColumn(controlPath)
  }
}

sealed trait OrderColumn {
  val sql: String
}

case object Created                    extends OrderColumn { override val sql = "created" }
case object CreatedBy                  extends OrderColumn { override val sql = "username" }
case object LastModified               extends OrderColumn { override val sql = "last_modified_time" }
case object LastModifiedBy             extends OrderColumn { override val sql = "last_modified_by" }
case object WorkflowStage              extends OrderColumn { override val sql = "stage" }
case class ControlColumn(path: String) extends OrderColumn { override val sql = "val" }

object OrderDirection {
  def apply(s: String): OrderDirection = s.toLowerCase.trim match {
    case "asc"  => Ascending
    case "desc" => Descending
    case _      => throw new IllegalArgumentException(s"Invalid order direction: $s")
  }
}

sealed trait OrderDirection {
  val sql: String
}
case object Ascending  extends OrderDirection { override val sql = "ASC" }
case object Descending extends OrderDirection { override val sql = "DESC" }

case class OrderBy(column: OrderColumn, direction: OrderDirection)

case class SearchRequest(
  provider           : Provider,
  appForm            : AppForm,
  version            : SearchVersion,
  credentials        : Option[Credentials],
  isInternalAdminUser: Boolean,
  pageSize           : Int,
  pageNumber         : Int,
  orderBy            : OrderBy,
  createdGteOpt      : Option[Instant],
  createdLtOpt       : Option[Instant],
  createdBy          : Set[String],
  lastModifiedGteOpt : Option[Instant],
  lastModifiedLtOpt  : Option[Instant],
  lastModifiedBy     : Set[String],
  workflowStage      : Set[String],
  controls           : List[Control],
  drafts             : Drafts,
  freeTextSearch     : Option[String],
  anyOfOperations    : Option[Set[Operation]]
) extends SearchRequestCommon

sealed trait FilterType

object FilterType {
  case object None                             extends FilterType
  case class  Substring (filter: String)       extends FilterType
  case class  Exact     (filter: String)       extends FilterType
  case class  Token     (filter: List[String]) extends FilterType
}

case class Control(
  path       : String,
  filterType : FilterType
)

sealed trait Drafts

object Drafts {
  case object ExcludeDrafts                        extends Drafts
  case class  OnlyDrafts(whichDrafts: WhichDrafts) extends Drafts
  case object IncludeDrafts                        extends Drafts
}

sealed trait WhichDrafts

object WhichDrafts {
  case object AllDrafts                               extends WhichDrafts
  case object DraftsForNeverSavedDocs                 extends WhichDrafts
  case class  DraftsForDocumentId(documentId: String) extends WhichDrafts
}
