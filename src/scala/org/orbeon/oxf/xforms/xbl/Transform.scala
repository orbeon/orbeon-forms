/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.xbl

import org.orbeon.scaxon.XML
import org.orbeon.oxf.util.PipelineUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.processor.DOMSerializer
import org.dom4j.{Document, Element, QName}
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.pipeline.{PipelineConfig, PipelineProcessor, PipelineReader}
import org.orbeon.oxf.xml.XMLConstants

object Transform {

    // Create a transformation pipeline configuration for processing XBL templates
    def createTransformConfig(transformQName: QName, transform: Element, lastModified: Long) = {
        // Create reusable pipeline config
        val pipelineConfig = {
            val pipeline = XML.elemToDom4j(
                <p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
                          xmlns:oxf="http://www.orbeon.com/oxf/processors">

                    <p:param type="input" name="transform"/>
                    <p:param type="input" name="data"/>
                    <p:param type="output" name="data"/>

                    <p:processor name={transformQName.getQualifiedName}><!-- namespace for QName might not be in scope! -->
                        <p:input name="config" href="#transform"/>
                        <p:input name="data" href="#data"/>
                        <p:output name="data" ref="data"/>
                    </p:processor>

                </p:config>)

            val ast = PipelineReader.readPipeline(pipeline, lastModified)
            PipelineProcessor.createConfigFromAST(ast)
        }

        // Create transform input separately to help with namespaces (easier with a separate document)
        // NOTE: We don't create and connect the pipeline here because we don't yet have the data input. Ideally we
        // should have something similar to what the pipeline processor does, with the ability to dynamically connect
        // pipeline inputs and inputs while still allowing caching of the pipeline itself.
        val domGeneratorConfig = PipelineUtils.createDOMGenerator(
            Dom4jUtils.createDocumentCopyParentNamespaces(transform),
            "xbl-transform-config",
            lastModified,
            Dom4jUtils.makeSystemId(transform)
        )

        (pipelineConfig, domGeneratorConfig)
    }

    // Run a transformation created above on a bound element
    def transformBoundElement(pipelineConfig: PipelineConfig, domGeneratorConfig: DOMGenerator, boundElement: Element) = {
        val pipeline = new PipelineProcessor(pipelineConfig)
        PipelineUtils.connect(domGeneratorConfig, "data", pipeline, "transform")

        // Connect the bound element to the processor data input
        val domGeneratorData = PipelineUtils.createDOMGenerator(
            Dom4jUtils.createDocumentCopyParentNamespaces(boundElement),
            "xbl-transform-data",
            DOMGenerator.ZeroValidity,
            Dom4jUtils.makeSystemId(boundElement)
        )
        PipelineUtils.connect(domGeneratorData, "data", pipeline, "data")

        // Connect a DOM serializer to the processor data output
        val domSerializerData = new DOMSerializer
        PipelineUtils.connect(pipeline, "data", domSerializerData, "data")

        // Run the transformation
        val newPipelineContext = new PipelineContext
        var success = false

        try {
            pipeline.reset(newPipelineContext)
            domSerializerData.start(newPipelineContext)

            // Get the result, move its root element into a xbl:template and return it
            val result = domSerializerData.getDocument(newPipelineContext)
            success = true
            result
        } finally
            newPipelineContext.destroy(success)
    }

    // Create an XSLT pipeline to transform XBL source
    private def createXSLTPipeline(path: String, transform: Document, lastModified: Long) = {
        // Create pipeline config
        val pipelineConfig = {
            val pipeline = XML.elemToDom4j(
                <p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
                          xmlns:oxf="http://www.orbeon.com/oxf/processors">

                    <p:param type="input" name="transform"/>
                    <p:param type="output" name="data"/>

                    <p:processor name="oxf:unsafe-xslt">
                        <p:input name="config" href="#transform"/>
                        <p:input name="data"><null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/></p:input>
                        <p:output name="data" ref="data"/>
                    </p:processor>

                </p:config>)

            val ast = PipelineReader.readPipeline(pipeline, lastModified)
            PipelineProcessor.createConfigFromAST(ast)
        }

        // Create transform generator
        val domGeneratorConfig = PipelineUtils.createDOMGenerator(
            transform,
            "xbl-transform-config",
            lastModified,
            path
        )

        // Create pipeline and connect transform input
        val pipeline = new PipelineProcessor(pipelineConfig)
        PipelineUtils.connect(domGeneratorConfig, "data", pipeline, "transform")

        // Connect a DOM serializer to the processor data output
        val domSerializerData = new DOMSerializer
        PipelineUtils.connect(pipeline, "data", domSerializerData, "data")

        (pipeline, domSerializerData)
    }

    // Transform an XBL document using XSLT
    private def transformXBLDocument(path: String, transform: Document, lastModified: Long) = {

        val (pipeline, domSerializerData) = createXSLTPipeline(path, transform, lastModified)

        // Run the transformation
        val newPipelineContext = new PipelineContext
        var success = false
        try {
            pipeline.reset(newPipelineContext)
            domSerializerData.start(newPipelineContext)

            // Get the result, move its root element into a xbl:template and return it
            val result = domSerializerData.getDocument(newPipelineContext)
            success = true
            result
        } finally
            newPipelineContext.destroy(success)
    }

    // Transform an XBL document using XSLT if it is an XSLT document
    def transformXBLDocumentIfNeeded(path: String, sourceXBL: Document, lastModified: Long) = {
        // Support /xsl:* or /*[@xsl:version = '2.0']
        val isXSLT = {
            val rootElement = sourceXBL.getRootElement
            rootElement.getNamespaceURI == XMLConstants.XSLT_NAMESPACE_URI || rootElement.attributeValue(XMLConstants.XSLT_VERSION_QNAME) == "2.0"
        }

        if (isXSLT) {
            // Consider the XBL document to be an XSLT transformation and run it
            // NOTE: We don't handle XSLT last modified dependencies at all at this time. Could we?
            transformXBLDocument(path, sourceXBL, lastModified)
        } else
            // Return unmodified XBL document
            sourceXBL
    }
}