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

import org.orbeon.oxf.externalcontext.ServletWebAppContext
import org.orbeon.oxf.pipeline.InitUtils.runWithServletContext
import org.orbeon.oxf.servlet._
import org.orbeon.oxf.webapp.ServletPortlet._

// For backward compatibility
class OrbeonServletContextListenerDelegate extends JavaxOrbeonServletContextListener

class JavaxOrbeonServletContextListener   extends JavaxServletContextListener  (new OrbeonServletContextListener with WithJavaxServletException)
class JakartaOrbeonServletContextListener extends JakartaServletContextListener(new OrbeonServletContextListener with WithJakartaServletException)

/**
 * This listener listens for HTTP context lifecycle changes.
 */
abstract class OrbeonServletContextListener extends ServletContextListener with WithServletException {

  def newServletException(throwable: Throwable): Exception

  private val InitProcessorPrefix     = "oxf.context-initialized-processor."
  private val InitInputPrefix         = "oxf.context-initialized-processor.input."
  private val DestroyProcessorPrefix  = "oxf.context-destroyed-processor."
  private val DestroyInputPrefix      = "oxf.context-destroyed-processor.input."

  private implicit val logger = ProcessorService.Logger

  def logPrefix = "Context listener"
  def initParameters = Map()

  override def contextInitialized(event: ServletContextEvent): Unit =
    withRootException("context creation", newServletException) {
      runWithServletContext(event.getServletContext, None, logPrefix, "Context initialized.", InitProcessorPrefix, InitInputPrefix)
    }

  override def contextDestroyed(event: ServletContextEvent): Unit =
    withRootException("context destruction", newServletException) {
      runWithServletContext(event.getServletContext, None, logPrefix, "Context destroyed.", DestroyProcessorPrefix, DestroyInputPrefix)
      // NOTE: This calls all listeners, because the listeners are stored in the actual web app context's attributes
      // TODO: Shouldn't a singleton `WebAppContext` be available instead?
      ServletWebAppContext(event.getServletContext).webAppDestroyed()
    }
}