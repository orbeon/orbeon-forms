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
import org.orbeon.oxf.xforms.analysis.model.StaticBind;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

public class RuntimeBind implements XFormsObject {

    public final XFormsModel model;
    public final BindIteration parentIteration;

    public final StaticBind staticBind;
    public final List<Item> items;
    public final List<BindNode> bindNodes;

    public XFormsContainingDocument containingDocument() {
        return model.containingDocument;
    }

    // To work around Scala compiler bug ("error: not found: value BindTree") when accessing staticBind directly
    public NamespaceMapping namespaceMapping() {
        return staticBind.namespaceMapping();
    }

    public RuntimeBind(XFormsModel model, StaticBind staticBind, BindIteration parentIteration, boolean isSingleNodeContext) {
        this.model = model;
        this.parentIteration = parentIteration;
        this.staticBind = staticBind;

        final XFormsContextStack contextStack = model.getContextStack();

        // Compute items and bind nodes
        contextStack.pushBinding(staticBind.element(), model.getEffectiveId(), model.getResolutionScope());
        {
            // NOTE: This should probably go into XFormsContextStack
            final BindingContext bindingContext = contextStack.getCurrentBindingContext();
            if (bindingContext.newBind()) {
                // Case where a @nodeset or @ref attribute is present -> a current nodeset is therefore available
                items = bindingContext.nodeset();
            } else {
                // Case where of missing @nodeset attribute (it is optional in XForms 1.1 and defaults to the context item)
                final Item contextItem = bindingContext.contextItem();
                items = (contextItem == null) ? XFormsConstants.EMPTY_ITEM_LIST : Collections.singletonList(contextItem);
            }

            assert items != null;

            final XFormsModelBinds binds = model.getBinds();

            // "4.7.2 References to Elements within a bind Element [...] If a target bind element is outermost, or if
            // all of its ancestor bind elements have nodeset attributes that select only one node, then the target bind
            // only has one associated bind object, so this is the desired target bind object whose nodeset is used in
            // the Single Node Binding or Node Set Binding"
            if (isSingleNodeContext)
                binds.singleNodeContextBinds().put(staticBind.staticId(), this);

            final int nodesetSize = items.size();
            if (nodesetSize > 0) {
                // Only then does it make sense to create BindNodes

                final scala.collection.Seq<StaticBind> childrenStaticBinds = staticBind.children();
                if (childrenStaticBinds.size() > 0) {
                    // There are children binds (and maybe MIPs)
                    bindNodes = new ArrayList<BindNode>(nodesetSize);

                    // Iterate over nodeset and produce child iterations
                    int currentPosition = 1;
                    for (final Item item : items) {
                        contextStack.pushIteration(currentPosition);
                        {
                            // Create iteration and remember it
                            final boolean isNewSingleNodeContext = isSingleNodeContext && nodesetSize == 1;
                            final BindIteration currentBindIteration = new BindIteration(this, currentPosition, item, isNewSingleNodeContext, childrenStaticBinds);
                            bindNodes.add(currentBindIteration);

                            // Create mapping context node -> iteration
                            final NodeInfo iterationNodeInfo = (NodeInfo) items.get(currentPosition - 1);
                            List<BindIteration> iterations = binds.iterationsForContextNodeInfo().get(iterationNodeInfo);
                            if (iterations == null) {
                                iterations = new ArrayList<BindIteration>();
                                binds.iterationsForContextNodeInfo().put(iterationNodeInfo, iterations);
                            }
                            iterations.add(currentBindIteration);
                        }
                        contextStack.popBinding();

                        currentPosition++;
                    }
                } else if (staticBind.hasMIPs()) {
                    // No children binds, but we have MIPs, so create holders too
                    bindNodes = new ArrayList<BindNode>(nodesetSize);

                    int currentPosition = 1;
                    for (final Item item : items) {
                        bindNodes.add(new BindNode(this, currentPosition, item));
                        currentPosition++;
                    }
                } else {
                    bindNodes = Collections.emptyList();
                }
            } else {
                bindNodes = Collections.emptyList();
            }

        }
        contextStack.popBinding();
    }

    public void applyBinds(XFormsModelBinds.BindRunner bindRunner) {
        // We can only apply if we have bind nodes
        if (bindNodes != null) {
            for (final BindNode bindNode : bindNodes) {
                // Handle current node
                bindRunner.applyBind(bindNode);

                // Handle children binds if any
                if (bindNode instanceof BindIteration)
                    ((BindIteration) bindNode).applyBinds(bindRunner);
            }
        }
    }

    public String staticId() {
        return staticBind.staticId();
    }

    public String getEffectiveId() {
        return XFormsUtils.getRelatedEffectiveId(model.getEffectiveId(), staticId());
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
}