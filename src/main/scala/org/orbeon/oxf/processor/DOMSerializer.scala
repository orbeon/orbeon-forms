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
package org.orbeon.oxf.processor

import org.dom4j.Document
import org.dom4j.io.DOMWriter
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.saxon.om.DocumentInfo
import org.w3c.dom.{Document ⇒ W3CDocument }

/**
 * Serializes the data input into a Document.
 */
class DOMSerializer extends ProcessorImpl {

    import ProcessorImpl._

    addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA))

    /**
     * Find the last modified timestamp of the dependencies of this processor.
     *
     * @param pipelineContext       pipeline context
     * @return                      timestamp, <= 0 if unknown
     */
    def findInputLastModified(pipelineContext: PipelineContext): Long =
        findInputLastModified(pipelineContext, getInputByName(INPUT_DATA), false)

    @deprecated("use runGetDocument", "4.0")
    def getDocument(pipelineContext: PipelineContext): Document =
        pipelineContext.getAttribute(this).asInstanceOf[Document]

    @deprecated("use runGetW3CDocument", "4.0")
    def getW3CDocument(pipelineContext: PipelineContext): W3CDocument = {
        val writer = new DOMWriter(XMLUtils.createDocument.getClass)
        writer.write(getDocument(pipelineContext))
    }

    override def start(pipelineContext: PipelineContext): Unit = {
        // Q: should use Context instead?
        pipelineContext.setAttribute(this, readCacheInputAsDOM4J(pipelineContext, INPUT_DATA))
    }

    def runGetW3CDocument(pipelineContext: PipelineContext): W3CDocument =
        readCacheInputAsDOM(pipelineContext, INPUT_DATA)

    def runGetDocument(pipelineContext: PipelineContext): Document =
        readCacheInputAsDOM4J(pipelineContext, INPUT_DATA)

    def runGetTinyTree(pipelineContext: PipelineContext): DocumentInfo =
        readCacheInputAsTinyTree(pipelineContext, XPathCache.getGlobalConfiguration, INPUT_DATA)
}