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

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.Text;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.SelectionControlTrait;
import org.orbeon.oxf.xforms.control.LHHAValue;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;
import scala.Option;

import java.util.*;

/**
 * Utilities to deal with items and itemsets.
 */
public class XFormsItemUtils {

    public static final QName[] ATTRIBUTES_TO_PROPAGATE = { XFormsConstants.CLASS_QNAME, XFormsConstants.STYLE_QNAME, XFormsConstants.XXFORMS_OPEN_QNAME };

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
                // Trim for select only
                controlValue = controlValue.trim();
                itemValue = itemValue.trim();// TODO: maybe this should be trimmed in the itemset in the first place
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
                // Do exact string comparison for select1
                selected = controlValue.equals(itemValue);
            }
        }
        return selected;
    }

    /**
     * Evaluate the itemset for a given xf:select or xf:select1 control.
     *
     * @param select1Control        control to evaluate
     * @return                      Itemset
     */
    public static Itemset evaluateItemset(final XFormsSelect1Control select1Control) {

        final SelectionControlTrait staticControl = (SelectionControlTrait) select1Control.staticControl();

        // Optimize static itemsets
        if (staticControl.hasStaticItemset())
            return staticControl.staticItemset().get();

        final boolean isMultiple = staticControl.isMultiple();
        final XBLContainer container = select1Control.container();

        final Itemset result = new Itemset(isMultiple);

        // Set binding on this control, after saving the current context because the context stack must
        // remain unmodified.
        final XFormsContextStack contextStack = container.getContextStack();
        final BindingContext savedBindingContext = contextStack.getCurrentBindingContext();
        contextStack.setBinding(select1Control.bindingContext());

        // TODO: This visits all of the control's descendants. It should only visit the top-level item|itemset|choices elements.
        final boolean isEncryptItemValues = select1Control.isEncodeValues();
        Dom4jUtils.visitSubtree(select1Control.element(), new Dom4jUtils.VisitorListener() {

            private int position = 0;
            private ItemContainer currentContainer = result;

            private String getElementEffectiveId(Element element) {
                return XFormsUtils.getRelatedEffectiveId(select1Control.getEffectiveId(), XFormsUtils.getElementId(element));
            }

            public void startElement(Element element) {
                final String localname = element.getName();

                if (XFormsConstants.XFORMS_ITEM_QNAME.getName().equals(localname)) {
                    // xf:item

                    contextStack.pushBinding(element, getElementEffectiveId(element), select1Control.getChildElementScope(element));

                    final LHHAValue label  = getLabelValue(element.element(XFormsConstants.LABEL_QNAME), true);
                    final LHHAValue help   = getLabelValue(element.element(XFormsConstants.HELP_QNAME), false);
                    final LHHAValue hint   = getLabelValue(element.element(XFormsConstants.HINT_QNAME), false);
                    final String value = getValueValue(element.element(XFormsConstants.XFORMS_VALUE_QNAME));

                    final Map<QName, String> attributes = getAttributes(element);
                    currentContainer.addChildItem(Item.apply(position++, isMultiple, isEncryptItemValues, attributes, label, Option.apply(help), Option.apply(hint), StringUtils.defaultString(value)));

                } else if (XFormsConstants.XFORMS_ITEMSET_QNAME.getName().equals(localname)) {
                    // xf:itemset

                    contextStack.pushBinding(element, getElementEffectiveId(element), select1Control.getChildElementScope(element));

                    final BindingContext currentBindingContext = contextStack.getCurrentBindingContext();

                    //if (model == null || model == currentBindingContext.getModel()) { // it is possible to filter on a particular model
                    final List<org.orbeon.saxon.om.Item> currentNodeSet = currentBindingContext.nodeset();
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

                                // Handle children of xf:itemset

                                // We support relevance of items as an extension to XForms

                                // NOTE: If a node is non-relevant, all its descendants will be non-relevant as
                                // well. If a node is non-relevant, it should be as if it had not even been part of
                                // the nodeset.
                                final boolean isRelevant = (!(currentNodeInfo instanceof NodeInfo)) || InstanceData.getInheritedRelevant((NodeInfo) currentNodeInfo);
                                if (isRelevant) {
                                    final LHHAValue label = getLabelValue(element.element(XFormsConstants.LABEL_QNAME), true);
                                    final LHHAValue help  = getLabelValue(element.element(XFormsConstants.HELP_QNAME), false);
                                    final LHHAValue hint  = getLabelValue(element.element(XFormsConstants.HINT_QNAME), false);
                                    final Element valueCopyElement;
                                    {
                                        final Element valueElement = element.element(XFormsConstants.XFORMS_VALUE_QNAME);
                                        valueCopyElement = (valueElement != null)
                                                ? valueElement : element.element(XFormsConstants.XFORMS_COPY_QNAME);
                                    }
                                    if (valueCopyElement == null)
                                        throw new ValidationException("xf:itemset element must contain one xf:value or one xf:copy element.", select1Control.getLocationData());

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
                                                currentContainer = currentContainer.parent();
                                            }
                                        } else if (newLevel > currentLevel) {
                                            // Going up one level, set new container as last added child
                                            currentContainer = currentContainer.lastChild();
                                        }
                                        currentLevel = newLevel;
                                    }

                                    // Handle new item
                                    if (valueCopyElement.getName().equals(XFormsConstants.XFORMS_VALUE_QNAME.getName())) {
                                        // Handle xf:value
                                        // TODO: This could be optimized for xf:value/@ref|@value as we could get the expression from the cache only once
                                        final String value = getValueValue(valueCopyElement);

                                        // NOTE: At this point, if the value is null, we should consider the item
                                        // non-relevant if it is a leaf item. But we don't yet know if this item is
                                        // a leaf item, so we prune such non-relevant items later.

                                        final Map<QName, String> attributes = getAttributes(element);
                                        currentContainer.addChildItem(Item.apply(position++, isMultiple, isEncryptItemValues, attributes, label, Option.apply(help), Option.apply(hint), value));
                                    } else {
                                        // TODO: handle xf:copy
                                        throw new ValidationException("xf:copy is not yet supported.", select1Control.getLocationData());
                                    }

                                    // Always push the last item to the stack
                                    nodeStack.push(currentNodeInfo);

                                }
                            }
                            contextStack.popBinding();
                        }
                    }

                } else if (XFormsConstants.XFORMS_CHOICES_QNAME.getName().equals(localname)) {
                    // xf:choices

                    contextStack.pushBinding(element, getElementEffectiveId(element), select1Control.getChildElementScope(element));

                    final Element labelElement = element.element(XFormsConstants.LABEL_QNAME);
                    if (labelElement != null) {
                        final LHHAValue label = getLabelValue(labelElement, true);

                        // NOTE: returned label can be null in some cases

                        final Map<QName, String> attributes = getAttributes(element);
                        final Item newContainer = Item.apply(position++, isMultiple, isEncryptItemValues, attributes, label, Option.<LHHAValue>apply(null), Option.<LHHAValue>apply(null), null);
                        currentContainer.addChildItem(newContainer);
                        currentContainer = newContainer;
                    }
                }
            }

            public void endElement(Element element) {
                final String localname = element.getName();
                if (XFormsConstants.XFORMS_ITEM_QNAME.getName().equals(localname)) {
                    contextStack.popBinding();
                } else if (XFormsConstants.XFORMS_ITEMSET_QNAME.getName().equals(localname)) {
                    contextStack.popBinding();
                } else if (XFormsConstants.XFORMS_CHOICES_QNAME.getName().equals(localname)) {
                    contextStack.popBinding();

                    final Element labelElement = element.element(XFormsConstants.LABEL_QNAME);
                    if (labelElement != null) {
                        currentContainer = currentContainer.parent();
                    }
                }
            }

            private String getValueValue(Element valueElement) {
                if (valueElement == null)
                    throw new ValidationException("xf:item or xf:itemset must contain an xf:value element.", select1Control.getLocationData());
                final Scope elementScope = select1Control.getChildElementScope(valueElement);
                final String elementEffectiveId = getElementEffectiveId(valueElement);
                return XFormsUtils.getChildElementValue(container, elementEffectiveId, elementScope, valueElement, false, false, null);
            }

            private LHHAValue getLabelValue(Element labelElement, boolean required) {
                if (required && labelElement == null)
                    throw new ValidationException("xf:item or xf:itemset must contain an xf:label element.", select1Control.getLocationData());

                if (labelElement == null)
                    return null;

                final Scope elementScope = select1Control.getChildElementScope(labelElement);
                final String elementEffectiveId = getElementEffectiveId(labelElement);
                final boolean supportsHTML = select1Control.isFullAppearance(); // Only support HTML when appearance is "full"
                final boolean[] containsHTML = new boolean[] { false };

                // FIXME: Would be good to do this check statically
                final boolean defaultToHTML = LHHAAnalysis.isHTML(labelElement);
                final String label =  XFormsUtils.getChildElementValue(container, elementEffectiveId, elementScope, labelElement, supportsHTML, defaultToHTML, containsHTML);

                if (required) {
                    return new LHHAValue(StringUtils.trimToEmpty(label), containsHTML[0]);
                } else {
                    final String labelOrNull = StringUtils.trimToNull(label);
                    return labelOrNull != null ? new LHHAValue(labelOrNull, containsHTML[0]) : null;
                }
            }

            private Map<QName, String> getAttributes(Element itemChoiceItemsetElement) {
                final String elementEffectiveId = getElementEffectiveId(itemChoiceItemsetElement);

                final Map<QName, String> result = new LinkedHashMap<QName, String>();
                for (QName attributeName : ATTRIBUTES_TO_PROPAGATE) {
                    final String attributeValue = itemChoiceItemsetElement.attributeValue(attributeName);
                    if (attributeValue != null)
                        addAttributeAVTValue(itemChoiceItemsetElement, attributeName, attributeValue, elementEffectiveId, result);
                }
                return result;
            }

            private void addAttributeAVTValue(Element itemChoiceItemsetElement, QName attributeName, String attributeValue, String elementEffectiveId, Map<QName, String> result) {
                if (!XFormsUtils.maybeAVT(attributeValue)) {
                    // Definitely not an AVT
                    result.put(attributeName, attributeValue);
                } else {
                    // Possible AVT
                    final BindingContext currentBindingContext = contextStack.getCurrentBindingContext();
                    final List<org.orbeon.saxon.om.Item> currentNodeset = currentBindingContext.nodeset();
                    if (currentNodeset != null && currentNodeset.size() > 0) {
                        String tempResult;
                        try {
                            tempResult = XPathCache.evaluateAsAvt(
                                    currentNodeset, currentBindingContext.position(),
                                    attributeValue, container.getNamespaceMappings(itemChoiceItemsetElement),
                                    contextStack.getCurrentBindingContext().getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(),
                                    contextStack.getFunctionContext(elementEffectiveId), null,
                                    (LocationData) itemChoiceItemsetElement.getData(),
                                    container.getContainingDocument().getRequestStats().getReporter());
                        } catch (Exception e) {
                            XFormsError.handleNonFatalXPathError(container, e);
                            tempResult = "";
                        }

                        result.put(attributeName, tempResult);
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

        // Make sure to restore the stack
        contextStack.setBinding(savedBindingContext);

        // Prune non-relevant children
        result.pruneNonRelevantChildren();

        return result;
    }
}
