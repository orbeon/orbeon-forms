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

import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl._
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInputOutputInfo, ProcessorOutput}
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.XMLReceiver


class SearchProcessor
  extends ProcessorImpl
    with SearchRequestParser
    with SearchLogic
    with SearchResult {

  self =>

  addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA))
  addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA))

  override def createOutput(name: String): ProcessorOutput =
    addOutput(
      name, new CacheableTransformerOutputImpl(self, name) {
        def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          val searchDocument = readInputAsTinyTree(
            pipelineContext,
            getInputByName(ProcessorImpl.INPUT_DATA),
            XPath.GlobalConfiguration
          )
          val request = parseRequest(searchDocument, SearchLogic.searchVersion(httpRequest))

          val (result, count) = doSearch(request)

          outputResult(request, result, count, xmlReceiver)
        }
      }
    )
}