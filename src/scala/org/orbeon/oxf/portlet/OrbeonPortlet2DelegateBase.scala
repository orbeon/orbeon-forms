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
package org.orbeon.oxf.portlet

import collection.JavaConverters._
import org.orbeon.oxf.webapp.ProcessorService
import org.orbeon.oxf.webapp.ProcessorService._
import org.orbeon.oxf.pipeline.InitUtils._
import org.orbeon.oxf.common.{OXFException, Version}
import javax.portlet.{PortletException, GenericPortlet}
import org.orbeon.oxf.pipeline.api.{ProcessorDefinition, PipelineContext}

/**
 * OrbeonPortlet2 and OrbeonPortlet2Delegate are the Portlet (JSR-286) entry point of Orbeon. OrbeonPortlet2 simply
 * delegates to OrbeonPortlet2Delegate and provides an option of using the Orbeon Class Loader.
 *
 * Several OrbeonServlet and OrbeonPortlet2 instances can be used in the same Web or Portlet application.
 * They all share the same Servlet context initialization parameters, but each Portlet can be
 * configured with its own main processor and inputs.
 *
 * All OrbeonServlet and OrbeonPortlet2 instances in a given Web application share the same resource manager.
 *
 * WARNING: OrbeonPortlet2 must only depend on the Servlet API and the Orbeon Class Loader.
 */
abstract class OrbeonPortlet2DelegateBase extends GenericPortlet {
    
    private val InitProcessorPrefix = "oxf.portlet-initialized-processor."
    private val InitInputPrefix = "oxf.portlet-initialized-processor.input."
    private val DestroyProcessorPrefix = "oxf.portlet-destroyed-processor."
    private val DestroyInputPrefix = "oxf.portlet-destroyed-processor.input."

    private val LogPrefix = "Portlet"

    private var _processorService: ProcessorService = _
    def processorService = _processorService

    override def init() {

        // This is a PE feature
        Version.instance.checkPEFeature("Orbeon Forms portlet")

        // NOTE: Here we assume that an Orbeon Forms WebAppContext context has already
        // been initialized. This can be done by another Servlet or Filter. The only reason we
        // cannot use the WebAppContext appears to be that it has to pass the ServletContext to
        // the resource manager, which uses in turn to read resources from the Web app classloader.

        _processorService =
            searchDefinition(MAIN_PROCESSOR_PROPERTY_PREFIX, MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX) match {
                case Some(definition) ⇒
                    try {
                        // Create and initialize service
                        val processorService = new ProcessorService
                        processorService.init(definition, searchDefinition(ERROR_PROCESSOR_PROPERTY_PREFIX, ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX).orNull)
                        processorService
                    } catch {
                        case e: Exception ⇒ throw new PortletException(OXFException.getRootThrowable(e))
                    }
                case _ ⇒
                    throw new PortletException("Unable to find main processor definition")
            }

        // Run listeners
        runListenerProcessor(InitProcessorPrefix, InitInputPrefix)
        logger.info(LogPrefix + " - " + "Portlet initialized.")
    }

    override def destroy() {
        // Run listeners
        logger.info(LogPrefix + " - " + "Portlet destroyed.")
        runListenerProcessor(DestroyProcessorPrefix, DestroyInputPrefix)
        _processorService.destroy()
    }
    
    // Search a processor definition in order from: portlet parameters, properties, context parameters
    private def searchDefinition(processorPrefix: String, inputPrefix: String) = {
        // All search functions
        val functions: Seq[(String, String) ⇒ ProcessorDefinition] =
            Seq(getDefinitionFromMap(portletInitParameters.asJava, _, _),
                getDefinitionFromProperties _,
                getDefinitionFromMap(contextInitParameters.asJava, _, _))
        
        // Call functions until we find a result
        functions map (_(processorPrefix, inputPrefix)) find (_ ne null)
    }

    private def runListenerProcessor(processorPrefix: String, inputPrefix: String) {
        try {
            // Create and run processor if definition is found
            searchDefinition(processorPrefix, inputPrefix) foreach  { definition ⇒
                logger.info(LogPrefix + " - About to run processor: " +  definition.toString)
                
                val processor = createProcessor(definition)
                val externalContext = new PortletContextExternalContext(getPortletContext)
                runProcessor(processor, externalContext, new PipelineContext, logger)
            }
        } catch {
            case e: Exception ⇒
                logger.error(LogPrefix + " - Exception when running Portlet listener processor.", OXFException.getRootThrowable(e))
        }
    }

    // Immutable map of context parameters
    protected lazy val contextInitParameters = {
        val portletContext = getPortletContext
        portletContext.getInitParameterNames.asScala map
            (n ⇒ n → portletContext.getInitParameter(n)) toMap
    }

    // Immutable map of portlet parameters
    private lazy val portletInitParameters =
        getInitParameterNames.asScala map
            (n ⇒ n → getInitParameter(n)) toMap
}