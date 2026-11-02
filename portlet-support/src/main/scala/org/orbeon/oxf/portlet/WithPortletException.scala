/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.portlet


trait WithPortletException {
  def newPortletException(message: String): Exception
  def newPortletException(throwable: Throwable): Exception
  def newPortletException(message: String, throwable: Throwable): Exception
}

trait WithJavaxPortletException extends WithPortletException {
  def newPortletException(message: String): Exception                       = new javax.portlet.PortletException(message)
  def newPortletException(throwable: Throwable): Exception                  = new javax.portlet.PortletException(throwable)
  def newPortletException(message: String, throwable: Throwable): Exception = new javax.portlet.PortletException(message, throwable)
}

trait WithJakartaPortletException extends WithPortletException {
  def newPortletException(message: String): Exception                       = new jakarta.portlet.PortletException(message)
  def newPortletException(throwable: Throwable): Exception                  = new jakarta.portlet.PortletException(throwable)
  def newPortletException(message: String, throwable: Throwable): Exception = new jakarta.portlet.PortletException(message, throwable)
}
