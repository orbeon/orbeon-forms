/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.fr.datamigration._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId


object GridDataMigration {

  //@XPathFunction
  def dataMaybeMigratedFromDatabaseFormat(
    app         : String,
    form        : String,
    data        : DocumentNodeInfoType,
    metadataOpt : Option[DocumentNodeInfoType]
  ): DocumentNodeInfoType =
    MigrationSupport.migrateDataWithFormMetadataMigrations(
      appForm             = AppForm(app, form),
      data                = data,
      metadataRootElemOpt = metadataOpt.map(_.rootElement),
      srcVersion          = FormRunnerPersistence.providerDataFormatVersionOrThrow(app, form),
      dstVersion          = DataFormatVersion.Edge,
      pruneMetadata       = false
    ) getOrElse
      data

  // NOTE: Exposed to some users.
  //@XPathFunction
  def dataMaybeMigratedToDatabaseFormat(
    app         : String,
    form        : String,
    data        : DocumentNodeInfoType,
    metadataOpt : Option[DocumentNodeInfoType]
  ): DocumentNodeInfoType =
    MigrationSupport.migrateDataWithFormMetadataMigrations(
      appForm             = AppForm(app, form),
      data                = data,
      metadataRootElemOpt = metadataOpt.map(_.rootElement),
      srcVersion          = DataFormatVersion.Edge,
      dstVersion          = FormRunnerPersistence.providerDataFormatVersionOrThrow(app, form),
      pruneMetadata       = false
    ) getOrElse
      data

  // NOTE: Exposed to some users.
  //@XPathFunction
  def dataMaybeMigratedFromFormDefinition(
    data     : DocumentNodeInfoType,
    form     : DocumentNodeInfoType,
    format   : String
  ): DocumentNodeInfoType = {
    MigrationSupport.migrateDataWithFormDefinition(
      data          = data,
      form          = form,
      srcVersion    = DataFormatVersion.Edge,
      dstVersion    = DataFormatVersion.withNameInsensitive(format),
      pruneMetadata = false
    ) getOrElse
      data
  }

  //@XPathFunction
  def dataMaybeMigratedFromEdge(
    app                     : String,
    form                    : String,
    data                    : DocumentNodeInfoType,
    metadataOpt             : Option[DocumentNodeInfoType],
    dataFormatVersionString : String,
    pruneMetadata           : Boolean
  ): DocumentNodeInfoType =
    MigrationSupport.migrateDataWithFormMetadataMigrations(
      appForm             = AppForm(app, form),
      data                = data,
      metadataRootElemOpt = metadataOpt.map(_.rootElement),
      srcVersion          = DataFormatVersion.Edge,
      dstVersion          = DataFormatVersion.withNameIncludeEdge(dataFormatVersionString),
      pruneMetadata       = pruneMetadata
    ) getOrElse
      data

  //@XPathFunction
  def dataMigratedToEdgeOrEmpty(
    app                     : String,
    form                    : String,
    data                    : DocumentNodeInfoType,
    metadataOpt             : Option[DocumentNodeInfoType],
    dataFormatVersionString : String
  ): Option[DocumentWrapper] =
    MigrationSupport.migrateDataWithFormMetadataMigrations(
      appForm             = AppForm(app, form),
      data                = data,
      metadataRootElemOpt = metadataOpt.map(_.rootElement),
      srcVersion          = dataFormatVersionString.trimAllToOpt    map
                              DataFormatVersion.withNameIncludeEdge getOrElse
                              DataFormatVersion.V400,
      dstVersion          = DataFormatVersion.Edge,
      pruneMetadata       = false
    )
}
