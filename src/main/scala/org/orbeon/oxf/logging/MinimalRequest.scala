/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.logging

import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Session, SessionListener, SessionScope}
import org.orbeon.oxf.externalcontext.RequestAdapter
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.servlet.{HttpServletRequest, HttpSession}
import org.orbeon.oxf.webapp.ServletSupport


private class MinimalSession(session: HttpSession) extends Session {

  def getId = session.getId

  def getCreationTime: Long                                           = throw new UnsupportedOperationException
  def isNew: Boolean                                                  = throw new UnsupportedOperationException
  def getLastAccessedTime: Long                                       = throw new UnsupportedOperationException
  def removeListener(sessionListener: SessionListener): Unit          = throw new UnsupportedOperationException
  def setMaxInactiveInterval(interval: Int): Unit                     = throw new UnsupportedOperationException
  def addListener(sessionListener: SessionListener): Unit             = throw new UnsupportedOperationException
  def invalidate(): Unit                                              = throw new UnsupportedOperationException
  def getMaxInactiveInterval: Int                                     = throw new UnsupportedOperationException

  def getAttribute(name: String, scope: SessionScope)                 = throw new UnsupportedOperationException
  def setAttribute(name: String, value: AnyRef, scope: SessionScope)  = throw new UnsupportedOperationException
  def removeAttribute(name: String, scope: SessionScope)              = throw new UnsupportedOperationException
  def getAttributeNames(scope: SessionScope): List[String]            = throw new UnsupportedOperationException
}

private class MinimalRequest(req: HttpServletRequest) extends RequestAdapter {

  override lazy val getAttributesMap = new InitUtils.RequestMap(req)
  override def getRequestPath        = ServletSupport.getRequestPathInfo(req)
  override def getMethod             = HttpMethod.withNameInsensitive(req.getMethod)

  private lazy val sessionWrapper = new MinimalSession(req.getSession(true))

  override def getSession(create: Boolean): Session = {
    val underlyingSession = req.getSession(create)
    if (underlyingSession ne null)
      sessionWrapper
    else
      null
  }
}

object MinimalRequest {
  def apply(req: HttpServletRequest): Request = new MinimalRequest(req)
}
