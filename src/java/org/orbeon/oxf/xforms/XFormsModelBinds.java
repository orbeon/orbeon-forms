/**
 *  Copyright (C) 2008 Orbeon, Inc.
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

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.action.actions.XFormsSetvalueAction;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.style.StandardNames;
import org.orbeon.saxon.sxpath.XPathEvaluator;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.BuiltInAtomicType;
import org.orbeon.saxon.type.BuiltInSchemaFactory;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.value.ValidationErrorValue;
import org.orbeon.saxon.value.SequenceExtent;
import org.apache.commons.collections.map.CompositeMap;

import java.util.*;

/**
 * Represent a given model's binds.
 */
public class XFormsModelBinds {

    private final XFormsModel model;                            // model to which we belong
    private final XFormsContainingDocument containingDocument;  // current containing document
    private final boolean computedBindsCalculate;               // whether computed binds (readonly, required, relevant) are evaluated with recalculate or revalidate
    private XFormsContextStack contextStack;                    // context stack for evaluation

    private List topLevelBinds = new ArrayList();               // List<Bind>
    private Map singleNodeContextBinds = new HashMap();         // Map<String, Bind>
    private Map iterationsForContextNodeInfo = new HashMap();   // Map<NodeInfo, List<BindIteration>>
    private List offlineBinds = new ArrayList();                // List<Bind>
    private Map variableNamesToIds = new HashMap();             // Map<String, String> of name to id

    public XFormsModelBinds(XFormsModel model) {
        this.model = model;
        this.contextStack = new XFormsContextStack(model);

        this.containingDocument = model.getContainingDocument();
        this.computedBindsCalculate = XFormsProperties.getComputedBinds(containingDocument).equals(XFormsProperties.COMPUTED_BINDS_RECALCULATE_VALUE);
    }

    /**
     * Rebuild all binds, computing all bind nodesets (but not computing the MIPs)
     *
     * @param pipelineContext   current PipelineContext
     */
    public void rebuild(PipelineContext pipelineContext) {
        // Reset everything
        contextStack.resetBindingContext(pipelineContext, model);
        topLevelBinds.clear();
        singleNodeContextBinds.clear();
        iterationsForContextNodeInfo.clear();
        offlineBinds.clear();
        variableNamesToIds.clear();

        // Iterate through all top-level bind elements
        final List childElements = model.getModelDocument().getRootElement().elements(new QName("bind", XFormsConstants.XFORMS_NAMESPACE));
        for (Iterator i = childElements.iterator(); i.hasNext();) {
            final Element currentBindElement = (Element) i.next();
            // Create and remember as top-level bind
            final Bind currentBind = new Bind(pipelineContext, currentBindElement, true);
            topLevelBinds.add(currentBind);
        }
    }

    /**
     * Apply calculate binds.
     *
     * @param pipelineContext   current PipelineContext
     */
    public void applyCalculateBinds(final PipelineContext pipelineContext) {
        // Handle calculations
        iterateBinds(pipelineContext, new BindRunner() {
            public void applyBind(PipelineContext pipelineContext, Bind bind, List nodeset, int position) {
                handleCalculateBinds(pipelineContext, bind, nodeset, position);
            }
        });

        // Update computed expression binds
        if (computedBindsCalculate) {
            applyComputedExpressionBinds(pipelineContext);
        }
    }

    /**
     * Apply required, relevant and readonly binds.
     *
     * @param pipelineContext   current PipelineContext
     */
    public void applyComputedExpressionBinds(final PipelineContext pipelineContext) {
        // Clear state
        final List instances = model.getInstances();
        if (instances != null) {
            for (Iterator i =  instances.iterator(); i.hasNext();) {
                XFormsUtils.iterateInstanceData(((XFormsInstance) i.next()), new XFormsUtils.InstanceWalker() {
                    public void walk(NodeInfo nodeInfo) {
                        InstanceData.clearOtherState(nodeInfo);
                    }
                }, true);
            }

            iterateBinds(pipelineContext, new BindRunner() {
                public void applyBind(PipelineContext pipelineContext, Bind bind, List nodeset, int position) {
                    handleComputedExpressionBinds(pipelineContext, bind, nodeset, position);
                }
            });
        }
    }

    /**
     * Apply validation binds
     *
     * @param pipelineContext   current PipelineContext
     */
    public void applyValidationBinds(final PipelineContext pipelineContext) {
        // Handle validation
        iterateBinds(pipelineContext, new BindRunner() {
            public void applyBind(PipelineContext pipelineContext, Bind bind, List nodeset, int position) {
                handleValidationBind(pipelineContext, bind, nodeset, position);
            }
        });

        // Update computed expression binds
        if (!computedBindsCalculate) {
            applyComputedExpressionBinds(pipelineContext);
        }
    }

    /**
     * Return the nodeset for a given bind and context item, as per "4.7.2 References to Elements within a bind
     * Element".
     *
     * @param bindId        id of the bind to handle
     * @param contextItem   context item if necessary
     * @return              bind nodeset
     */
    public List getBindNodeset(String bindId, Item contextItem) {

        final Bind singleNodeContextBind = (Bind) singleNodeContextBinds.get(bindId);
        if (singleNodeContextBind != null) {
            // This bind has a single-node context (incl. top-level bind), so ignore context item and just return the bind nodeset
            return singleNodeContextBind.getNodeset();
        } else {
            // Nested bind, context item will be used

            // This requires a context node, not just any item
            if (contextItem instanceof NodeInfo) {
                final List iterationsForContextNode = (List) iterationsForContextNodeInfo.get(contextItem);
                if (iterationsForContextNode != null) {
                    for (Iterator i = iterationsForContextNode.iterator(); i.hasNext();) {
                        final BindIteration currentIteration = (BindIteration) i.next();
                        final Bind currentBind = currentIteration.getBind(bindId);
                        if (currentBind != null) {
                            // Found
                            return currentBind.getNodeset();
                        }
                    }
                }
            }
        }

        // Nothing found
        return Collections.EMPTY_LIST;
    }

    /**
     * Return all binds marked with xxforms:offline="true".
     *
     * @return  List<Bind> of offline binds
     */
    public List getOfflineBinds() {
        return offlineBinds;
    }

    /**
     * Return a JSON string containing the control -> mips and variable -> control mappings for all models.
     *
     * Example of output:
     *
     * {
     *     "mips": {
     *         "total-control": { "calculate": "$units * $price", ... other MIPs ... },
     *         ... other controls ...
     *     },
     *     "variables": {
     *         "units": "units-control",
     *         "price": "price-control"
     *     }
     * };
     *
     * @return  JSON string
     */
    public static String getOfflineBindMappings(XFormsContainingDocument containingDocument) {
        final Map idsToXFormsControls = containingDocument.getXFormsControls().getCurrentControlsState().getIdsToXFormsControls();
        final FastStringBuffer sb = new FastStringBuffer('{');

        // Handle MIPs
        sb.append("\"mips\": {");
        {
            boolean controlFound = false;
            // Iterate through models
            for (Iterator h = containingDocument.getModels().iterator(); h.hasNext();) {
                final XFormsModel currentModel = (XFormsModel) h.next();
                final List offlineBinds = currentModel.getBinds().getOfflineBinds();
                //  Iterate through offline binds
                for (Iterator i = offlineBinds.iterator(); i.hasNext();) {
                    final Bind currentBind = (Bind) i.next();
                    final List currentNodeset = currentBind.getNodeset();

                    final List boundControls = getBoundControls(idsToXFormsControls, currentNodeset);
                    if (boundControls.size() > 0) {
                        for (Iterator j = boundControls.iterator(); j.hasNext();) {
                            final XFormsControl currentControl = (XFormsControl) j.next();

                            if (controlFound)
                                sb.append(',');
                            controlFound = true;

                            // Control id
                            sb.append('"');
                            sb.append(currentControl.getEffectiveId());
                            sb.append("\": {");

                            // Output MIPs
                            boolean mipFound = false;
                            mipFound = appendNameValue(sb, mipFound, "calculate", currentBind.getCalculate());
                            mipFound = appendNameValue(sb, mipFound, "relevant", currentBind.getRelevant());
                            mipFound = appendNameValue(sb, mipFound, "readonly", currentBind.getReadonly());
                            mipFound = appendNameValue(sb, mipFound, "required", currentBind.getRequired());
                            mipFound = appendNameValue(sb, mipFound, "constraint", currentBind.getConstraint());

                            // Output type MIP as an exploded QName
                            final String typeMip = currentBind.getType();
                            if (typeMip != null) {
                                final QName typeMipQName = Dom4jUtils.extractTextValueQName(containingDocument.getStaticState().getNamespaceMappings(currentBind.getBindElement()), typeMip);
                                mipFound = appendNameValue(sb, mipFound, "type", Dom4jUtils.qNameToexplodedQName(typeMipQName));
                            }

                            sb.append('}');
                        }
                    }
                }
            }
        }
        // Handle variables
        sb.append("},\"variables\": {");
        {
            // Iterate through models
            for (Iterator h = containingDocument.getModels().iterator(); h.hasNext();) {
                final XFormsModel currentModel = (XFormsModel) h.next();

                // Iterate through variables
                // NOTE: We assume top-level or single-node context binds
                final Map variables = currentModel.getBinds().getVariables(null);
                boolean controlFound = false;
                for (Iterator i = variables.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String currentVariableName = (String) currentEntry.getKey();
                    final SequenceExtent currentSequenceExtent = (SequenceExtent) currentEntry.getValue();

                    // Find controls bound to the bind exposed as a variable
                    final List currentNodeset = sequenceExtentToList(currentSequenceExtent);
                    final List boundControls = getBoundControls(idsToXFormsControls, currentNodeset);
                    if (boundControls.size() > 0) {
                        // NOTE: We only handle the first control found
                        final String effectiveControlId = ((XFormsControl) boundControls.get(0)).getEffectiveId();
                        controlFound = appendNameValue(sb, controlFound, currentVariableName, effectiveControlId);
                    }
                }
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static final List sequenceExtentToList(SequenceExtent sequenceExtent) {
        try {
            final List result = new ArrayList(sequenceExtent.getLength());
            final SequenceIterator si = sequenceExtent.iterate(null);
            for (Item item = si.next(); item != null; item = si.next()) {
                result.add(item);
            }
            return result;
        } catch (XPathException e) {
            // Should not happen with SequenceExtent
            throw new OXFException(e);
        }
    }

    private static List getBoundControls(Map idsToXFormsControls, List nodeset) {
        final List result = new ArrayList();

        // Iterate through nodeset
        for (Iterator j = nodeset.iterator(); j.hasNext();) {
            final NodeInfo currentNodeInfo = (NodeInfo) j.next();
            // Iterate through controls
            for (Iterator k = idsToXFormsControls.entrySet().iterator(); k.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) k.next();
                final XFormsControl currentControl = (XFormsControl) currentEntry.getValue();

                // Only check value controls
                final NodeInfo boundNode = currentControl.getBoundNode();
                if (currentControl instanceof XFormsValueControl && boundNode != null) {
                    if (boundNode.isSameNodeInfo(currentNodeInfo)) {
                        // There is a match
                        result.add(currentControl);
                    }
                }
            }
        }
        return result;
    }

    private static final boolean appendNameValue(FastStringBuffer sb, boolean found, String name, String value) {
        if (value != null) {
            if (found)
                sb.append(',');

            sb.append("\"");
            sb.append(name);
            sb.append("\": \"");
            sb.append(value);
            sb.append('"');
            return true;
        } else {
            return found;
        }
    }

    /**
     * Iterate over all binds and for each one do the callback.
     */
    private void iterateBinds(PipelineContext pipelineContext, BindRunner bindRunner) {
        // Iterate over top-level binds
        for (Iterator i = topLevelBinds.iterator(); i.hasNext();) {
            final Bind currentBind = (Bind) i.next();
            try {
                currentBind.applyBinds(pipelineContext, bindRunner);
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(currentBind.getLocationData(), "evaluating XForms binds", currentBind.getBindElement()));
            }
        }
    }

    private void handleCalculateBinds(final PipelineContext pipelineContext, Bind bind, List nodeset, int position) {
        // Handle calculate MIP
        if (bind.getCalculate() != null) {
            // Compute calculated value
            try {
                final NodeInfo currentNodeInfo = (NodeInfo) nodeset.get(position - 1);

                final String stringResult = XPathCache.evaluateAsString(pipelineContext, nodeset, position, bind.getCalculate(),
                        containingDocument.getNamespaceMappings(bind.getBindElement()), getVariables(currentNodeInfo),
                        XFormsContainingDocument.getFunctionLibrary(), contextStack.getFunctionContext(),
                        bind.getLocationData().getSystemID(), bind.getLocationData());

                // TODO: Detect if we have already handled this node and dispatch xforms-binding-exception
                // TODO: doSetValue may dispatch an xforms-binding-exception. It should reach the bind, but we don't support that yet so pass the model.
                XFormsSetvalueAction.doSetValue(pipelineContext, containingDocument, model, currentNodeInfo, stringResult, null, true);

            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms calculate bind",
                        bind.getBindElement(), new String[] { "expression", bind.getCalculate() }));
            }
        }
    }

    private Map getVariables(NodeInfo contextNodeInfo) {

        if (variableNamesToIds.size() > 0) {
            final Map bindVariablesValues = new HashMap();

            // Add bind variables
            for (Iterator i = variableNamesToIds.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String currentVariableName = (String) currentEntry.getKey();
                final String currentBindId = (String) currentEntry.getValue();

                final List currentBindNodeset = getBindNodeset(currentBindId, contextNodeInfo);
                bindVariablesValues.put(currentVariableName, new SequenceExtent(currentBindNodeset));
            }

            // Combine bind variables with model variables
            return new CompositeMap(bindVariablesValues, contextStack.getCurrentVariables());
        } else {
            // Just return the regular variables in scope
            return contextStack.getCurrentVariables();
        }
    }

    private void handleComputedExpressionBinds(PipelineContext pipelineContext, Bind bind, List nodeset, int position) {

        final NodeInfo currentNodeInfo = (NodeInfo) nodeset.get(position - 1);
        final Map currentVariables = getVariables(currentNodeInfo);

        // Handle required MIP
        if (bind.getRequired() != null) {
            // Evaluate "required" XPath expression on this node
            try {
                // Get MIP value
                final String xpath = "boolean(" + bind.getRequired() + ")";
                final boolean required = ((Boolean) XPathCache.evaluateSingle(pipelineContext,
                    nodeset, position, xpath, containingDocument.getNamespaceMappings(bind.getBindElement()), currentVariables,
                    XFormsContainingDocument.getFunctionLibrary(), contextStack.getFunctionContext(), bind.getLocationData().getSystemID(), bind.getLocationData())).booleanValue();

                // Update node with MIP value
                InstanceData.setRequired(currentNodeInfo, required);
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms required bind",
                        bind.getBindElement(), new String[] { "expression", bind.getRequired() }));
            }
        }

        // Handle relevant MIP
        if (bind.getRelevant() != null) {
                // Evaluate "relevant" XPath expression on this node
                try {
                    final String xpath = "boolean(" + bind.getRelevant() + ")";
                    boolean relevant = ((Boolean) XPathCache.evaluateSingle(pipelineContext,
                        nodeset, position, xpath, containingDocument.getNamespaceMappings(bind.getBindElement()), currentVariables,
                        XFormsContainingDocument.getFunctionLibrary(), contextStack.getFunctionContext(), bind.getLocationData().getSystemID(), bind.getLocationData())).booleanValue();
                    // Mark node
                    InstanceData.setRelevant(currentNodeInfo, relevant);
                } catch (Exception e) {
                    throw ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms relevant bind",
                            bind.getBindElement(), new String[] { "expression", bind.getRelevant() }));
                }
        }

        // Handle readonly MIP
        if (bind.getReadonly() != null) {
            // The bind has a readonly attribute
            // Evaluate "readonly" XPath expression on this node
            try {
                final String xpath = "boolean(" + bind.getReadonly() + ")";
                boolean readonly = ((Boolean) XPathCache.evaluateSingle(pipelineContext,
                    nodeset, position, xpath, containingDocument.getNamespaceMappings(bind.getBindElement()), currentVariables,
                    XFormsContainingDocument.getFunctionLibrary(), contextStack.getFunctionContext(), bind.getLocationData().getSystemID(), bind.getLocationData())).booleanValue();

                // Mark node
                InstanceData.setReadonly(currentNodeInfo, readonly);
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms readonly bind",
                        bind.getBindElement(), new String[] { "expression", bind.getReadonly() }));
            }
        } else if (bind.getCalculate() != null) {
            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
            // Mark node
            InstanceData.setReadonly(currentNodeInfo, true);
        }

        // Handle xxforms:externalize bind
        if (bind.getXXFormsExternalize() != null) {
            // The bind has an xxforms:externalize attribute
            // Evaluate "externalize" XPath expression on this node
            try {
                final String xpath = "boolean(" + bind.getXXFormsExternalize() + ")";
                boolean xxformsExternalize = ((Boolean) XPathCache.evaluateSingle(pipelineContext,
                    nodeset, position, xpath, containingDocument.getNamespaceMappings(bind.getBindElement()), currentVariables,
                    XFormsContainingDocument.getFunctionLibrary(), contextStack.getFunctionContext(), bind.getLocationData().getSystemID(), bind.getLocationData())).booleanValue();

                // Mark node
                InstanceData.setXXFormsExternalize(currentNodeInfo, xxformsExternalize);
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms xxforms:externalize bind",
                        bind.getBindElement(), new String[] { "expression", bind.getXXFormsExternalize() }));
            }
        }
    }

    private void handleValidationBind(PipelineContext pipelineContext, Bind bind, List nodeset, int position) {

        final NodeInfo currentNodeInfo = (NodeInfo) nodeset.get(position - 1);

        // Handle XPath constraint MIP
        if (bind.getConstraint() != null) {
            // Evaluate constraint
            try {
                // Get MIP value
                final String xpath = "boolean(" + bind.getConstraint() + ")";
                final Boolean valid = (Boolean) XPathCache.evaluateSingle(pipelineContext,
                    nodeset, position, xpath, containingDocument.getNamespaceMappings(bind.getBindElement()), getVariables(currentNodeInfo),
                    XFormsContainingDocument.getFunctionLibrary(), contextStack.getFunctionContext(), bind.getLocationData().getSystemID(), bind.getLocationData());

                // Update node with MIP value
                InstanceData.updateConstraint(currentNodeInfo, valid.booleanValue());
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms constraint bind",
                        bind.getBindElement(), new String[] { "expression", bind.getConstraint() }));
            }
        }

        // Handle type MIP
        if (bind.getType() != null) {

            // "The type model item property is not applied to instance nodes that contain child elements"
            if (currentNodeInfo.getNodeKind() != org.w3c.dom.Document.ELEMENT_NODE || !XFormsUtils.hasChildrenElements(currentNodeInfo)) {

                // Need an evaluator to check and convert type below
                final XPathEvaluator xpathEvaluator;
                try {
                    xpathEvaluator = new XPathEvaluator();
                    // NOTE: Not sure declaring namespaces here is necessary just to perform the cast
                    final IndependentContext context = xpathEvaluator.getStaticContext();
                    final Map namespaceMap = containingDocument.getNamespaceMappings(bind.getBindElement());
                    for (Iterator j = namespaceMap.keySet().iterator(); j.hasNext();) {
                        final String prefix = (String) j.next();
                        context.declareNamespace(prefix, (String) namespaceMap.get(prefix));
                    }
                } catch (Exception e) {
                    throw ValidationException.wrapException(e, bind.getLocationData());
                }

                {
                    // Get type namespace and local name
                    final String typeQName = bind.getType();
                    final String typeNamespaceURI;
                    final String typeLocalname;
                    {
                        final int prefixPosition = typeQName.indexOf(':');
                        if (prefixPosition > 0) {
                            final String prefix = typeQName.substring(0, prefixPosition);
                            typeNamespaceURI = (String) containingDocument.getNamespaceMappings(bind.getBindElement()).get(prefix);
                            if (typeNamespaceURI == null)
                                throw new ValidationException("Namespace not declared for prefix '" + prefix + "'",
                                        bind.getLocationData());

                            typeLocalname = typeQName.substring(prefixPosition + 1);
                        } else {
                            typeNamespaceURI = "";
                            typeLocalname = typeQName;
                        }
                    }

                    // Get value to validate
                    final String nodeValue = XFormsInstance.getValueForNodeInfo(currentNodeInfo);

                    // TODO: "[...] these datatypes can be used in the type model item property without the addition of the
                    // XForms namespace qualifier if the namespace context has the XForms namespace as the default
                    // namespace."

                    final boolean isBuiltInSchemaType = XMLConstants.XSD_URI.equals(typeNamespaceURI);
                    final boolean isBuiltInXFormsType = XFormsConstants.XFORMS_NAMESPACE_URI.equals(typeNamespaceURI);
                    final boolean isBuiltInXXFormsType = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(typeNamespaceURI);

                    if (isBuiltInXFormsType && nodeValue.length() == 0) {
                        // Don't consider the node invalid if the string is empty with xforms:* types
                    } else if (isBuiltInSchemaType || isBuiltInXFormsType) {
                        // Built-in schema or XForms type

                        // Use XML Schema namespace URI as Saxon doesn't know anytyhing about XForms types
                        final String newTypeNamespaceURI = XMLConstants.XSD_URI;

                        // Get type information
                        final int requiredTypeFingerprint = StandardNames.getFingerprint(newTypeNamespaceURI, typeLocalname);
                        if (requiredTypeFingerprint == -1) {
                            throw new ValidationException("Invalid schema type '" + bind.getType() + "'", bind.getLocationData());
                        }

                        // Try to perform casting
                        // TODO: Should we actually perform casting? This for example removes leading and trailing space around tokens. Is that expected?
                        final StringValue stringValue = new StringValue(nodeValue);
                        final XPathContext xpContext = new XPathContextMajor(stringValue, xpathEvaluator.getStaticContext().getConfiguration());
                        final AtomicValue result = stringValue.convertPrimitive((BuiltInAtomicType) BuiltInSchemaFactory.getSchemaType(requiredTypeFingerprint), true, xpContext);

                        // Set error on node if necessary
                        if (result instanceof ValidationErrorValue) {
                            InstanceData.updateValueValid(currentNodeInfo, false, bind.getId());
                        }
                    } else if (isBuiltInXXFormsType) {
                        // Built-in extension types

                        if (typeLocalname.equals("xml")) {
                            // xxforms:xml type

                            if (!XMLUtils.isWellFormedXML(nodeValue))
                                InstanceData.updateValueValid(currentNodeInfo, false, bind.getId());

                        } else {
                            throw new ValidationException("Invalid schema type '" + bind.getType() + "'", bind.getLocationData());
                        }

                    } else if (model.getSchemaValidator() != null) {
                        // Other type and there is a schema

                        // There are possibly types defined in the schema
                        final String validationError
                                = model.getSchemaValidator().validateDatatype(currentNodeInfo, nodeValue, typeNamespaceURI, typeLocalname, typeQName, bind.getLocationData(), bind.getId());

                        // Set error on node if necessary
                        if (validationError != null) {
                            InstanceData.addSchemaError(currentNodeInfo, validationError, nodeValue, bind.getId());
                        }
                    } else {
                        throw new ValidationException("Invalid schema type '" + bind.getType() + "'", bind.getLocationData());
                    }

                    // Set type on node
                    InstanceData.setType(currentNodeInfo, XMLUtils.buildExplodedQName(typeNamespaceURI, typeLocalname));
                }
            }
        }
    }

    private static interface BindRunner {
        public void applyBind(PipelineContext pipelineContext, Bind bind, List nodeset, int position);
    }

    private class Bind {

        private Element bindElement;
        private String id;          // bind id
        private String name;        // bind name
        private List nodeset;       // actual nodeset for this bind

        private List childrenIterations; // List<BindIteration>

        public Bind(PipelineContext pipelineContext, Element bindElement, boolean isSingleNodeContext) {
            this.bindElement = bindElement;
            this.id = bindElement.attributeValue("id");
            this.name = bindElement.attributeValue("name");

            // Remember variables
            if (name != null)
                variableNamesToIds.put(name, id);

            // If this bind is marked for offline handling, remember it
            if ("true".equals(bindElement.attributeValue(XFormsConstants.XXFORMS_OFFLINE_QNAME)))
                offlineBinds.add(this);

            // Compute nodeset for this bind
            contextStack.pushBinding(pipelineContext, bindElement);
            {
                this.nodeset = contextStack.getCurrentNodeset();
                final int nodesetSize = nodeset.size();

                // "4.7.2 References to Elements within a bind Element [...] If a target bind element is outermost, or if
                // all of its ancestor bind elements have nodeset attributes that select only one node, then the target bind
                // only has one associated bind object, so this is the desired target bind object whose nodeset is used in
                // the Single Node Binding or Node Set Binding"
                if (isSingleNodeContext)
                    singleNodeContextBinds.put(id, this);

                final List childElements = bindElement.elements(new QName("bind", XFormsConstants.XFORMS_NAMESPACE));
                if (childElements.size() > 0) {
                    // There are children binds
                    childrenIterations = new ArrayList();

                    // Iterate over nodeset and produce child iterations
                    for (int currentPosition = 1; currentPosition <= nodesetSize; currentPosition++) {
                        contextStack.pushIteration(currentPosition);
                        {
                            // Create iteration and remember it
                            final boolean isNewSingleNodeContext = isSingleNodeContext && nodesetSize == 1;
                            final BindIteration currentBindIteration = new BindIteration(pipelineContext, isNewSingleNodeContext, childElements);
                            childrenIterations.add(currentBindIteration);

                            // Create mapping context node -> iteration
                            final NodeInfo iterationNodeInfo = (NodeInfo) nodeset.get(currentPosition - 1);
                            List iterations = (List) iterationsForContextNodeInfo.get(iterationNodeInfo);
                            if (iterations == null) {
                                iterations = new ArrayList();
                                iterationsForContextNodeInfo.put(iterationNodeInfo, iterations);
                            }
                            iterations.add(currentBindIteration);

                        }
                        contextStack.popBinding();
                    }
                }
            }
            contextStack.popBinding();
        }

        public void applyBinds(PipelineContext pipelineContext, BindRunner bindRunner) {
            if (nodeset != null && nodeset.size() > 0) {
                // Handle each node in this node-set
                final Iterator j = (childrenIterations != null) ? childrenIterations.iterator() : null;

                for (int index = 1; index <= nodeset.size(); index++) {
                    final BindIteration currentBindIteration = (BindIteration) ((j != null) ? j.next() : null);

                    // Handle curent node
                    bindRunner.applyBind(pipelineContext, this, nodeset, index);

                    // Handle children iterations if any
                    if (currentBindIteration != null) {
                        currentBindIteration.applyBinds(pipelineContext, bindRunner);
                    }
                }
            }
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List getNodeset() {
            return nodeset;
        }

        public LocationData getLocationData() {
            return new ExtendedLocationData((LocationData) bindElement.getData(), "xforms:bind element", bindElement);
        }

        public Element getBindElement() {
            return bindElement;
        }

        public String getRelevant() {
            return bindElement.attributeValue("relevant");
        }

        public String getCalculate() {
            return bindElement.attributeValue("calculate");
        }

        public String getType() {
            return bindElement.attributeValue("type");
        }

        public String getConstraint() {
            return bindElement.attributeValue("constraint");
        }

        public String getRequired() {
            return bindElement.attributeValue("required");
        }

        public String getReadonly() {
            return bindElement.attributeValue("readonly");
        }

        public String getXXFormsExternalize() {
            return bindElement.attributeValue(XFormsConstants.XXFORMS_EXTERNALIZE_QNAME);
        }
    }

    private class BindIteration {

        private List childrenBinds; // List<Bind>

        public BindIteration(PipelineContext pipelineContext, boolean isSingleNodeContext, List childElements) {

            if (childElements.size() > 0) {
                // There are child elements
                childrenBinds = new ArrayList();

                // Iterate over child elements and create children binds
                for (Iterator i = childElements.iterator(); i.hasNext();) {
                    final Element currentBindElement = (Element) i.next();
                    final Bind currentBind = new Bind(pipelineContext, currentBindElement, isSingleNodeContext);
                    childrenBinds.add(currentBind);
                }
            }
        }

        public void applyBinds(PipelineContext pipelineContext, BindRunner bindRunner) {
            for (Iterator i = childrenBinds.iterator(); i.hasNext();) {
                final Bind currentBind = (Bind) i.next();
                currentBind.applyBinds(pipelineContext, bindRunner);
            }
        }

        public Bind getBind(String bindId) {
            for (Iterator i = childrenBinds.iterator(); i.hasNext();) {
                final Bind currentBind = (Bind) i.next();
                if (currentBind.getId().equals(bindId))
                    return currentBind;
            }
            return null;
        }
    }
}

