/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.util.LoggerFactory
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener

class OrbeonServletContextListenerDelegate extends OrbeonServletContextListener

/**
 * This listener listens for HTTP context lifecycle changes.
 */
class OrbeonServletContextListener extends ServletContextListener {

    private val logger = LoggerFactory.createLogger(classOf[OrbeonServletContextListener])

    private val InitProcessorPrefix     = "oxf.context-initialized-processor."
    private val InitInputPrefix         = "oxf.context-initialized-processor.input."
    private val DestroyProcessorPrefix  = "oxf.context-destroyed-processor."
    private val DestroyInputPrefix      = "oxf.context-destroyed-processor.input."

    def logPrefix = "Servlet Context Listener"

    def contextInitialized(event: ServletContextEvent): Unit =
        try {
            InitUtils.run(event.getServletContext, null, null, logger, logPrefix, "Context initialized.", InitProcessorPrefix, InitInputPrefix)
        } catch {
            case e: Exception ⇒
                logger.error(logPrefix + " - Exception when running Servlet context initialization processor.", OXFException.getRootThrowable(e))
                throw new OXFException(e)
        }

    def contextDestroyed(event: ServletContextEvent): Unit =
        try {
            InitUtils.run(event.getServletContext, null, null, logger, logPrefix, "Context destroyed.", DestroyProcessorPrefix, DestroyInputPrefix)
            WebAppContext.instance(event.getServletContext).webAppDestroyed
        } catch {
            case e: Exception ⇒
                logger.error(logPrefix + " - Exception when running Servlet context destruction processor.", OXFException.getRootThrowable(e))
                throw new OXFException(e)
        }
}