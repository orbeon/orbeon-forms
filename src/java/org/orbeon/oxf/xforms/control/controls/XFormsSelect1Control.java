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
package org.orbeon.oxf.xforms.control.controls;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent;
import org.orbeon.oxf.xforms.itemset.Item;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xforms.itemset.XFormsItemUtils;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.helpers.AttributesImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an xforms:select1 control.
 */
public class XFormsSelect1Control extends XFormsValueControl {

    public static final String FULL_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XFORMS_FULL_APPEARANCE_QNAME);
    public static final String TREE_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_TREE_APPEARANCE_QNAME);
    public static final String MENU_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_MENU_APPEARANCE_QNAME);
    public static final String AUTOCOMPLETE_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME);

    public static final QName ENCRYPT_ITEM_VALUES = new QName("encrypt-item-values", XFormsConstants.XXFORMS_NAMESPACE);

    // List of attributes to handle as AVTs for select1 with appearance="full"
    private static final QName[] EXTENSION_ATTRIBUTES_SELECT1_APPEARANCE_FULL = {
            XFormsConstants.XXFORMS_GROUP_QNAME
    };

    private final boolean isNorefresh;
    private final boolean isOpenSelection;
    private final boolean xxformsEncryptItemValues;
    private Itemset itemset;

    public XFormsSelect1Control(XBLContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);
        // TODO: part of static state?
        this.isNorefresh = "false".equals(element.attributeValue(XFormsConstants.XXFORMS_REFRESH_ITEMS_QNAME));
        this.isOpenSelection = isOpenSelection(element);
        this.xxformsEncryptItemValues = isEncryptItemValues(containingDocument, element); 
    }

    @Override
    protected QName[] getExtensionAttributes() {
        if (!(this instanceof XFormsSelectControl) && isFullAppearance())
            return EXTENSION_ATTRIBUTES_SELECT1_APPEARANCE_FULL;
        else
            return super.getExtensionAttributes();
    }

    public String getGroupName() {
        // Return the custom group name if present, otherwise return the effective id
        final String customGroupName = getExtensionAttributeValue(XFormsConstants.XXFORMS_GROUP_QNAME);
        return (customGroupName != null) ? customGroupName : getEffectiveId();
    }

    @Override
    public boolean hasJavaScriptInitialization() {
        final String appearance = getAppearance();
        return appearance != null
                && (TREE_APPEARANCE.equals(appearance) || MENU_APPEARANCE.equals(appearance) || AUTOCOMPLETE_APPEARANCE.equals(appearance) || "compact".equals(appearance));
    }

    @Override
    public void markDirty() {
        super.markDirty();
        // Force recalculation of items here
        itemset = null;
    }

    /**
     * Convenience method to test for the "full" appearance.
     *
     * @return true iif appearance is "full"
     */
    public boolean isFullAppearance() {
        return FULL_APPEARANCE.equals(getAppearance());
    }

    /**
     * Get itemset for a selection control given either directly or by id. If the control is null or non-relevant,
     * lookup by id takes place and the control must have a static itemset or otherwise null is returned.
     *
     * @param pipelineContext       current pipeline context
     * @param containingDocument    current containing document
     * @param control               control from which to obtain itemset (may be null if control has a static itemset)
     * @param prefixedId            prefixed id of control from which to obtain itemset (if control is null)
     * @return                      itemset or null if it is not possible to obtain it
     */
    public static Itemset getInitialItemset(PipelineContext pipelineContext, XFormsContainingDocument containingDocument,
                                                            XFormsSelect1Control control, String prefixedId) {

        if (control != null && control.isRelevant()) {
            // Control is there and relevant so just ask it (this will include static itemsets evaluation as well)
            return control.getItemset(pipelineContext, true);
        } else if (isStaticItemset(containingDocument, prefixedId)) {
            // Control is not there or is not relevant, so use static itemsets
            // NOTE: This way we output static itemsets during initialization as well, even for non-relevant controls
            return XFormsItemUtils.evaluateStaticItemsets(containingDocument, prefixedId);
        } else {
            // Not possible so return null
            return null;
        }
    }

    /**
     * Get this control's itemset.
     *
     * @param propertyContext   current context
     * @param setBinding        whether to set the current binding on the control first
     * @return                  itemset
     */
    public Itemset getItemset(PropertyContext propertyContext, boolean setBinding) {
        try {
            // Non-relevant control does not return an itemset
            final org.orbeon.saxon.om.Item boundItem = getBoundItem();
            // TODO: this relevance logic duplicates what's in XFormsSingleNodeControl, which is no good; can we simply use isRelevant()?
            final boolean isRelevant = boundItem != null && (!(boundItem instanceof NodeInfo) || InstanceData.getInheritedRelevant((NodeInfo) boundItem));
            if (!isRelevant)
                return null;

            if (isNorefresh) {
                // Items are not automatically refreshed and stored globally
                // NOTE: Store them by prefixed id because the itemset might be different between XBL template instantiations
                Itemset constantItemset =  containingDocument.getControls().getConstantItems(getPrefixedId());
                if (constantItemset == null) {
                    constantItemset = XFormsItemUtils.evaluateItemset(propertyContext, XFormsSelect1Control.this, setBinding);
                    containingDocument.getControls().setConstantItems(getPrefixedId(), constantItemset);
                }
                return constantItemset;
            } else {
                // Items are stored in the control
                if (itemset == null) {
                    itemset = XFormsItemUtils.evaluateItemset(propertyContext, XFormsSelect1Control.this, setBinding);
                }
                return itemset;
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "evaluating itemset", getControlElement()));
        }
    }

    /**
     * Whether the given control has a static set of items.
     *
     * @param containingDocument    containing document
     * @param prefixedId            prefixed id
     * @return                      true iif control has a static set of items
     */
    public static boolean isStaticItemset(XFormsContainingDocument containingDocument, String prefixedId) {
        final XFormsStaticState.ItemsInfo itemsInfo = containingDocument.getStaticState().getItemsInfo(prefixedId);
        return itemsInfo != null && !itemsInfo.hasNonStaticItem();
    }

    /**
     * Whether the given control is a multiple-selection control.
     *
     * @param containingDocument    containing document
     * @param prefixedId            prefixed id
     * @return                      true iif control is a multiple-selection control
     */
    public static boolean isMultiple(XFormsContainingDocument containingDocument, String prefixedId) {
        final XFormsStaticState.ItemsInfo itemsInfo = containingDocument.getStaticState().getItemsInfo(prefixedId);
        return itemsInfo != null && itemsInfo.isMultiple();
    }

    /**
     * Whether this control has a static set of items.
     *
     * @return                      true iif control has a static set of items
     */
    public boolean isStaticItemset() {
        return isStaticItemset(containingDocument, getPrefixedId());
    }

    public boolean isOpenSelection() {
        return isOpenSelection;
    }

    public static boolean isOpenSelection(Element controlElement) {
        return "open".equals(controlElement.attributeValue("selection"));
    }

    public boolean isEncryptItemValues() {
        return xxformsEncryptItemValues;
    }

    public static boolean isEncryptItemValues(XFormsContainingDocument containingDocument, Element controlElement) {
        // Local property overrides global property
        final String isLocalEncryptItemValues = controlElement.attributeValue(ENCRYPT_ITEM_VALUES);
        return !isOpenSelection(controlElement) && (isLocalEncryptItemValues != null ?
                "true".equals(isLocalEncryptItemValues) : XFormsProperties.isEncryptItemValues(containingDocument));
    }

    @Override
    protected void evaluateExternalValue(PropertyContext propertyContext) {
        final String internalValue = getValue(propertyContext);
        final String updatedValue;

        if (StringUtils.isEmpty(internalValue)) {
            // Keep null or ""
            // In the latter case, this is important for multiple selection, as the client expects a blank value to mean "nothing selected"
            updatedValue = internalValue;
        } else {
            // NOTE: We could in the future check that the value is in fact part of the itemset first, and send a blank value otherwise..
            if (isEncryptItemValues()) {
                // For closed selection, values sent to client must be encrypted
                updatedValue = XFormsItemUtils.encryptValue(propertyContext, internalValue);
            } else {
                // For open selection, values sent to client are the internal values
                updatedValue = internalValue;
            }
        }
        super.setExternalValue(updatedValue);
    }

    @Override
    public void storeExternalValue(PropertyContext propertyContext, String value, String type, Element filesElement) {

        if (!(this instanceof XFormsSelectControl)) {// kind of a HACK due to the way our class hierarchy is setup
            // Handle xforms:select1-specific logic

            // Decrypt incoming value. With open selection, values are sent to the client.
            if (isEncryptItemValues())
                value = XFormsItemUtils.decryptValue(propertyContext, value);

            // Current control value
            final String controlValue = getValue(propertyContext);

            // Iterate over all the items
            final Itemset itemset = getItemset(propertyContext, true);
            final List<XFormsEvent> selectEvents = new ArrayList<XFormsEvent>();
            final List<XFormsEvent> deselectEvents = new ArrayList<XFormsEvent>();
            if (itemset != null) {
                for (Item currentItem: itemset.toList()) {
                    final String currentItemValue = currentItem.getValue();
                    final boolean itemWasSelected = controlValue.equals(currentItemValue);
                    final boolean itemIsSelected;
                    if (value.equals(currentItemValue)) {
                        // Value is currently selected in the UI
                        itemIsSelected = true;
                    } else {
                        // Value is currently NOT selected in the UI
                        itemIsSelected = false;
                    }

                    // Handle xforms-select / xforms-deselect
                    // TODO: Dispatch to itemset or item once we support doing that
                    if (!itemWasSelected && itemIsSelected) {
                        selectEvents.add(new XFormsSelectEvent(containingDocument, this, currentItemValue));
                    } else if (itemWasSelected && !itemIsSelected) {
                        deselectEvents.add(new XFormsDeselectEvent(containingDocument, this, currentItemValue));
                    }
                }
            }

            // Dispatch xforms-deselect events
            if (deselectEvents.size() > 0) {
                for (XFormsEvent currentEvent: deselectEvents) {
                    currentEvent.getTargetObject().getXBLContainer(containingDocument).dispatchEvent(propertyContext, currentEvent);
                }
            }
            // Select events must be sent after all xforms-deselect events
            final boolean hasSelectedItem = selectEvents.size() > 0;
            if (hasSelectedItem) {
                for (XFormsEvent currentEvent: selectEvents) {
                    currentEvent.getTargetObject().getXBLContainer(containingDocument).dispatchEvent(propertyContext, currentEvent);
                }
            }

            if (hasSelectedItem || isOpenSelection()) {
                // Only then do we store the external value. This ensures that if the value is NOT in the itemset AND
                // we are a closed selection then we do NOT store the value in instance.
                super.storeExternalValue(propertyContext, value, type, filesElement);
            }
        } else {
            // Forward to superclass
            super.storeExternalValue(propertyContext, value, type, filesElement);
        }
    }

    @Override
    public boolean equalsExternal(PropertyContext propertyContext, XFormsControl other) {

        if (other == null || !(other instanceof XFormsSelect1Control))
            return false;

        if (this == other)
            return true;

        final XFormsSelect1Control otherSelect1Control = (XFormsSelect1Control) other;

        // Itemset comparison
        if (mustSendItemsetUpdate(propertyContext, otherSelect1Control))
            return false;

        return super.equalsExternal(propertyContext, other);
    }

    private boolean mustSendItemsetUpdate(PropertyContext propertyContext, XFormsSelect1Control otherSelect1Control) {
        final XFormsStaticState.ItemsInfo itemsInfo = containingDocument.getStaticState().getItemsInfo(getPrefixedId());
        if (itemsInfo != null && !itemsInfo.hasNonStaticItem()) {
            // There is no need to send an update:
            //
            // 1. Items are static...
            // 2. ...and they have been outputted statically in the HTML page, directly or in repeat template
            return false;
        } else if (isStaticReadonly()) {
            // There is no need to send an update for static readonly controls
            return false;
        } else {
            // There is a possible change
            if (XFormsSingleNodeControl.isRelevant(otherSelect1Control) != XFormsSingleNodeControl.isRelevant(this)) {
                // If relevance changed, then we need to send an itemset update
                return true;
            } else {
                // If the itemsets changed, then we need to send an update
                // NOTE: This also covers the case where the control was and is non-relevant
                return !Itemset.compareItemsets(otherSelect1Control.getItemset(propertyContext, true), getItemset(propertyContext, true));
            }
        }
    }

    @Override
    public void outputAjaxDiff(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {
        // Output regular diff
        super.outputAjaxDiff(pipelineContext, ch, other, attributesImpl, isNewlyVisibleSubtree);

        // Output itemset diff
        if (mustSendItemsetUpdate(pipelineContext, (XFormsSelect1Control) other)) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "itemset", new String[]{"id", getEffectiveId()});
            {
                final Itemset itemset = getItemset(pipelineContext, true);
                if (itemset != null) {
                    final String result = itemset.getJSONTreeInfo(pipelineContext, null, false, null);// TODO: pass LocationData
                    if (result.length() > 0)
                        ch.text(result);
                }
            }
            ch.endElement();
        }
    }

    // Work in progress for in-bounds/out-of-bounds
//    protected void evaluateValue(PipelineContext pipelineContext) {
//        super.evaluateValue(pipelineContext);
//    }

// Work in progress for in-bounds/out-of-bounds
//    public boolean isInBounds(List<XFormsItemUtils.Item> items) {
//        return true;
////        final String value = getValue(null);
////        for (Iterator i = items.iterator(); i.hasNext();) {
////            final XFormsItemUtils.Item currentItem = (XFormsItemUtils.Item) i.next();
////            final String currentItemValue = currentItem.getValue();
////            if (value.equals(currentItemValue)) {
////                return true;
////            }
////        }
////        return false;
//    }

//    /**
//     * Represents xforms:itemset information.
//     *
//     * TODO: Work in progress for dependencies?
//     */
//    public static class ItemsetInfo {
//        private String id;
//        private String label;
//        private String value;
//
//        private NodeInfo nodeInfo;
//
//        public ItemsetInfo(String id, String label, String value, NodeInfo nodeInfo) {
//            this.id = id;
//            this.label = label;
//            this.value = value;
//            this.nodeInfo = nodeInfo;
//        }
//
//        public String getId() {
//            return id;
//        }
//
//        public String getLabel() {
//            return label;
//        }
//
//        public String getValue() {
//            return value;
//        }
//
//        public NodeInfo getNodeInfo() {
//            return nodeInfo;
//        }
//
//        public boolean equals(Object obj) {
//            if (obj == null || !(obj instanceof ItemsetInfo))
//                return false;
//
//            final ItemsetInfo other = (ItemsetInfo) obj;
//            return id.equals(other.id) && label.equals(other.label) && value.equals(other.value);
//        }
//    }
}
