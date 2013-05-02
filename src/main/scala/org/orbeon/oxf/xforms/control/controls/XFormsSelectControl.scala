/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls

import org.dom4j.Element
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.analysis.XPathDependencies
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent
import org.orbeon.oxf.xforms.itemset.Item
import org.orbeon.oxf.xforms.xbl.XBLContainer
import collection.mutable
import collection.{Set ⇒ CSet}

/**
 * Represents an xf:select control.
 *
 * xf:select represents items as a list of space-separated tokens.
 */
class XFormsSelectControl(container: XBLContainer, parent: XFormsControl, element: Element, id: String)
        extends XFormsSelect1Control(container, parent, element, id) {

    import XFormsSelectControl._

    /**
     * Set an external value. This consists of a list of space-separated tokens.
     *
     * - Itemset values which are in the list of tokens are merged with the bound control's value.
     * - Itemset values which are not in the list of tokens are removed from the bound control's value.
     */
    override def translateExternalValue(externalValue: String) = {

        val existingValues = valueAsLinkedSet(getValue)
        val itemsetValues  = mutable.LinkedHashSet(getItemset.allItemsIterator map (_.value) toList: _*)

        val incomingValuesFiltered = {
            val newUIValues = valueAsSet(externalValue)

            val matches: Item ⇒ Boolean =
                if (isEncryptValues)
                    item ⇒ newUIValues(item.position.toString)
                else
                    item ⇒ newUIValues(item.value)

            mutable.LinkedHashSet(getItemset.allItemsIterator filter matches map (_.value) toList: _*)
        }

        val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
            updateSelection(existingValues, itemsetValues, incomingValuesFiltered)

        // Dispatch xforms-deselect events
        for (value ← newlySelectedValues)
            Dispatch.dispatchEvent(new XFormsSelectEvent(this, value))

        // Select events must be sent after all xforms-deselect events
        for (value ← newlyDeselectedValues)
            Dispatch.dispatchEvent(new XFormsDeselectEvent(this, value))

        newInstanceValue mkString " "
    }

    override def markDirtyImpl(): Unit = {

        // Default implementation
        super.markDirtyImpl()
        if (! isExternalValueDirty && containingDocument.getXPathDependencies.requireItemsetUpdate(getPrefixedId)) {
            // If the itemset has changed but the value has not changed, the external value might still need to be
            // re-evaluated.
            markExternalValueDirty()
        }
    }

    override def evaluateExternalValue(): Unit = {

        // If the control is relevant, its internal value and itemset must be defined
        val internalValue = getValue   ensuring (_ ne null)
        val itemset       = getItemset ensuring (_ ne null)

        val updatedValue =
            if (internalValue == "") {
                // This means that nothing is selected
                internalValue
            } else {
                // Values in the itemset
                val instanceValues = valueAsSet(internalValue)

                // All itemset external values for which the value exists in the instance
                val intersection =
                    for {
                        item ← itemset.allItemsIterator
                        if instanceValues(item.value)
                    } yield
                        item.externalValue

                // NOTE: In encoded mode, external values are guaranteed to be distinct, but in non-encoded mode,
                // there might be duplicates.
                intersection mkString " "
            }

        setExternalValue(updatedValue)
    }
}

object XFormsSelectControl {
    // Compute a new item selection
    def updateSelection(existingValues: CSet[String], itemsetValues: CSet[String], incomingValuesFiltered: CSet[String]) = {

        val newlySelectedValues   = incomingValuesFiltered -- existingValues
        val newlyDeselectedValues = (itemsetValues -- incomingValuesFiltered) intersect existingValues

        val newInstanceValue = existingValues ++ newlySelectedValues -- newlyDeselectedValues

        (newlySelectedValues, newlyDeselectedValues, newInstanceValue)
    }

    private def valueAsLinkedSet(s: String) = nonEmptyOrNone(s) match {
        case Some(list) ⇒ mutable.LinkedHashSet(list split """\s+""": _*)
        case None ⇒ Set[String]()
    }

    private def valueAsSet(s: String) = nonEmptyOrNone(s) match {
        case Some(list) ⇒ list split """\s+""" toSet
        case None ⇒ Set[String]()
    }
}