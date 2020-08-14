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
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerPersistence.DataFormatVersionName
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.SimplePath._

trait FormRunnerSummary {

  //@XPathFunction
  def findIndexedControlsAsXML(formDoc: DocumentInfo, app: String, form: String): Seq[NodeInfo] =
    Index.findIndexedControls(
      formDoc,
      FormRunnerPersistence.providerDataFormatVersionOrThrow(app, form)
    ) map
      (_.toXML)

  //@XPathFunction
  def duplicate(
    data          : NodeInfo,
    app           : String,
    form          : String,
    fromDocument  : String,
    toDocument    : String,
    formVersion   : String,
    workflowStage : String
  ): Unit = {

    val databaseDataFormatVersion = FormRunnerPersistence.providerDataFormatVersionOrThrow(app, form)

    putWithAttachments(
      data               = data.root,
      migrate            = identity,
      toBaseURI          = "", // local save
      fromBasePath       = createFormDataBasePath(app, form, isDraft = false, fromDocument),
      toBasePath         = createFormDataBasePath(app, form, isDraft = false, toDocument),
      filename           = "data.xml",
      commonQueryString  = s"$DataFormatVersionName=${databaseDataFormatVersion.entryName}",
      forceAttachments   = true,
      formVersion        = Some(formVersion),
      workflowStage      = Some(workflowStage)
    )
  }
}

object FormRunnerSummary extends FormRunnerSummary