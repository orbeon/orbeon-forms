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
import org.scalajs.dom.crypto.GlobalCrypto

import scala.concurrent.ExecutionContext
import scala.scalajs.js.typedarray.Uint8Array


object CoreCrossPlatformSupport extends CoreCrossPlatformSupportTrait {

  type FileItemType = Unit // TODO

  implicit def executionContext: ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

  def isPE: Boolean = true
  def isJsEnv: Boolean = true

  private val hexDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  def randomHexId: String = {
    val values = new Uint8Array(40 / 2)
    GlobalCrypto.crypto.getRandomValues(values)
    values.map(v => "" + hexDigits(v >> 4) + hexDigits(v & 0xf)).mkString
  }

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
