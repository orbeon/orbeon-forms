/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.pipeline

import org.orbeon.dom.{Document, Element, QName}
import org.orbeon.oxf.pipeline.InitUtils.withPipelineContext
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.processor.pipeline.{PipelineConfig, PipelineProcessor, PipelineReader}
import org.orbeon.oxf.processor.{DOMSerializer, ProcessorSupport, XPLConstants}
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{IndentedLogger, PipelineUtils}
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.{XMLConstants, XMLParsing}
import org.orbeon.scaxon.NodeConversions

object Transform {

  import Private._

  sealed trait ReadDocument { def path: String }
  case class   FileReadDocument  (path: String)                                    extends ReadDocument
  case class   InlineReadDocument(path: String, doc: Document, lastModified: Long) extends ReadDocument

  def lastModifiedByPath(path: String)(implicit logger: IndentedLogger): Long = {
    debug("checking last modified", List("path" -> path))
    rm.lastModified(path, true)
  }

  def existsByPath(path: String)(implicit logger: IndentedLogger): Boolean = {
    debug("checking existence", List("path" -> path))
    rm.exists(path)
  }

  def contentAsDOM4J(path: String)(implicit logger: IndentedLogger): Document = {
    debug("reading content", List("path" -> path))
    rm.getContentAsDOM4J(path, XMLParsing.ParserConfiguration.XINCLUDE_ONLY, false)
  }

  // Transform a document
  def transformDocument(
    transform      : ReadDocument,
    data           : Option[ReadDocument],
    transformQName : QName)(implicit
    logger         : IndentedLogger
  ): Document = {

    val (pipeline, domSerializerData) = createTransformPipeline(transform, data, transformQName)

    // Run the transformation
    withPipelineContext { newPipelineContext =>
      pipeline.reset(newPipelineContext)
      domSerializerData.runGetDocument(newPipelineContext)
    }
  }

  // Read a document and process as XSLT if it is an XSLT stylesheet (simplified or not)
  def readDocumentOrSimplifiedStylesheet(doc: InlineReadDocument)(implicit logger: IndentedLogger): Document = {

    // Support /xsl:* or /*[@xsl:version = '2.0']
    val isXSLT = {
      val rootElement = doc.doc.getRootElement
      rootElement.getNamespaceURI == XMLConstants.XSLT_NAMESPACE_URI ||
        rootElement.attributeValue(XMLConstants.XSLT_VERSION_QNAME) == "2.0"
    }

    if (isXSLT) {
      // Consider the document to be an XSLT transformation and run it
      // NOTE: We don't handle XSLT last modified dependencies at all at this time. Could we?
      transformDocument(doc, None, XPLConstants.UNSAFE_XSLT_PROCESSOR_QNAME)
    } else {
      // Return unmodified XBL document
      doc.doc
    }
  }

  def createPipelineConfig(transformQName: QName, lastModified: Long): PipelineConfig = {

    val pipelineDoc =
      NodeConversions.elemToDom4j(
        <p:config
          xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

          <p:param type="input"  name="transform"/>
          <p:param type="input"  name="data"/>
          <p:param type="output" name="data"/>

          <p:processor name={transformQName.qualifiedName}><!-- namespace for QName might not be in scope! -->
            <p:input   name="config" href="#transform"/>
            <p:input   name="data"   href="#data"/>
            <p:output  name="data"   ref="data"/>
          </p:processor>

        </p:config>
      )

    PipelineProcessor.createConfigFromAST(
      PipelineReader.readPipeline(
        pipelineDoc,
        lastModified
      )
    )
  }

  def transformFromPipelineConfig(
    pipelineConfig     : PipelineConfig,
    domGeneratorConfig : DOMGenerator,
    elem               : Element
  ): Document = {

    val pipeline = new PipelineProcessor(pipelineConfig)
    PipelineUtils.connect(domGeneratorConfig, "data", pipeline, "transform")

    // Connect the bound element to the processor data input
    val domGeneratorData = PipelineUtils.createDOMGenerator(
      elem.createDocumentCopyParentNamespaces(detach = false),
      "xbl-transform-data",
      DOMGenerator.ZeroValidity,
      ProcessorSupport.makeSystemId(elem)
    )
    PipelineUtils.connect(domGeneratorData, "data", pipeline, "data")

    // Connect a DOM serializer to the processor data output
    val domSerializerData = new DOMSerializer
    PipelineUtils.connect(pipeline, "data", domSerializerData, "data")

    // Run the transformation
    withPipelineContext { newPipelineContext =>
      pipeline.reset(newPipelineContext)
      domSerializerData.runGetDocument(newPipelineContext)
    }
  }

  private object Private {

    val rm = ResourceManagerWrapper.instance

    def normalizeReadDocument(doc: ReadDocument)(implicit logger: IndentedLogger): InlineReadDocument =
      doc match {
        case FileReadDocument(path) => InlineReadDocument(path, contentAsDOM4J(path), lastModifiedByPath(path))
        case d: InlineReadDocument  => d
      }

    def createDomGenerator(doc: InlineReadDocument, name: String) =
      PipelineUtils.createDOMGenerator(
        doc.doc,
        name,
        doc.lastModified,
        doc.path
      )

    // Create a transformation pipeline
    def createTransformPipeline(
      transform      : ReadDocument,
      dataOpt        : Option[ReadDocument],
      transformQName : QName)(implicit
      logger         : IndentedLogger
    ): (PipelineProcessor, DOMSerializer) = {

      val normalizedTransform = normalizeReadDocument(transform)
      val normalizedDataOpt   = dataOpt map normalizeReadDocument

      // Create pipeline config
      val pipeline =
        new PipelineProcessor(createPipelineConfig(transformQName, normalizedTransform.lastModified))

      val nullDoc =
        NodeConversions.elemToDom4j(
          <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
        )

      // Create transform generator
      val domGeneratorTransform =
        createDomGenerator(
          normalizedTransform,
          "transform-config"
        )

      val domGeneratorData =
        createDomGenerator(
          normalizedDataOpt getOrElse InlineReadDocument("", nullDoc, normalizedTransform.lastModified),
          "transform-data"
        )

      // Connect pipeline
      PipelineUtils.connect(domGeneratorTransform, "data", pipeline, "transform")
      PipelineUtils.connect(domGeneratorData,      "data", pipeline, "data")

      // Connect a DOM serializer to the processor data output
      val domSerializerData = new DOMSerializer
      PipelineUtils.connect(pipeline, "data", domSerializerData, "data")

      (pipeline, domSerializerData)
    }
  }
}
