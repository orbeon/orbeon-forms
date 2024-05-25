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
import org.orbeon.oxf.fr.{AppForm, SearchVersion}
import org.orbeon.oxf.fr.permission.Operation
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.search.adt.Metadata.LastModified

import java.time.Instant


trait SearchRequestCommon {
  def provider           : Provider
  def appForm            : AppForm
  def version            : SearchVersion
  def credentials        : Option[Credentials]
  def anyOfOperations    : Option[Set[Operation]]
  def isInternalAdminUser: Boolean
}

sealed trait FilterType
sealed trait ControlFilterType  extends FilterType
sealed trait MetadataFilterType extends FilterType { def sql: String }

object ControlFilterType {
  case class Substring(filter: String)       extends ControlFilterType
  case class Exact    (filter: String)       extends ControlFilterType
  case class Token    (filter: List[String]) extends ControlFilterType
}

object MetadataFilterType {
  sealed trait InstantFilterType extends MetadataFilterType { def filter: Instant }
  sealed trait StringFilterType  extends MetadataFilterType { def filter: String }

  case class GreaterThanOrEqual(filter: Instant) extends InstantFilterType { override val sql = ">=" }
  case class LowerThan         (filter: Instant) extends InstantFilterType { override val sql = "<" }
  case class Exact             (filter: String)  extends StringFilterType  { override val sql = "=" }
}

sealed trait Metadata {
  def string   : String
  def sqlColumn: String
}

object Metadata {
  case object Created        extends Metadata {
    override val string    = "created"
    override val sqlColumn = "created"
  }
  case object CreatedBy      extends Metadata {
    override val string    = "created-by"
    override val sqlColumn = "username"
  }
  case object LastModified   extends Metadata {
    override val string    = "last-modified"
    override val sqlColumn = "last_modified_time"
  }
  case object LastModifiedBy extends Metadata {
    override val string    = "last-modified-by"
    override val sqlColumn = "last_modified_by"
  }
  case object WorkflowStage  extends Metadata {
    override val string    = "workflow-stage"
    override val sqlColumn = "stage"
  }

  val values: Seq[Metadata] = Seq(Created, CreatedBy, LastModified, LastModifiedBy, WorkflowStage)

  def apply(string: String): Metadata =
    values.find(_.string == string).getOrElse(throw new IllegalArgumentException(s"Invalid metadata: $string"))
}

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

sealed trait Query {
  type FT <: FilterType
  def filterType    : Option[FT]
  def sqlColumn     : String
  def orderDirection: Option[OrderDirection]
}

case class ControlQuery (
  path          : String,
  filterType    : Option[ControlFilterType],
  orderDirection: Option[OrderDirection]
) extends Query {
  type FT = ControlFilterType
  override val sqlColumn: String = "val"
}
case class MetadataQuery(
  metadata      : Metadata,
  filterType    : Option[MetadataFilterType],
  orderDirection: Option[OrderDirection]
) extends Query {
  type FT = MetadataFilterType
  override val sqlColumn: String = metadata.sqlColumn
}

case class SearchRequest(
  provider           : Provider,
  appForm            : AppForm,
  version            : SearchVersion,
  credentials        : Option[Credentials],
  isInternalAdminUser: Boolean,
  pageSize           : Int,
  pageNumber         : Int,
  queries            : List[Query],
  drafts             : Drafts,
  freeTextSearch     : Option[String],
  anyOfOperations    : Option[Set[Operation]]
) extends SearchRequestCommon {
  val orderQuery: Query =
    queries.filter(_.orderDirection.isDefined) match {
      case headQuery :: Nil => headQuery
      case Nil              => MetadataQuery(LastModified, filterType = None, Some(Descending)) // Default order
      case _                => throw new IllegalArgumentException("Only one order query is allowed")
    }
}

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
