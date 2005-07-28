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

import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.mip.*;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.util.*;

/**
 * Instance of this class are used to decorate the XForms instance.
 */
public class InstanceData implements Cloneable {

    private LocationData locationData;
    private boolean generated = false;
    private RelevantModelItemProperty relevant = new RelevantModelItemProperty();
    private ReadonlyModelItemProperty readonly = new ReadonlyModelItemProperty();

    private RequiredModelItemProperty required = new RequiredModelItemProperty();
    private ValidModelItemProperty valid;
    private TypeModelItemProperty type = new TypeModelItemProperty();

    private ValidModelItemProperty valueValid = new ValidModelItemProperty();
    private ValidModelItemProperty constraint = new ValidModelItemProperty();

    private boolean valueChanged;
    private boolean previousRequiredState;
    private boolean previousValidState;
    private boolean previousRelevantState;
    private boolean previousReadonlyState;

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

    public BooleanModelItemProperty getRelevant() {
        return relevant;
    }

    public RequiredModelItemProperty getRequired() {
        return required;
    }

    public BooleanModelItemProperty getReadonly() {
        return readonly;
    }

    public BooleanModelItemProperty getValid() {
        if (valid == null) {
            valid = new ValidModelItemProperty() {

                public boolean get() {
                    // If the constraint make the node invalid, the node is really invalid!
                    if (!constraint.get())
                        return false;

                    // Handle type and required constraints
                    if (valueValid.get() && getRequired().get()) {
                        // Valid and required, check that the value is actually non-empty
                        return !(getRequired().getStringValue().length() == 0);
                    } else if (!valueValid.get() && !getRequired().get()) {
                        // Not valid and not required, checked that the value is actually empty
                        return valueValid.getStringValue().length() == 0;
                    } else {
                        return valueValid.get();
                    }
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
        return locationData.getSystemID();
    }

    public Iterator getSchemaErrorsMsgs() {
        final Collection unmod;
        if (schemaErrors == null) {
            unmod = Collections.EMPTY_LIST;
        } else {
            unmod = Collections.unmodifiableCollection(schemaErrors);
        }
        final Iterator ret = unmod.iterator();
        return ret;
    }

//    public void clearSchemaError() {
//        valueValid = new ValidModelItemProperty();
//        if (schemaErrors != null)
//            schemaErrors.clear();
//    }

    public void addSchemaError(final String msg, final String stringValue) {
        valueValid.set(false, stringValue);
        if (schemaErrors == null) {
            schemaErrors = new ArrayList(1);
        }
        schemaErrors.add(msg);
    }

//    private void clearComputedExpressionState() {
//
//        // Save required state
//        previousRequiredState = getRequired().get();
//        previousRelevantState = getRelevant().get();
//        previousReadonlyState = getReadonly().get();
//
//        // clear required MIPs
//        required = new RequiredModelItemProperty();
//        relevant = new RelevantModelItemProperty();
//        readonly = new ReadonlyModelItemProperty();
//    }
//
    public void clearValidationState() {

        // Clear everything related to validity (except required)
        valueValid = new ValidModelItemProperty();
        constraint = new ValidModelItemProperty();
        type = new TypeModelItemProperty();
        if (schemaErrors != null)
            schemaErrors.clear();
    }

    public void clearInstanceDataEventState() {
        previousRequiredState = getRequired().get();
        previousRelevantState = getRelevant().get();
        previousReadonlyState = getReadonly().get();
        previousValidState = getValid().get();
        valueChanged = false;
    }

    public boolean getPreviousRequiredState() {
        return previousRequiredState;
    }

    public boolean getPreviousValidState() {
        return previousValidState;
    }

    public boolean getPreviousRelevantState() {
        return previousRelevantState;
    }

    public boolean getPreviousReadonlyState() {
        return previousReadonlyState;
    }

    public void updateRequired(boolean value, Node node, String modelBindId) {
        required.set(value, node.getStringValue());
    }

    public void updateConstraint(boolean value, Node node, String modelBindId) {
        constraint.set(value, node.getStringValue());
    }

    public void updateValueValid(boolean value, Node node, String modelBindId) {
        if (!value) {
            valueValid.set(value, node.getStringValue());

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

    public Object clone() throws CloneNotSupportedException {
        final InstanceData result = (InstanceData) super.clone();

        try {
            result.relevant = (RelevantModelItemProperty) this.relevant.clone();
            result.required = (RequiredModelItemProperty) this.required.clone();
            result.readonly = (ReadonlyModelItemProperty) this.readonly.clone();
            result.valueValid = (ValidModelItemProperty) this.valueValid.clone();
            result.constraint = (ValidModelItemProperty) this.constraint.clone();
            result.type = (TypeModelItemProperty) this.type.clone();
        } catch (CloneNotSupportedException e) {
            // This should not happen because the classes cloned are Cloneable
            throw new OXFException(e);
        }

        result.valueChanged = this.valueChanged;
        result.idToNodeMap = (this.idToNodeMap == null) ? null : new HashMap(this.idToNodeMap);
        result.schemaErrors = (this.schemaErrors == null) ? null : new ArrayList(this.schemaErrors);

        return result;
    }
}
