/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.webapp

import org.orbeon.oxf.pipeline.InitUtils._
import org.orbeon.oxf.webapp.ProcessorService._
import org.orbeon.oxf.pipeline.api.{PipelineContext, ProcessorDefinition}
import org.orbeon.oxf.common.OXFException
import collection.JavaConverters._

// Servlet/portlet helper for processor definitions and services
trait ServletPortlet {

    def logPrefix: String
    def initParameters: Map[String, String]

    private var _processorService: ProcessorService = _
    def processorService = _processorService

    // Web application context instance shared between all components of a web application
    private var _webAppContext: WebAppContext = _
    def webAppContext = _webAppContext

    def withRootException(action: String, newException: Throwable ⇒ Exception)(body: ⇒ Any) =
        try body
        catch {
            case e: Exception ⇒
                logger.error(logPrefix + " - Exception when running " + action, OXFException.getRootThrowable(e))
                throw newException(OXFException.getRootThrowable(e))
        }

    // Initialize the servlet or portlet
    def init(webAppContext: WebAppContext, processor: Option[(String, String)]): Unit = {
        _webAppContext = webAppContext
        _processorService = getProcessorService

        // Run listener if needed
        processor foreach { case (processorPrefix, inputPrefix) ⇒ runListenerProcessor(_, _) }
        logger.info(logPrefix + " initialized.")
    }

    // Destroy the servlet or portlet
    def destroy(processor: Option[(String, String)]): Unit = {
        // Run listener if needed
        processor foreach { case (processorPrefix, inputPrefix) ⇒ runListenerProcessor(_, _) }
        logger.info(logPrefix + " destroyed.")

         // Clean-up
        _processorService.destroy()
        _processorService = null
        _webAppContext = null
    }

    private def runListenerProcessor(processorPrefix: String, inputPrefix: String): Unit = {
        // Create and run processor if definition is found
        searchDefinition(processorPrefix, inputPrefix) foreach  { definition ⇒
            logger.info(logPrefix + " - About to run processor: " +  definition.toString)

            val processor = createProcessor(definition)
            val externalContext = new WebAppExternalContext(webAppContext)
            runProcessor(processor, externalContext, new PipelineContext, logger)
        }
    }

    private def getProcessorService =
        searchDefinition(MAIN_PROCESSOR_PROPERTY_PREFIX, MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX) match {
            case Some(definition) ⇒
                // Create and initialize service
                val processorService = new ProcessorService
                processorService.init(definition, searchDefinition(ERROR_PROCESSOR_PROPERTY_PREFIX, ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX).orNull)
                processorService
            case _ ⇒
                throw new OXFException("Unable to find main processor definition")
        }

    // Search a processor definition in order from: servlet/portlet parameters, properties, context parameters
    private def searchDefinition(processorPrefix: String, inputPrefix: String) = {
        // All search functions
        val functions: Seq[(String, String) ⇒ ProcessorDefinition] =
            Seq(getDefinitionFromMap(initParameters.asJava, _, _),
                getDefinitionFromProperties _,
                getDefinitionFromMap(webAppContext.initParameters.asJava, _, _))

        // Call functions until we find a result
        functions map (_(processorPrefix, inputPrefix)) find (_ ne null)
    }
}
