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
    public ReadonlyInstanceData(LocationData locationData, int id) {
        super(locationData, id);

        this.relevant = new RelevantModelItemProperty() {
            public void set(boolean value) {
                throwException();
            }
        };
        this.readonly = new ReadonlyModelItemProperty() {
            public void set(boolean value) {
                throwException();
            }
        };

        this.required = new RequiredModelItemProperty() {
            public void set(boolean value) {

            }
        };
        this.type = new TypeModelItemProperty() {
            public void set(int value) {
                throwException();
            }
        };

        this.valueValid = new ValidModelItemProperty() {
            public void set(boolean value) {
                throwException();
            }
        };
        this.constraint = new ValidModelItemProperty() {
            public void set(boolean value) {
                throwException();
            }
        };

        setInheritedReadonly(new ReadonlyModelItemProperty());
        getInheritedReadonly().set(false);
        setInheritedRelevant(new RelevantModelItemProperty());
        getInheritedRelevant().set(true);
    }

    private void throwException() {
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
