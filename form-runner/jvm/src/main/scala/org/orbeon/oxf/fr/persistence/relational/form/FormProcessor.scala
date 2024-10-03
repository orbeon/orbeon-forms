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

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.AppFormOpt
import org.orbeon.oxf.fr.persistence.relational.form.adt.{FormRequest, FormResponse}
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl.*
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInputOutputInfo, ProcessorOutput}
import org.orbeon.oxf.util.{IndentedLogger, NetUtils, XPath}
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.NodeConversions.nodeInfoToElem

import scala.util.matching.Regex


class FormProcessor extends ProcessorImpl {

  self =>

  addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA))
  addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA))

  override def createOutput(name: String): ProcessorOutput =
    addOutput(
      name, new ProcessorOutputImpl(self, name) {
        def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          implicit val externalContext: ExternalContext = NetUtils.getExternalContext
          implicit val indentedLogger : IndentedLogger  = RelationalUtils.newIndentedLogger

          try {
            val httpRequest = NetUtils.getExternalContext.getRequest

            // If GET request (compatibility mode), we'll need optional app/form names
            val (provider, appOpt, formOpt) = httpRequest.getRequestPath match {
              case FormProcessor.Path(provider, app, form)  => (Provider.withName(provider), Option(app), Option(form))
              case _                                        => throw new IllegalArgumentException(s"Invalid path: ${httpRequest.getRequestPath}")
            }

            // If POST request, we'll need a body
            val bodyFromPipeline = readInputAsTinyTree(
              pipelineContext,
              getInputByName(ProcessorImpl.INPUT_DATA),
              XPath.GlobalConfiguration
            )

            val formRequest = FormRequest.parseOrThrowBadRequest {
              FormRequest(
                request             = httpRequest,
                bodyFromPipelineOpt = Some(bodyFromPipeline),
                appFormFromUrlOpt   = AppFormOpt(appOpt, formOpt)
              )
            }

            val forms = FormLogic.forms(provider, formRequest)

            NodeConversions.elemToSAX(nodeInfoToElem(forms.toXML), xmlReceiver)
          } catch {
            case e: IllegalArgumentException =>
              throw HttpStatusCodeException(StatusCode.BadRequest, throwable = Some(e))
          }
        }
      }
    )
}

object FormProcessor {

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
