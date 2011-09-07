/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.dom4j.{Document, QName, Element}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.XFormsConstants._
import analysis.IdGenerator
import collection.JavaConversions._
import scala.collection.JavaConverters._
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.scaxon.XML
import org.orbeon.oxf.processor.pipeline.{PipelineProcessor, PipelineReader}
import org.orbeon.oxf.util.PipelineUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.processor.DOMSerializer
import org.orbeon.oxf.pipeline.api.PipelineContext

// Holds details of an xbl:xbl/xbl:binding
case class AbstractBinding(
    qNameMatch: QName,
    bindingElement: Element,
    lastModified: Long,
    bindingId: Option[String],
    scripts: Seq[Element],
    styles: Seq[Element],
    handlers: Seq[Element],
    implementations: Seq[Document],
    global: Option[Document]
) {
    def templateElement = Option(bindingElement.element(XBL_TEMPLATE_QNAME))

    private def transformQNameOption = templateElement flatMap
        (e => Option(Dom4jUtils.extractAttributeValueQName(e, XXBL_TRANSFORM_QNAME)))

    private def templateRootOption = templateElement map { e =>
        if (e.elements.size != 1)
            throw new OXFException("xxbl:transform requires a single child element.")
        e.elements.get(0).asInstanceOf[Element]
    }

    private lazy val transformConfig =
        for {
            transformQName <- transformQNameOption
            templateRoot <- templateRootOption
        } yield {
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
            val domGeneratorConfig = PipelineUtils.createDOMGenerator(
                Dom4jUtils.createDocumentCopyParentNamespaces(templateRoot),
                "xbl-transform-config",
                lastModified,
                Dom4jUtils.makeSystemId(templateRoot)
            )

            (pipelineConfig, domGeneratorConfig)
        }

    // A transform cannot be reused, so this creates a new one when called, based on the config
    def newTransform(boundElement: Element) = transformConfig map {
        case (pipelineConfig, domGeneratorConfig) =>
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
            val generatedDocument =
                try {
                    pipeline.reset(newPipelineContext)
                    domSerializerData.start(newPipelineContext)

                    // Get the result, move its root element into a xbl:template and return it
                    val result = domSerializerData.getDocument(newPipelineContext)
                    success = true
                    result
                } finally {
                    newPipelineContext.destroy(success)
                }

            val generatedRootElement = generatedDocument.getRootElement.detach.asInstanceOf[Element]
            generatedDocument.addElement(new QName("template", XFormsConstants.XBL_NAMESPACE, "xbl:template"))
            val newRoot = generatedDocument.getRootElement
            newRoot.add(XFormsConstants.XBL_NAMESPACE)
            newRoot.add(generatedRootElement)

            generatedDocument
    }
}

object AbstractBinding {
    // Construct an AbstractBinding
    def apply(bindingElement: Element, lastModified: Long, scripts: Seq[Element], namespaceMapping: NamespaceMapping, idGenerator: IdGenerator) = {

        assert(bindingElement ne null)

        def extractChildrenModels(parentElement: Element, detach: Boolean): Seq[Document] =
            Dom4jUtils.elements(parentElement, XFORMS_MODEL_QNAME).asScala map
                (Dom4jUtils.createDocumentCopyParentNamespaces(_, detach))

        val bindingId = {
            val existingBindingId = XFormsUtils.getElementStaticId(bindingElement)
            Option(existingBindingId) orElse (Option(idGenerator) map (_.getNextId))
        }

        val styles =
            for {
                resourcesElement <- Dom4jUtils.elements(bindingElement, XBL_RESOURCES_QNAME)
                styleElement <- Dom4jUtils.elements(resourcesElement, XBL_STYLE_QNAME)
            } yield
                styleElement

        val handlers =
            for {
                handlersElement <- Option(bindingElement.element(XBL_HANDLERS_QNAME)).toSeq
                handlerElement <- Dom4jUtils.elements(handlersElement, XBL_HANDLER_QNAME)
            } yield
                handlerElement

        val implementations =
            for {
                implementationElement <- Option(bindingElement.element(XBL_IMPLEMENTATION_QNAME)).toSeq
                modelDocument <- extractChildrenModels(implementationElement, true)
            } yield
                modelDocument

        val global = Option(bindingElement.element(XXBL_GLOBAL_QNAME)) map
            (Dom4jUtils.createDocumentCopyParentNamespaces(_, true))

        new AbstractBinding(qNameMatch(bindingElement, namespaceMapping), bindingElement, lastModified, bindingId, scripts, styles, handlers, implementations, global)
    }

    def qNameMatch(bindingElement: Element, namespaceMapping: NamespaceMapping) = {
        val elementAttribute = bindingElement.attributeValue(ELEMENT_QNAME)
        Dom4jUtils.extractTextValueQName(namespaceMapping.mapping, elementAttribute.replace('|', ':'), true)
    }
}