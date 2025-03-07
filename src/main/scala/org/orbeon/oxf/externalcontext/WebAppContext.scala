/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.servlet.ServletContext
import org.orbeon.oxf.webapp.Orbeon

import javax.portlet.PortletContext
import scala.jdk.CollectionConverters.*


// Expose parameters and attributes as maps
trait ParametersAndAttributes {

  // Immutable context initialization parameters
  lazy val initParameters =
    getInitParameterNames map (n => n -> getInitParameter(n)) toMap

  // Mutable context attributes backed by the actual context
  lazy val attributes = new collection.mutable.Map[String, AnyRef] {
    def get(k: String): Option[AnyRef] = Option(getAttribute(k))
    def iterator: Iterator[(String, AnyRef)] = getAttributeNames map (k => k -> getAttribute(k)) iterator
    override def size: Int = getAttributeNames.size
    def addOne(kv: (String, AnyRef)): this.type = { setAttribute(kv._1, kv._2); this }
    def subtractOne(k: String)      : this.type = { removeAttribute(k); this }
  }

  protected def getInitParameter(s: String): String
  protected def getInitParameterNames: Seq[String]

  protected def getAttribute(k: String): AnyRef
  protected def getAttributeNames: Seq[String]
  protected def setAttribute(k: String, v: AnyRef): Unit
  protected def removeAttribute(k: String): Unit
}

trait OrbeonWebApp {

  self: WebAppContext =>

  // Run initialization only once per web app
  WebAppContext.synchronized {
    val WebAppInitialized = "oxf.webapp.initialized"
    self.attributes.getOrElseUpdate(WebAppInitialized, { Orbeon.initialize(self); "true" })
  }
}

// Servlet implementation
class ServletWebAppContext(val servletContext: ServletContext) extends WebAppContext with ParametersAndAttributes with OrbeonWebApp {

  def getResource(s: String) = servletContext.getResource(s)
  def getResourceAsStream(s: String) = servletContext.getResourceAsStream(s)
  def getRealPath(s: String) = servletContext.getRealPath(s)

  def log(message: String, throwable: Throwable)  = servletContext.log(message, throwable)
  def log(message: String) = servletContext.log(message)

  protected def getInitParameter(s: String) = servletContext.getInitParameter(s)
  protected def getInitParameterNames: Seq[String] = servletContext.getInitParameterNames.asScala.toList

  protected def getAttribute(k: String) = servletContext.getAttribute(k)
  protected def getAttributeNames: Seq[String] = servletContext.getAttributeNames.asScala.toList
  protected def setAttribute(k: String, v: AnyRef) = servletContext.setAttribute(k, v)
  protected def removeAttribute(k: String) = servletContext.removeAttribute(k)
}

// Portlet implementation
class PortletWebAppContext(val portletContext: PortletContext) extends WebAppContext with ParametersAndAttributes with OrbeonWebApp {

  def getResource(s: String) = portletContext.getResource(s)
  def getResourceAsStream(s: String) = portletContext.getResourceAsStream(s)
  def getRealPath(s: String) = portletContext.getRealPath(s)

  def log(message: String, throwable: Throwable)  = portletContext.log(message, throwable)
  def log(message: String) = portletContext.log(message)

  protected def getInitParameter(s: String) = portletContext.getInitParameter(s)
  protected def getInitParameterNames: Seq[String] = portletContext.getInitParameterNames.asScala.toList

  protected def getAttribute(k: String) = portletContext.getAttribute(k)
  protected def getAttributeNames: Seq[String] = portletContext.getAttributeNames.asScala.toList
  protected def setAttribute(k: String, v: AnyRef) = portletContext.setAttribute(k, v)
  protected def removeAttribute(k: String) = portletContext.removeAttribute(k)
}

/**
 * Return the singleton WebAppContext for the current web app. When WebAppContext is created, also initialize Orbeon.
 */
object ServletWebAppContext {
  def apply(servletContext: ServletContext): WebAppContext = new ServletWebAppContext(servletContext)
}

object PortletWebAppContext {
  def apply(portletContext: PortletContext): WebAppContext = new PortletWebAppContext(portletContext)
}
