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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.analysis.controls.SelectionControl;
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
import org.xml.sax.helpers.AttributesImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents an xforms:select1 control.
 */
public class XFormsSelect1Control extends XFormsValueControl {

    public static final String FULL_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XFORMS_FULL_APPEARANCE_QNAME);
    public static final String TREE_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_TREE_APPEARANCE_QNAME);
    public static final String MENU_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_MENU_APPEARANCE_QNAME);

    // List of attributes to handle as AVTs for select1 with appearance="full"
    private static final QName[] EXTENSION_ATTRIBUTES_SELECT1_APPEARANCE_FULL = {
            XFormsConstants.XXFORMS_GROUP_QNAME
    };

    private ControlProperty<Itemset> itemsetProperty = new ControlProperty<Itemset>() {
        @Override
        protected void notifyCompute() {
            containingDocument.getXPathDependencies().notifyComputeItemset();
        }

        @Override
        protected void notifyOptimized() {
            containingDocument.getXPathDependencies().notifyOptimizeItemset();
        }

        @Override
        protected Itemset evaluateValue() {
            return XFormsItemUtils.evaluateItemset(XFormsSelect1Control.this);
        }

        @Override
        protected boolean requireUpdate() {
            return containingDocument.getXPathDependencies().requireItemsetUpdate(getPrefixedId());
        }
    };

    public XFormsSelect1Control(XBLContainer container, XFormsControl parent, Element element, String name, String id, Map<String, Element> state) {
        super(container, parent, element, name, id);
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        // Evaluate itemsets only if restoring dynamic state
        // NOTE: This doesn't sound like it is the right place to do this, does it?
        if (containingDocument.isRestoringDynamicState())
            getItemset();
    }

    public SelectionControl getSelectionControl() {
        return (SelectionControl) super.getElementAnalysis();
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
                && (TREE_APPEARANCE.equals(appearance) || MENU_APPEARANCE.equals(appearance) || "compact".equals(appearance));
    }

    @Override
    protected void markDirtyImpl(XPathDependencies xpathDependencies) {
        super.markDirtyImpl(xpathDependencies);

        if (itemsetProperty != null)
            itemsetProperty.handleMarkDirty();
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
     * @param containingDocument    current containing document
     * @param control               control from which to obtain itemset (may be null if control has a static itemset)
     * @param prefixedId            prefixed id of control from which to obtain itemset (if control is null)
     * @return                      itemset or null if it is not possible to obtain it
     */
    public static Itemset getInitialItemset(XFormsContainingDocument containingDocument,
                                            XFormsSelect1Control control, String prefixedId) {

        if (control != null && control.isRelevant()) {
            // Control is there and relevant so just ask it (this will include static itemsets evaluation as well)
            return control.getItemset();
        } else if (isStaticItemset(containingDocument, prefixedId)) {
            // Control is not there or is not relevant, so use static itemsets
            // NOTE: This way we output static itemsets during initialization as well, even for non-relevant controls
            return ((SelectionControl) containingDocument.getStaticOps().getControlAnalysisOption(prefixedId).get()).evaluateStaticItemset();
        } else {
            // Not possible so return null
            return null;
        }
    }

    /**
     * Get this control's itemset.
     *
     * @return                  itemset
     */
    public Itemset getItemset() {
        try {
            // Non-relevant control does not return an itemset
            if (!isRelevant())
                return null;

            if (isNorefresh()) {
                // Items are not automatically refreshed and stored globally
                // NOTE: Store them by prefixed id because the itemset might be different between XBL template instantiations
                Itemset constantItemset =  containingDocument.getControls().getConstantItems(getPrefixedId());
                if (constantItemset == null) {
                    constantItemset = XFormsItemUtils.evaluateItemset(XFormsSelect1Control.this);
                    containingDocument.getControls().setConstantItems(getPrefixedId(), constantItemset);
                }
                return constantItemset;
            } else {
                // Items are stored in the control
                return itemsetProperty.getValue();
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
        final SelectionControl analysis = containingDocument.getStaticOps().getSelect1Analysis(prefixedId);
        return analysis.hasStaticItemset();
    }

    public boolean isOpenSelection() {
        return getSelectionControl().isOpenSelection();
    }

    public boolean isEncryptItemValues() {
        return getSelectionControl().isEncryptValues();
    }

    public boolean isNorefresh() {
        return getSelectionControl().isNorefresh();
    }

    @Override
    protected void evaluateExternalValue() {
        final String internalValue = getValue();
        final String updatedValue;

        if (StringUtils.isEmpty(internalValue)) {
            // Keep null or ""
            // In the latter case, this is important for multiple selection, as the client expects a blank value to mean "nothing selected"
            updatedValue = internalValue;
        } else {
            // NOTE: We could in the future check that the value is in fact part of the itemset first, and send a blank value otherwise..
            if (isEncryptItemValues()) {
                // For closed selection, values sent to client must be encrypted
                updatedValue = XFormsItemUtils.encryptValue(internalValue);
            } else {
                // For open selection, values sent to client are the internal values
                updatedValue = internalValue;
            }
        }
        super.setExternalValue(updatedValue);
    }

    @Override
    public void storeExternalValue(String value, String type) {

        if (!(this instanceof XFormsSelectControl)) {// kind of a HACK due to the way our class hierarchy is setup
            // Handle xforms:select1-specific logic

            // Decrypt incoming value. With open selection, values are sent to the client.
            if (isEncryptItemValues()) {
                try {
                    value = XFormsItemUtils.decryptValue(value);
                } catch (IllegalArgumentException e) {
                    getIndentedLogger().logError("", "exception decrypting value", "control id", getEffectiveId(), "value", value);
                    throw e;
                }
            }

            // Current control value
            final String controlValue = getValue();

            // Iterate over all the items
            final Itemset itemset = getItemset();
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
                    currentEvent.getTargetObject().getXBLContainer(containingDocument).dispatchEvent(currentEvent);
                }
            }
            // Select events must be sent after all xforms-deselect events
            final boolean hasSelectedItem = selectEvents.size() > 0;
            if (hasSelectedItem) {
                for (XFormsEvent currentEvent: selectEvents) {
                    currentEvent.getTargetObject().getXBLContainer(containingDocument).dispatchEvent(currentEvent);
                }
            }

            if (hasSelectedItem || isOpenSelection()) {
                // Only then do we store the external value. This ensures that if the value is NOT in the itemset AND
                // we are a closed selection then we do NOT store the value in instance.
                super.storeExternalValue(value, type);
            }
        } else {
            // Forward to superclass
            super.storeExternalValue(value, type);
        }
    }

    @Override
    public Object getBackCopy() {
        final XFormsSelect1Control cloned = (XFormsSelect1Control) super.getBackCopy();

        // If we have an itemset, make sure the computed value is used as basis for comparison
        if (itemsetProperty != null)
            cloned.itemsetProperty = new ConstantControlProperty<Itemset>(itemsetProperty.getValue());

        return cloned;
    }

    @Override
    public boolean equalsExternal(XFormsControl other) {

        if (other == null || !(other instanceof XFormsSelect1Control))
            return false;

        if (this == other)
            return true;

        final XFormsSelect1Control otherSelect1Control = (XFormsSelect1Control) other;

        // Itemset comparison
        if (mustSendItemsetUpdate(otherSelect1Control))
            return false;

        return super.equalsExternal(other);
    }

    private boolean mustSendItemsetUpdate(XFormsSelect1Control otherSelect1Control) {
        if (getSelectionControl().hasStaticItemset()) {
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
            if (XFormsSingleNodeControl.isRelevant(otherSelect1Control) != isRelevant()) {
                // Relevance changed
                // Here we decide to send an update only if we become relevant, as the client will know that the
                // new state of the control is non-relevant and can handle the itemset on the client as it wants.
                return isRelevant();
            } else if (!XFormsSingleNodeControl.isRelevant(this)) {
                // We were and are non-relevant, no update
                return false;
            } else {
                // If the itemsets changed, then we need to send an update
                // NOTE: This also covers the case where the control was and is non-relevant
                return !Itemset.compareItemsets(otherSelect1Control.getItemset(), getItemset());
            }
        }
    }

    @Override
    public void outputAjaxDiff(ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {
        // Output regular diff
        super.outputAjaxDiff(ch, other, attributesImpl, isNewlyVisibleSubtree);

        // Output itemset diff
        if (mustSendItemsetUpdate((XFormsSelect1Control) other)) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "itemset", new String[]{"id", XFormsUtils.namespaceId(containingDocument, getEffectiveId())});
            {
                final Itemset itemset = getItemset();
                if (itemset != null) {
                    final String result = itemset.getJSONTreeInfo(null, false, getLocationData());
                    if (result.length() > 0)
                        ch.text(result);
                }
            }
            ch.endElement();
        }
    }

    @Override
    public boolean setFocus() {
        // Don't accept focus if we have the internal appearance
        return !XFormsGroupControl.INTERNAL_APPEARANCE.equals(getAppearance()) && super.setFocus();
    }

    @Override
    public boolean supportAjaxUpdates() {
        return !XFormsGroupControl.INTERNAL_APPEARANCE.equals(getAppearance());
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
}
