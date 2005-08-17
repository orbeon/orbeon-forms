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
package org.orbeon.oxf.xml.dom4j;

import org.xml.sax.Locator;
import org.orbeon.oxf.common.OXFException;

/**
 * LocationData information with additional description.
 */
public class ExtendedLocationData extends LocationData {

    private String description;

    public ExtendedLocationData(String systemID, int line, int col, String description) {
        super(systemID, line, col);
        this.description = description;
    }

    public ExtendedLocationData(LocationData locationData, String description) {
        super((locationData == null) ? null : locationData.getSystemID(), (locationData == null) ? -1 : locationData.getLine(), (locationData == null) ? -1 : locationData.getCol());
        this.description = description;
    }

    public ExtendedLocationData(LocationData locationData, String description, String[] parameters) {
        super((locationData == null) ? null : locationData.getSystemID(), (locationData == null) ? -1 : locationData.getLine(), (locationData == null) ? -1 : locationData.getCol());
        if (parameters == null) {
            this.description = description;
        } else {
            if (parameters.length % 2 == 1)
                throw new OXFException("Invalid number of parameters passed to ExtendedLocationData");
            final StringBuffer sb = new StringBuffer(description);
            boolean first = true;
            for (int i = 0; i < parameters.length; i += 2) {
                final String paramName = parameters[i];
                final String paramValue = parameters[i + 1];

                if (paramValue != null) {

                    sb.append((first) ? ": " : ", ");

                    sb.append(paramName);
                    sb.append("='");
                    sb.append(paramValue);
                    sb.append("'");

                    first = false;
                }
            }
            this.description = sb.toString();
        }
    }

    public ExtendedLocationData(Locator locator, String description) {
        super(locator);
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String toString() {
        return super.toString() + ", description " + description;
    }
}
