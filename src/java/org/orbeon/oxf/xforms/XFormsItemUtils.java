/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.dom4j.Text;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.*;

/**
 * Utilities to deal with items and itemsets.
 */
public class XFormsItemUtils {

    /**
     * Return a list of items as a JSON tree.
     *
     * @param items         list of XFormsSelect1Control.Item
     * @return              String representing a JSON tree
     */
    public static String getJSONTreeInfo(PipelineContext pipelineContext, List items, LocationData locationData) {
        return getJSONTreeInfo(pipelineContext, items, null, false, locationData);
    }

    /**
     * Return a list of items as a JSON tree.
     *
     * @param items         list of XFormsSelect1Control.Item
     * @param controlValue  current value of the control (to determine selected item) or null
     * @param many          whether multiple selection is allowed (to determine selected item)
     * @return              String representing a JSON tree
     */
    public static String getJSONTreeInfo(final PipelineContext pipelineContext, List items, final String controlValue, final boolean many, LocationData locationData) {
        // Produce a JSON fragment with hierachical information
        if (items.size() > 0) {
            final FastStringBuffer sb = new FastStringBuffer(100);
            sb.append("[");
            visitItemsTree(null, items, locationData, new TreeListener() {
                public void startLevel(ContentHandler contentHandler, int level) throws SAXException {
                }

                public void endLevel(ContentHandler contentHandler, int level) throws SAXException {
                }

                public void startItem(ContentHandler contentHandler, Item item, boolean first) throws SAXException {
                    final String value = item.getValue();

                    if (!first)
                        sb.append(',');
                    sb.append("[");

                    sb.append('"');
                    sb.append(item.getExternalJSLabel());
                    sb.append("\",\"");
                    sb.append(item.getExternalJSValue(pipelineContext));
                    sb.append('\"');

                    if (controlValue != null) {
                        // We allow the value to be null when this method is used just to produce the structure of the tree without selection
                        sb.append(',');
                        sb.append(Boolean.toString((value != null) && isSelected(many, controlValue, value)));
                    }
                }

                public void endItem(ContentHandler contentHandler) throws SAXException {
                    sb.append("]");
                }
            });
            sb.append("]");

            return sb.toString();

        } else {
            // Safer to return an empty array rather than en empty string
            return "[]";
        }
    }

    public static String encryptValue(PipelineContext pipelineContext, String value) {
        return SecureUtils.encrypt(pipelineContext, XFormsProperties.getXFormsPassword(), value);
    }

    public static String decryptValue(PipelineContext pipelineContext, String value) {
        return SecureUtils.decryptAsString(pipelineContext, XFormsProperties.getXFormsPassword(), value);
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

    /**
     * Visit a list of items.
     *
     * @param contentHandler        optional ContentHandler object, or null if not used
     * @param items                 List of items to visit
     * @param locationData          LocationData in case of error, or null
     * @param listener              TreeListener to call back
     */
    public static void visitItemsTree(ContentHandler contentHandler, List items, LocationData locationData, TreeListener listener) {
        if (items != null && items.size() > 0) { // may be null when there is no item in the itemset

//            XFormsServer.logger.info("xxx logging items:");

            int currentLevel = 0;
            int startItemCount = 0;
            boolean first = true;
            try {
                for (Iterator j = items.iterator(); j.hasNext();) {
                    final Item currentItem = (Item) j.next();

                    final int newLevel = currentItem.getLevel();
//                    XFormsServer.logger.info(currentItem.toString());

                    if (newLevel < currentLevel) {
                        //  We are going down one or more levels
                        for (int i = currentLevel; i > newLevel; i--) {
                            listener.endItem(contentHandler);
                            startItemCount--;
                            if (startItemCount < 0) throw new ValidationException("Too many endItem() generated.", locationData);
                            listener.endLevel(contentHandler, i);
                        }
                        listener.endItem(contentHandler);
                        startItemCount--;
                        if (startItemCount < 0) throw new ValidationException("Too many endItem() generated.", locationData);
                    } else if (newLevel > currentLevel) {
                        // We are going up one or more levels
                        for (int i = currentLevel + 1; i <= newLevel; i++) {
                            listener.startLevel(contentHandler, i);
                        }
                    } else {
                        // Same level as previous item
                        listener.endItem(contentHandler);
                        startItemCount--;
                        if (startItemCount < 0) throw new ValidationException("Too many endItem() generated.", locationData);
                    }

                    startItemCount++;
                    listener.startItem(contentHandler, currentItem, first);
                    currentLevel = newLevel;
                    first = false;
                }

                // Make sure we go back down all levels
                for (int i = currentLevel; i > 0; i--) {
                    listener.endItem(contentHandler);
                    startItemCount--;
                    if (startItemCount < 0) throw new ValidationException("Too many endItem() generated.", locationData);
                    listener.endLevel(contentHandler, i);
                }
            } catch (SAXException e) {
                throw new ValidationException("Error while creating itemset tree", e, locationData);
            }
        }
    }

    /**
     * Evaluate the itemset for a given xforms:select or xforms:select1 control.
     *
     * @param pipelineContext       current pipeline context
     * @param containingDocument    current containing document
     * @param select1Control        control to evaluate
     * @param setBinding            whether this method must set the evaluation binding (false if it is already set)
     * @return                      List of Item
     */
    public static List evaluateItemsets(final PipelineContext pipelineContext, final XFormsContainingDocument containingDocument, final XFormsSelect1Control select1Control, boolean setBinding) {

        final List newItems = new ArrayList();
        final XFormsContextStack contextStack = containingDocument.getXFormsControls().getContextStack();

        // Set binding on this control if required
        if (setBinding)
            contextStack.setBinding(select1Control);

        // TODO: Work on dependencies
//        final List existingItems = containingDocument.getXFormsControls().getConstantItems(getOriginalId());
//        final boolean[] mayReuse = new boolean[] { existingItems != null };

        final boolean isOpenSelection = select1Control.isOpenSelection();

        Dom4jUtils.visitSubtree(select1Control.getControlElement(), new Dom4jUtils.VisitorListener() {

            private int hierarchyLevel = 0;

            public void startElement(Element element) {
                final String localname = element.getName();
                if ("item".equals(localname)) {
                    // xforms:item

//                    mayReuse[0] = false;

                    final Element labelElement = element.element(XFormsConstants.XFORMS_LABEL_QNAME);
                    if (labelElement == null)
                        throw new ValidationException("xforms:item must contain an xforms:label element.", select1Control.getLocationData());
                    final String label = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, labelElement, false, null);

                    final Element valueElement = element.element(XFormsConstants.XFORMS_VALUE_QNAME);
                    if (valueElement == null)
                        throw new ValidationException("xforms:item must contain an xforms:value element.", select1Control.getLocationData());
                    final String value = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, valueElement, false, null);

                    newItems.add(new Item(!isOpenSelection, element.attributes(), label != null ? label : "", value != null ? value : "", hierarchyLevel + 1));// TODO: must filter attributes on element.attributes()

                } else if ("itemset".equals(localname)) {
                    // xforms:itemset

                    final int itemsetLevel = hierarchyLevel;
                    contextStack.pushBinding(pipelineContext, element);
                    {
                        final XFormsContextStack.BindingContext currentBindingContext = contextStack.getCurrentBindingContext();

                        //if (model == null || model == currentBindingContext.getModel()) { // it is possible to filter on a particular model
                        final List currentNodeSet = currentBindingContext.getNodeset();
                        if (currentNodeSet != null) {
                            final Stack nodeStack = new Stack();
                            final int iterationCount = currentNodeSet.size();
                            for (int currentPosition = 1; currentPosition <= iterationCount; currentPosition++) {

                                // Push iteration
                                contextStack.pushIteration(currentPosition);
                                {
                                    final NodeInfo currentNodeInfo = (NodeInfo) currentNodeSet.get(currentPosition - 1);

                                    // NOTE: We support relevance of items as an extension to XForms.
                                    final boolean isRelevant = InstanceData.getInheritedRelevant(currentNodeInfo);

                                    // Handle children of xforms:itemset
                                    final String label;
                                    final Element valueCopyElement;
                                    if (isRelevant) {
                                        {
                                            final Element labelElement = element.element(XFormsConstants.XFORMS_LABEL_QNAME);
                                            if (labelElement == null)
                                                throw new ValidationException("xforms:itemset element must contain one xforms:label element.", select1Control.getLocationData());

                                            label = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, element.element(XFormsConstants.XFORMS_LABEL_QNAME), false, null);
                                        }


                                        {
                                            final Element valueElement = element.element(XFormsConstants.XFORMS_VALUE_QNAME);
                                            valueCopyElement = (element != null)
                                                    ? valueElement : element.element(XFormsConstants.XFORMS_COPY_QNAME);
                                        }
                                        if (valueCopyElement == null)
                                            throw new ValidationException("xforms:itemset element must contain one xforms:value or one xforms:copy element.", select1Control.getLocationData());
                                    } else {
                                        label = null;
                                        valueCopyElement = null;
                                    }

                                    // NOTE: For now, we calculate the position in the hierarchy even if the node is
                                    // non-relevant. Is this the right thing to do?
                                    final int newLevel = itemsetLevel + getNodeLevel(currentNodeInfo, nodeStack);
                                    if (hierarchyLevel - newLevel >= 0) {
                                        //  We are going down one or more levels
                                        for (int i = newLevel; i <= hierarchyLevel; i++) {
                                            nodeStack.pop();
                                        }
                                    }

                                    // Handle new item if relevant
                                    if (isRelevant) {
                                        if (valueCopyElement.getName().equals("value")) {
                                            // Handle xforms:value
                                            // TODO: This could be optimized for xforms:value/@ref|@value as we could get the expression from the cache only once
                                            final String value = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, element.element(XFormsConstants.XFORMS_VALUE_QNAME), false, null);
                                            newItems.add(new Item(!isOpenSelection, element.attributes(), label != null ? label : "", value, newLevel));// TODO: must filter attributes on element.attributes()
                                        } else {
                                            // TODO: handle xforms:copy
                                            throw new ValidationException("xforms:copy is not yet supported.", select1Control.getLocationData());
                                        }
                                    }

                                    nodeStack.push(currentNodeInfo);
                                    hierarchyLevel = newLevel;
                                }
                                contextStack.popBinding();
                            }
                        }
                    }
                    contextStack.popBinding();
                    hierarchyLevel = itemsetLevel; // restore to level of xforms:itemset

                } else if ("choices".equals(localname)) {
                    // xforms:choices

                    final Element labelElement = element.element(XFormsConstants.XFORMS_LABEL_QNAME);
                    if (labelElement != null) {
                        final String label = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, element.element(XFormsConstants.XFORMS_LABEL_QNAME), false, null);
                        hierarchyLevel++;
                        newItems.add(new Item(!isOpenSelection, element.attributes(), label, null, hierarchyLevel));// TODO: must filter attributes on element.attributes()
                    }
                }
            }

            public void endElement(Element element) {
                final String localname = element.getName();
                 if ("choices".equals(localname)) {
                    // xforms:choices

                    final Element labelElement = element.element(XFormsConstants.XFORMS_LABEL_QNAME);
                    if (labelElement != null) {
                        hierarchyLevel--;
                    }
                }
            }

            public void text(Text text) {
            }

            private int getNodeLevel(NodeInfo nodeInfo, Stack stack) {
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

            private boolean isAncestorNode(NodeInfo node, NodeInfo potentialAncestor) {
                NodeInfo parent = node.getParent();
                while (parent != null) {
                    if (parent.isSameNodeInfo(potentialAncestor))
                        return true;
                    parent = parent.getParent();
                }

                return false;
            }

        });
        return newItems;
    }

    /**
     * Call back interface for visitItemsTree()
     */
    public interface TreeListener {
        public void startLevel(ContentHandler contentHandler, int level) throws SAXException;
        public void endLevel(ContentHandler contentHandler, int level) throws SAXException;
        public void startItem(ContentHandler contentHandler, Item item, boolean first) throws SAXException;
        public void endItem(ContentHandler contentHandler) throws SAXException;
    }

    /**
     * Represents an item (xforms:item, xforms:choice, or item in itemset).
     */
    public static class Item {

        private boolean isEncryptValue; // whether this item is part of an open selection control
        private List attributesList;
        private String label;
        private String value;
        private int level;

        public Item(boolean isEncryptValue, List attributesList, String label, String value, int level) {
            this.isEncryptValue = isEncryptValue;
            this.attributesList = attributesList;
            this.label = label;
            this.value = value;
            this.level = level;
        }

        public boolean isEncryptValue() {
            return isEncryptValue;
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

        public String getExternalValue(PipelineContext pipelineContext) {
            return value == null ? "" : isEncryptValue ? XFormsItemUtils.encryptValue(pipelineContext, value) : value;
        }

        public String getExternalJSValue(PipelineContext pipelineContext) {
            return value == null ? "" : isEncryptValue ? XFormsItemUtils.encryptValue(pipelineContext, value) : escapeJavaScriptQuotes(value);
        }

        public String getExternalJSLabel() {
            return label == null? "" : escapeJavaScriptQuotes(label);
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

        public String toString() {

            final StringBuffer sb = new StringBuffer();

            sb.append(getLevel());
            sb.append(" ");
            for (int i = 0; i < getLevel(); i++)
                sb.append("  ");
            sb.append(getLabel());
            sb.append(" => ");
            sb.append(getValue());

            return sb.toString();
        }

        private static String escapeJavaScriptQuotes(String value) {
            return StringUtils.replace(value, "\"", "\\\"");
        }
    }
}
