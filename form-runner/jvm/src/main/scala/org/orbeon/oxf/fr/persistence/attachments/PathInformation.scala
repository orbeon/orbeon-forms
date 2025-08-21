/**
 * Copyright (C) 2025 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.attachments

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.fr.{AppForm, FormOrData, Version}
import org.orbeon.oxf.http.HttpMethod.DELETE
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}


case class PathInformation(
  appForm    : AppForm,
  formOrData : FormOrData,
  draft      : Boolean,
  documentId : Option[String],
  version    : Option[Int],
  filenameOpt: Option[String]
) {
  // Fixed path to the attachment file (same for local filesystem and S3)
  lazy val pathSegments: List[String] =
    List(
      appForm.app,
      appForm.form,
      if (draft) "draft" else formOrData.entryName
    ) :++
      documentId :++
      version.map(_.toString) :++
      filenameOpt
}

private[attachments] object PathInformation {

  private val FormPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/form/([^/]+)".r
  private val DataPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/(data|draft)/([^/]+)(?:/([^/]+))?".r

  def providerAndPathInformation(httpRequest: Request): (Provider, PathInformation) = {
    val versionFromHeaders = httpRequest.getFirstHeaderIgnoreCase(Version.OrbeonFormDefinitionVersion).map(_.toInt)

    httpRequest.getRequestPath match {
      case FormPath(providerName, app, form, filename) =>
        (Provider.withName(providerName), PathInformation(
          appForm     = AppForm(app, form),
          formOrData  = FormOrData.Form,
          draft       = false,
          documentId  = None,
          version     = versionFromHeaders,
          filenameOpt = filename.some
        ))

      // Filename is optional for DELETE requests on data/draft paths
      case DataPath(providerName, app, form, dataOrDraft, documentId, filename) if Option(filename).isDefined || httpRequest.getMethod == DELETE =>
        (Provider.withName(providerName), PathInformation(
          appForm     = AppForm(app, form),
          formOrData  = FormOrData.Data,
          draft       = dataOrDraft == "draft",
          documentId  = Some(documentId),
          version     = versionFromHeaders,
          filenameOpt = Option(filename)
        ))

      case _ =>
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }
  }
}
