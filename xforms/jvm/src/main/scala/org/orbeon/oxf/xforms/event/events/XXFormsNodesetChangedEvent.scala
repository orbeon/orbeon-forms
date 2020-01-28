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
package org.orbeon.oxf.xforms.event.events

import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents.XXFORMS_NODESET_CHANGED
import org.orbeon.oxf.xforms.event.{XFormsEventTarget, XFormsEvent}

class XXFormsNodesetChangedEvent(
  target: XFormsEventTarget,
  properties: PropertyGetter
) extends XFormsUIEvent(
  XXFORMS_NODESET_CHANGED,
  target.asInstanceOf[XFormsControl],
  properties,
  bubbles    = true,
  cancelable = false
) {

  def this(
    target                : XFormsControl,
    newIterations         : Seq[XFormsRepeatIterationControl],
    oldIterationPositions : Seq[Int],
    newIterationPositions : Seq[Int]
  ) = {
    this(target, EmptyGetter)
    this.newIterationsOpt         = Option(newIterations map (_.iterationIndex))
    this.oldIterationPositionsOpt = Option(oldIterationPositions)
    this.newIterationPositionsOpt = Option(newIterationPositions)
  }

  private var newIterationsOpt        : Option[Seq[Int]] = None
  private var oldIterationPositionsOpt: Option[Seq[Int]] = None
  private var newIterationPositionsOpt: Option[Seq[Int]] = None

  override def lazyProperties =
    super.lazyProperties orElse getters(this, XXFormsNodesetChangedEvent.Getters)
}

private object XXFormsNodesetChangedEvent {

  import XFormsEvent._

  val Getters = Map[String, XXFormsNodesetChangedEvent => Option[Any]](
    xxfName("new-positions")  -> (_.newIterationsOpt),
    xxfName("from-positions") -> (_.oldIterationPositionsOpt),
    xxfName("to-positions")   -> (_.newIterationPositionsOpt)
  )
}