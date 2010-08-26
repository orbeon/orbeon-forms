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

    public final XBLBindings.Scope scope;
    public final Document document;

    public final Map<String, Instance> instances;

    public final String staticId;
    public final String prefixedId;
    public final String defaultInstanceStaticId;
    public final String defaultInstancePrefixedId;

    public final List<Element> bindElements;
    public final Set<String> bindIds;
    public final Map<String, Map<String, String>> customMIPs;
    public final boolean figuredBindAnalysis;
    public final Set<String> bindInstances;
    public final Set<String> computedBindExpressionsInstances;
    public final Set<String> validationBindInstances;

    public Model(XFormsStaticState staticState, XBLBindings.Scope scope, Document document) {

        assert scope != null;
        assert document != null;

        this.staticState = staticState;
        this.scope = scope;
        this.document = document;

        final List<Element> instanceElements = Dom4jUtils.elements(document.getRootElement(), XFormsConstants.XFORMS_INSTANCE_QNAME);
        instances = new LinkedHashMap<String, Instance>(instanceElements.size());

        for (final Element instanceElement: instanceElements) {
            final Instance newInstance = new Instance(instanceElement);
            instances.put(newInstance.staticId, newInstance);
        }

        staticId = XFormsUtils.getElementStaticId(document.getRootElement());
        prefixedId = scope.getFullPrefix() + staticId;
        final boolean hasInstances = instances.size() > 0;
        defaultInstanceStaticId = hasInstances  ? instances.keySet().iterator().next() : null;
        defaultInstancePrefixedId = hasInstances ? scope.getFullPrefix() + defaultInstanceStaticId : null;

        bindElements = Dom4jUtils.elements(document.getRootElement(), XFormsConstants.XFORMS_BIND_QNAME);
        if (hasBinds()) {
            // Analyse binds
            bindIds = new HashSet<String>();
            customMIPs = new HashMap<String, Map<String, String>>();
            bindInstances = new HashSet<String>();
            computedBindExpressionsInstances = new HashSet<String>();
            validationBindInstances = new HashSet<String>();
            figuredBindAnalysis = analyzeBinds(bindElements) && staticState.isXPathAnalysis();
            if (!figuredBindAnalysis) {
                bindInstances.clear();
                computedBindExpressionsInstances.clear();
                validationBindInstances.clear();
            }
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

    private boolean analyzeBinds(List<Element> bindElements) {
        final List<SimpleAnalysis> stack = new ArrayList<SimpleAnalysis>();
        stack.add(new SimpleAnalysis(staticState, scope, null, null, null, false) {
            @Override
            protected String computeModelPrefixedId() {
                return Model.this.prefixedId;
            }

            @Override
            protected String getDefaultInstancePrefixedId() {
                return Model.this.defaultInstancePrefixedId;
            }

            @Override
            protected XPathAnalysis computeBindingAnalysis(Element element) {
                if (defaultInstancePrefixedId != null) {
                    // Start with instance('defaultInstanceId')
                    return analyzeXPath(staticState, null, modelPrefixedId, XPathAnalysis.buildInstanceString(defaultInstancePrefixedId));
                } else {
                    return null;
                }
            }
        });
        return analyzeBinds(bindElements, stack);
    }

    private boolean analyzeBinds(List<Element> bindElements, List<SimpleAnalysis> stack) {
        boolean result = true;
        for (final Element element: bindElements) {
            // Add id of this element
            final String staticId = XFormsUtils.getElementStaticId(element);
            bindIds.add(staticId);

            // See if there are custom MIPs
            final boolean hasCustomMIP = processCustomMIPs(staticId, element);

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
                    final SimpleAnalysis analysis = new SimpleAnalysis(staticState, scope, element, stack.get(stack.size() - 1), null, false) {
                        @Override
                        protected String getDefaultInstancePrefixedId() {
                            return Model.this.defaultInstancePrefixedId;
                        }
                    };
                    if (analysis.bindingAnalysis != null && analysis.bindingAnalysis.figuredOutDependencies) {
                        // Analysis succeeded

                        // Add instances
                        final Set<String> returnableInstances = analysis.bindingAnalysis.returnableInstances;
                        bindInstances.addAll(returnableInstances);
                        if (hasCustomMIP || hasCalculateComputedBind(element))
                            computedBindExpressionsInstances.addAll(returnableInstances);

                        if (hasValidateBind(element))
                            validationBindInstances.addAll(returnableInstances);

                        // Recurse to find nested bind elements
                        stack.add(analysis);
                        result &= analyzeBinds(Dom4jUtils.elements(element, XFormsConstants.XFORMS_BIND_QNAME), stack);
                        stack.remove(stack.size() - 1);
                    } else {
                        // Analysis failed
                        result = false;
                    }
                } else {
                    // Just ignore this xforms:bind
                    // Recurse to find nested bind elements
                    result &= analyzeBinds(Dom4jUtils.elements(element, XFormsConstants.XFORMS_BIND_QNAME), stack);
                }
            } else {
                // Recurse to find nested bind elements
                analyzeBinds(Dom4jUtils.elements(element, XFormsConstants.XFORMS_BIND_QNAME), stack);
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

    public void toXML(PropertyContext propertyContext, ContentHandlerHelper helper) {
        helper.startElement("model", new String[] {
                "scope", scope.scopeId,
                "prefixed-id", prefixedId,
                "default-instance-prefixed-id", defaultInstancePrefixedId,
                "analyzed-binds", Boolean.toString(figuredBindAnalysis),

        });

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
}
