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
package org.orbeon.oxf.fr.persistence.relational.index

import org.orbeon.oxf.controller.NativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.persistence.relational.*
import org.orbeon.oxf.fr.{AppForm, Version}
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.util.*


/**
 * Route repopulating the relational indices. This doesn't create the tables, but deletes their content
 * and repopulates them from scratch.
 *
 * - mapped to `/fr/service/[provider]/reindex` in `fr/page-flow.xml`
 */
object ReindexRoute extends NativeRoute {

  private val ReindexPathRegex    = """/fr/service/([^/]+)/reindex(?:/([^/]+)/([^/]+))?""".r

  def process(
    matchResult: MatchResult
  )(implicit
    pc         : PipelineContext,
    ec         : ExternalContext
  ): Unit = {

    implicit val indentedLogger: IndentedLogger  = RelationalUtils.newIndentedLogger

    ec.getRequest.getRequestPath match {
      case ReindexPathRegex(providerToken, null, null) =>
        Index.reindex(Provider.withName(providerToken), WhatToReindex.AllData, clearOnly = false)
      case ReindexPathRegex(providerToken, app, form) =>

        // Version is required if we pass app/form
        val incomingVersion =
          ec
            .getRequest
            .getFirstHeaderIgnoreCase(Version.OrbeonFormDefinitionVersion)
            .map(_.toInt)
            .getOrElse(throw HttpStatusCodeException(StatusCode.BadRequest))

        Index.reindex(Provider.withName(providerToken), WhatToReindex.DataForForm((AppForm(app, form), incomingVersion)), clearOnly = false)
    }
  }
}
