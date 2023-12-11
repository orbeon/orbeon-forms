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

import java.io.InputStream
import java.net.URL

object ServletContext {
  def apply(servletContext: javax.servlet.ServletContext): JavaxServletContext     = new JavaxServletContext(servletContext)
  def apply(servletContext: jakarta.servlet.ServletContext): JakartaServletContext = new JakartaServletContext(servletContext)
}

trait ServletContext {
  // javax/jakarta.servlet.ServletContext
  def getNativeServletContext: AnyRef

  def addFilter(filterName: String, filterClass: Class[_ <: JavaxOrJakartaFilter]): FilterRegistration
  def addListener(listenerClass: Class[_ <: java.util.EventListener]): Unit
  def addServlet(servletName: String, servletClass: Class[_ <: JavaxOrJakartaServlet]): ServletRegistration
  def getAttribute(name: String): AnyRef
  def getAttributeNames: java.util.Enumeration[String]
  def getContext(uripath: String): ServletContext
  def getInitParameter(name: String): String
  def getInitParameterNames: java.util.Enumeration[String]
  def getRealPath(path: String): String
  def getRequestDispatcher(path: String): RequestDispatcher
  def getResource(path: String): URL
  def getResourceAsStream(path: String): InputStream
  def log(message: String): Unit
  def log(message: String, throwable: Throwable): Unit
  def setAttribute(name: String, `object`: AnyRef): Unit
  def removeAttribute(name: String): Unit
}

class JavaxServletContext(servletContext: javax.servlet.ServletContext) extends ServletContext {
  override def getNativeServletContext: javax.servlet.ServletContext = servletContext

  override def addFilter(filterName: String, filterClass: Class[_ <: JavaxOrJakartaFilter]): FilterRegistration = FilterRegistration(servletContext.addFilter(filterName, filterClass.asInstanceOf[Class[_ <: javax.servlet.Filter]]))
  override def addListener(listenerClass: Class[_ <: java.util.EventListener]): Unit = servletContext.addListener(listenerClass)
  override def addServlet(servletName: String, servletClass: Class[_ <: JavaxOrJakartaServlet]): ServletRegistration = ServletRegistration(servletContext.addServlet(servletName, servletClass.asInstanceOf[Class[_ <: javax.servlet.Servlet]]))
  override def getAttribute(name: String): AnyRef = servletContext.getAttribute(name)
  override def getAttributeNames: java.util.Enumeration[String] = servletContext.getAttributeNames
  override def getContext(uripath: String): ServletContext = ServletContext(servletContext.getContext(uripath))
  override def getInitParameter(name: String): String = servletContext.getInitParameter(name)
  override def getInitParameterNames: java.util.Enumeration[String] = servletContext.getInitParameterNames
  override def getRealPath(path: String): String = servletContext.getRealPath(path)
  override def getRequestDispatcher(path: String): RequestDispatcher = RequestDispatcher(servletContext.getRequestDispatcher(path))
  override def getResource(path: String): URL = servletContext.getResource(path)
  override def getResourceAsStream(path: String): InputStream = servletContext.getResourceAsStream(path)
  override def log(message: String): Unit = servletContext.log(message)
  override def log(message: String, throwable: Throwable): Unit = servletContext.log(message, throwable)
  override def setAttribute(name: String, `object`: AnyRef): Unit = servletContext.setAttribute(name, `object`)
  override def removeAttribute(name: String): Unit = servletContext.removeAttribute(name)
}

class JakartaServletContext(servletContext: jakarta.servlet.ServletContext) extends ServletContext {
  override def getNativeServletContext: jakarta.servlet.ServletContext = servletContext

  override def addFilter(filterName: String, filterClass: Class[_ <: JavaxOrJakartaFilter]): FilterRegistration = FilterRegistration(servletContext.addFilter(filterName, filterClass.asInstanceOf[Class[_ <: jakarta.servlet.Filter]]))
  override def addListener(listenerClass: Class[_ <: java.util.EventListener]): Unit = servletContext.addListener(listenerClass)
  override def addServlet(servletName: String, servletClass: Class[_ <: JavaxOrJakartaServlet]): ServletRegistration = ServletRegistration(servletContext.addServlet(servletName, servletClass.asInstanceOf[Class[_ <: jakarta.servlet.Servlet]]))
  override def getAttribute(name: String): AnyRef = servletContext.getAttribute(name)
  override def getAttributeNames: java.util.Enumeration[String] = servletContext.getAttributeNames
  override def getContext(uripath: String): ServletContext = ServletContext(servletContext.getContext(uripath))
  override def getInitParameter(name: String): String = servletContext.getInitParameter(name)
  override def getInitParameterNames: java.util.Enumeration[String] = servletContext.getInitParameterNames
  override def getRealPath(path: String): String = servletContext.getRealPath(path)
  override def getRequestDispatcher(path: String): RequestDispatcher = RequestDispatcher(servletContext.getRequestDispatcher(path))
  override def getResource(path: String): URL = servletContext.getResource(path)
  override def getResourceAsStream(path: String): InputStream = servletContext.getResourceAsStream(path)
  override def log(message: String): Unit = servletContext.log(message)
  override def log(message: String, throwable: Throwable): Unit = servletContext.log(message, throwable)
  override def setAttribute(name: String, `object`: AnyRef): Unit = servletContext.setAttribute(name, `object`)
  override def removeAttribute(name: String): Unit = servletContext.removeAttribute(name)
}
