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
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.XFormsEvent

import scala.collection.mutable

trait XFormsContainerControl extends VisitableTrait {

  private var _children: mutable.Buffer[XFormsControl] = _ // allow null internally
  private def hasChildren = (_children ne null) && _children.nonEmpty

  // Get all the direct children controls (never null)
  def children: Seq[XFormsControl] = Option(_children) getOrElse Seq.empty

  // Add a child control
  def addChild(control: XFormsControl): Unit = {
    if (_children eq null)
      _children = mutable.Buffer[XFormsControl]()
    _children += control
  }

  // Number of direct children control
  def getSize: Int = children.size

  // Set all the direct children at once
  protected def setChildren(children: mutable.Buffer[XFormsControl]): Unit = {
    require(children ne null)
    this._children = children
  }

  // Remove all children at once
  def clearChildren(): Unit =
    this._children = null

  // Update this control's effective id and its descendants based on the parent's effective id
  override def updateEffectiveId(): Unit = {
    super.updateEffectiveId()

    if (hasChildren)
      for (currentControl <- _children)
        currentControl.updateEffectiveId()
  }

  override def getBackCopy(collector: ErrorEventCollector): AnyRef = {
    // Clone this
    val cloned = super.getBackCopy(collector).asInstanceOf[XFormsContainerControl]

    // Clone children if any
    if (hasChildren) {
      cloned._children = new mutable.ArrayBuffer[XFormsControl](_children.size)
      for (currentChildControl <- _children) {
        val currentChildClone = currentChildControl.getBackCopy(collector).asInstanceOf[XFormsControl]

        if (currentChildClone ne currentChildControl)
          currentChildClone.parent = null // cloned control doesn't need a parent
        cloned._children += currentChildClone
      }
    }

    cloned
  }

  override def iterationRemoved(): Unit = {
    if (hasChildren)
      for (currentControl <- _children)
        currentControl.iterationRemoved()
  }

  override def followDescendantsForFocus: Iterator[XFormsControl] =
    if (hasChildren)
      _children.iterator
    else
      Iterator.empty
}