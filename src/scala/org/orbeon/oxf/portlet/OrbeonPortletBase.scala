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
import org.orbeon.oxf.webapp.ProcessorService._
import org.orbeon.oxf.common.{OXFException, Version}
import javax.portlet.{PortletException, GenericPortlet}
import org.orbeon.oxf.webapp.{ServletPortletDefinitions, WebAppContext, ProcessorService}

/**
 * This is the Portlet (JSR-286) entry point of Orbeon.
 *
 * Several servlets and portlets can be used in the same web application. They all share the same context initialization
 * parameters, but each servlet and portlet can be configured with its own main processor and inputs.
 *
 * All servlets and portlets instances in a given web app share the same resource manager.
 */
abstract class OrbeonPortletBase extends GenericPortlet with ServletPortletDefinitions {
    
    private val InitProcessorPrefix     = "oxf.portlet-initialized-processor."
    private val InitInputPrefix         = "oxf.portlet-initialized-processor.input."
    private val DestroyProcessorPrefix  = "oxf.portlet-destroyed-processor."
    private val DestroyInputPrefix      = "oxf.portlet-destroyed-processor.input."

    def logPrefix = "Portlet"

    private var _webAppContext: WebAppContext = _
    def webAppContext = _webAppContext

    private var _processorService: ProcessorService = _
    def processorService = _processorService

    // Immutable map of portlet parameters
    lazy val initParameters =
        getInitParameterNames.asScala map
            (n ⇒ n → getInitParameter(n)) toMap

    override def init(): Unit =
        try {

            // This is a PE feature
            Version.instance.checkPEFeature("Orbeon Forms portlet")

            _webAppContext = WebAppContext.instance(getPortletContext)
            _processorService = getProcessorService

            // Run listeners
            runListenerProcessor(InitProcessorPrefix, InitInputPrefix)
            logger.info(logPrefix + " - " + "Portlet initialized.")
        } catch {
            case e: Exception ⇒ throw new PortletException(OXFException.getRootThrowable(e))
        }

    override def destroy(): Unit =
        try {
            // Run listeners
            logger.info(logPrefix + " - " + "Portlet destroyed.")
            runListenerProcessor(DestroyProcessorPrefix, DestroyInputPrefix)

            // Clean-up
            _processorService.destroy()
            _processorService = null
            _webAppContext = null
        } catch {
            case e: Exception ⇒ throw new PortletException(OXFException.getRootThrowable(e))
        }
}