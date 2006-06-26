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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.mip.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Instances of this class are used to annotate XForms instance nodes with MIPs and other information.
 */
public class InstanceData {

    private LocationData locationData;
    private boolean generated = false;
    private RelevantModelItemProperty relevant = new RelevantModelItemProperty();
    private ReadonlyModelItemProperty readonly = new ReadonlyModelItemProperty();

    private RequiredModelItemProperty required = new RequiredModelItemProperty();
    private ValidModelItemProperty valid;
    private TypeModelItemProperty type = new TypeModelItemProperty();

    private ValidModelItemProperty valueValid = new ValidModelItemProperty();
    private ValidModelItemProperty constraint = new ValidModelItemProperty();

    private RelevantModelItemProperty inheritedRelevant;
    private ReadonlyModelItemProperty inheritedReadonly;

    // This is an extension property used only by XForms Classic for now
    private XXFormsExternalizeModelItemProperty xxformsExternalize;

    private boolean valueChanged;
    private boolean previousRequiredState;
    private boolean previousValidState;
    private boolean previousInheritedRelevantState;
    private boolean previousInheritedReadonlyState;

    private Map switchIdsToCaseIds;

    private int id = -1;
    private String invalidBindIds = null;
    private Map idToNodeMap;
    private List schemaErrors = null;

    public InstanceData(LocationData locationData) {
        this.locationData = locationData;
    }

    public InstanceData(LocationData locationData, int id) {
        this(locationData);
        this.id = id;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
    }

    public RelevantModelItemProperty getRelevant() {
        return relevant;
    }

    public RequiredModelItemProperty getRequired() {
        return required;
    }

    public ReadonlyModelItemProperty getReadonly() {
        return readonly;
    }

    public RelevantModelItemProperty getInheritedRelevant() {
        return inheritedRelevant;
    }

    public void setInheritedRelevant(RelevantModelItemProperty inheritedRelevant) {
        this.inheritedRelevant = inheritedRelevant;
    }

    public ReadonlyModelItemProperty getInheritedReadonly() {
        return inheritedReadonly;
    }

    public void setInheritedReadonly(ReadonlyModelItemProperty inheritedReadonly) {
        this.inheritedReadonly = inheritedReadonly;
    }

    public BooleanModelItemProperty getValid() {
        if (valid == null) {
            valid = new ValidModelItemProperty() {

                public boolean get() {
                    // "An instance node is valid if and only if the following conditions hold:"

                    // "* the constraint model item property is true"
                    if (!constraint.get())
                        return false;

                    // "* the node satisfies any applicable XML schema definitions (including those
                    // associated by the type model item property) NOTE: A node that satifies the
                    // above conditions is valid even if it is required but empty."
                    return valueValid.get();
                }

                public void set(boolean value) {
                    throw new UnsupportedOperationException();
                }

                public void unSet() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        return valid;
    }

    public TypeModelItemProperty getType() {
        return type;
    }

    public BooleanModelItemProperty getXXFormsExternalize() {
        // This extension MIP may not be used, so create lazily
        if (xxformsExternalize == null)
            xxformsExternalize = new XXFormsExternalizeModelItemProperty();

        return xxformsExternalize;
    }

    public String getInvalidBindIds() {
        return invalidBindIds;
    }

    public void setInvalidBindIds(String invalidBindIds) {
        this.invalidBindIds = invalidBindIds;
    }

    public int getId() {
        if (id == -1)
            throw new OXFException("InstanceData id is in invalid state");
        return id;
    }

    public Map getIdToNodeMap() {
        return idToNodeMap;
    }

    public void setIdToNodeMap(Map idToNodeMap) {
        this.idToNodeMap = idToNodeMap;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public String getSystemId() {
        return (locationData == null) ? null : locationData.getSystemID();
    }

    public Iterator getSchemaErrorsMsgs() {
        final Collection unmod;
        if (schemaErrors == null) {
            unmod = Collections.EMPTY_LIST;
        } else {
            unmod = Collections.unmodifiableCollection(schemaErrors);
        }
        return unmod.iterator();
    }

    public void addSchemaError(final String msg, final String stringValue) {
        valueValid.set(false, stringValue);
        if (schemaErrors == null) {
            schemaErrors = new ArrayList(1);
        }
        schemaErrors.add(msg);
    }

    public void clearValidationState() {

        // Clear everything related to validity (except required)
        valueValid = new ValidModelItemProperty();
        constraint = new ValidModelItemProperty();
        type = new TypeModelItemProperty();
        if (schemaErrors != null)
            schemaErrors.clear();
    }

    public void clearOtherState() {
        relevant.set(relevant.getDefaultValue());
        readonly.set(readonly.getDefaultValue());
        required.set(required.getDefaultValue(), null);

        switchIdsToCaseIds = null;
    }

    public void clearInstanceDataEventState() {
        previousRequiredState = getRequired().get();
        previousInheritedRelevantState = getInheritedRelevant().get();
        previousInheritedReadonlyState = getInheritedReadonly().get();
        previousValidState = getValid().get();
        valueChanged = false;
    }

    public boolean getPreviousRequiredState() {
        return previousRequiredState;
    }

    public boolean getPreviousValidState() {
        return previousValidState;
    }

    public boolean getPreviousInheritedRelevantState() {
        return previousInheritedRelevantState;
    }

    public boolean getPreviousInheritedReadonlyState() {
        return previousInheritedReadonlyState;
    }

    public void updateRequired(boolean value, NodeInfo nodeInfo, String modelBindId) {
        required.set(value, nodeInfo.getStringValue());
    }

    public void updateConstraint(boolean value, NodeInfo nodeInfo, String modelBindId) {
        constraint.set(value, nodeInfo.getStringValue());
    }

    public void updateValueValid(boolean value, NodeInfo nodeInfo, String modelBindId) {
        if (!value) {
            valueValid.set(value, nodeInfo.getStringValue());

            if (modelBindId != null)
                setInvalidBindIds(getInvalidBindIds() == null ? modelBindId : getInvalidBindIds() + " " + modelBindId);
        }
    }

    public boolean isValueChanged() {
        return valueChanged;
    }

    public void markValueChanged() {
        this.valueChanged = true;
    }

    public void addSwitchIdToCaseId(String switchId, String caseId) {
        if (switchIdsToCaseIds == null)
            switchIdsToCaseIds = new HashMap();

        switchIdsToCaseIds.put(switchId,  caseId);
    }

    public void setSwitchIdsToCaseIds(Map switchIdsToCaseIds) {
        this.switchIdsToCaseIds = switchIdsToCaseIds;
    }

    public Map getSwitchIdsToCaseIds() {
        return switchIdsToCaseIds;
    }

    public String getCasedIdForSwitchId(String switchId) {
        if (switchIdsToCaseIds == null)
            return null;
        else
            return (String) switchIdsToCaseIds.get(switchId);
    }
}
