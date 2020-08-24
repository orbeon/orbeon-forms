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
package org.orbeon.oxf.xforms.event

import org.orbeon.xforms.xbl.Scope
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.oxf.xforms.XFormsObject
import org.orbeon.oxf.xforms.event.Dispatch.EventListener

import scala.collection.immutable

/**
 * XFormsEventTarget is implemented by classes that support dispatching of events.
 */
trait XFormsEventTarget extends XFormsObject {
  def scope: Scope
  def container: XBLContainer

  def getId: String
  def getPrefixedId: String
  def getEffectiveId: String

  def getLocationData: LocationData

  def parentEventObserver: XFormsEventTarget

  def performTargetAction(event: XFormsEvent): Unit
  def performDefaultAction(event: XFormsEvent): Unit

  def allowExternalEvent(eventName: String): Boolean

  def addListener(eventName: String, listener: EventListener)
  def removeListener(eventName: String, listener: Option[EventListener])
  def getListeners(eventName: String): immutable.Seq[EventListener]
}