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

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.saxon.om.NodeInfo;

/**
 * Interface to dependencies implementation.
 */
public interface XPathDependencies {
    void markValueChanged(XFormsModel model, NodeInfo nodeInfo);
    void markStructuralChange(XFormsModel model);
    void refreshDone();
    boolean requireBindingUpdate(String controlPrefixedId);
    boolean requireValueUpdate(String controlPrefixedId);
    boolean requireLHHAUpdate(XFormsConstants.LHHA lhha, String controlPrefixedId);
    boolean requireBindCalculation(Model model, String instancePrefixedId);
    boolean requireBindValidation(Model model, String instancePrefixedId);
}
