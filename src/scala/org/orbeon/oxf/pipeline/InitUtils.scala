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

import collection.JavaConverters._
import java.util.{Enumeration ⇒ JEnumeration}
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession
import org.apache.log4j.Logger
import org.dom4j.Document
import org.dom4j.Element
import org.orbeon.errorified.Exceptions
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.cache.ObjectCache
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.pipeline.api.ProcessorDefinition
import org.orbeon.oxf.processor._
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.resources.ResourceNotFoundException
import org.orbeon.oxf.util.AttributesToMap
import org.orbeon.oxf.util.PipelineUtils
import org.orbeon.oxf.util.ScalaUtils.nonEmptyOrNone
import org.orbeon.oxf.webapp.{WebAppContext, HttpStatusCodeException, WebAppExternalContext}
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.NodeInfo

object InitUtils {

    val CacheSizeProperty = "oxf.cache.size"
    val PrologueProperty  = "oxf.prologue"
    val DefaultPrologue   = "oxf:/processors.xml"

    // Run with a pipeline context and destroy the pipeline when done
    def withPipelineContext[T](body: PipelineContext ⇒ T) = {
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
    def runProcessor(processor: Processor, externalContext: ExternalContext, pipelineContext: PipelineContext, logger: Logger) {

        // Record start time for this request
        val tsBegin = if (logger.isInfoEnabled) System.currentTimeMillis else 0L

        if (logger.isInfoEnabled)
            nonEmptyOrNone(externalContext.getStartLoggerString) foreach logger.info

        // Set ExternalContext into PipelineContext
        pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext)

        var success = false
        try {
            // Set cache size
            val cacheMaxSize = Properties.instance.getPropertySet.getInteger(CacheSizeProperty)
            if (cacheMaxSize != null) ObjectCache.instance.setMaxSize(cacheMaxSize)

            // Start execution
            processor.reset(pipelineContext)
            processor.start(pipelineContext)
            success = true
        } catch {
            case t: Throwable ⇒
                val locationData = ValidationException.getRootLocationData(t)

                Exceptions.getRootThrowable(t) match {
                    case e: HttpStatusCodeException ⇒
                        externalContext.getResponse.sendError(e.code)
                        val message = Option(locationData) map ("HttpStatusCodeException at " + _) getOrElse "HttpStatusCodeException with no location data"
                        logger.info(message)
                    case e: ResourceNotFoundException ⇒
                        externalContext.getResponse.sendError(404)

                        logger.info(
                            "Resource not found" +
                            (Option(e.resource)   map (": " + _)   getOrElse "") +
                            (Option(locationData) map (" at " + _) getOrElse " with no location data"))
                    case _ ⇒ throw t
                }
        } finally {
            if (logger.isInfoEnabled) {
                val timing = System.currentTimeMillis - tsBegin
                val requestPath = Option(externalContext.getRequest) map (_.getRequestPath) getOrElse "Done running processor"
                logger.info(requestPath  + " - Timing: " + timing)
            }
            try pipelineContext.destroy(success)
            catch {
                case f: Throwable ⇒
                    logger.debug("Exception while destroying context after exception" + OrbeonFormatter.format(f))
            }
        }
    }

    // Create a processor and connect its inputs to static URLs
    def createProcessor(processorDefinition: ProcessorDefinition): Processor = {
        // Create the processor
        val processor = ProcessorFactoryRegistry.lookup(processorDefinition.getName).createInstance

        // Connect its inputs based on the definition
        for ((inputName, value) ← processorDefinition.getEntries.asScala) {

            import DOMGenerator._
            import ProcessorImpl.OUTPUT_DATA
            import PipelineUtils._

            def connectInput(file: Option[String], create: (String, Long, String) ⇒ DOMGenerator) =
                connect(create("init input", ZeroValidity, file getOrElse DefaultContext), OUTPUT_DATA, processor, inputName)

            value match {
                case url: String ⇒
                    val urlGenerator = createURLGenerator(url)
                    connect(urlGenerator, OUTPUT_DATA, processor, inputName)
                case element: Element ⇒
                    val locationData = ProcessorUtils.getElementLocationData(element)
                    connectInput(Option(locationData) map (_.getSystemID), createDOMGenerator(element, _, _, _))
                case document: Document ⇒
                    val locationData = ProcessorUtils.getElementLocationData(document.getRootElement)
                    connectInput(Option(locationData) map (_.getSystemID), createDOMGenerator(document, _, _, _))
                case nodeInfo: NodeInfo ⇒
                    connectInput(Option(nodeInfo.getSystemId), createDOMGenerator(nodeInfo, _, _, _))
                case value ⇒
                    throw new IllegalStateException("Incorrect type in processor definition: " + value.getClass)
            }
        }

        processor
    }

     // Run a processor based on definitions found in properties or the web app context. This is
     // useful for context/session listeners. Don't run if a definition is not found, no exception is thrown.
    def runWithServletContext(servletContext: ServletContext, session: Option[HttpSession], logger: Logger, logMessagePrefix: String, message: String, uriNamePropertyPrefix: String, processorInputProperty: String): Unit = {

        require(servletContext ne null)

        // Make sure the Web app context is initialized
        WebAppContext.instance(servletContext)

        if (message != null)
            logger.info(logMessagePrefix + " - " + message)

        val processorDefinitionOption =
            getDefinitionFromProperties(uriNamePropertyPrefix, processorInputProperty) orElse
            getDefinitionFromServletContext(servletContext, uriNamePropertyPrefix, processorInputProperty)

        processorDefinitionOption foreach { processorDefinition ⇒
            logger.info(logMessagePrefix + " - About to run processor: " + processorDefinition)
            val processor = createProcessor(processorDefinition)
            val externalContext = new WebAppExternalContext(WebAppContext.instance(servletContext), session)

            withPipelineContext { pipelineContext ⇒
                runProcessor(processor, externalContext, pipelineContext, logger)
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

            withPipelineContext { pipelineContext ⇒
                processorDefinitions.reset(pipelineContext)
                registry.reset(pipelineContext)
                registry.start(pipelineContext)
            }
        }

        // Register processors from processors.xml and from custom property
        val propertySet = Properties.instance.getPropertySet
        Seq(DefaultPrologue) ++ Option(propertySet.getString(PrologueProperty)) foreach registerProcessors
    }

    def getDefinitionFromServletContext(servletContext: ServletContext, uriNamePropertyPrefix: String, inputPropertyPrefix: String) =
        getDefinitionFromMap(new ServletContextInitMap(servletContext), uriNamePropertyPrefix, inputPropertyPrefix)

    def getDefinitionFromProperties(uriNamePropertyPrefix: String, inputPropertyPrefix: String) =
        getDefinitionFromMap(PropertiesMap, uriNamePropertyPrefix, inputPropertyPrefix)

    // Create a ProcessorDefinition from a Map. Only Map.get() and Map.keySet() are used
    def getDefinitionFromMap(map: Map[String, String], uriNamePropertyPrefix: String, inputPropertyPrefix: String) =
        map.get(uriNamePropertyPrefix + "name") map { processorName ⇒
            val processorDefinition = new ProcessorDefinition
            processorDefinition.setName(Dom4jUtils.explodedQNameToQName(processorName))

            for ((name, value) ← map)
                if (name.startsWith(inputPropertyPrefix))
                    processorDefinition.addInput(name.substring(inputPropertyPrefix.length), value)

            processorDefinition
        }

    // Read-only view of the properties as a Map
    private object PropertiesMap extends Map[String, String] {
        def get(key: String) = Option(Properties.instance.getPropertySet.getObject(key)) map (_.toString)
        def iterator = Properties.instance.getPropertySet.keySet.asScala.toIterator map (key ⇒ key → this(key))

        def -(key: String) = Map() ++ this - key
        def +[B1 >: String](kv: (String, B1)) = Map() ++ this + kv
    }

    // Read-only view of the ServletContext initialization parameters as a Map
    private class ServletContextInitMap(servletContext: ServletContext) extends Map[String, String] {
        def get(key: String) = Option(servletContext.getInitParameter(key))
        def iterator = servletContext.getInitParameterNames.asInstanceOf[JEnumeration[String]].asScala.toIterator map (key ⇒ key → this(key))

        def -(key: String) = Map() ++ this - key
        def +[B1 >: String](kv: (String, B1)) = Map() ++ this + kv
    }

    // View of the HttpSession properties as a Map
    class SessionMap(httpSession: HttpSession) extends AttributesToMap[AnyRef](new AttributesToMap.Attributeable[AnyRef] {
        def getAttribute(s: String) = httpSession.getAttribute(s)
        def getAttributeNames = httpSession.getAttributeNames.asInstanceOf[JEnumeration[String]]
        def removeAttribute(s: String): Unit = httpSession.removeAttribute(s)
        def setAttribute(s: String, o: AnyRef): Unit = httpSession.setAttribute(s, o)
    })

    // View of the HttpServletRequest properties as a Map
    class RequestMap(httpServletRequest: HttpServletRequest) extends AttributesToMap[AnyRef](new AttributesToMap.Attributeable[AnyRef] {
        def getAttribute(s: String) = httpServletRequest.getAttribute(s)
        def getAttributeNames = httpServletRequest.getAttributeNames.asInstanceOf[JEnumeration[String]]
        def removeAttribute(s: String): Unit = httpServletRequest.removeAttribute(s)
        def setAttribute(s: String, o: AnyRef): Unit =  httpServletRequest.setAttribute(s, o)
    })
}