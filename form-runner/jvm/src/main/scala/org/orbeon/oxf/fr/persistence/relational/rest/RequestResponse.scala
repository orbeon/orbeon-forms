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

import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.persistence.relational._
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.NetUtils


case class DataPart(
  isDraft   : Boolean,
  documentId: String,
  stage     : Option[String]
)

case class LockUnlockRequest(
  provider: Provider,
  dataPart: DataPart
)

case class CrudRequest(
  provider: Provider,
  appForm : AppForm,
  version : Option[Int],
  filename: Option[String],
  dataPart: Option[DataPart]
) {
  def forForm       : Boolean = dataPart.isEmpty
  def forData       : Boolean = dataPart.isDefined
  def forAttachment : Boolean = filename.isDefined
}

// xxx move to an object
trait RequestResponse extends Common {

  def tableName(request: CrudRequest, master: Boolean = false): String =
    Seq(
      Some("orbeon_form"),
      request.forForm                   option "_definition",
      request.forData                   option "_data",
      request.forAttachment && ! master option "_attach"
    ).flatten.mkString

  def httpRequest = NetUtils.getExternalContext.getRequest
  def headerValueIgnoreCase(name: String): Option[String] = httpRequest.getFirstHeaderIgnoreCase(name)

  def requestUsername      : Option[String] = headerValueIgnoreCase(Headers.OrbeonUsername)
  def requestGroup         : Option[String] = headerValueIgnoreCase(Headers.OrbeonGroup)
  def requestFlatView      : Boolean        = headerValueIgnoreCase("orbeon-create-flat-view").contains("true")
  def requestWorkflowStage : Option[String] = headerValueIgnoreCase(StageHeader.HeaderName)

  val CrudFormPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/form/([^/]+)".r
  val CrudDataPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/(data|draft)/([^/]+)/([^/]+)".r

  def httpResponse = NetUtils.getExternalContext.getResponse
}
