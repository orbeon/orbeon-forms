/**
* Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.oxf.fr.datamigration

import org.orbeon.dom.saxon.{DocumentWrapper, NodeWrapper}
import org.orbeon.oxf.fr.DataFormatVersion.MigrationVersion
import org.orbeon.saxon.om.{DocumentInfo, Name10Checker}

import scala.util.matching.Regex


trait MigrationSet {
  val version: MigrationVersion
}

case class PathElem(value: String) {
  require(Name10Checker.getInstance.isValidNCName(value) || PathElem.SpecialFormBuilderPaths(value))
}

object PathElem {

  // The format of the path can be like `(section-3)/(section-3-iteration)/(grid-4)`. Form Builder
  // puts parentheses for the abandoned case of a custom XML format, and we kept that when producing
  // the migration data.
  val TrimPathElementRE: Regex = """\s*\(?([^)^/]+)\)?\s*""".r

  // With Form Builder, `value` can be a `QName` and can even have a predicate. It is ugly to have this
  // here but for now it will do.
  private val SpecialFormBuilderPaths = Set(
    "xh:head",
    "xf:model[@id = 'fr-form-model']",
    "xf:instance[@id = 'fr-form-metadata']/*"
  )
}

sealed trait MigrationResult

object MigrationResult {
  case object None extends MigrationResult
  case object Some extends MigrationResult

  def apply(value: Boolean): MigrationResult =
    if (value) MigrationResult.Some else MigrationResult.None
}

// This until we have Scala 3's dependent function types: `(ops: MigrationOps) => ops.M `! See:
//
//   https://dotty.epfl.ch/docs/reference/new-types/dependent-function-types.html
//
trait MigrationGetter {
  def find(ops: MigrationOps): Option[ops.M]
}

trait MigrationOps {

  type M <: MigrationSet
  val version: MigrationVersion

  def buildMigrationSet(
    outerDocument        : DocumentInfo,
    availableXBLBindings : Option[DocumentInfo],
    legacyGridsOnly      : Boolean
  ): Option[M]

  def encodeMigrationsToJson(
    migrationSet: M
  ): String

  def decodeMigrationSetFromJson(
    jsonString : String
  ): M

  def migrateDataDown(
    dataRootElem : NodeWrapper,
    migrationSet : M
  ): MigrationResult

  def migrateDataUp(
    dataRootElem : NodeWrapper,
    migrationSet : M
  ): MigrationResult

  def migrateOthersTo(
    outerDocument : DocumentWrapper,
    migrationSet  : M
  ): MigrationResult

  def adjustPathTo40(
    migrationSet : M,
    path         : List[PathElem]
  ): Option[List[PathElem]]
}