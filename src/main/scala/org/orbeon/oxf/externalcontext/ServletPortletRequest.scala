/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.URLRewriterUtils
import org.orbeon.oxf.webapp._

object ServletPortletRequest {

  private val CredentialsSessionKey = "org.orbeon.auth.credentials"

  def findCredentialsInSession(session: SessionFacade): Option[Credentials] = // portlet/servlet session
     collectByErasedType[Credentials](session.getAttribute(CredentialsSessionKey))

   def storeCredentialsInSession(session: SessionFacade, credentials: Credentials): Unit =
    session.setAttribute(CredentialsSessionKey, credentials)
}

// Implementations shared between ServletExternalContext and Portlet2ExternalContext.
trait ServletPortletRequest extends ExternalContext.Request {

  protected def headerValuesMap: Map[String, Array[String]]

  lazy val credentials: Option[Credentials] =
    sessionOpt flatMap (_.getAttribute(ServletPortletRequest.CredentialsSessionKey)) collect {
      case credentials: Credentials => credentials
    }

  def isUserInRole(role: String): Boolean =
    credentials exists (_.roles exists (_.roleName == role))

  def getClientContextPath(urlString: String): String =
    if (URLRewriterUtils.isPlatformPath(urlString))
      platformClientContextPath
    else
      applicationClientContextPath

  private lazy val platformClientContextPath: String =
    URLRewriterUtils.getClientContextPath(this, true)

  private lazy val applicationClientContextPath: String =
    URLRewriterUtils.getClientContextPath(this, false)
}
