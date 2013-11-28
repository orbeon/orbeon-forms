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
package org.orbeon.oxf.xforms;

import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.analysis.model.BindTree;
import org.orbeon.oxf.xforms.analysis.model.StaticBind;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

public class RuntimeBind implements XFormsObject {

    private final XFormsModelBinds binds;
    public  BindTree foo;
    public final StaticBind staticBind;
    public final List<Item> nodeset;       // actual nodeset for this bind

    public final QName typeQName;

    private List<BindNode> bindNodes; // List<BindIteration>

    public XFormsContainingDocument containingDocument() {
        return binds.containingDocument;
    }

    public XFormsModel model() {
        return binds.model;
    }

    // To work around Scala compiler bug ("error: not found: value BindTree") when accessing staticBind directly
    public NamespaceMapping namespaceMapping() {
        return staticBind.namespaceMapping();
    }

    public RuntimeBind(XFormsModelBinds binds, StaticBind staticBind, boolean isSingleNodeContext) {
        this.binds = binds;
        this.staticBind = staticBind;

        // Compute nodeset for this bind
        binds.model.getContextStack().pushBinding(staticBind.element(), binds.model.getEffectiveId(), binds.model.getResolutionScope());
        {
            // NOTE: This should probably go into XFormsContextStack
            if (binds.model.getContextStack().getCurrentBindingContext().newBind()) {
                // Case where a @nodeset or @ref attribute is present -> a current nodeset is therefore available
                nodeset = binds.model.getContextStack().getCurrentBindingContext().nodeset();
            } else {
                // Case where of missing @nodeset attribute (it is optional in XForms 1.1 and defaults to the context item)
                final Item contextItem = binds.model.getContextStack().getCurrentBindingContext().contextItem();
                nodeset = (contextItem == null) ? XFormsConstants.EMPTY_ITEM_LIST : Collections.singletonList(contextItem);
            }

            assert nodeset != null;

            // "4.7.2 References to Elements within a bind Element [...] If a target bind element is outermost, or if
            // all of its ancestor bind elements have nodeset attributes that select only one node, then the target bind
            // only has one associated bind object, so this is the desired target bind object whose nodeset is used in
            // the Single Node Binding or Node Set Binding"
            if (isSingleNodeContext)
                binds.singleNodeContextBinds().put(staticBind.staticId(), this);

            // Set type on node
            // Get type namespace and local name
            typeQName = evaluateTypeQName(staticBind.namespaceMapping().mapping);

            final int nodesetSize = nodeset.size();
            if (nodesetSize > 0) {
                // Only then does it make sense to create BindNodes

                final List<StaticBind> childrenStaticBinds = staticBind.jChildren();
                if (childrenStaticBinds.size() > 0) {
                    // There are children binds (and maybe MIPs)
                    bindNodes = new ArrayList<BindNode>(nodesetSize);

                    // Iterate over nodeset and produce child iterations
                    int currentPosition = 1;
                    for (final Item item : nodeset) {
                        binds.model.getContextStack().pushIteration(currentPosition);
                        {
                            // Create iteration and remember it
                            final boolean isNewSingleNodeContext = isSingleNodeContext && nodesetSize == 1;
                            final BindIteration currentBindIteration = new BindIteration(getStaticId(), isNewSingleNodeContext, item, childrenStaticBinds, typeQName);
                            bindNodes.add(currentBindIteration);

                            // Create mapping context node -> iteration
                            final NodeInfo iterationNodeInfo = (NodeInfo) nodeset.get(currentPosition - 1);
                            List<BindIteration> iterations = binds.iterationsForContextNodeInfo().get(iterationNodeInfo);
                            if (iterations == null) {
                                iterations = new ArrayList<BindIteration>();
                                binds.iterationsForContextNodeInfo().put(iterationNodeInfo, iterations);
                            }
                            iterations.add(currentBindIteration);
                        }
                        binds.model.getContextStack().popBinding();

                        currentPosition++;
                    }
                } else if (staticBind.hasMIPs()) {
                    // No children binds, but we have MIPs, so create holders anyway
                    bindNodes = new ArrayList<BindNode>(nodesetSize);

                    for (final Item item : nodeset)
                        bindNodes.add(new BindNode(getStaticId(), item, typeQName));
                }
            }

        }
        binds.model.getContextStack().popBinding();
    }

    public void applyBinds(XFormsModelBinds.BindRunner bindRunner) {
        if (nodeset.size() > 0) {
            // Handle each node in this node-set
            final Iterator<BindNode> j = (bindNodes != null) ? bindNodes.iterator() : null;

            for (int index = 1; index <= nodeset.size(); index++) {
                final BindNode currentBindIteration = (j != null) ? j.next() : null;

                // Handle current node
                bindRunner.applyBind(this, index);

                // Handle children binds if any
                if (currentBindIteration instanceof BindIteration)
                    ((BindIteration) currentBindIteration).applyBinds(bindRunner);
            }
        }
    }

    public String getStaticId() {
        return staticBind.staticId();
    }

    public String getEffectiveId() {
        return XFormsUtils.getRelatedEffectiveId(binds.model.getEffectiveId(), getStaticId());
    }

    public QName evaluateTypeQName(Map<String, String> namespaceMap) {
        final String typeQNameString = staticBind.dataTypeOrNull();
        if (typeQNameString != null) {
            final String typeNamespacePrefix;
            final String typeNamespaceURI;
            final String typeLocalname;

            final int prefixPosition = typeQNameString.indexOf(':');
            if (prefixPosition > 0) {
                typeNamespacePrefix = typeQNameString.substring(0, prefixPosition);
                typeNamespaceURI = namespaceMap.get(typeNamespacePrefix);
                if (typeNamespaceURI == null)
                    throw new ValidationException("Namespace not declared for prefix '" + typeNamespacePrefix + "'", staticBind.locationData());

                // TODO: xxx check what XForms event must be dispatched

                typeLocalname = typeQNameString.substring(prefixPosition + 1);
            } else {
                typeNamespacePrefix = "";
                typeNamespaceURI = "";
                typeLocalname = typeQNameString;
            }

            return QName.get(typeLocalname, new Namespace(typeNamespacePrefix, typeNamespaceURI));
        } else {
            return null;
        }
    }

    public BindNode getBindNode(int position) {
        return (bindNodes != null) ? bindNodes.get(position - 1) : null;
    }

    // Delegate to BindNode
    public void setRelevant(int position, boolean value) {
        getBindNode(position).setRelevant(value);
    }

    public void setReadonly(int position, boolean value) {
        getBindNode(position).setReadonly(value);
    }

    public void setRequired(int position, boolean value) {
        getBindNode(position).setRequired(value);
    }

    public void setCustom(int position, String name, String value) {
        getBindNode(position).setCustom(name, value);
    }

    public void setTypeValidity(int position, boolean value) {
        getBindNode(position).setTypeValid(value);
    }

    public void setRequiredValidity(int position, boolean value) {
        getBindNode(position).setRequiredValid(value);
    }

    public boolean isValid(int position) {
        return getBindNode(position).valid();
    }

    // Bind node that also contains nested binds
    class BindIteration extends BindNode {

        private List<RuntimeBind> childrenBinds;

        public BindIteration(String bindStaticId, boolean isSingleNodeContext, Item item, List<StaticBind> childrenStaticBinds, QName typeQName) {

            super(bindStaticId, item, typeQName);

            assert childrenStaticBinds.size() > 0;

            // Iterate over children and create children binds
            childrenBinds = new ArrayList<RuntimeBind>(childrenStaticBinds.size());
            for (final StaticBind staticBind : childrenStaticBinds)
                childrenBinds.add(new RuntimeBind(binds, staticBind, isSingleNodeContext));
        }

        public void applyBinds(XFormsModelBinds.BindRunner bindRunner) {
            for (final RuntimeBind currentBind : childrenBinds)
                currentBind.applyBinds(bindRunner);
        }

        public RuntimeBind getBind(String bindId) {
            for (final RuntimeBind currentBind : childrenBinds)
                if (currentBind.staticBind.staticId().equals(bindId))
                    return currentBind;
            return null;
        }
    }
}