/**
  * Copyright (C) 2019 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr.datamigration

import org.orbeon.dom.Document
import org.orbeon.dom.saxon.{DocumentWrapper, NodeWrapper}
import org.orbeon.oxf.fr.DataFormatVersion.MigrationVersion
import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.{AppForm, DataFormatVersion, XMLNames}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{StaticXPath, XPath}
import org.orbeon.oxf.xforms.action.XFormsAPI.{delete, instanceRoot}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.util.StaticXPath.VirtualNodeType
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.collection.{immutable => i}


object MigrationSupport {

  val AllMigrationOps: List[MigrationOps] = List(MigrationOps48, MigrationOps20191)

  // `val migrationsFromForm: (ops: MigrationOps) => ops.M ` = ops => ...`
  class MigrationsFromForm(
    outerDocument        : DocumentNodeInfoType,
    availableXBLBindings : Option[DocumentNodeInfoType],
    legacyGridsOnly      : Boolean
  ) extends MigrationGetter {
    def find(ops: MigrationOps): Option[ops.M] =
      ops.buildMigrationSet(outerDocument, availableXBLBindings, legacyGridsOnly)
  }

  // `val migrationsFromMetadata: (ops: MigrationOps) => ops.M ` = ops => ...`
  class MigrationsFromMetadata(
    metadataRootElemOpt : Option[NodeInfo]
  ) extends MigrationGetter {
    def find(ops: MigrationOps): Option[ops.M] =
      metadataRootElemOpt flatMap (metadata => findMigrationForVersion(metadata, ops.version)) match {
        // Using `map` fails because Scala 2.12 cannot represent the dependent function type!
        case Some(jsonString) => Some(ops.decodeMigrationSetFromJson(jsonString))
        case None             => None
      }
  }

  def findAllGrids(doc: DocumentNodeInfoType, repeat: Boolean): Seq[NodeInfo] =
    frc.getFormRunnerBodyElem(doc) descendant * filter frc.IsGrid filter (g => frc.isRepeat(g) ^ ! repeat)

  def findLegacyRepeatedGrids(doc: DocumentNodeInfoType): Seq[NodeInfo] =
    findAllGrids(doc, repeat = true) filter frc.isLegacyRepeat

  def findLegacyUnrepeatedGrids(doc: DocumentNodeInfoType): Seq[NodeInfo] =
    findAllGrids(doc, repeat = false) filter frc.isLegacyUnrepeatedGrid

  def findMigrationForVersion(metadataRootElem: NodeInfo, version: MigrationVersion): Option[String] =
    metadataRootElem                                        child
      "migration"                                           find
      (_.attValueOpt("version") contains version.entryName) flatMap
      (_.stringValue.trimAllToOpt)

  def buildGridMigrations[M](
    outerDocument         : DocumentNodeInfoType,
    availableXBLBindings  : Option[DocumentNodeInfoType],
    migrationsForBinding  : (DocumentNodeInfoType, Boolean) => Seq[M],
    updateWithBindingPath : (M, List[PathElem]) => M
  ): Seq[M] = {

    // Process section templates only if bindings are provided
    val (sectionsWithTemplates, xblBindingsByURIQualifiedName) =
      availableXBLBindings match {
        case Some(bindingsDocument) =>
          (
            frc.findSectionsWithTemplates(frc.getFormRunnerBodyElem(outerDocument)),
            frc.sectionTemplateXBLBindingsByURIQualifiedName(bindingsDocument.rootElement / XBLXBLTest)
          )
        case None =>
          (
            Nil,
            Map.empty[URIQualifiedName, DocumentNodeInfoType]
          )
      }

    def migrationsForSectionTemplate(section: NodeInfo): Seq[M] = {

      val sectionName    = frc.getControlNameOpt(section).get            // section must have a name
      val xblBindingName = frc.sectionTemplateBindingName(section).get   // section must have a binding
      val xblBinding     = xblBindingsByURIQualifiedName(xblBindingName) // binding must be available

      // NOTE: Don't use findDataHolders. We don't want current holders, as there might be none if there is are
      // currently no iterations around a section template, for example. We must find this statically.
      val BindPath(_, holderPath) = frc.findBindAndPathStatically(outerDocument, sectionName).head // bind must be found

      migrationsForBinding(xblBinding.root, false) map (updateWithBindingPath(_, holderPath))
    }

    migrationsForBinding(outerDocument, true) ++ (sectionsWithTemplates flatMap migrationsForSectionTemplate)
  }

  def buildGridMigrationSet(
    outerDocument        : DocumentNodeInfoType,
    availableXBLBindings : Option[DocumentNodeInfoType],
    legacyGridsOnly      : Boolean
  ): List[(MigrationVersion, String)] =
    for {
      ops        <- AllMigrationOps
      m          <- ops.buildMigrationSet(outerDocument, availableXBLBindings, legacyGridsOnly).toList
      jsonString = ops.encodeMigrationsToJson(m)
    } yield
      m.version -> jsonString

  def isMigrateUp(
    srcVersion : DataFormatVersion,
    dstVersion : DataFormatVersion
  ): Boolean =
    DataFormatVersion.indexOf(srcVersion) < DataFormatVersion.indexOf(dstVersion)

  def findMigrationOps(
    srcVersion : DataFormatVersion,
    dstVersion : DataFormatVersion
  ): (Boolean, i.Seq[MigrationOps]) = {

    val migrateUp = isMigrateUp(srcVersion, dstVersion)

    val migrationOpsVersions =
      if (migrateUp)
        DataFormatVersion.values.reverse dropWhile (_ != dstVersion) takeWhile (_ != srcVersion) reverse
      else
        DataFormatVersion.values.reverse dropWhile (_ != srcVersion) takeWhile (_ != dstVersion)

    (migrateUp, migrationOpsVersions flatMap (v => AllMigrationOps find (_.version == v)))
  }

  // Migrate data in place from one version to the other
  //
  // The function takes as parameter a function, `findMigrationSet`, (encapsulated as a method until Scala 3)
  // which finds the migration metadata.
  //
  def migrateDataInPlace(
    dataRootElem     : NodeWrapper,
    srcVersion       : DataFormatVersion,
    dstVersion       : DataFormatVersion,
    findMigrationSet : MigrationGetter // `(ops: MigrationOps) => ops.M `
  ): MigrationResult =
    if (srcVersion == dstVersion) {
      MigrationResult.None
    } else {

      val (migrateUp, migrationOpsToApply) = findMigrationOps(srcVersion, dstVersion)

      migrationOpsToApply.foldLeft(MigrationResult.None: MigrationResult) {
        case (migrationResultSoFar, ops) =>

          val migrate =
            if (migrateUp)
              ops.migrateDataUp(dataRootElem, _)
            else
              ops.migrateDataDown(dataRootElem, _)

          val newResultOpt = findMigrationSet.find(ops) map migrate

          MigrationResult(
            migrationResultSoFar == MigrationResult.Some || newResultOpt.contains(MigrationResult.Some)
          )
      }
    }

  // 2021-03-11: When migrating "down", this only supports "4.8.0" and "2019.1.0" as destination.
  def migrateOtherInPlace(
    formRootElem     : DocumentWrapper,
    srcVersion       : DataFormatVersion,
    dstVersion       : DataFormatVersion,
    findMigrationSet : MigrationGetter // `(ops: MigrationOps) => ops.M `
  ): MigrationResult =
    if (srcVersion == dstVersion) {
      MigrationResult.None
    } else {

      val (migrateUp, migrationOpsToApply) = findMigrationOps(srcVersion, dstVersion)

      migrationOpsToApply.foldLeft(MigrationResult.None: MigrationResult) {
        case (migrationResultSoFar, ops) =>

          val migrate =
            if (migrateUp)
              ops.migrateOthersUp(formRootElem, _)
            else
              ops.migrateOthersDown(formRootElem, _)

          val newResultOpt = findMigrationSet.find(ops) map migrate

          MigrationResult(
            migrationResultSoFar == MigrationResult.Some || newResultOpt.contains(MigrationResult.Some)
          )
      }
    }

  def migrateDataWithFormMetadataMigrations(
    appForm             : AppForm,
    data                : DocumentNodeInfoType,
    metadataRootElemOpt : Option[NodeInfo],
    srcVersion          : DataFormatVersion,
    dstVersion          : DataFormatVersion,
    pruneMetadata       : Boolean
  ): Option[DocumentWrapper] =
    appForm match {
      case AppForm.FormBuilder => None
      case _ =>
        val mutableData = copyDocumentKeepInstanceData(data)

        val result =
          migrateDataInPlace(
            dataRootElem     = mutableData.rootElement.asInstanceOf[NodeWrapper],
            srcVersion       = srcVersion,
            dstVersion       = dstVersion,
            findMigrationSet = new MigrationsFromMetadata(metadataRootElemOpt)
          )

        if (pruneMetadata)
          pruneFormRunnerMetadataFromMutableData(mutableData)

        result == MigrationResult.Some || pruneMetadata option mutableData
    }

  def migrateDataWithFormDefinition(
    data          : DocumentNodeInfoType,
    form          : DocumentNodeInfoType,
    srcVersion    : DataFormatVersion,
    dstVersion    : DataFormatVersion,
    pruneMetadata : Boolean
  ): Option[DocumentWrapper] = {
      val mutableData = copyDocumentKeepInstanceData(data)

      val result =
        migrateDataInPlace(
          dataRootElem     = mutableData.rootElement.asInstanceOf[NodeWrapper],
          srcVersion       = srcVersion,
          dstVersion       = dstVersion,
          findMigrationSet = new MigrationsFromForm(form, instanceRoot("fb-components-instance") map (_.root), legacyGridsOnly = false)
        )

      if (pruneMetadata)
        pruneFormRunnerMetadataFromMutableData(mutableData)

      result == MigrationResult.Some || pruneMetadata option mutableData
    }

  // TODO: This is not strictly related to migration, maybe move somewhere else.
  def copyDocumentKeepInstanceData(data: DocumentNodeInfoType): DocumentWrapper =
    new DocumentWrapper(
      data match {
        case virtualNode: VirtualNodeType =>
          virtualNode.getUnderlyingNode match {
            case doc: Document => Document(doc.getRootElement.createCopy)
            case _             => throw new IllegalStateException
          }
        case _ =>
          StaticXPath.tinyTreeToOrbeonDom(data)
      },
      null,
      XPath.GlobalConfiguration
    )

  // Remove all `fr:*` elements and attributes
  def pruneFormRunnerMetadataFromMutableData(mutableData: DocumentWrapper): DocumentWrapper = {

    // Delete elements from concrete `List` to avoid possible laziness
    val frElements = mutableData descendant * filter (_.namespaceURI == XMLNames.FR) toList

    frElements.foreach (delete(_, doDispatch = false))

    // Attributes: see https://github.com/orbeon/orbeon-forms/issues/3568
    val allElements = mutableData descendant *

    // Prune except `fr:relevant` attributes, as we need them to handle extra relevance information. Those will
    // be kept/removed later as needed by the submission code.
    // https://github.com/orbeon/orbeon-forms/issues/3568
    val frAttributes = allElements /@ @* filter (a => a.namespaceURI == XMLNames.FR && a.localname != "relevant") toList

    frAttributes.foreach (delete(_, doDispatch = false))

    // Also remove all `fr:*` namespaces
    // TODO: This doesn't work: we find the nodes but the delete action doesn't manage to delete the node.
    val frlNamespaces = allElements.namespaceNodes filter (_.stringValue == XMLNames.FR)

    frlNamespaces.foreach (delete(_, doDispatch = false))

    mutableData
  }

  def applyPath(mutableData: NodeInfo, path: i.Seq[PathElem]): Seq[NodeInfo] =
    path.foldLeft(Seq(mutableData)) { case (e, p) => e child p.value }
}
