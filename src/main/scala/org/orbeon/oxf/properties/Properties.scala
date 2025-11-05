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
package org.orbeon.oxf.properties

import org.orbeon.dom.QName
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.util.CoreCrossPlatformSupport


object Properties {

  def initialize(propertiesURI: String): Unit = {
    ResourcesPropertyProvider.configure(propertiesURI)
    PropertyLoader.initialize()
  }

  // For backward compatibility
  val instance: Properties.type = this

  def getPropertySet: PropertySet =
    PropertyLoader.getPropertyStore(findRequest).globalPropertySet

  def getPropertySetOrThrow: PropertySet = {
    val ps = getPropertySet
    if (ps eq null)
      throw new OXFException("property set not found")
    ps
  }

  // Used by processors and `XFormsCrossPlatformSupport.createHTMLFragmentXmlReceiver()`
  def getPropertySet(processorName: QName): PropertySet =
    PropertyLoader.getPropertyStore(findRequest).processorPropertySet(processorName)

  private def findRequest: Option[ExternalContext.Request] =
    Option(CoreCrossPlatformSupport.externalContext).flatMap(ec => Option(ec.getRequest))
}
