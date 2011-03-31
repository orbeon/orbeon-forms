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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.Variable;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.EmptySequence;
import org.orbeon.saxon.value.Value;

/**
 * Representation of a variable in a tree of controls.
 *
 * Some of the logic is similar to what is in XFormsValueControl, except this works with ValueRepresentation.
 */
public class XXFormsVariableControl extends XFormsSingleNodeControl {

    // Actual variable name/value representation
    private Variable variable;

    private ValueRepresentation value;
    // Previous value for refresh
    private ValueRepresentation previousValue;

    public XXFormsVariableControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
        variable = new Variable(container, container.getContextStack(), element);
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        // Case should be caught by the requireValueUpdate() below, but it's more fail-safe to mark things dirty here too
        value = null;
        variable.markDirty();
    }

    @Override
    protected void markDirtyImpl(XPathDependencies xpathDependencies) {
        super.markDirtyImpl(xpathDependencies);

        // Handle value update
        if (xpathDependencies.requireValueUpdate(getPrefixedId())) {
            value = null;
            variable.markDirty();
        }
    }

    @Override
    protected void evaluateImpl() {

        // Evaluate other aspects of the control if necessary
        super.evaluateImpl();

        // Evaluate control values
        if (isRelevant()) {
            // Control is relevant
            getContextStack().setBinding(this);
            value = variable.getVariableValue(getEffectiveId(), false);
        } else {
            // Control is not relevant
            // NOTE: Nobody should use this variable if it's non-relevant, but right now we still have possible uses
            // of non-relevant variables.
            value = EmptySequence.getInstance();
        }
    }

    @Override
    public boolean isValueChanged() {
        final boolean result = !compareValues(previousValue, value);
        previousValue = value;
        return result;
    }

    private static boolean compareValues(ValueRepresentation value1, ValueRepresentation value2) {
        if (value1 instanceof Value && value2 instanceof Value) {
            try {
                final SequenceIterator iter1 = ((Value) value1).iterate();
                final SequenceIterator iter2 = ((Value) value2).iterate();
                while (true) {
                    final Item item1 = iter1.next();
                    final Item item2 = iter2.next();

                    if (item1 == null && item2 == null) {
                        return true;
                    }

                    if (!XFormsUtils.compareItems(item1, item2)) {
                        return false;
                    }
                }
            } catch (XPathException e) {
                throw new OXFException(e);
            }
        } else if (value1 == null && value2 == null) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * Return the control's internal value.
     *
     */
    public final ValueRepresentation getValue() {
        return value;
    }

    public String getVariableName() {
        return variable.getVariableName();
    }

    @Override
    public boolean equalsExternal(PropertyContext propertyContext, XFormsControl other) {

        if (other == null || !(other instanceof XXFormsVariableControl))
            return false;

        if (this == other)
            return true;

        final XXFormsVariableControl otherValueControl = (XXFormsVariableControl) other;

        if (!compareValues(getValue(), otherValueControl.getValue()))
            return false;

        return super.equalsExternal(propertyContext, other);
    }

    @Override
    public boolean supportAjaxUpdates() {
        return false;
    }

    @Override
    public boolean setFocus() {
        // Variable can never have focus
        return false;
    }
}
