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
package org.orbeon.oxf.xforms.analysis.controls;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.List;
import java.util.Map;

public class RootAnalysis extends ContainerAnalysis {
    public RootAnalysis(PropertyContext propertyContext, XFormsStaticState staticState) {
        super(propertyContext, staticState, null, "#controls", null, staticState.getLocationData(), 1, false, false, null, null);
    }

    @Override
    protected XPathAnalysis computeBindingAnalysis() {
        final Map<String, Document> modelDocuments = staticState.getModelDocuments();

        if (modelDocuments.size() > 0) {
            final Map.Entry<String, Document> entry = modelDocuments.entrySet().iterator().next();

            final String modelId = entry.getKey(); // TODO: use model
            final List<Element> instanceElements = Dom4jUtils.elements(entry.getValue().getRootElement(), XFormsConstants.XFORMS_INSTANCE_QNAME);
            if (instanceElements.size() > 0) {
                final String instanceId = instanceElements.get(0).attributeValue("id");

                // Start with instance('defaultInstanceId')
                return analyzeXPath(staticState, null, prefixedId, "instance('" + instanceId.replaceAll("'", "''") + "')");
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}
