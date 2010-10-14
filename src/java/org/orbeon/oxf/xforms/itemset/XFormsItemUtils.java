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
package org.orbeon.oxf.xforms.itemset;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.dom4j.Text;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.controls.SelectionControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xforms.control.controls.XFormsSelectControl;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Utilities to deal with items and itemsets.
 */
public class XFormsItemUtils {

    private static final String[] ATTRIBUTES_TO_PROPAGATE = { "class", "style" };

    public static String encryptValue(PropertyContext propertyContext, String value) {
        return SecureUtils.encrypt(propertyContext, XFormsProperties.getXFormsPassword(), value);
    }

    public static String decryptValue(PropertyContext propertyContext, String value) {
        return SecureUtils.decryptAsString(propertyContext, XFormsProperties.getXFormsPassword(), value);
    }

    /**
     * Return whether a select control's value is selected given an item value.
     *
     * @param isMultiple    whether multiple selection is allowed
     * @param controlValue  current value of the control (to determine selected item) or null
     * @param itemValue     item value to check
     * @return              true is selected, false otherwise
     */
    public static boolean isSelected(boolean isMultiple, String controlValue, String itemValue) {
        boolean selected = false;
        if (controlValue != null) {
            if (isMultiple) {
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
        }
        return selected;
    }

    /**
     * Evaluate the itemset for a given xforms:select or xforms:select1 control.
     *
     * @param propertyContext       current context
     * @param select1Control        control to evaluate
     * @param setBinding            whether this method must set the evaluation binding (false if it is already set)
     * @return                      Itemset
     */
    public static Itemset evaluateItemset(final PropertyContext propertyContext, final XFormsSelect1Control select1Control, boolean setBinding) {

        final boolean isMultiple = select1Control instanceof XFormsSelectControl;
        final XBLContainer container = select1Control.getXBLContainer();

        // Optimize static itemsets
        {
            final boolean isStaticItemset; {
            final SelectionControl analysis = container.getContainingDocument().getStaticState().getSelect1Analysis(select1Control.getPrefixedId());
                isStaticItemset = analysis != null && !analysis.hasNonStaticItem();
            }

            if (isStaticItemset)
                return evaluateStaticItemsets(container.getContainingDocument(), select1Control.getPrefixedId());
        }

        final Itemset result = new Itemset();

        // Set binding on this control if required
        final XFormsContextStack contextStack = container.getContextStack();
        if (setBinding)
            contextStack.setBinding(select1Control);

        // TODO: Work on dependencies
//        final List existingItems = containingDocument.getXFormsControls().getConstantItems(getOriginalId());
//        final boolean[] mayReuse = new boolean[] { existingItems != null };

        final boolean isEncryptItemValues = select1Control.isEncryptItemValues();
        Dom4jUtils.visitSubtree(select1Control.getControlElement(), new Dom4jUtils.VisitorListener() {

            private ItemContainer currentContainer = result;

            private String getElementEffectiveId(Element element) {
                return XFormsUtils.getRelatedEffectiveId(select1Control.getEffectiveId(), element.attributeValue(XFormsConstants.ID_QNAME));
            }

            public void startElement(Element element) {
                final String localname = element.getName();
                if (XFormsConstants.ITEM_QNAME.getName().equals(localname)) {
                    // xforms:item

//                    mayReuse[0] = false;
                    final String label = getLabelValue(element.element(XFormsConstants.LABEL_QNAME));
                    final String value = getValueValue(element.element(XFormsConstants.XFORMS_VALUE_QNAME));

                    final Map<String, String> attributes = getAttributes(element);
                    currentContainer.addChildItem(new Item(isMultiple, isEncryptItemValues, attributes, StringUtils.defaultString(label), StringUtils.defaultString(value)));

                } else if (XFormsConstants.ITEMSET_QNAME.getName().equals(localname)) {
                    // xforms:itemset
                    contextStack.pushBinding(propertyContext, element, getElementEffectiveId(element), select1Control.getChildElementScope(element));
                    {
                        final XFormsContextStack.BindingContext currentBindingContext = contextStack.getCurrentBindingContext();

                        //if (model == null || model == currentBindingContext.getModel()) { // it is possible to filter on a particular model
                        final List<org.orbeon.saxon.om.Item> currentNodeSet = currentBindingContext.getNodeset();
                        if (currentNodeSet != null) {

                            // Node stack tracks the relative position of the current node wrt ancestor nodes
                            final Stack<org.orbeon.saxon.om.Item> nodeStack = new Stack<org.orbeon.saxon.om.Item>();
                            int currentLevel = 0;

                            final int iterationCount = currentNodeSet.size();
                            for (int currentPosition = 1; currentPosition <= iterationCount; currentPosition++) {

                                // Push iteration
                                contextStack.pushIteration(currentPosition);
                                {
                                    final org.orbeon.saxon.om.Item currentNodeInfo = currentNodeSet.get(currentPosition - 1);

                                    // Handle children of xforms:itemset

                                    // We support relevance of items as an extension to XForms

                                    // NOTE: If a node is non-relevant, all its descendants will be non-relevant as
                                    // well. If a node is non-relevant, it should be as if it had not even been part of
                                    // the nodeset.
                                    final boolean isRelevant = (!(currentNodeInfo instanceof NodeInfo)) || InstanceData.getInheritedRelevant((NodeInfo) currentNodeInfo);
                                    if (isRelevant) {
                                        final String label = getLabelValue(element.element(XFormsConstants.LABEL_QNAME));
                                        final Element valueCopyElement;
                                        {
                                            final Element valueElement = element.element(XFormsConstants.XFORMS_VALUE_QNAME);
                                            valueCopyElement = (valueElement != null)
                                                    ? valueElement : element.element(XFormsConstants.COPY_QNAME);
                                        }
                                        if (valueCopyElement == null)
                                            throw new ValidationException("xforms:itemset element must contain one xforms:value or one xforms:copy element.", select1Control.getLocationData());

                                        // Update stack and containers
                                        if (nodeStack.size() != 0) {
                                            final int newLevel = getItemLevel(currentNodeInfo, nodeStack);
                                            if (newLevel == currentLevel) {
                                                //  We are staying at the same level, pop old item
                                                nodeStack.pop();
                                            } else if (newLevel < currentLevel) {
                                                //  We are going down one or more levels
                                                nodeStack.pop();
                                                for (int i = newLevel; i < currentLevel; i++) {
                                                    nodeStack.pop();
                                                    currentContainer = currentContainer.getParent();
                                                }
                                            } else if (newLevel > currentLevel) {
                                                // Going up one level, set new container as last added child
                                                final List<Item> children = currentContainer.getChildren();
                                                currentContainer = children.get(children.size() - 1);
                                            }
                                            currentLevel = newLevel;
                                        }

                                        // Handle new item
                                        if (valueCopyElement.getName().equals(XFormsConstants.XFORMS_VALUE_QNAME.getName())) {
                                            // Handle xforms:value
                                            // TODO: This could be optimized for xforms:value/@ref|@value as we could get the expression from the cache only once
                                            final String value = getValueValue(valueCopyElement);

                                            // NOTE: At this point, if the value is null, we should consider the item
                                            // non-relevant if it is a leaf item. But we don't yet know if this item is
                                            // a leaf item, so we prune such non-relevant items later.

                                            final Map<String, String> attributes = getAttributes(element);
                                            currentContainer.addChildItem(new Item(isMultiple, isEncryptItemValues, attributes, StringUtils.defaultString(label), value));
                                        } else {
                                            // TODO: handle xforms:copy
                                            throw new ValidationException("xforms:copy is not yet supported.", select1Control.getLocationData());
                                        }

                                        // Always push the last item to the stack
                                        nodeStack.push(currentNodeInfo);

                                    }
                                }
                                contextStack.popBinding();
                            }
                        }
                    }
                    contextStack.popBinding();

                } else if (XFormsConstants.CHOICES_QNAME.getName().equals(localname)) {
                    // xforms:choices

                    final Element labelElement = element.element(XFormsConstants.LABEL_QNAME);
                    if (labelElement != null) {
                        final String label = getLabelValue(labelElement);

                        // NOTE: returned label can be null in some cases

                        final Map<String, String> attributes = getAttributes(element);
                        final Item newContainer = new Item(isMultiple, isEncryptItemValues, attributes, label, null);
                        currentContainer.addChildItem(newContainer);
                        currentContainer = newContainer;
                    }
                }
            }

            private String getValueValue(Element valueElement) {
                if (valueElement == null)
                    throw new ValidationException("xforms:item or xforms:itemset must contain an xforms:value element.", select1Control.getLocationData());
                final XBLBindings.Scope elementScope = select1Control.getChildElementScope(valueElement);
                final String elementEffectiveId = getElementEffectiveId(valueElement);
                return XFormsUtils.getChildElementValue(propertyContext, container, elementEffectiveId, elementScope, valueElement, false, null);
            }

            private String getLabelValue(Element labelElement) {
                if (labelElement == null)
                    throw new ValidationException("xforms:item or xforms:itemset must contain an xforms:label element.", select1Control.getLocationData());
                final XBLBindings.Scope elementScope = select1Control.getChildElementScope(labelElement);
                final String elementEffectiveId = getElementEffectiveId(labelElement);
                return XFormsUtils.getChildElementValue(propertyContext, container, elementEffectiveId, elementScope, labelElement, false, null);
            }

            private Map<String, String> getAttributes(Element itemChoiceItemsetElement) {
                final String elementEffectiveId = getElementEffectiveId(itemChoiceItemsetElement);

                final Map<String, String> result = new LinkedHashMap<String, String>();
                for (String attributeName: ATTRIBUTES_TO_PROPAGATE) {
                    final String attributeValue = itemChoiceItemsetElement.attributeValue(attributeName);
                    if (attributeValue != null)
                        addAttributeAVTValue(itemChoiceItemsetElement, attributeName, attributeValue, elementEffectiveId, result);
                }
                return result;
            }

            private void addAttributeAVTValue(Element itemChoiceItemsetElement, String attributeName, String attributeValue, String elementEffectiveId, Map<String, String> result) {
                if (!XFormsUtils.maybeAVT(attributeValue)) {
                    // Definitely not an AVT
                    result.put(attributeName, attributeValue);
                } else {
                    // Possible AVT
                    final XFormsContextStack.BindingContext currentBindingContext = contextStack.getCurrentBindingContext();
                    final List<org.orbeon.saxon.om.Item> currentNodeset = currentBindingContext.getNodeset();
                    if (currentNodeset != null && currentNodeset.size() > 0) {
                        final String tempResult = XPathCache.evaluateAsAvt(propertyContext,
                                currentNodeset, currentBindingContext.getPosition(),
                                attributeValue, container.getNamespaceMappings(itemChoiceItemsetElement),
                                contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                                contextStack.getFunctionContext(elementEffectiveId), null,
                                (LocationData) itemChoiceItemsetElement.getData());

                        contextStack.returnFunctionContext();

                        result.put(attributeName, tempResult);
                    }
                }
            }

            public void endElement(Element element) {
                final String localname = element.getName();
                if (XFormsConstants.CHOICES_QNAME.getName().equals(localname)) {
                    // xforms:choices

                    final Element labelElement = element.element(XFormsConstants.LABEL_QNAME);
                    if (labelElement != null) {
                        currentContainer = currentContainer.getParent();
                    }
                }
            }

            public void text(Text text) {
            }

            /**
             * Return the item level for the given item. If the stack is empty, the level is 0.
             *
             * @param item      item to check
             * @param stack     stack of potential ancestors
             * @return          node level
             */
            private int getItemLevel(org.orbeon.saxon.om.Item item, Stack<org.orbeon.saxon.om.Item> stack) {
                // Iterate stack from top to bottom
                if (item instanceof NodeInfo) {
                    int level = stack.size();
                    // Only nodes can have ancestor relationship
                    final NodeInfo nodeInfo = (NodeInfo) item;
                    // Reverse order
                    Collections.reverse(stack);
                    for (Iterator<org.orbeon.saxon.om.Item> i = stack.iterator(); i.hasNext(); level--) {
                        final org.orbeon.saxon.om.Item currentItem = i.next();
                        if (currentItem instanceof NodeInfo) {
                            // Only nodes can have ancestor relationship
                            final NodeInfo currentNode = (NodeInfo) currentItem;
                            if (isAncestorNode(nodeInfo, currentNode)) {
                                // Restore order
                                Collections.reverse(stack);
                                return level;
                            }
                        }
                    }
                    // Restore order
                    Collections.reverse(stack);
                    return level;
                } else {
                    // If it's not a node, stay at current level
                    return stack.size() - 1;
                }
            }

            /**
             * Whether the given node has potentialAncestor as ancestor.
             *
             * @param node                  node to check
             * @param potentialAncestor     potential ancestor
             * @return                      true iif potentialAncestor is an ancestor of node
             */
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

        // Prune non-relevant children
        result.pruneNonRelevantChildren();

        return result;
    }

    public static Itemset evaluateStaticItemsets(final XFormsContainingDocument containingDocument, String prefixedId) {

        final boolean isMultiple = XFormsSelect1Control.isMultiple(containingDocument, prefixedId);
        
        final Itemset result = new Itemset();

        final Element controlElement = containingDocument.getStaticState().getControlElement(prefixedId);
        final boolean isEncryptItemValues = XFormsSelect1Control.isEncryptItemValues(containingDocument, controlElement);

        Dom4jUtils.visitSubtree(controlElement, new Dom4jUtils.VisitorListener() {

            private ItemContainer currentContainer = result;

            public void startElement(Element element) {
                final String localname = element.getName();
                if (XFormsConstants.ITEM_QNAME.getName().equals(localname)) {
                    // xforms:item

                    final Element labelElement = element.element(XFormsConstants.LABEL_QNAME);
                    if (labelElement == null)
                        throw new ValidationException("xforms:item must contain an xforms:label element.", (LocationData) controlElement.getData());
                    final String label = XFormsUtils.getStaticChildElementValue(labelElement, false, null);

                    final Element valueElement = element.element(XFormsConstants.XFORMS_VALUE_QNAME);
                    if (valueElement == null)
                        throw new ValidationException("xforms:item must contain an xforms:value element.", (LocationData) controlElement.getData());
                    final String value = XFormsUtils.getStaticChildElementValue(valueElement, false, null);

                    final Map<String, String> attributes = getAttributes(element);
                    currentContainer.addChildItem(new Item(isMultiple, isEncryptItemValues, attributes, StringUtils.defaultString(label), StringUtils.defaultString(value)));

                } else if (XFormsConstants.ITEMSET_QNAME.getName().equals(localname)) {
                    // xforms:itemset

                    throw new ValidationException("xforms:itemset must not appear in static itemset.", (LocationData) controlElement.getData());

                } else if (XFormsConstants.CHOICES_QNAME.getName().equals(localname)) {
                    // xforms:choices

                    final Element labelElement = element.element(XFormsConstants.LABEL_QNAME);
                    if (labelElement != null) {
                        final String label = XFormsUtils.getStaticChildElementValue(labelElement, false, null);

                        assert label != null;

                        final Map<String, String> attributes = getAttributes(element);
                        final Item newContainer = new Item(isMultiple, isEncryptItemValues, attributes, label, null);
                        currentContainer.addChildItem(newContainer);
                        currentContainer = newContainer;
                    }
                }
            }

            public void endElement(Element element) {
                final String localname = element.getName();
                 if (XFormsConstants.CHOICES_QNAME.getName().equals(localname)) {
                    // xforms:choices

                    final Element labelElement = element.element(XFormsConstants.LABEL_QNAME);
                    if (labelElement != null) {
                        currentContainer = currentContainer.getParent();
                    }
                }
            }

            public void text(Text text) {
            }
        });
        return result;
    }

    private static Map<String, String> getAttributes(Element itemChoiceItemsetElement) {

        final Map<String, String> result = new LinkedHashMap<String, String>();
        for (String attributeName: ATTRIBUTES_TO_PROPAGATE) {
            final String attributeValue = itemChoiceItemsetElement.attributeValue(XFormsConstants.CLASS_QNAME);
            if (attributeValue != null)
                result.put(attributeName, attributeValue);
        }
        return result;
    }
}
