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
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.ContentHandlerHelper;

import java.util.Map;

public class ModelVariableAnalysis extends ModelAnalysis {

    public final String name;

    public ModelVariableAnalysis(XFormsStaticState staticState, XBLBindings.Scope scope, Element element, SimpleAnalysis parentAnalysis,
                                 Map<String, SimpleAnalysis> inScopeVariables, String modelPrefixedId, String defaultInstancePrefixedId) {
        super(staticState, scope, element, parentAnalysis, inScopeVariables, true, modelPrefixedId, defaultInstancePrefixedId);

        this.name = element.attributeValue("name");
    }

    @Override
    protected XPathAnalysis computeValueAnalysis() {
        // TODO: handle xxf:sequence
        final String selectAttribute = element.attributeValue("select");
        if (selectAttribute != null) {
            final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis(true);
            return new XPathAnalysis(staticState, selectAttribute, staticState.getMetadata().getNamespaceMapping(prefixedId),
                    baseAnalysis, parentAnalysis.getInScopeVariables(), scope, getModelPrefixedId(), getDefaultInstancePrefixedId(), locationData, element);
        } else {
            // Value is constant
            return XPathAnalysis.CONSTANT_ANALYSIS;
        }
    }

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
        if (getBindingAnalysis() != null) {
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
