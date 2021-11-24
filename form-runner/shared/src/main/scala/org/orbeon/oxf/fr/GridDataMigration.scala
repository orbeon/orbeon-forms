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
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.action.XFormsAPI.{delete, insert}
import org.orbeon.scaxon.SimplePath._


object GridDataMigration {

  //@XPathFunction
  def dataMaybeMigratedFromDatabaseFormat(
    app         : String,
    form        : String,
    data        : DocumentNodeInfoType,
    metadataOpt : Option[DocumentNodeInfoType]
  ): DocumentNodeInfoType = {

    val appForm              = AppForm(app, form)
    val dstDataFormatVersion = DataFormatVersion.Edge

    val migratedOrDuplicatedData =
      MigrationSupport.migrateDataWithFormMetadataMigrations(
        appForm             = appForm,
        data                = data,
        metadataRootElemOpt = metadataOpt.map(_.rootElement),
        srcVersion          = FormRunnerPersistence.providerDataFormatVersionOrThrow(app, form),
        dstVersion          = dstDataFormatVersion,
        pruneMetadata       = false
      ) getOrElse
        MigrationSupport.copyDocumentKeepInstanceData(data) // copy so we can handle `fr:data-format-version` below

    updateDataFormatVersion(appForm, dstDataFormatVersion, migratedOrDuplicatedData)

    migratedOrDuplicatedData
  }

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
  ): DocumentNodeInfoType = {

    val appForm              = AppForm(app, form)
    val dstDataFormatVersion = DataFormatVersion.withNameIncludeEdge(dataFormatVersionString)

    val migratedOrDuplicatedData =
      MigrationSupport.migrateDataWithFormMetadataMigrations(
        appForm             = appForm,
        data                = data,
        metadataRootElemOpt = metadataOpt.map(_.rootElement),
        srcVersion          = DataFormatVersion.Edge,
        dstVersion          = dstDataFormatVersion,
        pruneMetadata       = pruneMetadata
      ) getOrElse
        MigrationSupport.copyDocumentKeepInstanceData(data) // copy so we can handle `fr:data-format-version` below

    updateDataFormatVersion(appForm, dstDataFormatVersion, migratedOrDuplicatedData)

    migratedOrDuplicatedData
  }

  //@XPathFunction
  def dataMigratedToEdge(
    app                     : String,
    form                    : String,
    data                    : DocumentNodeInfoType,
    metadataOpt             : Option[DocumentNodeInfoType],
    dataFormatVersionString : String
  ): DocumentWrapper = {

    val appForm              = AppForm(app, form)
    val dstDataFormatVersion = DataFormatVersion.Edge

    val migratedOrDuplicatedData =
      MigrationSupport.migrateDataWithFormMetadataMigrations(
        appForm             = appForm,
        data                = data,
        metadataRootElemOpt = metadataOpt.map(_.rootElement),
        srcVersion          = dataFormatVersionString.trimAllToOpt    map
                                DataFormatVersion.withNameIncludeEdge getOrElse
                                DataFormatVersion.V400,
        dstVersion          = dstDataFormatVersion,
        pruneMetadata       = false
      ) getOrElse
        MigrationSupport.copyDocumentKeepInstanceData(data) // copy so we can handle `fr:data-format-version` below

    updateDataFormatVersion(appForm, dstDataFormatVersion, migratedOrDuplicatedData)

    migratedOrDuplicatedData
  }

  private def updateDataFormatVersion(
    appForm                  : AppForm,
    dataFormatVersion        : DataFormatVersion,
    migratedOrDuplicatedData : DocumentWrapper
  ): Unit =
    if (appForm != AppForm.FormBuilder)
      insert(
        into       = List(migratedOrDuplicatedData.rootElement),
        origin     = List(NodeInfoFactory.attributeInfo(XMLNames.FRDataFormatVersionQName, dataFormatVersion.entryName)),
        doDispatch = false
      )
    else
      delete(
        ref        = migratedOrDuplicatedData.rootElement /@ XMLNames.FRDataFormatVersionQName,
        doDispatch = false
      )
}
