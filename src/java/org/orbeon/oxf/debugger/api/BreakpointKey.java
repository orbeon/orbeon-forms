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
package org.orbeon.oxf.debugger.api;

import org.orbeon.oxf.xml.dom4j.LocationData;

/**
 * BreakpointKey encapsulates a key that uniquely identifies, for a given service, a breakpoint.
 */
public class BreakpointKey {
    private String systemId;
    private int column;
    private int line;

    public BreakpointKey(LocationData locationData) {
        if (locationData != null) {
            this.systemId = locationData.getSystemID();
            this.line = locationData.getLine();
            this.column = locationData.getCol();
        }
    }

    public BreakpointKey(String systemId, int line, int column) {
        this.systemId = systemId;
        this.line = line;
        this.column = column;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }
    public String toString() {
    	return systemId + " " + line + " " + column;
    }
}
