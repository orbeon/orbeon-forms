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
package org.orbeon.oxf.common;

import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;

public class ValidationException extends OXFException {

    public static LocationData getRootLocationData(Throwable e) {
        LocationData locationData = null;
        while (true) {
            if (e instanceof ValidationException) {
                ValidationException validationException = (ValidationException) e;
                if (validationException.getLocationData() != null)
                    locationData = validationException.getLocationData();
            } else if (e instanceof TransformerException) {
                TransformerException te = (TransformerException) e;
                Throwable nestedException = te.getException();
                if (nestedException == null || nestedException instanceof Exception) {
                    SourceLocator sourceLocator = te.getLocator();
                    if (sourceLocator != null)
                        locationData = new LocationData(sourceLocator.getSystemId(), sourceLocator.getLineNumber(), sourceLocator.getColumnNumber());
                }
            }
            Throwable nested = OXFException.getNestedException(e);
            if (nested == null) break;
            e = nested;
        }
        return locationData;
    }

    private LocationData locationData;
    private String simpleMessage;

    public ValidationException(String message, LocationData locationData) {
        this(message, null, locationData);
    }

    public ValidationException(Exception exception, LocationData locationData) {
        this(exception.getMessage(), exception, locationData);
    }

    public ValidationException(String message, Exception exception, LocationData locationData) {
        super((locationData == null ? "" : locationData.getSystemID() + ", line "
                + locationData.getLine() + ", column " + locationData.getCol() + ": ") + message, exception);
        this.simpleMessage = message;
        this.locationData = locationData;
    }

    public void setLocationData(LocationData locationData) {
        this.locationData = locationData;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public String getSimpleMessage() {
        return simpleMessage;
    }

    public String getMessage() {
        if (locationData != null)
            return locationData + " : " + simpleMessage + " : " + super.getMessage();
        else
            return simpleMessage + " : " + super.getMessage();

    }
}
