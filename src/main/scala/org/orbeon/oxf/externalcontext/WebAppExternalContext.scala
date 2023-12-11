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
package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.externalcontext.ExternalContext.Session
import org.orbeon.oxf.servlet.{HttpSession, ServletSessionImpl}

// External context which only exposes the web app, without request or response
// Session is None when called from init()/destroy()/contextInitialized()/contextDestroyed()
// Session is Some(_) when called from sessionCreated()/sessionDestroyed()
class WebAppExternalContext(webAppContext: WebAppContext, httpSession: Option[HttpSession] = None) extends ExternalContext {

  // Return null if we were not provided with a session. This allows detecting whether the session is available or not.
  private lazy val session: Session = httpSession map (new ServletSessionImpl(_)) orNull
  def getSession(create: Boolean) = session

  def getWebAppContext = webAppContext
  def getNativeRequest = null
  def getNativeResponse = null
  def getStartLoggerString = ""
  def getEndLoggerString = ""
  def getRequest = null
  def getResponse = null
}
