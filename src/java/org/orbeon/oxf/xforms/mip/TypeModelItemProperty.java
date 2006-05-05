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

import org.orbeon.saxon.style.StandardNames;

public class TypeModelItemProperty {

    private boolean isSet = false;
    private int value;

    public TypeModelItemProperty() {
    }

    public void set(int value) {
        this.value = value;
        isSet = true;
    }

    public int get() {
        return value;
    }

    public boolean isSet() {
        return isSet;
    }

    /**
     * Return the type name as a "Clark name", i.e. in the form "{uri}localname".
     */
    public String getAsString() {
        final int typeCode = get();
        return (typeCode != 0) ? StandardNames.getClarkName(typeCode) : null;
    }
}
