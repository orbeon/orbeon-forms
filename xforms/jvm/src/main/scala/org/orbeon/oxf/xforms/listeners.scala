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
package org.orbeon.oxf.xforms

import javax.servlet.http.{HttpSessionEvent, HttpSessionListener}
import javax.servlet.{ServletContextEvent, ServletContextListener}

import org.orbeon.oxf.cache
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.servlet.ServletSessionImpl
import org.orbeon.oxf.util.SLF4JLogging._
import org.orbeon.oxf.xforms.state.XFormsStateManager

class ReplicationServletContextListener extends ServletContextListener {

  override def contextInitialized(servletContextEvent: ServletContextEvent): Unit =
    if (XFormsGlobalProperties.isReplication) {
      Version.instance.requirePEFeature("State replication")
      withDebug("eagerly bootstrapping caches for replication")(Caches)(cache.Caches.Logger)
    }

  override def contextDestroyed(servletContextEvent: ServletContextEvent): Unit = ()
}

class XFormsServletContextListener extends HttpSessionListener {

  override def sessionCreated(httpSessionEvent: HttpSessionEvent): Unit =
    XFormsStateManager.sessionCreated(new ServletSessionImpl(httpSessionEvent.getSession))

  override def sessionDestroyed(httpSessionEvent: HttpSessionEvent): Unit =
    XFormsStateManager.sessionDestroyed(new ServletSessionImpl(httpSessionEvent.getSession))
}