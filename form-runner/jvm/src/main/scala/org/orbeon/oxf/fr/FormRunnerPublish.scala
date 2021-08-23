/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.library.FRComponentParamSupport

import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.MapFunctions
import org.orbeon.saxon.om.{Item, NodeInfo, ValueRepresentation}
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._


trait FormRunnerPublish {

  //@XPathFunction
  def publish(
    xhtml            : NodeInfo,
    toBaseURI        : String,
    app              : String,
    form             : String,
    documentOrEmpty  : String,
    username         : String,
    password         : String,
    forceAttachments : Boolean,
    formVersion      : String
  ): Item = {

    val documentOpt = documentOrEmpty.trimAllToOpt

    val fromBasePathWithVersion =
      documentOpt match {
        case Some(document) => (createFormDataBasePath(AppForm.FormBuilder.app, AppForm.FormBuilder.form, isDraft = false, document), 1)
        case None           => (createFormDefinitionBasePath(app, form),                                                              formVersion.toInt)
      }

    // `xhtml` must always be a form definition and must have form metadata
    val frDocCtx: FormRunnerDocContext = new FormRunnerDocContext {
      val formDefinitionRootElem: NodeInfo = xhtml.rootElement
    }

    val (globalVersionOpt, appVersionOpt) =
      FRComponentParamSupport.findLibraryVersions(frDocCtx.metadataRootElem)

    val basePathsWithVersions =
      List(
        fromBasePathWithVersion,
        (FormRunner.createFormDefinitionBasePath(app,                        Names.LibraryFormName), appVersionOpt.getOrElse(1)),
        (FormRunner.createFormDefinitionBasePath(Names.GlobalLibraryAppName, Names.LibraryFormName), globalVersionOpt.getOrElse(1))
      )

    val (beforeURLs, _, publishedVersion) =
      putWithAttachments(
        liveData          = xhtml.root,
        migrate           = None,
        toBaseURI         = toBaseURI,
        fromBasePaths     = basePathsWithVersions,
        toBasePath        = createFormDefinitionBasePath(app, form),
        filename          = "form.xhtml",
        commonQueryString = documentOpt map (document => encodeSimpleQuery(List("document" -> document))) getOrElse "",
        forceAttachments  = forceAttachments,
        username          = username.trimAllToOpt,
        password          = password.trimAllToOpt,
        formVersion       = formVersion.trimAllToOpt,
        workflowStage     = None
      )

    MapFunctions.createValue(
      Map[AtomicValue, ValueRepresentation](
        (SaxonUtils.fixStringValue("published-attachments"), beforeURLs.size),
        (SaxonUtils.fixStringValue("published-version"),     publishedVersion)
      )
    )
  }
}
