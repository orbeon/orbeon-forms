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

import org.orbeon.oxf.cache
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.servlet._
import org.orbeon.oxf.util.SLF4JLogging._
import org.orbeon.oxf.xforms.state.XFormsStateManager

// For backward compatibility
class ReplicationServletContextListener extends JavaxReplicationServletContextListener

class JavaxReplicationServletContextListener   extends JavaxServletContextListener  (new ReplicationServletContextListenerImpl)
class JakartaReplicationServletContextListener extends JakartaServletContextListener(new ReplicationServletContextListenerImpl)

class ReplicationServletContextListenerImpl extends ServletContextListener {

  override def contextInitialized(servletContextEvent: ServletContextEvent): Unit =
    if (XFormsGlobalProperties.isReplication) {
      Version.instance.requirePEFeature("State replication")
      withDebug("eagerly bootstrapping caches for replication")(Caches)(cache.CacheSupport.Logger)
    }

  override def contextDestroyed(servletContextEvent: ServletContextEvent): Unit = ()
}

// For backward compatibility
class XFormsServletContextListener extends JavaxXFormsServletContextListener

class JavaxXFormsServletContextListener   extends JavaxHttpSessionListener  (new XFormsServletContextListenerImpl)
class JakartaXFormsServletContextListener extends JakartaHttpSessionListener(new XFormsServletContextListenerImpl)

class XFormsServletContextListenerImpl extends HttpSessionListener {

  override def sessionCreated(httpSessionEvent: HttpSessionEvent): Unit =
    XFormsStateManager.sessionCreated(new ServletSessionImpl(httpSessionEvent.getSession))

  override def sessionDestroyed(httpSessionEvent: HttpSessionEvent): Unit =
    XFormsStateManager.sessionDestroyed(new ServletSessionImpl(httpSessionEvent.getSession))
}