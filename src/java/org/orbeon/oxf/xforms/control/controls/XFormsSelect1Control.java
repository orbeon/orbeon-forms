/**
 *  Copyright (C) 2006 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.om.NodeInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents an xforms:select1 control.
 */
public class XFormsSelect1Control extends XFormsValueControl {

    public static final String TREE_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_TREE_APPEARANCE_QNAME);
    public static final String MENU_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_MENU_APPEARANCE_QNAME);
    public static final String AUTOCOMPLETE_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME);

    private String xxformsRefresh;
    private List items;

    public XFormsSelect1Control(XFormsContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);
        this.xxformsRefresh = element.attributeValue(XFormsConstants.XXFORMS_REFRESH_ITEMS_QNAME);
    }

    public boolean hasJavaScriptInitialization() {
        final String appearance = getAppearance();
        return appearance != null
                && (TREE_APPEARANCE.equals(appearance) || MENU_APPEARANCE.equals(appearance) || AUTOCOMPLETE_APPEARANCE.equals(appearance) || "compact".equals(appearance));
    }

    public void markDirty() {
        super.markDirty();
        // Force recalculation of items here
        items = null;
    }

    /**
     * Get itemset for a selection control given either directly or by id. If by id, the control MUST have a static
     * itemset.
     *
     * @param pipelineContext       current pipeline context
     * @param containingDocument    current containing document
     * @param control               control from which to obtain itemset (may be null if control has a static itemset)
     * @param prefixedId            prefixed id of control from which to obtain itemset (if control is null)
     * @return                      itemset or null if it is not possible to obtain it
     */
    public static List getItemset(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, XFormsSelect1Control control, String prefixedId) {
        if (control != null) {
            // Control is there so just ask it
            return control.getItemset(pipelineContext, true);
        } else {
            final boolean isStaticItemset; {
                final XFormsStaticState.ItemsInfo itemsInfo = containingDocument.getStaticState().getItemsInfo(prefixedId);
                isStaticItemset = itemsInfo != null && !itemsInfo.hasNonStaticItem();
            }
            if (isStaticItemset) {
                // No control but the itemset is static so obtain it
                return XFormsItemUtils.evaluateStaticItemsets(containingDocument, prefixedId);
            } else {
                // Not possible so return null
                return null;
            }
        }
    }

    /**
     * Get this control's itemset.
     *
     * @param pipelineContext   current pipeline context
     * @param setBinding        whether to set the current binding on the control first
     * @return                  itemset
     */
    public List getItemset(PipelineContext pipelineContext, boolean setBinding) {
        try {
            if ("false".equals(xxformsRefresh)) {
                // Items are not automatically refreshed and stored globally
                List items =  containingDocument.getControls().getConstantItems(getId());
                if (items == null) {
                    items = XFormsItemUtils.evaluateItemsets(pipelineContext, containingDocument, XFormsSelect1Control.this, setBinding);
                    containingDocument.getControls().setConstantItems(getId(), items);
                }
                return items;
            } else {
                // Items are stored in the control
                if (items == null) {
                    items = XFormsItemUtils.evaluateItemsets(pipelineContext, containingDocument, XFormsSelect1Control.this, setBinding);
                }
                return items;
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "evaluating itemset", getControlElement()));
        }
    }

    public boolean isOpenSelection() {
        return isOpenSelection(getControlElement());
    }

    public static boolean isOpenSelection(Element controlElement) {
        return "open".equals(controlElement.attributeValue("selection"));
    }

    protected void evaluateExternalValue(PipelineContext pipelineContext) {
        final String internalValue = getValue(pipelineContext);
        final String updatedValue;
        if (internalValue == null) {
            updatedValue = null;
        } else {
            if (!isOpenSelection() && XFormsProperties.isEncryptItemValues(containingDocument)) {
                // For closed selection, values sent to client must be encrypted
                updatedValue = XFormsItemUtils.encryptValue(pipelineContext, internalValue);
            } else {
                // For open selection, values sent to client are the internal values
                updatedValue = internalValue;
            }
        }
        super.setExternalValue(updatedValue);
    }

    public void storeExternalValue(PipelineContext pipelineContext, String value, String type, Element filesElement) {

        if (!(this instanceof XFormsSelectControl)) {// kind of a HACK due to the way our class hierarchy is setup
            // Handle xforms:select1-specific logic

            // Decrypt incoming value. With open selection, values are sent to the client.
            if (!isOpenSelection() && XFormsProperties.isEncryptItemValues(containingDocument))
                value = XFormsItemUtils.decryptValue(pipelineContext, value);

            // Current control value
            final String controlValue = getValue(pipelineContext);

            // Iterate over all the items
            final List items = getItemset(pipelineContext, true);
            final List selectEvents = new ArrayList();
            final List deselectEvents = new ArrayList();
            for (Iterator i = items.iterator(); i.hasNext();) {
                final XFormsItemUtils.Item currentItem = (XFormsItemUtils.Item) i.next();
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
                    selectEvents.add(new XFormsSelectEvent(this, currentItemValue));
                } else if (itemWasSelected && !itemIsSelected) {
                    deselectEvents.add(new XFormsDeselectEvent(this, currentItemValue));
                }
            }

            // Dispatch xforms-deselect events
            if (deselectEvents.size() > 0) {
                for (Iterator i = deselectEvents.iterator(); i.hasNext();) {
                    final XFormsEvent currentEvent = (XFormsEvent) i.next();
                    currentEvent.getTargetObject().getContainer(containingDocument).dispatchEvent(pipelineContext, currentEvent);
                }
            }
            // Select events must be sent after all xforms-deselect events
            final boolean hasSelectedItem = selectEvents.size() > 0;
            if (hasSelectedItem) {
                for (Iterator i = selectEvents.iterator(); i.hasNext();) {
                    final XFormsEvent currentEvent = (XFormsEvent) i.next();
                    currentEvent.getTargetObject().getContainer(containingDocument).dispatchEvent(pipelineContext, currentEvent);
                }
            }

            if (hasSelectedItem || isOpenSelection()) {
                // Only then do we store the external value. This ensures that if the value is NOT in the itemset AND
                // we are a closed selection then we do NOT store the value in instance.
                super.storeExternalValue(pipelineContext, value, type, filesElement);
            }
        } else {
            // Forward to superclass
            super.storeExternalValue(pipelineContext, value, type, filesElement);
        }
    }

    /**
     * Represents xforms:itemset information.
     *
     * TODO: Work in progress for dependencies?
     */
    public static class ItemsetInfo {
        private String id;
        private String label;
        private String value;

        private NodeInfo nodeInfo;

        public ItemsetInfo(String id, String label, String value, NodeInfo nodeInfo) {
            this.id = id;
            this.label = label;
            this.value = value;
            this.nodeInfo = nodeInfo;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

        public NodeInfo getNodeInfo() {
            return nodeInfo;
        }

        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof ItemsetInfo))
                return false;

            final ItemsetInfo other = (ItemsetInfo) obj;
            return id.equals(other.id) && label.equals(other.label) && value.equals(other.value);
        }
    }

}
