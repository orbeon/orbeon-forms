package org.orbeon.oxf.cache

import org.orbeon.oxf.servlet.{JakartaServletContextListener, JavaxServletContextListener, ServletContextEvent, ServletContextListener}

// For backward compatibility
class ShutdownListener extends JavaxShutdownListener

class JavaxShutdownListener   extends JavaxServletContextListener  (new ShutdownListenerImpl)
class JakartaShutdownListener extends JakartaServletContextListener(new ShutdownListenerImpl)

class ShutdownListenerImpl extends ServletContextListener {
  override def contextInitialized(servletContextEvent: ServletContextEvent): Unit = ()
  override def contextDestroyed(servletContextEvent: ServletContextEvent): Unit = CacheSupport.close()
}
