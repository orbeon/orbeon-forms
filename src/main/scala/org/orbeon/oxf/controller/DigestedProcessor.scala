/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.controller

import org.orbeon.oxf.pipeline.api.{PipelineContext, XMLReceiver}
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.xml.XMLUtils.DigestContentHandler
import org.orbeon.oxf.processor.impl.{DigestState, DigestTransformerOutputImpl}

// This processor provides digest-based caching based on any content
class DigestedProcessor(content: XMLReceiver ⇒ Unit) extends ProcessorImpl {

    override def createOutput(name: String) =
        new DigestTransformerOutputImpl(DigestedProcessor.this, name) {

            def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver) = content(xmlReceiver)

            def fillOutState(pipelineContext: PipelineContext, digestState: DigestState) = true

            def computeDigest(pipelineContext: PipelineContext, digestState: DigestState) = {
                val digester = new DigestContentHandler
                content(digester)
                digester.getResult
            }
        }

    override def reset(context: PipelineContext): Unit =
        setState(context, new DigestState)
}
