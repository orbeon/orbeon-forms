/**
  * Copyright (C) 2024 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.form

import org.orbeon.oxf.controller.XmlNativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.AppFormOpt
import org.orbeon.oxf.fr.persistence.relational.form.adt.FormRequest
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.http.{HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.NodeConversions.nodeInfoToElem

import scala.util.matching.Regex


object PublishedFormMetadataRoute extends XmlNativeRoute {

  self =>

  def process(
    matchResult: MatchResult
  )(implicit
    pc         : PipelineContext,
    ec         : ExternalContext
  ): Unit = {

    implicit val indentedLogger: IndentedLogger = RelationalUtils.newIndentedLogger

    try {
      val httpRequest = ec.getRequest

      // If GET request (compatibility mode), we'll need optional app/form names
      val (provider, appOpt, formOpt) = httpRequest.getRequestPath match {
        case Path(provider, app, form)  => (Provider.withName(provider), Option(app), Option(form))
        case _                          => throw new IllegalArgumentException(s"Invalid path: ${httpRequest.getRequestPath}")
      }

      // If POST request, we'll need a body
      val formRequest = FormRequest.parseOrThrowBadRequest {
        FormRequest(
          request             = httpRequest,
          bodyFromPipelineOpt = httpRequest.getMethod == HttpMethod.POST option readRequestBodyAsTinyTree,
          appFormFromUrlOpt   = AppFormOpt(appOpt, formOpt)
        )
      }

      val forms = FormLogic.forms(provider, formRequest)

      NodeConversions.elemToSAX(nodeInfoToElem(forms.toXML), getResponseXmlReceiverSetContentType)
    } catch {
      case e: IllegalArgumentException =>
        throw HttpStatusCodeException(StatusCode.BadRequest, throwable = Some(e))
    }
  }

  private val Path: Regex = """/fr/service/([^/]+)/form(?:/([^/]+)(?:/([^/]+))?)?""".r

  def pathSuffix(appFormOpt: Option[AppFormOpt]): String = {

    val prefix = "/form"

    appFormOpt match {
      case None                              => prefix
      case Some(AppFormOpt(app, None))       => s"$prefix/$app"
      case Some(AppFormOpt(app, Some(form))) => s"$prefix/$app/$form"
    }
  }
}
