/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.rest

import org.orbeon.oxf.externalcontext.Credentials
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.persistence.relational.Provider

import java.time.Instant


case class DataPart(
  isDraft    : Boolean,
  documentId : String,
  stage      : Option[String],
  forceDelete: Boolean
)

case class LockUnlockRequest(
  provider: Provider,
  dataPart: DataPart
)

case class CrudRequest(
  provider       : Provider,
  appForm        : AppForm,
  version        : Option[Int],
  filename       : Option[String],
  dataPart       : Option[DataPart],
  lastModifiedOpt: Option[Instant], // or just plain string?
  username       : Option[String],
  groupname      : Option[String],
  flatView       : Boolean,
  credentials    : Option[Credentials],
  workflowStage  : Option[String]
) {
  def forForm       : Boolean = dataPart.isEmpty
  def forData       : Boolean = dataPart.isDefined
  def forAttachment : Boolean = filename.isDefined
}
