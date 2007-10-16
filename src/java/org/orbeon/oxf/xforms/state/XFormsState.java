/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.state;

/**
 * Encoded combination of static an dynamic state that fully represents an XForms document's current state.
 */
public class XFormsState {
    private String staticState;
    private String dynamicState;

    public XFormsState(String staticState, String dynamicState) {
        this.staticState = staticState;
        this.dynamicState = dynamicState;
    }

    public String getStaticState() {
        return staticState;
    }

    public String getDynamicState() {
        return dynamicState;
    }

    public String toString() {
        return staticState + "|" + dynamicState;
    }
}
