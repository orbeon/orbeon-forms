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
import org.orbeon.oxf.pipeline.api.{ProcessorDefinition, PipelineContext}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.ScalaUtils._

// Servlet/portlet helper for processor definitions and services
trait ServletPortlet {

    // Main and error processor property prefixes
    private val MainProcessorPropertyPrefix       = "oxf.main-processor."
    private val MainProcessorInputPropertyPrefix  = "oxf.main-processor.input."
    private val ErrorProcessorPropertyPrefix      = "oxf.error-processor."
    private val ErrorProcessorInputPropertyPrefix = "oxf.error-processor.input."

    def logPrefix: String
    def initParameters: Map[String, String]

    private var _processorService: Option[ProcessorService] = None
    def processorService = _processorService.get

    // Web application context instance shared between all components of a web application
    private var _webAppContext: Option[WebAppContext] = None
    def webAppContext = _webAppContext.get

    // Initialize the servlet or portlet
    def init(webAppContext: WebAppContext, processor: Option[(String, String)]): Unit = {
        _webAppContext    = Some(webAppContext)
        _processorService = Some(createProcessorService)

        // Run listener if needed
        processor foreach { case (processorPrefix, inputPrefix) ⇒ runInitDestroyListenerProcessor(_, _) }
        Logger.info(logPrefix + " initialized.")
    }

    // Destroy the servlet or portlet
    def destroy(processor: Option[(String, String)]): Unit = {
        // Run listener if needed
        processor foreach { case (processorPrefix, inputPrefix) ⇒ runInitDestroyListenerProcessor(_, _) }
        Logger.info(logPrefix + " destroyed.")

         // Clean-up
        _processorService = None
        _webAppContext    = None
    }

    private def runInitDestroyListenerProcessor(processorPrefix: String, inputPrefix: String): Unit = {
        // Create and run processor if definition is found
        searchDefinition(processorPrefix, inputPrefix) foreach  { definition ⇒
            Logger.info(logPrefix + " - About to run processor: " +  definition.toString)

            val processor = createProcessor(definition)
            val externalContext = new WebAppExternalContext(webAppContext)
            runProcessor(processor, externalContext, new PipelineContext, Logger)
        }
    }

    private def createProcessorService =
        searchDefinition(MainProcessorPropertyPrefix, MainProcessorInputPropertyPrefix) match {
            case Some(definition) ⇒
                // Create and initialize service
                new ProcessorService(definition, searchDefinition(ErrorProcessorPropertyPrefix, ErrorProcessorInputPropertyPrefix))
            case _ ⇒
                throw new OXFException("Unable to find main processor definition")
        }

    // Search a processor definition in order from: servlet/portlet parameters, properties, context parameters
    private def searchDefinition(processorPrefix: String, inputPrefix: String) = {

        val functions: Iterator[(String, String) ⇒ Option[ProcessorDefinition]] =
            Iterator(
                getDefinitionFromMap(initParameters, _, _),
                getDefinitionFromProperties _,
                getDefinitionFromMap(webAppContext.initParameters, _, _)
            )

        (functions flatMap (_(processorPrefix, inputPrefix))).nextOption()
    }
}
