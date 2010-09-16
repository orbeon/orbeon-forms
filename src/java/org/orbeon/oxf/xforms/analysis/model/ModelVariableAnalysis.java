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

import org.dom4j.Element;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.ViewAnalysis;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.ContentHandlerHelper;

import java.util.Map;

public class ModelVariableAnalysis extends ModelAnalysis {

    public final String name;

    public ModelVariableAnalysis(XFormsStaticState staticState, XBLBindings.Scope scope, Element element, SimpleAnalysis parentAnalysis,
                                 Map<String, SimpleAnalysis> inScopeVariables, Model containingModel) {
        super(staticState, scope, element, parentAnalysis, inScopeVariables, true, containingModel);

        this.name = element.attributeValue("name");
    }

    // NEED A TRAIT FOR THIS: duplicated in VariableAnalysis
    @Override
    protected XPathAnalysis computeValueAnalysis() {

        final Element sequenceElement = element.element(XFormsConstants.XXFORMS_SEQUENCE_QNAME);
        if (sequenceElement != null) {
            // Value is provided by nested xxf:sequence/@select

            // First figure out the scope for xxf:sequence
            final String sequencePrefixedId =  XFormsUtils.getRelatedEffectiveId(prefixedId, XFormsUtils.getElementStaticId(sequenceElement));
            final XBLBindings.Scope sequenceScope = staticState.getXBLBindings().getResolutionScopeByPrefixedId(sequencePrefixedId);

            final ViewAnalysis sequenceAnalysis = new ViewAnalysis(staticState, sequenceScope, sequenceElement, this, getInScopeVariables(), false) {
                @Override
                protected XPathAnalysis computeValueAnalysis() {
                    final String selectAttribute = element.attributeValue("select");
                    if (selectAttribute != null) {
                        // Value is provided by @select
                        final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis(this);
                        return analyzeXPath(staticState, baseAnalysis, prefixedId, selectAttribute);
                    } else {
                        // Value is constant
                        return XPathAnalysis.CONSTANT_ANALYSIS;
                    }
                }
            };
            sequenceAnalysis.analyzeXPath();
            return sequenceAnalysis.getValueAnalysis();
        } else {
            final String selectAttribute = element.attributeValue("select");
            if (selectAttribute != null) {
                // Value is provided by @select
                final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis(this);
                return analyzeXPath(staticState, baseAnalysis, prefixedId, selectAttribute);
            } else {
                // Value is constant
                return XPathAnalysis.CONSTANT_ANALYSIS;
            }
        }
    }

    // NEED A TRAIT FOR THIS: duplicated in VariableAnalysis
    public void toXML(PropertyContext propertyContext, ContentHandlerHelper helper) {
        helper.startElement("variable", new String[] {
                "scope", scope.scopeId,
                "prefixed-id", prefixedId,
                "model-prefixed-id", getModelPrefixedId(),
                "binding", Boolean.toString(hasNodeBinding),
                "value", Boolean.toString(canHoldValue),
                "name", name
        });

        // Control binding and value analysis
        if (getBindingAnalysis() != null && hasNodeBinding) {// NOTE: for now there can be a binding analysis even if there is no binding on the control (hack to simplify determining which controls to update)
            helper.startElement("binding");
            getBindingAnalysis().toXML(propertyContext, helper);
            helper.endElement();
        }
        if (getValueAnalysis() != null) {
            helper.startElement("value");
            getValueAnalysis().toXML(propertyContext, helper);
            helper.endElement();
        }

        helper.endElement();
    }
}
