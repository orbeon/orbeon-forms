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

import org.orbeon.oxf.pipeline.InitUtils.runWithServletContext
import javax.servlet.http.HttpSessionEvent
import javax.servlet.http.HttpSessionListener
import collection.JavaConverters._
import javax.servlet.ServletException
import org.orbeon.oxf.util.ScalaUtils._
import scala.util.control.NonFatal

// For backward compatibility
class OrbeonSessionListenerDelegate extends OrbeonSessionListener

/**
 * This listener listens for HTTP session lifecycle changes.
 */
class OrbeonSessionListener extends HttpSessionListener {

    private val InitProcessorPrefix     = "oxf.session-created-processor."
    private val InitInputPrefix         = "oxf.session-created-processor.input."
    private val DestroyProcessorPrefix  = "oxf.session-destroyed-processor."
    private val DestroyInputPrefix      = "oxf.session-destroyed-processor.input."

    private implicit val logger = ProcessorService.Logger

    def logPrefix = "Session listener"
    def initParameters = Map()

    def sessionCreated(event: HttpSessionEvent): Unit =
        withRootException("session creation", new ServletException(_)) {
            val httpSession = event.getSession
            val servletContext = httpSession.getServletContext
            runWithServletContext(servletContext, Some(httpSession), logger, logPrefix, "Session created.", InitProcessorPrefix, InitInputPrefix)
        }

    def sessionDestroyed(event: HttpSessionEvent): Unit =
        withRootException("session destruction", new ServletException(_)) {
            val httpSession = event.getSession
            if (httpSession ne null) {
                // Run processor
                val servletContext = httpSession.getServletContext
                runWithServletContext(servletContext, Some(httpSession), logger, logPrefix, "Session destroyed.", DestroyProcessorPrefix, DestroyInputPrefix)

                // Run listeners if any
                // One rationale for running this after the processor is that the processor might add new listeners
                val listeners = httpSession.getAttribute(SessionListeners.SessionListenersKey).asInstanceOf[SessionListeners]
                Option(listeners).toList flatMap (_.iterator.asScala) foreach {
                    // Run listener and ignore exceptions so we can continue running the remaining listeners
                    listener ⇒ try listener.sessionDestroyed() catch { case NonFatal(t) ⇒ logger.error("Throwable caught when calling listener", t) }
                }
            }
        }
}