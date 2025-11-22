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

import cats.effect.unsafe.IORuntime
import org.orbeon.dom.QName
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.properties.{PropertySet, PropertyStore}

import scala.concurrent.ExecutionContext


trait CoreCrossPlatformSupportTrait {

  type FileItemType

  implicit def executionContext: ExecutionContext
  implicit def runtime: IORuntime

  def logger: org.log4s.Logger
  def isPE: Boolean
  def isJsEnv: Boolean
  def randomHexId: String
  def getApplicationResourceVersion: Option[String]
  def propertyStore: PropertyStore

  // TODO: Update callers to use `propertyStore` directly.
  def properties: PropertySet = propertyStore.globalPropertySet
  def getPropertySet(processorName: QName): PropertySet = propertyStore.processorPropertySet(processorName)

  def requestOpt: Option[Request] =
    Option(externalContext).flatMap(ec => Option(ec.getRequest))

  protected val externalContextDyn  = new DynamicVariable[ExternalContext](initial = None, isInheritable = false)

  def externalContext: ExternalContext =
    externalContextDyn.value.orNull //.getOrElse(throw new IllegalStateException("missing ExternalContext"))

  def withExternalContext[T](ec: ExternalContext)(body: => T): T =
    externalContextDyn.withValue(ec) {
      body
    }
}
