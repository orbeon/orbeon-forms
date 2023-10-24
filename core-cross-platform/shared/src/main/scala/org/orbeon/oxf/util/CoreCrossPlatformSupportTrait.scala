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

import scala.concurrent.ExecutionContext


trait CoreCrossPlatformSupportTrait {

  type FileItemType

  implicit def executionContext: ExecutionContext

  def isPE: Boolean
  def isJsEnv: Boolean
  def randomHexId: String
  def getApplicationResourceVersion: Option[String]
  def properties: PropertySet
  def getPropertySet(processorName: QName): PropertySet
  def externalContext: ExternalContext
}
