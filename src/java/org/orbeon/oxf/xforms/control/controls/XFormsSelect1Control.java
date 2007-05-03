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
import org.dom4j.Text;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Represents an xforms:select1 control.
 */
public class XFormsSelect1Control extends XFormsValueControl {

    public static final String TREE_APPEARANCE = Dom4jUtils.qNameToexplodedQName(XFormsConstants.XXFORMS_TREE_APPEARANCE_QNAME);
    public static final String MENU_APPEARANCE = Dom4jUtils.qNameToexplodedQName(XFormsConstants.XXFORMS_MENU_APPEARANCE_QNAME);
    public static final String AUTOCOMPLETE_APPEARANCE = Dom4jUtils.qNameToexplodedQName(XFormsConstants.XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME);

    private boolean hasItemset;
    private boolean itemsetsEvaluated;
    private List itemsetInfos;

    public XFormsSelect1Control(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
    }

    public boolean hasJavaScriptInitialization() {
        final String appearance = getAppearance();
        return appearance != null
                && (TREE_APPEARANCE.equals(appearance) || MENU_APPEARANCE.equals(appearance) || AUTOCOMPLETE_APPEARANCE.equals(appearance) || "compact".equals(appearance));
    }

    public void markItemsetDirty() {
        itemsetsEvaluated = false;
    }

    private void evaluateItemsets(final PipelineContext pipelineContext) {

        hasItemset = false;
        itemsetInfos = new ArrayList();

        // Set binding on this control
        final XFormsControls xformsControls = containingDocument.getXFormsControls();
        xformsControls.setBinding(pipelineContext, this);

        Dom4jUtils.visitSubtree(getControlElement(), new Dom4jUtils.VisitorListener() {

            private int hierarchyLevel = 0;

            public void startElement(Element element) {
                final String localname = element.getName();
                if ("item".equals(localname)) {
                    // xforms:item

                    final String label = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, element.element(XFormsConstants.XFORMS_LABEL_QNAME), false);
                    final String value = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, element.element(XFormsConstants.XFORMS_VALUE_QNAME), false);

                    itemsetInfos.add(new Item(false, element.attributes(), label, value, hierarchyLevel + 1));// TODO: must filter attributes on element.attributes()

                } else if ("itemset".equals(localname)) {
                    // xforms:itemset

                    hasItemset = true;
                    final int itemsetLevel = hierarchyLevel;
                    xformsControls.pushBinding(pipelineContext, element);
                    {
                        final XFormsControls.BindingContext currentBindingContext = xformsControls.getCurrentBindingContext();

                        //if (model == null || model == currentBindingContext.getModel()) { // it is possible to filter on a particular model
                        final List currentNodeSet = currentBindingContext.getNodeset();
                        if (currentNodeSet != null) {
                            final Stack nodeStack = new Stack();
                            for (int currentPosition = 1; currentPosition <= currentNodeSet.size(); currentPosition++) {

                                // Push "artificial" binding with just current node in nodeset
                                xformsControls.getContextStack().push(new XFormsControls.BindingContext(currentBindingContext, currentBindingContext.getModel(), xformsControls.getCurrentNodeset(), currentPosition, null, true, null));
                                {
                                    // Handle children of xforms:itemset
                                    final String label;
                                    {
                                        final Element labelElement = element.element(XFormsConstants.XFORMS_LABEL_QNAME);
                                        if (labelElement == null)
                                            throw new ValidationException("xforms:itemset element must contain one xforms:label element.", getLocationData());

                                        label = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, element.element(XFormsConstants.XFORMS_LABEL_QNAME), false);
                                    }

                                    final Element valueCopyElement;
                                    {
                                        final Element valueElement = element.element(XFormsConstants.XFORMS_VALUE_QNAME);
                                        valueCopyElement = (element != null)
                                                ? valueElement : element.element(XFormsConstants.XFORMS_COPY_QNAME);
                                    }
                                    if (valueCopyElement == null)
                                        throw new ValidationException("xforms:itemset element must contain one xforms:value or one xforms:copy element.", getLocationData());


                                    final NodeInfo currentNodeInfo = (NodeInfo) currentNodeSet.get(currentPosition - 1);
                                    final int newLevel = itemsetLevel + getNodeLevel(currentNodeInfo, nodeStack);
                                    if (hierarchyLevel - newLevel >= 0) {
                                        //  We are going down one or more levels
                                        for (int i = newLevel; i <= hierarchyLevel; i++) {
                                            nodeStack.pop();
                                        }
                                    }

                                    if (valueCopyElement.getName().equals("value")) {
                                        // Handle xforms:value
                                        final String value = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, element.element(XFormsConstants.XFORMS_VALUE_QNAME), false);
                                        itemsetInfos.add(new Item(true, element.attributes(), label != null ? label : "", value, newLevel));// TODO: must filter attributes on element.attributes()
                                    } else {
                                        // TODO: handle xforms:copy
                                    }

                                    nodeStack.push(currentNodeInfo);
                                    hierarchyLevel = newLevel;
                                }
                                xformsControls.getContextStack().pop();
                            }
                        }
                    }
                    xformsControls.popBinding();

                } else if ("choices".equals(localname)) {
                    // xforms:choices

                    final Element labelElement = element.element(XFormsConstants.XFORMS_LABEL_QNAME);
                    if (labelElement != null) {
                        final String label = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, element.element(XFormsConstants.XFORMS_LABEL_QNAME), false);
                        hierarchyLevel++;
                        itemsetInfos.add(new Item(false, element.attributes(), label, null, hierarchyLevel));// TODO: must filter attributes on element.attributes()
                    }
                }
            }

            public void endElement(Element element) {
            }

            public void text(Text text) {
            }
        });
        itemsetsEvaluated = true;
    }

    public List getItemset(PipelineContext pipelineContext) {
        if (!itemsetsEvaluated) {
            evaluateItemsets(pipelineContext);
        }
        return itemsetInfos;
    }

    public boolean hasItemset() {
        return hasItemset;
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        super.performDefaultAction(pipelineContext, event);
    }

    /**
     * Represents an item (xforms:item, xforms:choice, or item in itemset).
     */
    public static class Item {

        private boolean isItemSet;
        private List attributesList;
        private String label;
        private String value;
        private int level;

        public Item(boolean isItemSet, List attributesList, String label, String value, int level) {
            this.isItemSet = isItemSet;
            this.attributesList = attributesList;
            this.label = label;
            this.value = value;
            this.level = level;
        }

        public boolean isItemSet() {
            return isItemSet;
        }

        public List getAttributesList() {
            return attributesList;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

        public int getLevel() {
            return level;
        }

        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof Item))
                return false;

            final Item other = (Item) obj;

            if (!((value == null && other.value == null) || (value != null && other.value != null && value.equals(other.value))))
                return false;

            if (!((label == null && other.label == null) || (label != null && other.label != null && label.equals(other.label))))
                return false;

            return true;
        }
    }

    public static int getNodeLevel(NodeInfo nodeInfo, Stack stack) {
        Collections.reverse(stack);
        int level = stack.size() + 1;
        for (Iterator i = stack.iterator(); i.hasNext(); level--) {
            final NodeInfo currentNode = (NodeInfo) i.next();
            if (isAncestorNode(nodeInfo, currentNode)) {
                Collections.reverse(stack);
                return level;
            }
        }
        Collections.reverse(stack);
        return level;
    }

    private static boolean isAncestorNode(NodeInfo node, NodeInfo potentialAncestor) {
        NodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent == potentialAncestor)
                return true;
            parent = parent.getParent();
        }

        return false;
    }

    /**
     * Represents xforms:itemset information.
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

    /**
     * Return a list of items as a JSON tree.
     *
     * @param items         list of XFormsSelect1Control.Item
     * @return              String representing a JSON tree
     */
    public static String getJSONTreeInfo(List items) {
        return getJSONTreeInfo(items, null, false);
    }

    /**
     * Return a list of items as a JSON tree.
     *
     * @param items         list of XFormsSelect1Control.Item
     * @param controlValue  current value of the control (to determine selected item) or null
     * @param many          whether multiple selection is allowed (to determine selected item)
     * @return              String representing a JSON tree
     */
    public static String getJSONTreeInfo(List items, String controlValue, boolean many) {
        // Produce a JSON fragment with hierachical information
        if (items.size() > 0) {
            final StringBuffer sb = new StringBuffer();

            sb.append("[");

            int level = 0;
            for (Iterator j = items.iterator(); j.hasNext();) {
                final XFormsSelect1Control.Item currentItem = (XFormsSelect1Control.Item) j.next();
                final String label = currentItem.getLabel();
                final String value = currentItem.getValue();

                final int newLevel = currentItem.getLevel();

                if (newLevel <= level) {
                    //  We are going down one or more levels
                    for (int i = newLevel; i <= level; i++) {
                        sb.append("]");
                    }
                    sb.append(",[");
                } else {
                    // We are going up one level
                    if (level > 0)
                        sb.append(",");

                    sb.append("[");
                }

                sb.append('"');
                sb.append(label);
                sb.append("\",\"");
                if (value != null)
                    sb.append(value);
                sb.append('\"');

                if (controlValue != null) {
                    // We allow the value to be null when this method is used just to produce the structure of the tree without selection
                    sb.append(',');
                    sb.append((value != null) && isSelected(many, controlValue, value));
                }

                level = newLevel;
            }

            // Close brackets
            for (int i = level; i >= 0; i--) {
                sb.append("]");
            }

            return sb.toString();
        } else {
            // Safer to return an empty array rather than en empty string
            return "[]";
        }
    }

    /**
     * Return whether a select control's value is selected given an item value.
     *
     * @param isMany        whether multiple selection is allowed
     * @param controlValue  current value of the control (to determine selected item) or null
     * @param itemValue     item value to check
     * @return              true is selected, false otherwise
     */
    public static boolean isSelected(boolean isMany, String controlValue, String itemValue) {
        boolean selected = false;
        if (isMany) {
            if ("".equals(controlValue)) {
                // Special case of empty string: check the item that has empty string if any
                if ("".equals(itemValue)) {
                    selected = true;
                }
            } else {
                // Case of multiple tokens
                for (final StringTokenizer st = new StringTokenizer(controlValue); st.hasMoreTokens();) {
                    final String token = st.nextToken();
                    if (token.equals(itemValue)) {
                        selected = true;
                        break;
                    }
                }
            }
        } else {
            selected = controlValue.equals(itemValue);
        }
        return selected;
    }
}
