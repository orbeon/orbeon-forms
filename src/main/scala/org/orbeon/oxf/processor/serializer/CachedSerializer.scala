/**
 * Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.processor.serializer

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInput, ProcessorInputOutputInfo}
import org.orbeon.oxf.xml.TransformerUtils


object CachedSerializer {

  val SerializerConfigNamespaceUri = "http://www.orbeon.com/oxf/serializer"

  val DefaultEncoding: String   = TransformerUtils.DEFAULT_OUTPUT_ENCODING
  val DefaultIndent             = true
  val DefaultIndentAmount       = 1 // changed to 1 because Saxon fails with 0
  val DefaultErrorCode          = 0
  val DefaultEmpty              = false
  val DefaultCacheUseLocalCache = true
  val DefaultOmitXmlDeclaration = false
}

abstract class CachedSerializer[Config] protected extends ProcessorImpl {

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))

  protected def readInput(
    context : PipelineContext,
    response: ExternalContext.Response,
    input   : ProcessorInput,
    config  : Config
  ): Unit
}