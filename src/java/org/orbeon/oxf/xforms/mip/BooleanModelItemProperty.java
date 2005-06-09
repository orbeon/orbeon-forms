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
package org.orbeon.oxf.xforms.mip;



public abstract class BooleanModelItemProperty implements Cloneable {

    private boolean isSet = false;
    private boolean value;

    public BooleanModelItemProperty() {
        value = getDefaultValue();
    }

    public void unSet() {
        isSet = false;
    }

    public void set(boolean value) {
        this.value = value;
        isSet = true;
    }

    public boolean get() {
        return value;
    }

    public boolean hasChangedFromDefault() {
        return get() != getDefaultValue();
    }

    public boolean isSet() {
        // FIXME: This should tell whether the MIP has been modified by a bind, but currently this
        // may not always be correct.
        return isSet;
    }

    protected abstract boolean getDefaultValue();

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
