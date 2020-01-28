/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import javax.portlet.filter.PortletRequestWrapper

import scala.collection.JavaConverters._

trait RequestPrependHeaders extends PortletRequestWrapper {

  def headersToPrepend: Map[String, Array[String]]

  override def getPropertyNames =
    (headersToPrepend.keysIterator ++ (super.getPropertyNames.asScala filterNot headersToPrepend.keySet))
      .asJavaEnumeration

  override def getProperty(name: String) =
    addedHeaderOption(name) getOrElse super.getProperty(name)

  override def getProperties(name: String) =
    headersToPrepend.get(name) map (_.iterator.asJavaEnumeration) getOrElse super.getProperties(name)

  private def addedHeaderOption(name: String) =
    headersToPrepend.get(name) filter (_.nonEmpty) map (_(0))
}

trait RequestRemoveHeaders extends PortletRequestWrapper {

  def headersToRemove: String => Boolean

  override def getPropertyNames = (super.getPropertyNames.asScala filterNot headersToRemove).asJavaEnumeration

  override def getProperty(name: String)   = if (headersToRemove(name)) null else super.getProperty(name)
  override def getProperties(name: String) = if (headersToRemove(name)) null else super.getProperties(name)
}