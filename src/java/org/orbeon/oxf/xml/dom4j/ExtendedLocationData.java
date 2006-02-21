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

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.xml.sax.Locator;

/**
 * LocationData information with additional description.
 */
public class ExtendedLocationData extends LocationData {

    private String description;
    private String[] parameters;
    private String elementString;

    public ExtendedLocationData(String systemID, int line, int col, String description) {
        super(systemID, line, col);
        this.description = description;
    }

    /**
     * Create extended location data with a description.
     *
     * If defaultIfNecessary is true and locationData is null or the systemId provided by
     * defaultIfNecessary is null, then default Java location data of the caller is provided.
     *
     * @param locationData
     * @param description
     */
    public ExtendedLocationData(LocationData locationData, String description) {
        super((locationData == null) ? null : locationData.getSystemID(), (locationData == null) ? -1 : locationData.getLine(), (locationData == null) ? -1 : locationData.getCol());
        this.description = description;
    }

    /**
     * Create extended location data with an element.
     *
     * If defaultIfNecessary is true and locationData is null or the systemId provided by
     * defaultIfNecessary is null, then default Java location data of the caller is provided.
     *
     * @param locationData
     * @param element
     */
    public ExtendedLocationData(LocationData locationData, String description, Element element) {
        this(locationData, description);
        if (element != null)
            this.elementString = Dom4jUtils.domToString(element);
    }

    /**
     * Create extended location data with a description.
     *
     * If defaultIfNecessary is true and locationData is null or the systemId provided by
     * defaultIfNecessary is null, then default Java location data of the caller is provided.
     *
     * @param locationData
     * @param description
     * @param parameters
     * @param defaultIfNecessary
     */
    public ExtendedLocationData(LocationData locationData, String description, String[] parameters, boolean defaultIfNecessary) {
        this(((locationData == null || locationData.getSystemID() == null) && defaultIfNecessary) ? Dom4jUtils.getLocationData(1, false) : locationData,
                description, parameters);
//        if (((locationData == null || locationData.getSystemID() == null) && defaultIfNecessary))
//            System.out.println("xxx defaultIfNecessary = true xxx ");
    }

    /**
     * Create extended location data with a description.
     *
     * If defaultIfNecessary is true and locationData is null or the systemId provided by
     * defaultIfNecessary is null, then default Java location data of the caller is provided.
     *
     * @param locationData
     * @param description
     * @param element
     * @param parameters
     * @param defaultIfNecessary
     */
    public ExtendedLocationData(LocationData locationData, String description, Element element, String[] parameters, boolean defaultIfNecessary) {
        this(((locationData == null || locationData.getSystemID() == null) && defaultIfNecessary) ? Dom4jUtils.getLocationData(1, false) : locationData,
                description, parameters);
//        if (((locationData == null || locationData.getSystemID() == null) && defaultIfNecessary))
//            System.out.println("xxx defaultIfNecessary = true xxx ");
        if (element != null)
            this.elementString = Dom4jUtils.domToString(element);
    }

    private ExtendedLocationData(LocationData locationData, String description, String[] parameters) {
        super((locationData == null) ? null : locationData.getSystemID(), (locationData == null) ? -1 : locationData.getLine(), (locationData == null) ? -1 : locationData.getCol());
        this.description = description;
        if (parameters != null) {
            if (parameters.length % 2 == 1)
                throw new OXFException("Invalid number of parameters passed to ExtendedLocationData");

            this.parameters = parameters;
        }
    }

    public ExtendedLocationData(Locator locator, String description) {
        super(locator);
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String getElementString() {
        return elementString;
    }


    public String getParametersString() {
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
        return sb.toString();
    }

    public String[] getParameters() {
        return parameters;
    }

    public String toString() {
        return super.toString() + ", description " + description;
    }
}
