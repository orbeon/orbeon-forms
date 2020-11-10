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
package org.orbeon.oxf.portlet

import javax.portlet.PortletSession
import scala.collection.JavaConverters._

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.SessionScope
import org.orbeon.oxf.webapp.SessionListeners

class PortletSessionImpl(portletSession: PortletSession)
  extends ExternalContext.Session {

  import PortletSession._

  import SessionListeners._

  // Delegate
  def getCreationTime                       = portletSession.getCreationTime
  def getId                                 = portletSession.getId
  def getLastAccessedTime                   = portletSession.getLastAccessedTime
  def getMaxInactiveInterval                = portletSession.getMaxInactiveInterval
  def invalidate()                          = portletSession.invalidate()
  def isNew                                 = portletSession.isNew
  def setMaxInactiveInterval(interval: Int) = portletSession.setMaxInactiveInterval(interval)

  def getAttribute(name: String, scope: SessionScope): Option[AnyRef] =
    Option(portletSession.getAttribute(name, scope.value))

  def setAttribute(name: String, value: AnyRef, scope: SessionScope): Unit =
    portletSession.setAttribute(name, value, scope.value)

  def removeAttribute(name: String, scope: SessionScope): Unit =
    portletSession.removeAttribute(name, scope.value)

  def getAttributeNames(scope: SessionScope): List[String]            =
    portletSession.getAttributeNames(scope.value).asScala.toList

  def addListener(sessionListener: ExternalContext.SessionListener): Unit =
    portletSession.getAttribute(SessionListenersKey, APPLICATION_SCOPE) match {
      case listeners: SessionListeners => listeners.addListener(sessionListener)
      case _ =>
        throw new IllegalStateException(
          "`SessionListeners` object not found in session. `OrbeonSessionListener` might be missing from web.xml."
        )
    }

  def removeListener(sessionListener: ExternalContext.SessionListener): Unit =
    portletSession.getAttribute(SessionListenersKey, APPLICATION_SCOPE) match {
      case listeners: SessionListeners => listeners.removeListener(sessionListener)
      case _ =>
    }
}