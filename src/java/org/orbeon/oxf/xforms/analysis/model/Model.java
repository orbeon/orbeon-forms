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
package org.orbeon.oxf.xforms.analysis.model;

import org.dom4j.*;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.*;

/**
 * Static analysis of an XForms model.
 */
public class Model {

    private final XFormsStaticState staticState;

    // TODO: ideally this should not be kept as a model variable: attributes should be extracted and copied etc.
    public final Document document;

    // Scope and ids
    public final XBLBindings.Scope scope;
    public final String staticId;
    public final String prefixedId;

    // Instances
    public final Map<String, Instance> instances;
    public final String defaultInstanceStaticId;
    public final String defaultInstancePrefixedId;

    // Variables
    public Map<String, SimpleAnalysis> variables;

    // Binds
    public List<Element> bindElements;
    public Set<String> bindIds;
    public Map<String, Map<String, String>> customMIPs; // staticId -> MIP mapping
    public boolean figuredBindAnalysis;
    public Set<String> bindInstances;
    public Set<String> computedBindExpressionsInstances;
    public Set<String> validationBindInstances;

    public Model(XFormsStaticState staticState, XBLBindings.Scope scope, Document document) {

        assert scope != null;
        assert document != null;

        this.staticState = staticState;
        this.document = document;

        this.scope = scope;
        staticId = XFormsUtils.getElementStaticId(document.getRootElement());
        prefixedId = scope.getFullPrefix() + staticId;

        final List<Element> instanceElements = Dom4jUtils.elements(document.getRootElement(), XFormsConstants.XFORMS_INSTANCE_QNAME);
        instances = new LinkedHashMap<String, Instance>(instanceElements.size());
        for (final Element instanceElement: instanceElements) {
            final Instance newInstance = new Instance(instanceElement, scope);
            instances.put(newInstance.staticId, newInstance);
        }

        final boolean hasInstances = instances.size() > 0;
        defaultInstanceStaticId = hasInstances  ? instances.keySet().iterator().next() : null;
        defaultInstancePrefixedId = hasInstances ? scope.getFullPrefix() + defaultInstanceStaticId : null;

        // Handle variables
        {
            final List<Element> variableElements = new ArrayList<Element>(); {
                for (final Element element : Dom4jUtils.elements(document.getRootElement())) {
                    if (element.getName().equals(XFormsConstants.XXFORMS_VARIABLE_NAME)) {
                        // Add xxforms:variable and exforms:variable (in fact *:variable)
                        variableElements.add(element);
                    }
                }
            }

            if (variableElements.size() > 0) {
                // Root analysis for model
                final SimpleAnalysis modelRootAnalysis = new ModelAnalysis(staticState, scope, null, null, Collections.<String, SimpleAnalysis>emptyMap(), false, this) {

                    @Override
                    protected XPathAnalysis computeBindingAnalysis(Element element) {
                        if (defaultInstancePrefixedId != null) {
                            // Start with instance('defaultInstanceId')
                            return analyzeXPath(staticState, null, getModelPrefixedId(), XPathAnalysis.buildInstanceString(defaultInstancePrefixedId));
                        } else {
                            return null;
                        }
                    }
                };

                // Iterate and resolve all variables in order
                variables = new LinkedHashMap<String, SimpleAnalysis>();
                for (final Element element : variableElements) {
                    // TODO: Here all variables are passed with the same Map of in-scope variables. In the end, all variables see all other variables, which is not correct.
                    final ModelVariableAnalysis currentVariableAnalysis = new ModelVariableAnalysis(staticState, scope, element, modelRootAnalysis, variables, this);
                    variables.put(currentVariableAnalysis.name, currentVariableAnalysis);
                }
            } else {
                variables = Collections.emptyMap();
            }
        }

        // Handle binds
        {
            // TODO: use and produce variables introduced with xf:bind/@name

            bindElements = Dom4jUtils.elements(document.getRootElement(), XFormsConstants.XFORMS_BIND_QNAME);
            if (hasBinds()) {
                // Analyse binds
                bindIds = new LinkedHashSet<String>();
                customMIPs = new LinkedHashMap<String, Map<String, String>>();

                analyzeBinds(bindElements);

                bindInstances = new LinkedHashSet<String>();
                computedBindExpressionsInstances = new LinkedHashSet<String>();
                validationBindInstances = new LinkedHashSet<String>();

            } else {
                // Easy case to figure out
                bindIds = Collections.emptySet();
                customMIPs = Collections.emptyMap();
                figuredBindAnalysis = true;
                bindInstances = Collections.emptySet();
                computedBindExpressionsInstances = Collections.emptySet();
                validationBindInstances = Collections.emptySet();
            }
        }
    }

    private void analyzeBinds(List<Element> bindElements) {
        for (final Element element : bindElements) {

            // Add id of this element
            final String bindStaticId = XFormsUtils.getElementStaticId(element);
            bindIds.add(bindStaticId);

            // See if there are custom MIPs
            processCustomMIPs(bindStaticId, element);

            // Recurse to find nested bind elements
            analyzeBinds(Dom4jUtils.elements(element, XFormsConstants.XFORMS_BIND_QNAME));
        }
    }

    public  void analyzeXPath() {
        // Variables
        for (final SimpleAnalysis variableAnalysis : variables.values()) {
            variableAnalysis.analyzeXPath();
        }
        // Binds
        figuredBindAnalysis = analyzeBindsXPath(bindElements);
        if (!figuredBindAnalysis) {
            bindInstances.clear();
            computedBindExpressionsInstances.clear();
            validationBindInstances.clear();
        }
    }

    private boolean analyzeBindsXPath(List<Element> bindElements) {
        final List<SimpleAnalysis> stack = new ArrayList<SimpleAnalysis>();
        stack.add(new ModelAnalysis(staticState, scope, document.getRootElement(), null, variables, false, this) {

            @Override
            protected XPathAnalysis computeBindingAnalysis(Element element) {
                if (defaultInstancePrefixedId != null) {
                    // Start with instance('defaultInstanceId')
                    return analyzeXPath(staticState, null, getModelPrefixedId(), XPathAnalysis.buildInstanceString(defaultInstancePrefixedId));
                } else {
                    return null;
                }
            }
        });
        return analyzeBindsXPath(bindElements, stack);
    }

    private boolean analyzeBindsXPath(List<Element> bindElements, List<SimpleAnalysis> stack) {
        boolean result = true;
        for (final Element element: bindElements) {

            if (staticState.isXPathAnalysis()) {
                // Figure out instance dependencies if possible
                // TODO: model variables
                // TODO: handle @context
                final String bindingExpression;
                if (element.attributeValue("context") == null) {
                    bindingExpression = SimpleAnalysis.getBindingExpression(element);
                } else {
                    bindingExpression = null;
                }

                if (bindingExpression != null) {
                    // Analyze binding
                    final SimpleAnalysis analysis = new ModelAnalysis(staticState, scope, element, stack.get(stack.size() - 1), null, false, Model.this);
                    analysis.analyzeXPath();// evaluate aggressively

                    if (analysis.getBindingAnalysis() != null && analysis.getBindingAnalysis().figuredOutDependencies) {
                        // Analysis succeeded

                        // Add instances
                        final Set<String> returnableInstances = analysis.getBindingAnalysis().returnableInstances;
                        bindInstances.addAll(returnableInstances);
                        if (customMIPs.containsKey(XFormsUtils.getElementStaticId(element)) || hasCalculateComputedBind(element))
                            computedBindExpressionsInstances.addAll(returnableInstances);

                        if (hasValidateBind(element))
                            validationBindInstances.addAll(returnableInstances);

                        // Recurse to find nested bind elements
                        stack.add(analysis);
                        result &= analyzeBindsXPath(Dom4jUtils.elements(element, XFormsConstants.XFORMS_BIND_QNAME), stack);
                        stack.remove(stack.size() - 1);
                    } else {
                        // Analysis failed
                        result = false;
                    }
                } else {
                    // Just ignore this xforms:bind
                    // Recurse to find nested bind elements
                    result &= analyzeBindsXPath(Dom4jUtils.elements(element, XFormsConstants.XFORMS_BIND_QNAME), stack);
                }
            } else {
                // Recurse to find nested bind elements
                analyzeBindsXPath(Dom4jUtils.elements(element, XFormsConstants.XFORMS_BIND_QNAME), stack);
            }
        }
        return result;
    }

    public boolean hasBinds() {
        return bindElements != null && bindElements.size() > 0;
    }

    public boolean containsBind(String bindId) {
        return bindIds.contains(bindId);
    }

    private boolean hasCalculateComputedBind(Element bindElement) {
        return bindElement.attributeValue(XFormsConstants.RELEVANT_QNAME) != null
                || bindElement.attributeValue(XFormsConstants.CALCULATE_QNAME) != null
                || bindElement.attributeValue(XFormsConstants.READONLY_QNAME) != null
                || bindElement.attributeValue(XFormsConstants.XXFORMS_DEFAULT_QNAME) != null;
    }

    private boolean hasValidateBind(Element bindElement) {
        return bindElement.attributeValue(XFormsConstants.TYPE_QNAME) != null
                || bindElement.attributeValue(XFormsConstants.CONSTRAINT_QNAME) != null
                || bindElement.attributeValue(XFormsConstants.REQUIRED_QNAME) != null;
    }

    private boolean processCustomMIPs(String staticId, Element bindElement) {
        boolean hasCustomMIP = false;
        for (Iterator iterator = bindElement.attributeIterator(); iterator.hasNext();) {
            final Attribute attribute = (Attribute) iterator.next();
            final QName attributeQName = attribute.getQName();
            final String attributePrefix = attributeQName.getNamespacePrefix();
            final String attributeURI = attributeQName.getNamespaceURI();
            // NOTE: Also allow for xxforms:events-mode extension MIP
            if (attributePrefix != null && attributePrefix.length() > 0
                    && !(attributeURI.equals(XFormsConstants.XFORMS_NAMESPACE_URI)
                             || (attributeURI.equals(XFormsConstants.XXFORMS_NAMESPACE_URI)
                                    && !attributeQName.getName().equals(XFormsConstants.XXFORMS_EVENT_MODE_QNAME.getName()))
                             || attributePrefix.startsWith("xml"))) {
                // Any QName-but-not-NCName which is not in the xforms or xxforms namespace

                Map<String, String> mipMapping = customMIPs.get(staticId);
                if (mipMapping == null) {
                    mipMapping = new HashMap<String, String>();
                    customMIPs.put(staticId, mipMapping);
                }
                // E.g. foo:bar="true()" => "foo-bar" -> "true()"
                mipMapping.put(buildCustomMIPName(attribute.getQualifiedName()), attribute.getValue());

                hasCustomMIP = true;
            }
        }
        return hasCustomMIP;
    }

    public static String buildCustomMIPName(String qualifiedName) {
        return qualifiedName.replace(':', '-');
    }

    public Instance getDefaultInstance() {
        return (instances.size() > 0) ? instances.values().iterator().next() : null;
    }

    public void toXML(PropertyContext propertyContext, ContentHandlerHelper helper) {
        helper.startElement("model", new String[] {
                "scope", scope.scopeId,
                "prefixed-id", prefixedId,
                "default-instance-prefixed-id", defaultInstancePrefixedId,
                "analyzed-binds", Boolean.toString(figuredBindAnalysis),

        });

        if (variables.size() > 0) {
            for (final Map.Entry<String, SimpleAnalysis> entry: variables.entrySet()) {
                ((ModelVariableAnalysis) entry.getValue()).toXML(propertyContext, helper);
            }
        }

        outputInstanceList(helper, "bind-instances", bindInstances);
        outputInstanceList(helper, "computed-binds-instances", computedBindExpressionsInstances);
        outputInstanceList(helper, "validation-binds-instances", validationBindInstances);

        helper.endElement();
    }

    private static void outputInstanceList(ContentHandlerHelper helper, String name, Set<String> values) {
        if (values.size() > 0) {
            helper.startElement(name);
            for (final String value : values) {
                helper.element("instance", value);
            }
            helper.endElement();
        }
    }

    public void freeTransientState() {
        for (final SimpleAnalysis analysis : variables.values()) {
            analysis.freeTransientState();
        }
    }
}
