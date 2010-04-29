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
package org.orbeon.oxf.common;

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.analysis.DumbXPathDependencies;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;

import java.util.HashSet;
import java.util.Set;

public class CEVersion extends Version {

    private static final Set<String> WARNED_FEATURES = new HashSet<String>();

    public boolean isPEFeatureEnabled(boolean featureRequested, String featureName) {
        if (featureRequested) {
            // Feature is requested but disallowed
            if (!WARNED_FEATURES.contains(featureName)) { // just warn the first time
                logger.warn("Feature is not enabled in this version of the product: " + featureName);
                WARNED_FEATURES.add(featureName);
            }
        }
        return false;
    }

    public XPathDependencies createUIDependencies(XFormsContainingDocument containingDocument) {
        return new DumbXPathDependencies();
    }
}
