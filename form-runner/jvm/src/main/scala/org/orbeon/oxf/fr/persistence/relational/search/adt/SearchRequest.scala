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


sealed trait SearchType
object SearchType {
  case object DocumentSearch extends SearchType
  case object FieldSearch    extends SearchType

  def fromString(s: String): SearchType = s match {
    case "document" => DocumentSearch
    case "field"    => FieldSearch
    case other      => throw new IllegalArgumentException(s"Invalid search type: $other")
  }
}

sealed trait SearchRequest {
  def provider        : Provider
  def appForm         : AppForm
  def version         : SearchVersion
  def credentials     : Option[Credentials]
  def anyOfOperations : Option[Set[Operation]]
  def fields          : List[Field]
}

case class DocumentSearchRequest(
  override val provider        : Provider,
  override val appForm         : AppForm,
  override val version         : SearchVersion,
  override val credentials     : Option[Credentials],
  override val anyOfOperations : Option[Set[Operation]],
  override val fields          : List[Field],
  pageSize                     : Int,
  pageNumber                   : Int,
  drafts                       : Drafts,
  freeTextSearch               : Option[String]
) extends SearchRequest

case class FieldSearchRequest(
  override val provider        : Provider,
  override val appForm         : AppForm,
  override val version         : SearchVersion,
  override val credentials     : Option[Credentials],
  override val anyOfOperations : Option[Set[Operation]],
  override val fields          : List[Field]
) extends SearchRequest

sealed trait FilterType

object FilterType {
  case object None                             extends FilterType
  case class  Substring (filter: String)       extends FilterType
  case class  Exact     (filter: String)       extends FilterType
  case class  Token     (filter: List[String]) extends FilterType
}

case class Field(
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
