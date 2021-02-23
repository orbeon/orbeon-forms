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

import org.log4s
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.InitUtils.runWithServletContext
import org.orbeon.oxf.servlet.ServletSessionImpl
import org.orbeon.oxf.webapp.ServletPortlet._

import javax.servlet.ServletException
import javax.servlet.http.{HttpSessionEvent, HttpSessionListener}
import scala.util.control.NonFatal

// For backward compatibility
class OrbeonSessionListenerDelegate extends OrbeonSessionListener


/**
 * This listener listens for HTTP session lifecycle changes.
 */
class OrbeonSessionListener extends HttpSessionListener {

  import OrbeonSessionListener._

  private val InitProcessorPrefix     = "oxf.session-created-processor."
  private val InitInputPrefix         = "oxf.session-created-processor.input."
  private val DestroyProcessorPrefix  = "oxf.session-destroyed-processor."
  private val DestroyInputPrefix      = "oxf.session-destroyed-processor.input."

  def logPrefix = "Session listener"
  def initParameters = Map()

  override def sessionCreated(event: HttpSessionEvent): Unit =
    withRootException("session creation", new ServletException(_)) {

      val httpSession = event.getSession

      sessionListenersCreate(new ServletSessionImpl(httpSession))

      runWithServletContext(
        servletContext         = httpSession.getServletContext,
        session                = Some(httpSession),
        logMessagePrefix       = logPrefix,
        message                = "Session created.",
        uriNamePropertyPrefix  = InitProcessorPrefix,
        processorInputProperty = InitInputPrefix
      )
    }

  override def sessionDestroyed(event: HttpSessionEvent): Unit =
    withRootException("session destruction", new ServletException(_)) {

      val httpSession = event.getSession
      if (httpSession ne null) {

        runWithServletContext(
          servletContext         = httpSession.getServletContext,
          session                = Some(httpSession),
          logMessagePrefix       = logPrefix,
          message                = "Session destroyed.",
          uriNamePropertyPrefix  = DestroyProcessorPrefix,
          processorInputProperty = DestroyInputPrefix
        )

        // Run listeners after the processor because processor might add new listeners
        sessionListenersDestroy(new ServletSessionImpl(httpSession))
      }
    }
}

object OrbeonSessionListener {

  implicit val logger: log4s.Logger = ProcessorService.Logger

  // Immediately store `SessionListeners` into the session to avoid concurrency issues which can occur if we
  // do this lazily in `ServletExternalContext.addListener()`.
  def sessionListenersCreate(session: ExternalContext.Session): Unit =
    session.setAttribute(SessionListeners.SessionListenersKey, new SessionListeners)

  def sessionListenersDestroy(session: ExternalContext.Session): Unit = {

    val listeners =
      session.getAttribute(SessionListeners.SessionListenersKey).asInstanceOf[SessionListeners]

    for {
      listeners <- Option(listeners).iterator
      listener  <- listeners.iterateRemoveAndClose()
    } locally {
      try {
        listener.sessionDestroyed(session)
      } catch {
        case NonFatal(t) =>
          // Catch so we can continue running the remaining listeners
          logger.error(t)("Throwable caught when calling listener")
      }
    }
  }
}