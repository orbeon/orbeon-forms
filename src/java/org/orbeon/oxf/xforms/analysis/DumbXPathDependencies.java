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

import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.saxon.om.NodeInfo;

/**
 * This implementation of dependencies simply says that everything must be updated all the time.
 */
public class DumbXPathDependencies implements XPathDependencies {
    public void markValueChanged(XFormsModel model, NodeInfo nodeInfo) {
        // NOP
    }

    public void markStructuralChange(XFormsModel model, XFormsInstance instance) {
        // NOP
    }

    public void rebuildDone(Model model) {
        // NOP
    }

    public void recalculateDone(Model model) {
        // NOP
    }

    public void revalidateDone(Model model) {
        // NOP
    }

    public void refreshStart() {
        // NOP
    }

    public void refreshDone() {
        // NOP
    }

    public void afterInitialResponse() {
        // NOP
    }

    public void beforeUpdateResponse() {
        // NOP
    }

    public void afterUpdateResponse() {
        // NOP
    }

    public void notifyComputeLHHA() {
        // NOP
    }

    public void notifyOptimizeLHHA() {
        // NOP
    }

    public void notifyComputeItemset() {
        // NOP
    }

    public void notifyOptimizeItemset() {
        // NOP
    }

    public boolean requireBindingUpdate(String controlPrefixedId) {
        // Always update
        return true;
    }

    public boolean requireValueUpdate(String controlPrefixedId) {
        // Always update
        return true;
    }

    public boolean requireLHHAUpdate(String lhhaName, String controlPrefixedId) {
        // Always update
        return true;
    }

    public boolean requireItemsetUpdate(String controlPrefixedId) {
        // Always update
        return true;
    }

    public boolean hasAnyCalculationBind(Model model, String instancePrefixedId) {
        // Always update
        return true;
    }

    public boolean hasAnyValidationBind(Model model, String instancePrefixedId) {
        // Always update
        return true;
    }

    public boolean hasAnyCalculationBind(Model model) {
        // Always update
        return true;
    }

    public boolean hasAnyValidationBind(Model model) {
        // Always update
        return true;
    }

    public boolean requireModelMIPUpdate(Model model, String bindId, String mipName) {
        // Always update
        return true;
    }
}
