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

import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xforms.xbl.XBLBindings;

public class RootAnalysis extends ContainerAnalysis {
    public RootAnalysis(PropertyContext propertyContext, XFormsStaticState staticState, XBLBindings.Scope scope) {
        super(propertyContext, staticState, null, scope, "#controls", null, staticState.getLocationData(), 1, false, false, null, null);
    }

    @Override
    protected String computeModelPrefixedId() {
        return staticState.getDefaultModelId();
    }

    @Override
    protected XPathAnalysis computeBindingAnalysis() {
        if (staticState.getDefaultModelId() != null) {
            final String defaultInstanceId = staticState.getDefaultInstanceId();
            if (defaultInstanceId != null) {
                // Start with instance('defaultInstanceId')
                return analyzeXPath(staticState, null, prefixedId, XPathAnalysis.buildInstanceString(defaultInstanceId));
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}
