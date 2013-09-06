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
package org.orbeon.oxf.xml.dom4j;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

import javax.xml.transform.SourceLocator;

public class LocationData {

    private String systemID;
    private int line;
    private int col;

    public LocationData(String systemID, int line, int col) {
        this.col = col;
        this.line = line;
        this.systemID = systemID;
    }

    public LocationData(Locator locator) {
        if (locator != null) {
            systemID = locator.getSystemId();
            line = locator.getLineNumber();
            col = locator.getColumnNumber();
        }
    }

    public LocationData(SourceLocator sourceLocator) {
        if (sourceLocator != null) {
            systemID = sourceLocator.getSystemId();
            line = sourceLocator.getLineNumber();
            col = sourceLocator.getColumnNumber();
        }
    }

    public LocationData(SAXParseException exception) {
        systemID = exception.getSystemId();
        line = exception.getLineNumber();
        col = exception.getColumnNumber();
    }

    public String getSystemID() { return systemID; }
    public int getLine() { return line; }
    public int getCol() { return col; }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        final boolean hasLine;
        if (getLine() > 0) {
            sb.append("line ");
            sb.append(Integer.toString(getLine()));
            hasLine = true;
        } else {
            hasLine = false;
        }
        final boolean hasColumn;
        if (getCol() > 0) {
            if (hasLine)
                sb.append(", ");
            sb.append("column ");
            sb.append(Integer.toString(getCol()));
            hasColumn = true;
        } else {
            hasColumn = false;
        }
        if (getSystemID() != null) {
            if (hasLine || hasColumn)
                sb.append(" of ");
            sb.append(getSystemID());
        }
        return sb.toString();
    }

    public static LocationData createIfPresent(Locator locator) {
        if (locator != null) {
            final String filename = locator.getSystemId();
            final int line = locator.getLineNumber();

            if (StringUtils.isNotBlank(filename) && line != -1)
                return new LocationData(filename, line, locator.getColumnNumber());
            else
                return null;
        } else
            return null;
    }
}
