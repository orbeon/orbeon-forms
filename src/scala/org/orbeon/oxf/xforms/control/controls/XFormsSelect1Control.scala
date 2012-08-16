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
package org.orbeon.oxf.xforms.control.controls

import org.apache.commons.lang.StringUtils
import org.dom4j.Element
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.analysis.XPathDependencies
import org.orbeon.oxf.xforms.analysis.controls.SelectionControlTrait
import org.orbeon.oxf.xforms.control.FocusableTrait
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.XFormsControl.{ControlProperty, ImmutableControlProperty}
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent
import org.orbeon.oxf.xforms.itemset.{Item, Itemset, XFormsItemUtils}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.xml.sax.helpers.AttributesImpl
import scala.collection.mutable

/**
 * Represents an xforms:select1 control.
 */
class XFormsSelect1Control(container: XBLContainer, parent: XFormsControl, element: Element, id: String)
        extends XFormsValueControl(container, parent, element, id) with FocusableTrait {

    import XFormsSelect1Control._

    override type Control = SelectionControlTrait

    // This is a var just for getBackCopy
    private var itemsetProperty: ControlProperty[Itemset] = new MutableItemsetProperty(this)

    def isEncryptValues  = staticControl.isEncryptValues
    def isFullAppearance = staticControl.appearances(XFORMS_FULL_APPEARANCE_QNAME)

    override def onCreate() {
        super.onCreate()
        // Evaluate itemsets only if restoring dynamic state
        // NOTE: This doesn't sound like it is the right place to do this, does it?
        if (containingDocument.isRestoringDynamicState)
            getItemset
    }

    override def getExtensionAttributes =
        if (! staticControl.isMultiple && isFullAppearance)
            ExtensionAttributesSelect1AppearanceFull
        else
            super.getExtensionAttributes

    // Return the custom group name if present, otherwise return the effective id
    def getGroupName: String =
        Option(getExtensionAttributeValue(XXFORMS_GROUP_QNAME)) getOrElse getEffectiveId

    override def getJavaScriptInitialization = {
        val hasInitialization = staticControl.appearances exists AppearancesWithInitialization
        if (hasInitialization) getCommonJavaScriptInitialization else null
    }

    override def markDirtyImpl(xpathDependencies: XPathDependencies) {
        super.markDirtyImpl(xpathDependencies)
        if (itemsetProperty ne null)
            itemsetProperty.handleMarkDirty()
    }

    // Get this control's itemset
    def getItemset: Itemset =
        try {
            // Non-relevant control does not return an itemset
            if (! isRelevant)
                return null

            if (staticControl.isNorefresh) {
                // Items are not automatically refreshed and stored globally
                // NOTE: Store them by prefixed id because the itemset might be different between XBL template instantiations
                var constantItemset = containingDocument.getControls.getConstantItems(getPrefixedId)
                if (constantItemset eq null) {
                    constantItemset = XFormsItemUtils.evaluateItemset(XFormsSelect1Control.this)
                    containingDocument.getControls.setConstantItems(getPrefixedId, constantItemset)
                }
                constantItemset
            } else {
                // Items are stored in the control
                itemsetProperty.value()
            }
        } catch {
            case e: Exception ⇒
                throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData, "evaluating itemset", element))
        }

    override def evaluateExternalValue(): Unit = {
        val internalValue = getValue
        val updatedValue =
            if (StringUtils.isEmpty(internalValue)) {
                // Keep null or ""
                // In the latter case, this is important for multiple selection, as the client expects a blank value to mean "nothing selected"
                internalValue
            } else if (isEncryptValues) {
                // For closed selection, values sent to client must be encrypted
                val itemset = getItemset
                if (itemset ne null)
                    // Find the position of the first matching value
                    itemset.allItemsIterator find (_.value == internalValue) map (_.position.toString) orNull
                else
                    // Null itemset probably means the control was non-relevant. This should be handled better: if the
                    // control is not relevant, it should simply not be evaluated.
                    null
            } else
                // Values sent to client are the internal values
                internalValue

        super.setExternalValue(updatedValue)
    }

    override def translateExternalValue(externalValue: String) = {

        val existingValue = getValue

        // Find what got selected/deselected
        val (selectEvents, deselectEvents) = gatherEvents(externalValue, existingValue)

        // Dispatch xforms-deselect events
        for (currentEvent ← deselectEvents)
            Dispatch.dispatchEvent(currentEvent)

        if (selectEvents.nonEmpty) {
            // Select events must be sent after all xforms-deselect events
            for (currentEvent ← selectEvents)
                Dispatch.dispatchEvent(currentEvent)

            // Only then do we store the external value. This ensures that if the value is NOT in the itemset AND
            // we are a closed selection then we do NOT store the value in instance.
            selectEvents(0).getItemValue
        } else
            existingValue
    }

    private def gatherEvents(newValue: String, existingValue: String) = {

        val matches: Item ⇒ Boolean =
            if (isEncryptValues)
                item ⇒ newValue == item.position.toString
            else
                item ⇒ newValue == item.value

        val selectEvents   = mutable.Buffer[XFormsSelectEvent]()
        val deselectEvents = mutable.Buffer[XFormsDeselectEvent]()

        for (currentItem ← getItemset.allItemsIterator) {
            val currentItemValue = currentItem.value

            val itemWasSelected = existingValue == currentItemValue
            val itemIsSelected  = matches(currentItem)

            // Handle xforms-select / xforms-deselect
            if (! itemWasSelected && itemIsSelected)
                selectEvents += new XFormsSelectEvent(containingDocument, this, currentItemValue)
            else if (itemWasSelected && ! itemIsSelected)
                deselectEvents += new XFormsDeselectEvent(containingDocument, this, currentItemValue)
        }

        (selectEvents, deselectEvents)
    }

    override def getBackCopy: AnyRef = {
        val cloned = super.getBackCopy.asInstanceOf[XFormsSelect1Control]
        // If we have an itemset, make sure the computed value is used as basis for comparison
        cloned.itemsetProperty = new ImmutableControlProperty(itemsetProperty.value())
        cloned
    }

    override def equalsExternal(other: XFormsControl) =
        other match {
            case other if this eq other ⇒ true
            case other: XFormsSelect1Control ⇒
                // Itemset comparison
                ! mustSendItemsetUpdate(other) &&
                super.equalsExternal(other)
            case _ ⇒ false
        }

    private def mustSendItemsetUpdate(otherSelect1Control: XFormsSelect1Control): Boolean = {
        if (staticControl.hasStaticItemset) {
            // There is no need to send an update:
            //
            // 1. Items are static...
            // 2. ...and they have been output statically in the HTML page, directly or in repeat template
            false
        } else if (isStaticReadonly) {
            // There is no need to send an update for static readonly controls
            false
        } else {
            // There is a possible change
            if (XFormsSingleNodeControl.isRelevant(otherSelect1Control) != isRelevant) {
                // Relevance changed
                // Here we decide to send an update only if we become relevant, as the client will know that the
                // new state of the control is non-relevant and can handle the itemset on the client as it wants.
                isRelevant
            } else if (! XFormsSingleNodeControl.isRelevant(this)) {
                // We were and are non-relevant, no update
                false
            } else {
                // If the itemsets changed, then we need to send an update
                // NOTE: This also covers the case where the control was and is non-relevant
                otherSelect1Control.getItemset != getItemset
            }
        }
    }

    override def outputAjaxDiff(ch: ContentHandlerHelper, other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean): Unit = {

        // Output regular diff
        super.outputAjaxDiff(ch, other, attributesImpl, isNewlyVisibleSubtree)

        // Output itemset diff
        if (mustSendItemsetUpdate(other.asInstanceOf[XFormsSelect1Control])) {
            ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "itemset", Array("id", XFormsUtils.namespaceId(containingDocument, getEffectiveId)))

            val itemset = getItemset
            if (itemset ne null) {
                val result = itemset.getJSONTreeInfo(null, getLocationData)
                if (result.nonEmpty)
                    ch.text(result)
            }

            ch.endElement()
        }
    }

    // Don't accept focus if we have the internal appearance
    override def setFocus() =
        ! staticControl.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME) && super.setFocus()

    override def supportAjaxUpdates =
        ! staticControl.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME)
}

object XFormsSelect1Control {

    private val ExtensionAttributesSelect1AppearanceFull = Array(XXFORMS_GROUP_QNAME)

    private val AppearancesWithInitialization =
        Set(XXFORMS_TREE_APPEARANCE_QNAME, XXFORMS_MENU_APPEARANCE_QNAME, XFORMS_COMPACT_APPEARANCE_QNAME)

    // Get itemset for a selection control given either directly or by id. If the control is null or non-relevant,
    // lookup by id takes place and the control must have a static itemset or otherwise null is returned.
    def getInitialItemset(containingDocument: XFormsContainingDocument, control: XFormsSelect1Control, prefixedId: String): Itemset =
        if ((control ne null) && control.isRelevant) {
            // Control is there and relevant so just ask it (this will include static itemsets evaluation as well)
            control.getItemset
        } else if (isStaticItemset(containingDocument, prefixedId)) {
            // Control is not there or is not relevant, so use static itemsets
            // NOTE: This way we output static itemsets during initialization as well, even for non-relevant controls
            containingDocument.getStaticOps.getSelect1Analysis(prefixedId).evaluateStaticItemset()
        } else {
            // Not possible so return null
            null
        }

    // Whether the given control has a static set of items.
    private def isStaticItemset(containingDocument: XFormsContainingDocument, prefixedId: String): Boolean = {
        val analysis = containingDocument.getStaticOps.getSelect1Analysis(prefixedId)
        analysis.hasStaticItemset
    }
}