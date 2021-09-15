/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.pipeline

import javax.servlet.ServletContext
import javax.servlet.http.{HttpServletRequest, HttpSession}
import org.log4s.Logger
import org.orbeon.dom.{Document, Element}
import org.orbeon.errorified.Exceptions
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.cache.ObjectCache
import org.orbeon.oxf.common.OrbeonLocationException.getRootLocationData
import org.orbeon.oxf.externalcontext.{ExternalContext, ServletWebAppContext, WebAppExternalContext}
import org.orbeon.oxf.http.HttpStatusCodeException
import org.orbeon.oxf.pipeline.api.{PipelineContext, ProcessorDefinition}
import org.orbeon.oxf.processor._
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.resources.ResourceNotFoundException
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{AttributesToMap, PipelineUtils}
import org.orbeon.saxon.om.NodeInfo

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

object InitUtils {

  private val CacheSizeProperty            = "oxf.cache.size"
  private val ProcessorsProperty           = "oxf.pipeline.processors"
  private val DeprecatedProcessorsProperty = "oxf.prologue"
  private val DefaultProcessors            = "oxf:/processors.xml"

  // Run with a pipeline context and destroy the pipeline when done
  def withPipelineContext[T](body: PipelineContext => T): T = {
    var success = false
    val pipelineContext = new PipelineContext
    try {
      val result = body(pipelineContext)
      success = true
      result
    } finally
      pipelineContext.destroy(success)
  }

  // Run a processor with an ExternalContext
  def runProcessor(
    processor       : Processor,
    externalContext : ExternalContext,
    pipelineContext : PipelineContext)(implicit
    logger          : Logger
  ): Unit = {

    // Record start time for this request
    val tsBegin = if (logger.isInfoEnabled) System.currentTimeMillis else 0L

    if (logger.isInfoEnabled)
      externalContext.getStartLoggerString.trimAllToOpt foreach (logger.info(_))

    // Set ExternalContext into PipelineContext
    pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext)

    var success = false
    try {
      // Set cache size
      Properties.instance.getPropertySet.getIntOpt(CacheSizeProperty) foreach
        ObjectCache.instance.setMaxSize

      // Start execution
      processor.reset(pipelineContext)
      processor.start(pipelineContext)
      success = true
    } catch {
      case NonFatal(t) =>
        def locationData    = getRootLocationData(t)
        def locationMessage = locationData map ("at " + _) getOrElse "with no location data"

        Exceptions.getRootThrowable(t) match {
          case e: HttpStatusCodeException =>
            externalContext.getResponse.sendError(e.code)
            logger.info(e.toString + " " + locationMessage)
            if (logger.isDebugEnabled)
              logger.debug(e.throwable map OrbeonFormatter.format getOrElse "")
          case e: ResourceNotFoundException =>
            externalContext.getResponse.sendError(404)
            logger.info("Resource not found" + (Option(e.resource) map (": " + _) getOrElse "") + " " + locationMessage)
          case _ =>
            throw t
        }
    } finally {
      if (logger.isInfoEnabled) {
        val timing = System.currentTimeMillis - tsBegin
        val requestPath = Option(externalContext.getRequest) map (_.getRequestPath) getOrElse "Done running processor"
        logger.info(requestPath  + " - Timing: " + timing)
      }
      try pipelineContext.destroy(success)
      catch {
        case NonFatal(t) =>
          logger.debug("Exception while destroying context after exception" + OrbeonFormatter.format(t))
      }
    }
  }

  // Create a processor and connect its inputs to static URLs
  def createProcessor(processorDefinition: ProcessorDefinition): Processor = {
    // Create the processor
    val processor = ProcessorFactoryRegistry.lookup(processorDefinition.getName).createInstance

    // Connect its inputs based on the definition
    for ((inputName, value) <- processorDefinition.getEntries.asScala) {

      import DOMGenerator._
      import PipelineUtils._
      import ProcessorImpl.OUTPUT_DATA

      def connectInput(file: Option[String], create: (String, Long, String) => DOMGenerator) =
        connect(create("init input", ZeroValidity, file getOrElse DefaultContext), OUTPUT_DATA, processor, inputName)

      value match {
        case url: String =>
          val urlGenerator = createURLGenerator(url)
          connect(urlGenerator, OUTPUT_DATA, processor, inputName)
        case element: Element =>
          val locationData = ProcessorUtils.getElementLocationData(element)
          connectInput(Option(locationData) map (_.file), createDOMGenerator(element, _, _, _))
        case document: Document =>
          val locationData = ProcessorUtils.getElementLocationData(document.getRootElement)
          connectInput(Option(locationData) map (_.file), createDOMGenerator(document, _, _, _))
        case nodeInfo: NodeInfo =>
          connectInput(Option(nodeInfo.getSystemId), createDOMGenerator(nodeInfo, _, _, _))
        case value =>
          throw new IllegalStateException("Incorrect type in processor definition: " + value.getClass)
      }
    }

    processor
  }

   // Run a processor based on definitions found in properties or the web app context. This is
   // useful for context/session listeners. Don't run if a definition is not found, no exception is thrown.
  def runWithServletContext(
    servletContext         : ServletContext,
    session                : Option[HttpSession],
    logMessagePrefix       : String,
    message                : String,
    uriNamePropertyPrefix  : String,
    processorInputProperty : String)(implicit
    logger                 : Logger
  ): Unit = {

    require(servletContext ne null)

    // Make sure the Web app context is initialized
    val webAppContext = ServletWebAppContext(servletContext)

    if (message != null)
      logger.info(logMessagePrefix + " - " + message)

    val processorDefinitionOption =
      getDefinitionFromProperties(uriNamePropertyPrefix, processorInputProperty) orElse
      getDefinitionFromServletContext(servletContext, uriNamePropertyPrefix, processorInputProperty)

    processorDefinitionOption foreach { processorDefinition =>
      logger.info(logMessagePrefix + " - About to run processor: " + processorDefinition)
      val processor = createProcessor(processorDefinition)
      val externalContext = new WebAppExternalContext(webAppContext, session)

      withPipelineContext { pipelineContext =>
        runProcessor(processor, externalContext, pipelineContext)
      }
    }
  }

  // Register processor definitions with the default XML Processor Registry. This defines the
  // mapping of processor names to class names.
  lazy val processorDefinitions: Unit = {

    def registerProcessors(url: String) = {
      val processorDefinitions = PipelineUtils.createURLGenerator(url, true)
      val registry = new XMLProcessorRegistry
      PipelineUtils.connect(processorDefinitions, "data", registry, "config")

      withPipelineContext { pipelineContext =>
        processorDefinitions.reset(pipelineContext)
        registry.reset(pipelineContext)
        registry.start(pipelineContext)
      }
    }

    // Register processors from processors.xml and from custom properties
    val propertySet = Properties.instance.getPropertySet

    def fromProperty(s: String) = propertySet.getNonBlankString(s)

    val processors =
      fromProperty(ProcessorsProperty)           orElse
      fromProperty(DeprecatedProcessorsProperty) getOrElse
      DefaultProcessors

    registerProcessors(processors)
  }

  def getDefinitionFromServletContext(
    servletContext        : ServletContext,
    uriNamePropertyPrefix : String,
    inputPropertyPrefix   : String
  ): Option[ProcessorDefinition] =
    getDefinitionFromMap(
      new ServletContextInitMap(servletContext),
      uriNamePropertyPrefix,
      inputPropertyPrefix
    )

  def getDefinitionFromProperties(uriNamePropertyPrefix: String, inputPropertyPrefix: String): Option[ProcessorDefinition] =
    getDefinitionFromMap(PropertiesMap, uriNamePropertyPrefix, inputPropertyPrefix)

  // Create a ProcessorDefinition from a Map. Only Map.get() and Map.keySet() are used
  def getDefinitionFromMap(map: Map[String, String], uriNamePropertyPrefix: String, inputPropertyPrefix: String): Option[ProcessorDefinition] =
    map.get(uriNamePropertyPrefix + "name") map { processorName =>
      val processorDefinition = new ProcessorDefinition(ProcessorSupport.explodedQNameToQName(processorName, "p1"))

      for ((name, value) <- map)
        if (name.startsWith(inputPropertyPrefix))
          processorDefinition.addInput(name.substring(inputPropertyPrefix.length), value)

      processorDefinition
    }

  // Read-only view of the properties as a Map
  private object PropertiesMap extends Map[String, String] {
    def get(key: String) = Properties.instance.getPropertySet.getObjectOpt(key) map (_.toString)
    def iterator         = Properties.instance.getPropertySet.keySet.iterator map (key => key -> this(key))

    def -(key: String)                    = Map() ++ this - key
    def +[B1 >: String](kv: (String, B1)) = Map() ++ this + kv
  }

  // Read-only view of the ServletContext initialization parameters as a Map
  private class ServletContextInitMap(servletContext: ServletContext) extends Map[String, String] {
    def get(key: String) = Option(servletContext.getInitParameter(key))
    def iterator         = servletContext.getInitParameterNames.asScala map (key => key -> this(key))

    def -(key: String)                    = Map() ++ this - key
    def +[B1 >: String](kv: (String, B1)) = Map() ++ this + kv
  }

  // View of the HttpSession properties as a Map
  class SessionMap(session: HttpSession) extends AttributesToMap[AnyRef](new AttributesToMap.Attributeable[AnyRef] {
    def getAttribute(s: String)                  = session.getAttribute(s)
    def getAttributeNames                        = session.getAttributeNames
    def removeAttribute(s: String): Unit         = session.removeAttribute(s)
    def setAttribute(s: String, o: AnyRef): Unit = session.setAttribute(s, o)
  })

  // View of the HttpServletRequest properties as a Map
  class RequestMap(request: HttpServletRequest) extends AttributesToMap[AnyRef](new AttributesToMap.Attributeable[AnyRef] {
    def getAttribute(s: String)                  = request.getAttribute(s)
    def getAttributeNames                        = request.getAttributeNames
    def removeAttribute(s: String): Unit         = request.removeAttribute(s)
    def setAttribute(s: String, o: AnyRef): Unit = request.setAttribute(s, o)
  })
}