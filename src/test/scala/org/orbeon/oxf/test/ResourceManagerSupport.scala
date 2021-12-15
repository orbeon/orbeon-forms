/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.test

import org.apache.log4j.Logger
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.XMLProcessorRegistry
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.{IndentedLogger, Log4jSupport, LoggerFactory, PipelineUtils}
import org.orbeon.oxf.xml.XMLParsing
import org.scalatest.{BeforeAndAfter, Suite}

import scala.collection.JavaConverters._


trait ResourceManagerSupport extends Suite with BeforeAndAfter {

  ResourceManagerSupport

  locally {
    var pipelineContext: Option[PipelineContext] = None

    before { pipelineContext = Some(PipelineSupport.createPipelineContextWithExternalContext()) }
    after  { pipelineContext foreach (_.destroy(true)) }
  }
}

object ResourceManagerSupport {

  val logger: Logger = LoggerFactory.createLogger(ResourceManagerSupport.getClass)

  def newIndentedLogger: IndentedLogger = new IndentedLogger(logger, true)

  // For Java callers
  def initializeJava(): Unit = ()

  // Setup once when `ResourceManagerSupport` is accessed
  locally {

    // Avoid Log4j warning telling us no appender could be found
    Log4jSupport.initBasicLogger()

    // Setup resource manager
    val properties = System.getProperties

    val propsIt =
      for {
        name <- properties.propertyNames.asScala collect { case s: String => s}
        if name.startsWith("oxf.resources.")
      } yield
         name -> (properties.getProperty(name): AnyRef) // `AnyRef` because we pass a `WebAppContext` in one case

    val propsMap = propsIt.toMap

    logger.info("Initializing Resource Manager with: " + ResourceManagerWrapper.propertiesAsJson(propsMap))
    ResourceManagerWrapper.init(propsMap.asJava)

    // Initialize properties
    org.orbeon.oxf.properties.Properties.init("oxf:/ops/unit-tests/properties.xml")

    // Initialize logger
    Log4jSupport.initLogger()

    // Run processor registry so we can use XPL
    val registry = new XMLProcessorRegistry
    val processorsXML = "processors.xml"
    val doc = ResourceManagerWrapper.instance.getContentAsDOM4J(processorsXML, XMLParsing.ParserConfiguration.XINCLUDE_ONLY, true)
    val config = PipelineUtils.createDOMGenerator(doc, processorsXML, DOMGenerator.ZeroValidity, processorsXML)

    PipelineUtils.connect(config, "data", registry, "config")
    registry.start(new PipelineContext)
  }

}