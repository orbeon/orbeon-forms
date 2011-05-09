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

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.xml.sax.Locator;

/**
 * LocationData information with additional information.
 */
public class ExtendedLocationData extends LocationData {

    private String description;
    private String[] parameters;
    private Element element;

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
     * Create extended location data with a description and an element.
     *
     * If defaultIfNecessary is true and locationData is null or the systemId provided by
     * defaultIfNecessary is null, then default Java location data of the caller is provided.
     *
     * @param locationData
     * @param description
     * @param element
     */
    public ExtendedLocationData(LocationData locationData, String description, Element element) {
        this(locationData, description);
        this.element = element;
    }

    /**
     * Create extended location data with a description, an element and parameters.
     *
     * @param locationData
     * @param description
     * @param element
     * @param parameters
     */
    public ExtendedLocationData(LocationData locationData, String description, Element element, String... parameters) {
        this(locationData, description, element, parameters, false);
    }

    /**
     * Create extended location data with a description and parameters.
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
    }

    /**
     * Create extended location data with a description, an element and parameters.
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
        this.element = element;
    }

    /**
     * Create extended location data with a description and parameters.
     *
     * @param locationData
     * @param description
     * @param parameters
     */
    public ExtendedLocationData(LocationData locationData, String description, String... parameters) {
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
        return (element != null) ? Dom4jUtils.domToString(element) : null;
    }

    public String getElementDebugString() {
        return (element != null) ? Dom4jUtils.elementToDebugString(element) : null;
    }

    public String[] getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(super.toString());
        final String parametersString = getParametersString();
        final boolean hasDescription = getDescription() != null;
        final boolean hasParameters = parametersString.length() > 0;
        if (hasDescription|| hasParameters) {
            sb.append(" (");
            if (hasDescription)
                sb.append(getDescription());
            if (hasParameters) {
                if (hasDescription)
                    sb.append(": ");
                sb.append(parametersString);
            }
            sb.append(")");
        }
        return sb.toString();
    }

    private String getParametersString() {
        final StringBuilder sb = new StringBuilder("");
        if (parameters != null) {
            boolean first = true;
            for (int i = 0; i < parameters.length; i += 2) {
                final String paramName = parameters[i];
                final String paramValue = parameters[i + 1];

                if (paramValue != null) {
                    if (!first)
                        sb.append(", ");

                    sb.append(paramName);
                    sb.append("='");
                    sb.append(paramValue);
                    sb.append("'");

                    first = false;
                }
            }
        }
        return sb.toString();
    }
}
