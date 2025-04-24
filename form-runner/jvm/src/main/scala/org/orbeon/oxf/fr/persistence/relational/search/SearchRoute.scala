/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.search

import org.orbeon.oxf.controller.XmlNativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.util.IndentedLogger


object SearchRoute extends XmlNativeRoute {

  self =>

  private val Path = "/fr/service/([^/]+)/search/([^/]+)/([^/]+)".r

  def process(
    matchResult: MatchResult
  )(implicit
    pc         : PipelineContext,
    ec         : ExternalContext
  ): Unit = {

    implicit val indentedLogger: IndentedLogger  = RelationalUtils.newIndentedLogger

    try {
      val httpRequest = ec.getRequest

      val SearchRoute.Path(provider, app, form) = httpRequest.getRequestPath

      val appForm = AppForm(app, form)

      val formDefinitionVersion =
        PersistenceMetadataSupport.getEffectiveFormVersionForSearchMaybeCallApi(appForm, SearchLogic.searchVersion(httpRequest))

      val request =
        SearchRequestParser.parseRequest(
          Provider.withName(provider),
          appForm,
          PersistenceMetadataSupport.isInternalAdminUser(httpRequest.getFirstParamAsString),
          readRequestBodyAsTinyTree,
          formDefinitionVersion
        )

      val (result, count) = SearchLogic.doSearch(request, connectionOpt = None)

      SearchResult.outputResult(request, result, count, getResponseXmlReceiver)
    } catch {
      case e: IllegalArgumentException =>
        throw HttpStatusCodeException(StatusCode.BadRequest, throwable = Some(e))
    }
  }
}