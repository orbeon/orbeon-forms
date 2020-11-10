/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.servlet

import javax.servlet.http.HttpSession
import scala.collection.JavaConverters._

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.SessionScope
import org.orbeon.oxf.webapp.SessionListeners


trait SessionDelegate {

  protected def httpSession: HttpSession

  def getCreationTime                       = httpSession.getCreationTime
  def getId                                 = httpSession.getId
  def getLastAccessedTime                   = httpSession.getLastAccessedTime
  def getMaxInactiveInterval                = httpSession.getMaxInactiveInterval
  def invalidate()                          = httpSession.invalidate()
  def isNew                                 = httpSession.isNew
  def setMaxInactiveInterval(interval: Int) = httpSession.setMaxInactiveInterval(interval)
}

trait SessionListenerSupport {

  protected def httpSession: HttpSession

  def addListener(sessionListener: ExternalContext.SessionListener): Unit = {
    val listeners = httpSession.getAttribute(SessionListeners.SessionListenersKey).asInstanceOf[SessionListeners]
    if (listeners eq null)
      throw new IllegalStateException(
        "`SessionListeners` object not found in session. `OrbeonSessionListener` might be missing from web.xml."
      )
    listeners.addListener(sessionListener)
  }

  def removeListener(sessionListener: ExternalContext.SessionListener): Unit = {
    val listeners = httpSession.getAttribute(SessionListeners.SessionListenersKey).asInstanceOf[SessionListeners]
    if (listeners ne null)
      listeners.removeListener(sessionListener)
  }
}

class ServletSessionImpl(protected val httpSession: HttpSession)
  extends SessionDelegate with SessionListenerSupport with ExternalContext.Session {

  def getAttribute     (name: String,                scope: SessionScope) : Option[AnyRef] = Option(httpSession.getAttribute(name))
  def setAttribute     (name: String, value: AnyRef, scope: SessionScope) : Unit           = httpSession.setAttribute(name, value)
  def removeAttribute  (name: String,                scope: SessionScope) : Unit           = httpSession.removeAttribute(name)
  def getAttributeNames(                             scope: SessionScope) : List[String]   = httpSession.getAttributeNames.asScala.toList
}