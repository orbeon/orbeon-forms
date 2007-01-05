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
    private List itemsetInfos;

    public XFormsSelect1Control(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
    }

    public boolean hasJavaScriptInitialization() {
        final String appearance = getAppearance();
        return appearance != null
                && (TREE_APPEARANCE.equals(appearance) || MENU_APPEARANCE.equals(appearance) || AUTOCOMPLETE_APPEARANCE.equals(appearance) || "compact".equals(appearance));
    }

    public void evaluateItemsets(final PipelineContext pipelineContext) {
        // When entering this method, binding must be on control

        hasItemset = false;
        itemsetInfos = new ArrayList();
        final XFormsControls xformsControls = containingDocument.getXFormsControls();

        Dom4jUtils.visitSubtree(getControlElement(), new Dom4jUtils.VisitorListener() {

            private int hierarchyLevel = 0;

            public void startElement(Element element) {
                final String localname = element.getName();
                if ("item".equals(localname)) {
                    // xforms:item

                    final String label = getChildElementValue(pipelineContext, element.element(XFormsConstants.XFORMS_LABEL_QNAME), false);
                    final String value = getChildElementValue(pipelineContext, element.element(XFormsConstants.XFORMS_VALUE_QNAME), false);

                    itemsetInfos.add(new Item(false, element.attributes(), label, value, hierarchyLevel + 1));// TODO: must filter attributes on element.attributes()

                } else if ("itemset".equals(localname)) {
                    // xforms:itemset

                    hasItemset = true;
                    xformsControls.pushBinding(pipelineContext, element); // when entering this method, binding must be on control
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

                                        label = getChildElementValue(pipelineContext, element.element(XFormsConstants.XFORMS_LABEL_QNAME), false);
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
                                    final int newLevel = getNodeLevel(currentNodeInfo, nodeStack);
                                    if (hierarchyLevel - newLevel >= 0) {
                                        //  We are going down one or more levels
                                        for (int i = newLevel; i <= hierarchyLevel; i++) {
                                            nodeStack.pop();
                                        }
                                    }

                                    if (valueCopyElement.getName().equals("value")) {
                                        // Handle xforms:value
                                        final String value = getChildElementValue(pipelineContext, element.element(XFormsConstants.XFORMS_VALUE_QNAME), false);
                                        if (value != null)
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
                        final String label = getChildElementValue(pipelineContext, element.element(XFormsConstants.XFORMS_LABEL_QNAME), false);
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
    }

    public List getItemset() {
        return itemsetInfos;
    }

    public boolean hasItemset() {
        return hasItemset;
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        super.performDefaultAction(pipelineContext, event);
    }

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
}
