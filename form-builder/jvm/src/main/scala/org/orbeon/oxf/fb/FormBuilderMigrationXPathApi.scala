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
package org.orbeon.oxf.fb

import org.orbeon.dom.saxon.{DocumentWrapper, NodeWrapper}
import org.orbeon.oxf.fr.DataFormatVersion
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.datamigration.MigrationSupport.MigrationsFromForm
import org.orbeon.oxf.fr.datamigration._
import org.orbeon.saxon.om.{DocumentInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

// Form Builder migration operations called from XSLT/XForms. See also `GridDataMigrationTest`.
object FormBuilderMigrationXPathApi {

  //@XPathFunction
  def findAllRepeatNames(doc: DocumentInfo): SequenceIterator =
    getFormRunnerBodyElem(doc) descendant * filter isRepeat map getControlName

  // Return a sequence of (dataFormatVersion, jsonMigrations)
  //@XPathFunction
  def buildGridMigrationMapXPath(
    outerDocument        : DocumentInfo,
    availableXBLBindings : Option[DocumentInfo]
  ): SequenceIterator =
    MigrationSupport.buildGridMigrationSet(
      outerDocument        = outerDocument,
      availableXBLBindings = availableXBLBindings,
      legacyGridsOnly      = false
    ) flatMap { case (version, jsonString) =>
      List(version.entryName, jsonString)
    }

  //@XPathFunction
  def migrateGridsEnclosingElements(doc: DocumentWrapper): DocumentWrapper = {

    implicit val ctx = FormBuilderDocContext(doc)

    val migrationsFromForm =
      new MigrationsFromForm(
        outerDocument        = doc.root,
        availableXBLBindings = None,
        legacyGridsOnly      = true
      )

    // 1. All grids must have ids for what follows
    FormBuilder.addMissingGridIds(ctx.bodyElem)

    // 2. Migrate inline instance data
    MigrationSupport.migrateDataInPlace(
      dataRootElem     = (ctx.dataInstanceElem child *).head.asInstanceOf[NodeWrapper],
      srcVersion       = DataFormatVersion.V400, // ok because we only process legacy grids
      dstVersion       = DataFormatVersion.Edge,
      findMigrationSet = migrationsFromForm
    )

    // 3. Migrate other aspects such as binds and controls
    MigrationSupport.AllMigrationOps foreach { ops =>
      migrationsFromForm.find(ops) foreach (m => ops.migrateOthersTo(doc, m))
    }

    // Q: Should we migrate repeat templates here?

    doc
  }
}
