/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.event.events

import org.orbeon.errorified.Exceptions
import org.orbeon.exception._
import org.orbeon.oxf.common.OrbeonLocationException.getRootLocationData
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget, XFormsEvents}
import org.orbeon.datatypes.ExtendedLocationData
import XFormsEvent._

class XXFormsActionErrorEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFormsEvents.XXFORMS_ACTION_ERROR, target, properties, bubbles = true, cancelable = false) {

  def this(target: XFormsEventTarget, throwable: Throwable) = {
    this(target, EmptyGetter)
    throwableOpt = Option(throwable)
  }

  private var throwableOpt: Option[Throwable] = None
  def throwable = throwableOpt.orNull
  private lazy val rootLocationOpt = throwableOpt flatMap getRootLocationData

  override def lazyProperties = getters(this, XXFormsActionErrorEvent.Getters)
}

private object XXFormsActionErrorEvent {

  private def elementString(x: ExtendedLocationData) =
    x.params collectFirst { case ("element", v) => v }

  val Getters = Map[String, XXFormsActionErrorEvent => Option[Any]](
    "element"   -> (e => e.rootLocationOpt collect { case x: ExtendedLocationData if elementString(x).isDefined => elementString(x).get }),
    "system-id" -> (e => e.rootLocationOpt flatMap (l => Option(l.file))),
    "line"      -> (e => e.rootLocationOpt flatMap (l => Option(l.line))),
    "column"    -> (e => e.rootLocationOpt flatMap (l => Option(l.col))),
    "message"   -> (e => e.throwableOpt map Exceptions.getRootThrowable flatMap (t => Option(t.getMessage))),
    "throwable" -> (e => e.throwableOpt map OrbeonFormatter.format)
  )
}