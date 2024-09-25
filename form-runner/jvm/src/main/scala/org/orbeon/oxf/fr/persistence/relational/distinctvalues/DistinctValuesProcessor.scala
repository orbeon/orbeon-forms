/**
 * Copyright (C) 2023 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.distinctvalues

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.search.SearchLogic
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl.*
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInputOutputInfo, ProcessorOutput}
import org.orbeon.oxf.util.{IndentedLogger, NetUtils, XPath}
import org.orbeon.oxf.xml.XMLReceiver


class DistinctValuesProcessor
  extends ProcessorImpl
    with DistinctValuesRequestParser
    with DistinctValuesLogic
    with DistinctValuesResult {

  self =>

  addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA))
  addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA))

  def httpRequest: ExternalContext.Request = NetUtils.getExternalContext.getRequest

  override def createOutput(name: String): ProcessorOutput =
    addOutput(
      name, new ProcessorOutputImpl(self, name) {
        def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          implicit val externalContext: ExternalContext = NetUtils.getExternalContext
          implicit val indentedLogger : IndentedLogger  = RelationalUtils.newIndentedLogger

          val document = readInputAsTinyTree(
            pipelineContext,
            getInputByName(ProcessorImpl.INPUT_DATA),
            XPath.GlobalConfiguration
          )

          val DistinctValuesPath(provider, app, form) = httpRequest.getRequestPath

          val appForm = AppForm(app, form)

          val formDefinitionVersion =
            PersistenceMetadataSupport.getEffectiveFormVersionForSearchMaybeCallApi(appForm, SearchLogic.searchVersion(httpRequest))

          val request        = parseRequest(Provider.withName(provider), appForm, document, formDefinitionVersion)
          val distinctValues = queryDistinctValues(request)

          outputResult(distinctValues, xmlReceiver)
        }
      }
    )
}
