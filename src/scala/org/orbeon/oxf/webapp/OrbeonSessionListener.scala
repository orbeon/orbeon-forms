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

import org.apache.log4j.Logger
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.servlet.ServletExternalContext
import org.orbeon.oxf.util.LoggerFactory
import javax.servlet.http.HttpSessionEvent
import javax.servlet.http.HttpSessionListener
import collection.JavaConverters._

class OrbeonSessionListenerDelegate extends OrbeonSessionListener

/**
 * This listener listens for HTTP session lifecycle changes.
 */
class OrbeonSessionListener extends HttpSessionListener {

    private val logger = LoggerFactory.createLogger(classOf[OrbeonSessionListener])

    private val InitProcessorPrefix     = "oxf.session-created-processor."
    private val InitInputPrefix         = "oxf.session-created-processor.input."
    private val DestroyProcessorPrefix  = "oxf.session-destroyed-processor."
    private val DestroyInputPrefix      = "oxf.session-destroyed-processor.input."

    def logPrefix = "Session Listener"

    def sessionCreated(event: HttpSessionEvent): Unit =
        try {
            val httpSession = event.getSession
            val servletContext = httpSession.getServletContext
            InitUtils.run(servletContext, httpSession, null, logger, logPrefix, "Session created.", InitProcessorPrefix, InitInputPrefix)
        } catch {
            case e: Exception ⇒
                logger.error(logPrefix + " - Exception when running session creation processor.", OXFException.getRootThrowable(e))
                throw new OXFException(e)
        }

    def sessionDestroyed(event: HttpSessionEvent): Unit = {
        val httpSession = event.getSession
        if (httpSession ne null) {
            try {
                // Run processor
                val servletContext = httpSession.getServletContext
                InitUtils.run(servletContext, httpSession, null, logger, logPrefix, "Session destroyed.", DestroyProcessorPrefix, DestroyInputPrefix)

                // Run listeners if any
                // One rationale for running this after the processor is that the processor might add new listeners
                val listeners = httpSession.getAttribute(ServletExternalContext.SESSION_LISTENERS).asInstanceOf[ServletExternalContext.SessionListeners]
                Option(listeners).toList flatMap (_.iterator.asScala) foreach {
                    // Run listener and ignore exceptions so we can continue running the remaining listeners
                    listener ⇒ try listener.sessionDestroyed() catch { case t ⇒ logger.error("Throwable caught when calling listener", t) }
                }
            } catch {
                case e: Exception ⇒
                    logger.error(logPrefix + " - Exception when running session destruction processor.", OXFException.getRootThrowable(e))
                    throw new OXFException(e)
            }
        }
    }
}