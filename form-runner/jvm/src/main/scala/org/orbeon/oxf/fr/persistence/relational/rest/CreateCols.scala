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
package org.orbeon.oxf.fr.persistence.relational.rest

import java.sql.{PreparedStatement, Timestamp}
import org.orbeon.oxf.externalcontext.Organization
import org.orbeon.oxf.fr.persistence.relational.Provider.PostgreSQL
import org.orbeon.oxf.fr.persistence.relational.rest.{OrganizationSupport => _}

trait CreateCols extends RequestResponse with Common {

  type ParamSetterFunc = (PreparedStatement, Int) => Unit
  def param[T](setter: PreparedStatement => (Int, T) => Unit, value: => T): ParamSetterFunc = {
    (ps: PreparedStatement, i: Int) => setter(ps)(i, value)
  }

  case class Row(
    created      : Timestamp,
    username     : Option[String],
    group        : Option[String],
    organization : Option[(Int, Organization)],
    formVersion  : Option[Int],
    stage        : Option[String]
  )

  abstract class ColValue

  case class StaticColValue (
    value: String
  ) extends ColValue

  case class DynamicColValue (
    placeholder            : String,
    paramSetter            : ParamSetterFunc
  ) extends ColValue

  case class Col(
    included               : Boolean,
    name                   : String,
    value                  : ColValue
  )

  def insertCols(
    req                    : Request,
    existingRow            : Option[Row],
    delete                 : Boolean,
    versionToSet           : Int,
    currentUserOrganization: => Option[OrganizationId]
  ): List[Col]  = {

    val xmlCol = "xml"
    val xmlVal = if (req.provider == PostgreSQL) "XMLPARSE( DOCUMENT ? )" else "?"
    val isFormDefinition = req.forForm && !req.forAttachment
    val now = new Timestamp(System.currentTimeMillis())
    val organizationToSet = req.forData match {
      case false => None
      case true  => existingRow match {
        case Some(row) => row.organization.map(_._1)
        case None => currentUserOrganization.map(_.underlying)
      }
    }

    val (xmlOpt, metadataOpt) =
      if (! delete && ! req.forAttachment) {
        val (xml, metadataOpt) = RequestReader.dataAndMetadataAsString(req.provider, metadata = !req.forData)
        (Some(xml), metadataOpt)
      } else {
        (None, None)
      }

    List(
      Col(
        included      = true,
        name          = "created",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setTimestamp, existingRow.map(_.created).getOrElse(now))
        )
      ),
      Col(
        included      = true,
        name          = "last_modified_time",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setTimestamp, now)
        )
      ),
      Col(
        included      = true,
        name          = "last_modified_by",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setString, requestUsername.orNull)
        )
      ),
      Col(
        included      = true,
        name          = "app",
        value         = DynamicColValue(
          placeholder  = "?",
          paramSetter  = param(_.setString, req.app)
        )
      ),
      Col(
        included      = true,
        name          = "form",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setString, req.form)
        )
      ),
      Col(
        included      = true,
        name          = "form_version",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setInt, versionToSet)
        )
      ),
      Col(
        included      = req.forData && req.dataPart.get.stage.isDefined,
        name          = "stage",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setString, req.dataPart.get.stage.get)
        )
      ),
      Col(
        included      = req.forData,
        name          = "document_id",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setString, req.dataPart.get.documentId)
        )
      ),
      Col(
        included      = true,
        name          = "deleted",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setString, if (delete) "Y" else "N")
        )
      ),
      Col(
        included      = req.forData,
        name          = "draft",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setString, if (req.dataPart.get.isDraft) "Y" else "N")
        )
      ),
      Col(
        included      = req.forAttachment,
        name          = "file_name",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setString, req.filename.get)
        )
      ),
      Col(
        included      = req.forAttachment,
        name          = "file_content",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setBytes, RequestReader.bytes())
        )
      ),
      Col(
        included      = isFormDefinition,
        name          = "form_metadata",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setString, metadataOpt.orNull)
        )
      ),
      Col(
        included      = req.forData,
        name          = "username",
        value         = DynamicColValue(
          placeholder = "?" ,
          paramSetter = param(_.setString, existingRow.flatMap(_.username).getOrElse(requestUsername.orNull))
        )
      ),
      Col(
        included      = req.forData,
        name          = "groupname",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setString, existingRow.flatMap(_.group).getOrElse(requestGroup.orNull))
        )
      ),
      Col(
        included      = organizationToSet.isDefined,
        name          = "organization_id",
        value         = DynamicColValue(
          placeholder = "?",
          paramSetter = param(_.setInt, organizationToSet.get)
        )
      ),
      Col(
        included      = ! req.forAttachment ,
        name          = xmlCol,
        value         = DynamicColValue(
          placeholder = xmlVal,
          paramSetter = param(_.setString, xmlOpt.orNull)
        )
      )
    )
  }

}
