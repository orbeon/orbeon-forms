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

import cats.syntax.option._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerPersistence.FormXhtml
import org.orbeon.oxf.fr.library.FRComponentParamSupport
import org.orbeon.oxf.fr.persistence.relational.Version
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
    xhtml             : NodeInfo,
    toBaseURI         : String,
    app               : String,
    form              : String,
    documentIdOrEmpty : String,
    username          : String,
    password          : String,
    forceAttachments  : Boolean,
    formVersion       : String // `Option["next" | Int]`
  ): Item = {

    val documentIdOpt = documentIdOrEmpty.trimAllToOpt

    val dstFormVersionTrimmedOpt = formVersion.trimAllToOpt
    val dstFormVersion           = Version(None, None, dstFormVersionTrimmedOpt)

    val fromBasePathWithVersionOpt =
      documentIdOpt match {
        case Some(documentId) =>
          (createFormDataBasePath(AppForm.FormBuilder.app, AppForm.FormBuilder.form, isDraft = false, documentId), 1).some
        case None           =>
          dstFormVersion match {
            case Version.Unspecified | Version.Next => None
            case Version.Specific(version)          => (createFormDefinitionBasePath(app, form), version).some
            case Version.ForDocument(_, _)          => throw new IllegalStateException
          }
      }

    // `xhtml` must always be a form definition and must have form metadata
    val frDocCtx: FormRunnerDocContext = new InDocFormRunnerDocContext(xhtml.rootElement)

    val (globalVersionOpt, appVersionOpt) =
      FRComponentParamSupport.findLibraryVersions(frDocCtx.metadataRootElem)

    val basePathsWithVersions =
      fromBasePathWithVersionOpt.toList ::: List(
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
        filename          = FormXhtml,
        commonQueryString = documentIdOpt map (documentId => encodeSimpleQuery(List("document" -> documentId))) getOrElse "",
        forceAttachments  = forceAttachments,
        username          = username.trimAllToOpt,
        password          = password.trimAllToOpt,
        formVersion       = dstFormVersionTrimmedOpt,
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
