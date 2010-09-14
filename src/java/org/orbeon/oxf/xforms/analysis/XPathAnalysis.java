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
package org.orbeon.oxf.xforms.analysis;

import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.function.Instance;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsInstance;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.Axis;
import org.orbeon.saxon.om.NamePool;

import java.util.*;

public class XPathAnalysis implements XMLUtils.DebugXML {

    public PathMap pathmap; // this is used during analysis and can be freed afterwards

    public final XFormsStaticState staticState;
    public final String xpathString;

    public final boolean figuredOutDependencies;

    public final Set<String> valueDependentPaths = new HashSet<String>();
    public final Set<String> returnablePaths = new HashSet<String>();

    public final Set<String> dependentModels = new HashSet<String>();
    public final Set<String> dependentInstances = new HashSet<String>();
    public final Set<String> returnableInstances = new HashSet<String>();

    public static XPathAnalysis CONSTANT_ANALYSIS = new XPathAnalysis(true);
    public static XPathAnalysis CONSTANT_NEGATIVE_ANALYSIS = new XPathAnalysis(false);

    private XPathAnalysis(boolean figuredOutDependencies) {
        this.staticState = null;
        this.xpathString = null;
        this.pathmap = null;
        this.figuredOutDependencies = figuredOutDependencies;
    }

    public XPathAnalysis(XFormsStaticState staticState, String xpathString, NamespaceMapping namespaceMapping,
                         XPathAnalysis baseAnalysis, Map<String, SimpleAnalysis> inScopeVariables,
                         XBLBindings.Scope scope, String modelPrefixedId, String defaultInstancePrefixedId,
                         LocationData locationData, Element element) {

        // Create new expression
        // TODO: get expression from pool and pass in-scope variables (probably more efficient)
        // TODO: if we use the cache, be careful as expressions can be rewritten in XPathAnalysis()

        this(staticState,
                XPathCache.createExpression(staticState.getXPathConfiguration(), xpathString, namespaceMapping,
                        XFormsContainingDocument.getFunctionLibrary()),
                xpathString, baseAnalysis, inScopeVariables, scope, modelPrefixedId, defaultInstancePrefixedId, locationData, element);
    }

    private XPathAnalysis(XFormsStaticState staticState, Expression expression, String xpathString,
                         XPathAnalysis baseAnalysis, Map<String, SimpleAnalysis> inScopeVariables,
                         XBLBindings.Scope scope, String containingModelPrefixedId, String defaultInstancePrefixedId,
                         LocationData locationData, Element element) {
        
        this.staticState = staticState;
        this.xpathString = xpathString;

        try {
            final Map<String, PathMap> variables = new HashMap<String, PathMap>();
            if (inScopeVariables != null) {
                for (final Map.Entry<String, SimpleAnalysis> entry: inScopeVariables.entrySet()) {
                    final XPathAnalysis valueAnalysis = entry.getValue().getValueAnalysis();
                    variables.put(entry.getKey(), (valueAnalysis != null) ? valueAnalysis.pathmap : null);
                }
            }

            // Properties useful for PathMap analysis
            final Map<String, String> properties = Collections.emptyMap();

            final PathMap pathmap;
            if (baseAnalysis == null) {
                // We are at the top, start with a new PathMap
                pathmap = new PathMap(expression, variables, properties);
            } else {
                if ((expression.getDependencies() & StaticProperty.DEPENDS_ON_CONTEXT_ITEM) != 0) {
                    // Expression depends on the context item

                    if (baseAnalysis.figuredOutDependencies) {
                        // We clone the base analysis and add to an existing PathMap
                        pathmap = baseAnalysis.pathmap.clone();
                        pathmap.setProperties(variables, properties);

                        final PathMap.PathMapNodeSet newNodeset = expression.addToPathMap(pathmap, pathmap.findFinalNodes());

                        if (!pathmap.isInvalidated())
                            pathmap.updateFinalNodes(newNodeset);
                    } else {
                        // Base analysis failed, we fail analysis too
                        this.pathmap = null;
                        this.figuredOutDependencies = false;

                        return;
                    }
                } else {
                    // Expression does not depend on the context item
                    pathmap = new PathMap(expression, variables, properties);
                }
            }

            if (pathmap.isInvalidated()) {
                this.pathmap = null;
                this.figuredOutDependencies = false;
            } else {


//                System.out.println("xxx expressions");
//                System.out.println(expression.toString());
//                pathmap.diagnosticDump(System.out);

                // Try to reduce ancestor axis
                reduceAncestorAxis(pathmap);

    //            System.out.println("xxx after");
    //            pathmap.diagnosticDump(System.out);// xxx

                this.pathmap = pathmap;

                // Produce resulting paths
                this.figuredOutDependencies = processPaths(scope, containingModelPrefixedId, defaultInstancePrefixedId);

                if (!this.figuredOutDependencies) {
                    // This really shouldn't be touched if we can't figure out dependencies, but they are so clear them here
                    valueDependentPaths.clear();
                    returnablePaths.clear();
                    dependentModels.clear();
                    dependentInstances.clear();
                    returnableInstances.clear();
                }
            }

        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(locationData, "analysing XPath expression",
                    element, "expression", xpathString));
        }
    }

    public void combine(XPathAnalysis other) {

        assert figuredOutDependencies && other.figuredOutDependencies;

        pathmap.addRoots(other.pathmap.getPathMapRoots());
        valueDependentPaths.addAll(other.valueDependentPaths);
        returnablePaths.addAll(other.returnablePaths);
        dependentModels.addAll(other.dependentModels);
        dependentInstances.addAll(other.dependentInstances);
        returnableInstances.addAll(other.returnableInstances);
    }

    public boolean intersectsBinding(Set<String> touchedPaths) {
        // Return true if any path matches
        // TODO: for now naively just check exact paths
        for (final String path: valueDependentPaths) {
            if (touchedPaths.contains(path))
                return true;
        }
        return false;
    }

    public boolean intersectsValue(Set<String> touchedPaths) {
        // Return true if any path matches
        // TODO: for now naively just check exact paths
        for (final String path: returnablePaths) {
            if (touchedPaths.contains(path))
                return true;
        }
        for (final String path: valueDependentPaths) {
            if (touchedPaths.contains(path))
                return true;
        }
        return false;
    }

    public boolean intersectsModels(Set<String> touchedModels) {
        for (final String model: touchedModels) {
            if (dependentModels.contains(model))
                return true;
        }
        return false;
    }

    /**
     * Process the pathmap to extract paths and other information useful for handling dependencies.
     */
    private boolean processPaths(XBLBindings.Scope scope, String containingModelPrefixedId, String defaultInstancePrefixedId) {
        final List<Expression> stack = new ArrayList<Expression>();
        for (final PathMap.PathMapRoot root: pathmap.getPathMapRoots()) {
            stack.add(root.getRootExpression());
            final boolean success = processNode(stack, root, scope, containingModelPrefixedId, defaultInstancePrefixedId, false);
            if (!success)
                return false;
            stack.remove(stack.size() - 1);
        }
        return true;
    }

    public static String buildInstanceString(String instanceId) {
        return "instance('" + instanceId.replaceAll("'", "''") + "')";
    }

    private boolean processNode(List<Expression> stack, PathMap.PathMapNode node, XBLBindings.Scope scope, String containingModelPrefixedId, String defaultInstancePrefixedId, boolean ancestorAtomized) {
        boolean success = true;
        if (node.getArcs().length == 0 || node.isReturnable() || node.isAtomized() || ancestorAtomized) {

            final StringBuilder sb = new StringBuilder();
            success &= createPath(sb, stack, scope, containingModelPrefixedId, defaultInstancePrefixedId, node);
            if (success) {
                if (sb.length() > 0) {
                    // A path was created

                    // NOTE: A same node can be both returnable AND atomized in a given expression
                    final String s = sb.toString();
                    if (node.isReturnable())
                        returnablePaths.add(s);
                    if (node.isAtomized())
                        valueDependentPaths.add(s);
                } else {
                    // NOP: don't add the path as this is not considered a dependency
                }
            } else {
                // We can't deal with this path
                return false;
            }
        }

        // Process children nodes
        if (node.getArcs().length > 0) {
            for (final PathMap.PathMapArc arc: node.getArcs()) {
                stack.add(arc.getStep());
                success &= processNode(stack, arc.getTarget(), scope, containingModelPrefixedId, defaultInstancePrefixedId, node.isAtomized());
                if (!success) {
                    return false;
                }
                stack.remove(stack.size() - 1);
            }
        }

        // We managed to deal with this path
        return true;
    }

    private boolean createPath(StringBuilder sb, List<Expression> stack, XBLBindings.Scope scope, String containingModelPrefixedId, String defaultInstancePrefixedId, PathMap.PathMapNode node) {
        boolean success = true;
        for (final Expression expression: stack) {
            if (expression instanceof Instance || expression instanceof XXFormsInstance) {
                // Instance function
                final FunctionCall instanceExpression = (FunctionCall) expression;

                final boolean hasParameter = instanceExpression.getArguments().length > 0;
                if (!hasParameter) {
                    // instance() resolves to default instance for scope
                    if (defaultInstancePrefixedId != null) {
                        sb.append(buildInstanceString(defaultInstancePrefixedId));
                        {
                            // Static state doesn't yet know about the model if this expression is within the model. In this case, use containingModelPrefixedId
                            // TODO: not needed anymore because model now set before it is analyzed
                            final Model defaultModelForInstance = staticState.getModelByInstancePrefixedId(defaultInstancePrefixedId);
                            final String dependentModelPrefixedId = (defaultModelForInstance != null) ? defaultModelForInstance.prefixedId : containingModelPrefixedId;
                            dependentModels.add(dependentModelPrefixedId);
                        }

                        dependentInstances.add(defaultInstancePrefixedId);
                        if (node.isReturnable())
                            returnableInstances.add(defaultInstancePrefixedId);

                        // Rewrite expression to add/replace its argument with a prefixed instance id
                        ((FunctionCall) expression).setArguments(new Expression[] { new StringLiteral(defaultInstancePrefixedId) });
                    } else {
                        // Model does not have a default instance
                        // This is successful, but the path must not be added
                        sb.setLength(0);
                        success = true;
                        break;
                    }
                } else {
                    final Expression instanceNameExpression = instanceExpression.getArguments()[0];
                    if (instanceNameExpression instanceof StringLiteral) {
                        final String originalInstanceId = ((StringLiteral) instanceNameExpression).getStringValue();
                        final boolean searchAncestors = expression instanceof XXFormsInstance;

                        // This is hacky: we use RewrittenStringLiteral as a marker so we don't rewrite an instance() StringLiteral parameter twice
                        final boolean alreadyRewritten = instanceNameExpression instanceof PrefixedIdStringLiteral;

                        final String prefixedInstanceId;
                        if (alreadyRewritten) {
                            // Parameter is already a prefixed id
                            prefixedInstanceId = originalInstanceId;
                        } else if (searchAncestors) {
                            // xxforms:instance()
                            prefixedInstanceId = staticState.findInstancePrefixedId(scope, originalInstanceId);
                        } else if (originalInstanceId.indexOf(XFormsConstants.COMPONENT_SEPARATOR) != -1) {
                            // HACK: datatable e.g. uses instance(prefixedId)!
                            prefixedInstanceId = originalInstanceId;
                        } else {
                            // Normal use of instance()
                            prefixedInstanceId = scope.getPrefixedIdForStaticId(originalInstanceId);
                        }

                        if (prefixedInstanceId != null) {
                            // Instance found
                            sb.append(buildInstanceString(prefixedInstanceId));
                            {
                                // Static state doesn't yet know about the model if this expression is within the model. In this case, use containingModelPrefixedId
                                // TODO: not needed anymore because model now set before it is analyzed
                                final Model modelForInstance = staticState.getModelByInstancePrefixedId(prefixedInstanceId);
                                final String dependentModelPrefixedId = (modelForInstance != null) ? modelForInstance.prefixedId : containingModelPrefixedId;
                                dependentModels.add(dependentModelPrefixedId);
                            }
                            dependentInstances.add(prefixedInstanceId);
                            if (node.isReturnable())
                                returnableInstances.add(prefixedInstanceId);
                            // If needed, rewrite expression to replace its argument with a prefixed instance id
                            if (!alreadyRewritten)
                                ((FunctionCall) expression).setArguments(new Expression[] { new PrefixedIdStringLiteral(prefixedInstanceId) });
                        } else {
                            // Instance not found (could be reference to unknown instance e.g. author typo!)
                            // TODO: must also catch case where id is found but does not correspond to instance
                            // TODO: warn in log
                            // This is successful, but the path must not be added
                            sb.setLength(0);
                            success = true;
                            break;
                        }
                    } else {
                        // Non-literal instance name
                        success = false;
                        break;
                    }
                }
            } else if (expression instanceof AxisExpression) {
                final AxisExpression axisExpression = (AxisExpression) expression;
                if (axisExpression.getAxis() == Axis.SELF) {
                    // NOP
                } else if (axisExpression.getAxis() == Axis.CHILD || axisExpression.getAxis() == Axis.ATTRIBUTE) {
                    // Child or attribute axis
                    if (sb.length() > 0)
                        sb.append('/');
                    final int fingerprint = axisExpression.getNodeTest().getFingerprint();
                    if (fingerprint != -1) {
                        if (axisExpression.getAxis() == Axis.ATTRIBUTE)
                            sb.append("@");
                        sb.append(fingerprint);
                    } else {
                        // Unnamed node
                        success = false;
                        break;
                    }
                } else {
                    // Unhandled axis
                    success = false;
                    break;
                }
            } else {
                success = false;
                break;
            }
        }
        return success;
    }

    private static class PrefixedIdStringLiteral extends StringLiteral {
        public PrefixedIdStringLiteral(CharSequence value) {
            super(value);
        }
    }

    public void toXML(PropertyContext propertyContext, ContentHandlerHelper helper) {

        helper.startElement("analysis", new String[] { "expression", xpathString, "analyzed", Boolean.toString(figuredOutDependencies) } );

        if (valueDependentPaths.size() > 0) {
            helper.startElement("value-dependent");
            for (final String path: valueDependentPaths) {
                helper.element("path", getDisplayPath(path));
            }
            helper.endElement();
        }

        if (returnablePaths.size() > 0) {
            helper.startElement("returnable");
            for (final String path: returnablePaths) {
                helper.element("path", getDisplayPath(path));
            }
            helper.endElement();
        }

        if (dependentModels.size() > 0) {
            helper.startElement("dependent-models");
            for (final String path: dependentModels) {
                helper.element("model", getDisplayPath(path));
            }
            helper.endElement();
        }

        if (dependentInstances.size() > 0) {
            helper.startElement("dependent-instances");
            for (final String path: dependentInstances) {
                helper.element("instances", getDisplayPath(path));
            }
            helper.endElement();
        }

        if (returnableInstances.size() > 0) {
            helper.startElement("returnable-instances");
            for (final String path: returnableInstances) {
                helper.element("instances", getDisplayPath(path));
            }
            helper.endElement();
        }

        helper.endElement();
    }

    // For debugging/logging
    private static String getDisplayPath(String path) {
        final NamePool pool = XPathCache.getGlobalConfiguration().getNamePool();

        final StringTokenizer st = new StringTokenizer(path, "/");
        final StringBuilder sb = new StringBuilder();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.startsWith("@")) {
                sb.append('@');
                token = token.substring(1);
            }
            try {
                final int i = Integer.parseInt(token);
                sb.append(pool.getDisplayName(i));
            } catch (NumberFormatException e) {
                sb.append(token);
            }
            if (st.hasMoreTokens())
                sb.append('/');
        }
        return sb.toString();
    }

    // For unit tests
    public static String getInternalPath(Map<String, String> namespaces, String path) {
        final NamePool pool = XPathCache.getGlobalConfiguration().getNamePool();

        final StringTokenizer st = new StringTokenizer(path, "/");
        final StringBuilder sb = new StringBuilder();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();

            if (token.startsWith("instance(")) {
                // instance(...)
                sb.append(token);
            } else {
                if (token.startsWith("@")) {
                    // Attribute
                    sb.append('@');
                    token = token.substring(1);
                }

                // Element or attribute name
                final String prefix = XMLUtils.prefixFromQName(token);
                final String localname = XMLUtils.localNameFromQName(token);

                sb.append(pool.allocate(prefix, namespaces.get(prefix), localname));
            }
            // Separator
            if (st.hasMoreTokens())
                sb.append('/');
        }
        return sb.toString();
    }

    private static class NodeArc {
        public final PathMap.PathMapNode node;  // node, as we otherwise can't go back to the node from an arc
        public final PathMap.PathMapArc arc;    // one of the arcs of the node

        private NodeArc(PathMap.PathMapNode node, PathMap.PathMapArc arc) {
            this.node = node;
            this.arc = arc;
        }
    }

    private boolean reduceAncestorAxis(PathMap pathmap) {
        final List<NodeArc> stack = new ArrayList<NodeArc>();
        for (final PathMap.PathMapRoot root: pathmap.getPathMapRoots()) {
            // Apply as long as we find matches
            while (reduceAncestorAxis(stack, root)) {
                stack.clear();
            }
            stack.clear();
        }
        return true;
    }

    private boolean reduceAncestorAxis(List<NodeArc> stack, PathMap.PathMapNode node) {
        // Process children nodes
        for (final PathMap.PathMapArc arc: node.getArcs()) {

            final NodeArc newNodeArc = new NodeArc(node, arc);

            final AxisExpression e = arc.getStep();
            // TODO: handle ANCESTOR_OR_SELF axis
            if (e.getAxis() == Axis.ANCESTOR && e.getNodeTest().getFingerprint() != -1) {
                // Found ancestor::foobar
                final int nodeName = e.getNodeTest().getFingerprint();
                final List<PathMap.PathMapArc> ancestorArcs = ancestorsWithFingerprint(stack, nodeName);
                if (ancestorArcs.size() > 0) {
                    // There can be more than one ancestor with that fingerprint
                    for (final PathMap.PathMapArc ancestorArc : ancestorArcs)
                        moveArc(newNodeArc, ancestorArc.getTarget());
                    return true;
                } else {
                    // E.g.: /a/b/ancestor::c
                    // TODO
                }
            } else if (e.getAxis() == Axis.PARENT) { // don't test fingerprint as we could handle /a/*/..
                // Parent axis
                if (stack.size() >= 1) {
                    final NodeArc parentNodeArc = stack.get(stack.size() - 1);
                    moveArc(newNodeArc, parentNodeArc.node);
                    return true;
                } else {
                    // TODO: is this possible?
                }
            } else if (e.getAxis() == Axis.FOLLOWING_SIBLING || e.getAxis() == Axis.PRECEDING_SIBLING) {
                // Simplify preceding-sibling::foobar / following-sibling::foobar
                final NodeArc parentNodeArc = stack.get(stack.size() - 1);
                if (stack.size() > 2) {
                    final NodeArc grandparentNodeArc = stack.get(stack.size() - 2);

                    final AxisExpression newStep = new AxisExpression(parentNodeArc.arc.getStep().getAxis(), e.getNodeTest());
                    newStep.setContainer(e.getContainer());
                    grandparentNodeArc.node.createArc(newStep, arc.getTarget());
                    node.removeArc(arc);
                } else {
                    final AxisExpression newStep = new AxisExpression(Axis.CHILD, e.getNodeTest());
                    newStep.setContainer(e.getContainer());
                    parentNodeArc.node.createArc(newStep, arc.getTarget());
                    node.removeArc(arc);
                }
            }

            {
                stack.add(newNodeArc);
                if (reduceAncestorAxis(stack, arc.getTarget())) {
                    return true;
                }
                stack.remove(stack.size() - 1);
            }
        }

        // We did not find a match
        return false;
    }

    private boolean moveArc(NodeArc nodeArc, PathMap.PathMapNode ancestorNode) {
        if (ancestorNode != null) {

            // Move arcs
            ancestorNode.addArcs(nodeArc.arc.getTarget().getArcs());

            // Remove current arc from its node as it's been moved
            nodeArc.node.removeArc(nodeArc.arc);

            if (nodeArc.arc.getTarget().isReturnable())
                ancestorNode.setReturnable(true);

            if (nodeArc.arc.getTarget().isAtomized())
                ancestorNode.setAtomized();

            return true;
        } else {
            // Ignore for now
        }
        return false;
    }

    private List<PathMap.PathMapArc> ancestorsWithFingerprint(List<NodeArc> stack, int nodeName) {
        final List<PathMap.PathMapArc> result = new ArrayList<PathMap.PathMapArc>();
        Collections.reverse(stack);
        for (final NodeArc nodeArc: stack.subList(1, stack.size())) {
            final AxisExpression e = nodeArc.arc.getStep();

            if (e.getAxis() == Axis.CHILD && e.getNodeTest().getFingerprint() == nodeName) {
                result.add(nodeArc.arc);
            }
        }
        // Not found
        Collections.reverse(stack);
        return result;
    }

    public void freeTransientState() {
        this.pathmap = null;
    }
}
