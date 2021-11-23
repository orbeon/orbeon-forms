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

import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.fr.datamigration._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPath
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
  ): DocumentNodeInfoType = {

    val appForm = AppForm(app, form)

    val dataFormatVersion =
      DataFormatVersion.withNameIncludeEdge(dataFormatVersionString)

    val migratedOrDuplicatedData =
      MigrationSupport.migrateDataWithFormMetadataMigrations(
        appForm             = appForm,
        data                = data,
        metadataRootElemOpt = metadataOpt.map(_.rootElement),
        srcVersion          = DataFormatVersion.Edge,
        dstVersion          = dataFormatVersion,
        pruneMetadata       = pruneMetadata
      ) getOrElse {
        // Make a copy as we only want to set the `fr:data-format-version` attribute on the migrated data
        val originalDataClone = new DocumentWrapper(dom.Document(), null, XPath.GlobalConfiguration)
        insert(
          into                              = List(originalDataClone),
          origin                            = List(data.rootElement),
          removeInstanceDataFromClonedNodes = false // https://github.com/orbeon/orbeon-forms/issues/4911
        )
        originalDataClone
      }

    // Add `fr:data-format-version` attribute on the root element
    if (appForm != AppForm.FormBuilder)
      insert(
        into       = List(migratedOrDuplicatedData.rootElement),
        origin     = List(NodeInfoFactory.attributeInfo(XMLNames.FRDataFormatVersionQName, dataFormatVersion.entryName)),
        doDispatch = false
      )
    else
      delete(
        ref = migratedOrDuplicatedData.rootElement /@ XMLNames.FRDataFormatVersionQName
      )

    migratedOrDuplicatedData
  }

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
