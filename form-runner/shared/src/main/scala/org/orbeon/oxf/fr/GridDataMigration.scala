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

import org.orbeon.dom.saxon.{DocumentWrapper, NodeWrapper}
import org.orbeon.oxf.fr.datamigration.*
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.action.XFormsAPI.{delete, insert}
import org.orbeon.scaxon.SimplePath.*


object GridDataMigration {

  // Used by `fr-get-document-submission` in `persistence-model.xml`
  //@XPathFunction
  def dataMaybeMigratedFromDatabaseFormat(
    app         : String,
    form        : String,
    data        : DocumentNodeInfoType,
    metadataOpt : Option[DocumentNodeInfoType]
  ): DocumentNodeInfoType = {

    val appForm              = AppForm(app, form)
    val dstDataFormatVersion = FormRunnerPersistence.getOrGuessFormDataFormatVersion(metadataOpt.map(_.rootElement))

    val migratedOrDuplicatedData =
      MigrationSupport.migrateDataWithFormMetadataMigrations(
        appForm             = appForm,
        data                = data,
        metadataRootElemOpt = metadataOpt.map(_.rootElement),
        srcVersion          = FormRunnerPersistence.providerDataFormatVersionOrThrow(appForm),
        dstVersion          = dstDataFormatVersion,
        pruneMetadata       = false,
        pruneTmpAttMetadata = true // no need to keep this when reading form the persistence
      ) getOrElse
        MigrationSupport.copyDocumentKeepInstanceData(data) // copy so we can handle `fr:data-format-version` below

    updateDataFormatVersionInPlace(appForm, dstDataFormatVersion, migratedOrDuplicatedData.rootElement.asInstanceOf[NodeWrapper])

    migratedOrDuplicatedData
  }

  // 2024-08-08: Not used internally. However, this has been exposed to some external users as an XPath function for the
  // `before-publish` process.
  //@XPathFunction
  def dataMaybeMigratedToDatabaseFormat(
    app         : String,
    form        : String,
    data        : DocumentNodeInfoType,
    metadataOpt : Option[DocumentNodeInfoType]
  ): DocumentNodeInfoType =
    MigrationSupport.migrateDataWithFormMetadataMigrations(
      appForm              = AppForm(app, form),
      data                 = data,
      metadataRootElemOpt  = metadataOpt.map(_.rootElement),
      srcVersion           = FormRunnerPersistence.getOrGuessFormDataFormatVersion(metadataOpt.map(_.rootElement)),
      dstVersion           = FormRunnerPersistence.providerDataFormatVersionOrThrow(AppForm(app, form)),
      pruneMetadata        = false,
      pruneTmpAttMetadata  = true
    ) getOrElse
      data

  // 2024-08-08: Can't find a trace of this being used internally or externally. But it seems that at some point we
  // might have exposed it?
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

  // Used by background process in `persistence-model.xml`, `send`, and save to database.
  //@XPathFunction
  def dataMaybeMigratedFromEdge(
    app                       : String,
    form                      : String,
    data                      : DocumentNodeInfoType,
    metadataOpt               : Option[DocumentNodeInfoType],
    dstDataFormatVersionString: String,
    pruneMetadata             : Boolean,
    pruneTmpAttMetadata       : Boolean
  ): DocumentNodeInfoType = {

    val appForm              = AppForm(app, form)
    val dstDataFormatVersion = DataFormatVersion.withNameIncludeEdge(dstDataFormatVersionString)

    val migratedOrDuplicatedData =
      MigrationSupport.migrateDataWithFormMetadataMigrations(
        appForm              = appForm,
        data                 = data,
        metadataRootElemOpt  = metadataOpt.map(_.rootElement),
        srcVersion           = FormRunnerPersistence.getOrGuessFormDataFormatVersion(metadataOpt.map(_.rootElement)),
        dstVersion           = dstDataFormatVersion,
        pruneMetadata        = pruneMetadata,
        pruneTmpAttMetadata  = pruneTmpAttMetadata
      ) getOrElse
        MigrationSupport.copyDocumentKeepInstanceData(data) // copy so we can handle `fr:data-format-version` below

    updateDataFormatVersionInPlace(appForm, dstDataFormatVersion, migratedOrDuplicatedData.rootElement.asInstanceOf[NodeWrapper])

    migratedOrDuplicatedData
  }

  // Used for data submitted data to page and load from service in `persistence-model.xml`
  //@XPathFunction
  def dataMigratedToEdge(
    app                 : String,
    form                : String,
    data                : DocumentNodeInfoType,
    metadataOpt         : Option[DocumentNodeInfoType],
    srcDataFormatVersion: String
  ): DocumentWrapper =
    MigrationSupport.dataMigratedToEdge(
      appForm              = AppForm(app, form),
      data                 = data,
      metadataOpt          = metadataOpt.map(_.rootElement),
      srcDataFormatVersion = srcDataFormatVersion.trimAllToOpt.map(DataFormatVersion.withNameIncludeEdge).getOrElse(DataFormatVersion.V400)
    )

  def updateDataFormatVersionInPlace(
   appForm                      : AppForm,
   dataFormatVersion            : DataFormatVersion,
   migratedOrDuplicatedDataElem : NodeWrapper
  ): Unit = {

    require(migratedOrDuplicatedDataElem.isElement)

    if (appForm != AppForm.FormBuilder)
      insert(
        into       = List(migratedOrDuplicatedDataElem),
        origin     = List(NodeInfoFactory.attributeInfo(XMLNames.FRDataFormatVersionQName, dataFormatVersion.entryName)),
        doDispatch = false
      )
    else
      delete(
        ref        = migratedOrDuplicatedDataElem /@ XMLNames.FRDataFormatVersionQName,
        doDispatch = false
      )
  }
}
