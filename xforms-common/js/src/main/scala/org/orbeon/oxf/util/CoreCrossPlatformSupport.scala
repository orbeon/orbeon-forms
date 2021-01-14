/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.orbeon.dom.QName
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.properties.PropertySet


object CoreCrossPlatformSupport extends CoreCrossPlatformSupportTrait {

  type FileItemType = Unit // TODO

  def isPE: Boolean = true
  def isJsEnv: Boolean = true

  private val hexDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  // This is probably not as good as the server side code based on `SecureRandom`
  def randomHexId: String =
    0 until 40 map (_ => hexDigits((Math.random() * 16).floor.toInt)) mkString

  def getApplicationResourceVersion: Option[String] = None // TODO: CHECK

  // Global and updated during deserialization
  // Q: Multiple forms will update this. Are we ok with this?
  var properties: PropertySet = PropertySet.empty

  def getPropertySet(processorName: QName): PropertySet = PropertySet.empty

  private val externalContextDyn  = new DynamicVariable[ExternalContext]

  def withExternalContext[T](ec: ExternalContext)(body: => T): T = {
    externalContextDyn.withValue(ec) {
      body
    }
  }

  def externalContext: ExternalContext =
    externalContextDyn.value.getOrElse(throw new IllegalStateException("missing ExternalContext"))
}
