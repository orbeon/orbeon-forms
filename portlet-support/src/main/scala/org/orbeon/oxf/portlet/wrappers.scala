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

import cats.data.NonEmptyList

import java.util
import javax.portlet.filter.PortletRequestWrapper
import scala.jdk.CollectionConverters.*


trait RequestPrependHeaders extends PortletRequestWrapper {

  def headersToPrependAsMap: Map[String, NonEmptyList[String]]

  override def getPropertyNames: util.Enumeration[String] =
    (headersToPrependAsMap.keysIterator ++ (super.getPropertyNames.asScala filterNot mustPrependHeader))
      .asJavaEnumeration

  override def getProperty(name: String): String =
    addedHeaderSingleOption(name) getOrElse super.getProperty(name)

  override def getProperties(name: String): util.Enumeration[String] =
    addedHeaderOption(name) map (_.iterator.asJavaEnumeration) getOrElse super.getProperties(name)

  private def mustPrependHeader(name: String): Boolean =
    headersToPrependAsMap.keysIterator.exists(_.equalsIgnoreCase(name))

  private def addedHeaderOption(name: String): Option[NonEmptyList[String]] =
    headersToPrependAsMap
      .collectFirst { case (key, value) if key.equalsIgnoreCase(name) => value }

  private def addedHeaderSingleOption(name: String): Option[String] =
    addedHeaderOption(name).map(_.head)
}

trait RequestRemoveHeaders extends PortletRequestWrapper {

  // Override this if you have a `Set` of headers to remove
  def headersToRemoveAsSet: Set[String] = Set.empty

  override def getPropertyNames           : util.Enumeration[String] = (super.getPropertyNames.asScala filterNot mustRemoveHeader).asJavaEnumeration
  override def getProperty(name: String)  : String                   = if (mustRemoveHeader(name)) null else super.getProperty(name)
  override def getProperties(name: String): util.Enumeration[String] = if (mustRemoveHeader(name)) null else super.getProperties(name)

  private def mustRemoveHeader(name: String): Boolean =
    headersToRemoveAsSet.exists(_.equalsIgnoreCase(name))
}