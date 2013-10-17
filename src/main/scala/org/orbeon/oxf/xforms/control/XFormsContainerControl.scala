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

import collection.JavaConverters._
import collection.mutable.{ArrayBuffer, Buffer}
import org.orbeon.oxf.xml.XMLReceiverHelper
import java.util.{List ⇒ JList}

trait XFormsContainerControl extends VisitableTrait {

    private var _children: Buffer[XFormsControl] = _ // allow null internally
    private def hasChildren = (_children ne null) && _children.nonEmpty

    // Get all the direct children controls (never null)
    def children: Seq[XFormsControl] = Option(_children) getOrElse Seq.empty
    def childrenJava: JList[XFormsControl] = children.asJava

    // Add a child control
    def addChild(control: XFormsControl) {
        if (_children eq null)
            _children = Buffer[XFormsControl]()
        _children += control
    }

    // Number of direct children control
    def getSize = children.size

    // Set all the direct children at once
    protected def setChildren(children: Buffer[XFormsControl]) = {
        require(children ne null)
        this._children = children
    }

    // Remove all children at once
    def clearChildren() =
        this._children = null

    // Update this control's effective id and its descendants based on the parent's effective id
    override def updateEffectiveId() {
        super.updateEffectiveId()
        
        if (hasChildren)
            for (currentControl ← _children)
                currentControl.updateEffectiveId()
    }

    override def getBackCopy: AnyRef = {
        // Clone this
        val cloned = super.getBackCopy.asInstanceOf[XFormsContainerControl]

        // Clone children if any
        if (hasChildren) {
            cloned._children = new ArrayBuffer[XFormsControl](_children.size)
            for (currentChildControl ← _children) {
                val currentChildClone = currentChildControl.getBackCopy.asInstanceOf[XFormsControl]
                currentChildClone.parent = null // cloned control doesn't need a parent
                cloned._children += currentChildClone
            }
        }

        cloned
    }

    override def iterationRemoved() {
        if (hasChildren)
            for (currentControl ← _children)
                currentControl.iterationRemoved()
    }

    override def setFocus(inputOnly: Boolean): Boolean = {
        // "4.3.7 The xforms-focus Event [...] Setting the focus to a group or switch container form control set the
        // focus to the first form control in the container that is able to accept focus"
        if (isRelevant && hasChildren)
            for (currentControl ← _children)
                if (currentControl.setFocus(inputOnly))
                    return true

        false
    }

    override def toXML(helper: XMLReceiverHelper, attributes: List[String])(content: ⇒ Unit) {
        super.toXML(helper, attributes) {
            children foreach (_.toXML(helper, List.empty)())
        }
    }
}