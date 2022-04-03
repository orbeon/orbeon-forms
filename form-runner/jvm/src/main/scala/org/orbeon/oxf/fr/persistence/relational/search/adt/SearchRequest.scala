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

import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.permission.Operation
import org.orbeon.oxf.fr.persistence.relational.Provider


case class SearchRequest(
  provider        : Provider,
  appForm         : AppForm,
  version         : SearchVersion,
  username        : Option[String],
  group           : Option[String],
  pageSize        : Int,
  pageNumber      : Int,
  columns         : List[Column],
  drafts          : Drafts,
  freeTextSearch  : Option[String],
  anyOfOperations : Option[List[Operation]]
)

sealed trait FilterType

object FilterType {
  case object None                             extends FilterType
  case class  Substring (filter: String)       extends FilterType
  case class  Exact     (filter: String)       extends FilterType
  case class  Token     (filter: List[String]) extends FilterType
}

case class Column(
  path           : String,
  filterType     : FilterType
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
