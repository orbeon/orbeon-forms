/**
 *  Copyright (C) 2004 Orbeon, Inc.
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

import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xforms.mip.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.saxon.om.NodeInfo;

import java.util.Map;

/**
 * Read-only version of InstanceData for read-only instances.
 */
public class ReadonlyInstanceData extends InstanceData {

    private static final RelevantModelItemProperty CONSTANT_RELEVANT_MIP =  new RelevantModelItemProperty() {
        public void set(boolean value) {
            throwException();
        }
    };

    private static final ReadonlyModelItemProperty CONSTANT_READONLY_MIP = new ReadonlyModelItemProperty() {
        public void set(boolean value) {
            throwException();
        }
    };

    private static final RequiredModelItemProperty CONSTANT_REQUIRED_MIP = new RequiredModelItemProperty() {
        public void set(boolean value) {

        }
    };

    private static final TypeModelItemProperty CONSTANT_TYPE_MIP = new TypeModelItemProperty() {
        public void set(int value) {
            throwException();
        }
    };

    private static final ValidModelItemProperty CONSTANT_VALID_MIP = new ValidModelItemProperty() {
        public void set(boolean value) {
            throwException();
        }
    };

    public ReadonlyInstanceData(LocationData locationData, int id) {
        super(locationData, id);

        this.relevant = CONSTANT_RELEVANT_MIP;
        this.readonly = CONSTANT_READONLY_MIP;
        this.required = CONSTANT_REQUIRED_MIP;
        this.type = CONSTANT_TYPE_MIP;

        this.valueValid = CONSTANT_VALID_MIP;
        this.constraint = CONSTANT_VALID_MIP;

        setInheritedReadonly(new ReadonlyModelItemProperty());
        getInheritedReadonly().set(false);
        setInheritedRelevant(new RelevantModelItemProperty());
        getInheritedRelevant().set(true);
    }

    private static void throwException() {
        throw new OXFException("Cannot modify read-only instance.");
    }

    public void addSchemaError(final String msg, final String stringValue) {
        throwException();
    }

    public void clearValidationState() {
        // NOP
    }

    public void clearOtherState() {
        // NOP
    }

    public void clearInstanceDataEventState() {
        // NOP
    }

    public void setGenerated(boolean generated) {
        throwException();
    }

    public void setIdToNodeMap(Map idToNodeMap) {
        throwException();
    }

    public void updateRequired(boolean value, NodeInfo nodeInfo, String modelBindId) {
        throwException();
    }

    public void updateConstraint(boolean value, NodeInfo nodeInfo, String modelBindId) {
        throwException();
    }

    public void updateValueValid(boolean value, NodeInfo nodeInfo, String modelBindId) {
        throwException();
    }

    public void markValueChanged() {
        throwException();
    }

    public void addSwitchIdToCaseId(String switchId, String caseId) {
        throwException();
    }

    public void setSwitchIdsToCaseIds(Map switchIdsToCaseIds) {
        throwException();
    }
}
