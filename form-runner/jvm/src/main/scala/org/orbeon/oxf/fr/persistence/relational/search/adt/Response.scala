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

import org.orbeon.oxf.externalcontext.UserAndGroup

import java.sql.Timestamp

case class DocumentMetadata(
  documentId       : String,
  draft            : Boolean,
  createdTime      : Timestamp,
  lastModifiedTime : Timestamp,
  createdBy        : Option[UserAndGroup],
  lastModifiedBy   : Option[UserAndGroup],
  workflowStage    : Option[String],
  organizationId   : Option[Int]
)

case class DocumentValue(
  control : String,
  pos     : Int,
  value   : String
)

case class DocumentResult(
  metadata   : DocumentMetadata,
  operations : String,
  values     : List[DocumentValue]
)

case class FieldResult(
  path   : String,
  values : Seq[String]
)