/**
 * Copyright (C) 2023 Orbeon, Inc.
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
package org.orbeon.oxf.servlet

trait ServletContextListener {
  def contextInitialized(sce: ServletContextEvent): Unit
  def contextDestroyed(sce: ServletContextEvent): Unit
}

class JavaxServletContextListener(servletContextListener: ServletContextListener) extends javax.servlet.ServletContextListener {
  override def contextInitialized(sce: javax.servlet.ServletContextEvent): Unit = servletContextListener.contextInitialized(ServletContextEvent(sce))
  override def contextDestroyed(sce: javax.servlet.ServletContextEvent): Unit = servletContextListener.contextDestroyed(ServletContextEvent(sce))
}

class JakartaServletContextListener(servletContextListener: ServletContextListener) extends jakarta.servlet.ServletContextListener {
  override def contextInitialized(sce: jakarta.servlet.ServletContextEvent): Unit = servletContextListener.contextInitialized(ServletContextEvent(sce))
  override def contextDestroyed(sce: jakarta.servlet.ServletContextEvent): Unit = servletContextListener.contextDestroyed(ServletContextEvent(sce))
}
