/**
  * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational

import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.Logger
import org.orbeon.oxf.fr.persistence.relational.Version._
import org.orbeon.oxf.fr.persistence.relational.search.adt.{SearchRequest, SearchVersion}
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion, FormRunner}
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.scaxon.SimplePath.NodeInfoOps


object RelationalCommon {

  def joinColumns(cols: Seq[String], t1: String, t2: String): String =
    cols.map(c => s"$t1.$c = $t2.$c").mkString(" AND ")

  /**
    * For every request, there is a corresponding specific form version number. In the request, that specific version
    * can be specified, but the caller can also say that it wants the next version, the latest version, or the version
    * of the form used to create a specific document. This function finds the specific form version corresponding to
    * the request.
    *
    * Throws `HttpStatusCodeException` if `ForDocument` and the document is not found.
    */
  def requestedFormVersion(req: RequestCommon): Int =

    req.version match {
      case Unspecified                 => Private.latest(req.appForm).getOrElse(1)
      case Next                        => Private.latest(req.appForm).map(_ + 1).getOrElse(1)
      case Specific(v)                 => v
      case ForDocument(docId, isDraft) =>
        FormRunner.readDocumentFormVersion(req.appForm, docId, isDraft)
          .getOrElse(throw HttpStatusCodeException(StatusCode.NotFound))
    }

  def requestedFormVersion(req: SearchRequest): FormDefinitionVersion =
    req.version match {
      case SearchVersion.Unspecified  => Private.latest(req.appForm).map(FormDefinitionVersion.Specific).getOrElse(FormDefinitionVersion.Latest)
      case SearchVersion.All          => FormDefinitionVersion.Latest
      case SearchVersion.Specific(v)  => FormDefinitionVersion.Specific(v)
    }

  private object Private {

    def latest(appForm: AppForm): Option[Int] =
      for {
        metadata    <- FormRunner.readFormMetadataOpt(appForm, FormDefinitionVersion.Latest)
        formVersion <- metadata.child("form-version").headOption
      } yield
        formVersion.stringValue.toInt

  }
}
