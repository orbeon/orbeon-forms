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

import org.orbeon.oxf.util.PropertyContext
import java.util.{List ⇒ JList, ArrayList ⇒ JArrayList}
import scala.collection.JavaConverters._

trait XFormsContainerControl extends XFormsControl {

    private var children: JList[XFormsControl] = _

    private def hasChildren = (children ne null) && children.size > 0

    /**
     * Add a child control.
     */
    def addChild(XFormsControl: XFormsControl) {
        if (children eq null)
            children = new JArrayList[XFormsControl]
        children.add(XFormsControl)
    }

    /**
     * Get all the direct children controls.
     */
    def getChildren = children

    /**
     * Number of direct children control.
     */
    def getSize = if (children ne null) children.size else 0

    /**
     * Set all the direct children at once.
     */
    protected def setChildren(children: JList[XFormsControl]) {
        this.children = children
    }

    /**
     * Update this control's effective id and its descendants based on the parent's effective id.
     */
    override def updateEffectiveId() {
        super.updateEffectiveId()
        
        if (hasChildren)
            for (currentControl ← children.asScala)
                currentControl.updateEffectiveId()
    }

    /**
     * Notify container control that all its children have been added.
     */
    def childrenAdded() = ()

    override def getBackCopy: AnyRef = {
        // Clone this
        val cloned = super.getBackCopy.asInstanceOf[XFormsContainerControl]

        // Clone children if any
        if (hasChildren) {
            cloned.children = new JArrayList[XFormsControl](children.size)
            for (currentChildControl ← children.asScala) {
                val currentChildClone = currentChildControl.getBackCopy.asInstanceOf[XFormsControl]
                currentChildClone.setParent(cloned)
                cloned.children.add(currentChildClone)
            }
        }

        cloned
    }

    override def iterationRemoved() {
        if (hasChildren)
            for (currentControl ← children.asScala)
                currentControl.iterationRemoved()
    }

    override def setFocus(): Boolean = {
        // "4.3.7 The xforms-focus Event [...] Setting the focus to a group or switch container form control set the
        // focus to the first form control in the container that is able to accept focus"
        if (hasChildren)
            for (currentControl ← children.asScala)
                if (currentControl.setFocus())
                    return true

        false
    }

    override def equalsExternalRecurse(propertyContext: PropertyContext, other: XFormsControl): Boolean = {
        if ((other eq null) || ! other.isInstanceOf[XFormsNoSingleNodeContainerControl])
            return false

        if (this eq other)
            return true

        // Check children sizes
        val otherContainerControl = other.asInstanceOf[XFormsNoSingleNodeContainerControl]
        if (otherContainerControl.getSize != getSize)
            return false

        // Check this here as that might be faster than checking the children
        if (! super.equalsExternalRecurse(propertyContext, other))
            return false

        // Check children
        if (hasChildren) {
            val otherIterator = otherContainerControl.children.iterator
            for (control ← children.asScala) {
                val otherControl = otherIterator.next

                // Depth-first (not sure if better than breadth-first)
                if (! control.equalsExternalRecurse(propertyContext, otherControl))
                    return false
            }
        }

        true
    }
}