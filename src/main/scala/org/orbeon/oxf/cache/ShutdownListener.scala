package org.orbeon.oxf.cache

import javax.servlet.{ServletContextEvent, ServletContextListener}


class ShutdownListener extends ServletContextListener {

  override def contextInitialized(servletContextEvent: ServletContextEvent): Unit = ()

  override def contextDestroyed(servletContextEvent: ServletContextEvent): Unit =
    CacheSupport.close()
}
