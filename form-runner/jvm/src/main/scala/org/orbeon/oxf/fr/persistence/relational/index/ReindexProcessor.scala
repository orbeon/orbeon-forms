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

import org.orbeon.oxf.fr.persistence.relational._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util._

/**
 * Processor repopulating the relational indices. This doesn't create the tables, but deletes their content
 * and repopulates them from scratch.
 *
 * - mapped to `fr:persistence-reindex` in `processors.xml`
 * - mapped to `/fr/service/[provider]/reindex` in `fr/page-flow.xml`
 */
class ReindexProcessor extends ProcessorImpl {

  private val ReindexPathRegex    = """/fr/service/([^/]+)/reindex""".r

  override def start(pipelineContext: PipelineContext): Unit = {
    val ReindexPathRegex(providerToken) = NetUtils.getExternalContext.getRequest.getRequestPath
    RelationalUtils.withConnection(Index.reindex(Provider.withName(providerToken), _, WhatToReindex.AllData))
  }
}
