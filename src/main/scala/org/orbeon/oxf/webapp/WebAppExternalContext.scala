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

import java.util.{Map ⇒ JMap}
import javax.servlet.http.HttpSession

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.webapp.ExternalContext.{ApplicationSessionScope, Session, SessionScope}

// External context which only exposes the web app, without request or response
// Session is None when called from init()/destroy()/contextInitialized()/contextDestroyed()
// Session is Some(_) when called from sessionCreated()/sessionDestroyed()
class WebAppExternalContext(webAppContext: WebAppContext, httpSession: Option[HttpSession] = None) extends ExternalContext {

  // Return null if we were not provided with a session. This allows detecting whether the session is available or not.
  private lazy val session: Session = httpSession map (new SessionImpl(_)) orNull
  def getSession(create: Boolean) = session

  def getWebAppContext = webAppContext
  def getNativeRequest = null
  def getNativeResponse = null
  def getStartLoggerString = ""
  def getEndLoggerString = ""
  def getRequest = null
  def getResponse = null
  def getRequestDispatcher(path: String, isContextRelative: Boolean): ExternalContext.RequestDispatcher = null

  private class SessionImpl(private val httpSession: HttpSession) extends ExternalContext.Session {

    private var sessionAttributesMap: JMap[String, AnyRef] = _

    def getCreationTime                       = httpSession.getCreationTime
    def getId                                 = httpSession.getId
    def getLastAccessedTime                   = httpSession.getLastAccessedTime
    def getMaxInactiveInterval                = httpSession.getMaxInactiveInterval
    def invalidate()                          = httpSession.invalidate()
    def isNew                                 = httpSession.isNew
    def setMaxInactiveInterval(interval: Int) = httpSession.setMaxInactiveInterval(interval)

    def getAttributesMap = {
      if (sessionAttributesMap eq null)
        sessionAttributesMap = new InitUtils.SessionMap(httpSession)

      sessionAttributesMap
    }

    def getAttributesMap(scope: SessionScope) = {
      if (scope != ApplicationSessionScope)
        throw new OXFException("Invalid session scope scope: only the application scope is allowed in Servlets")

      getAttributesMap
    }

    def addListener(sessionListener: ExternalContext.SessionListener) =
      throw new UnsupportedOperationException

    def removeListener(sessionListener: ExternalContext.SessionListener) =
      throw new UnsupportedOperationException
  }
}
