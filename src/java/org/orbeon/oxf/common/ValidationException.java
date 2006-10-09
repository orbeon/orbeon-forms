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
import org.xml.sax.SAXParseException;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Iterator;

public class ValidationException extends OXFException {

    /**
     * Return all the LocationData information for that throwable
     */
    public static List getAllLocationData(Throwable throwable) {

        // List of exceptions starting from root cause
        final List exceptionChain = new ArrayList();
        {
            Throwable currentThrowable = throwable;
            while (currentThrowable != null) {
                exceptionChain.add(currentThrowable);
                currentThrowable = OXFException.getNestedException(currentThrowable);
            }
            Collections.reverse(exceptionChain);
        }

        // Extract all location data starting from root cause and add to the list
        final List locationDataList = new ArrayList();
        for (Iterator i = exceptionChain.iterator(); i.hasNext();) {
            final Throwable currentThrowable = (Throwable) i.next();
            final List currentLocationDataList = getLocationData(currentThrowable);
            if (currentLocationDataList != null && currentLocationDataList.size() > 0)
                locationDataList.addAll(currentLocationDataList);
        }

        return locationDataList;
    }

    public static LocationData getRootLocationData(Throwable throwable) {
        LocationData locationData = null;
        while (true) {
            final List currentLocationData = getLocationData(throwable);
            if (currentLocationData != null && currentLocationData.size() > 0)
                locationData = (LocationData) currentLocationData.get(0);
            final Throwable nested = OXFException.getNestedException(throwable);
            if (nested == null)
                break;
            throwable = nested;
        }
        return locationData;
    }

    private static List getLocationData(Throwable throwable) {
        if (throwable instanceof ValidationException) {
            // Regular OPS case
            final ValidationException validationException = (ValidationException) throwable;
            if (validationException.getAllLocationData().size() > 0)
                return validationException.getAllLocationData();
        } else if (throwable instanceof TransformerException) {
            // Special case of TransformerException
            final TransformerException te = (TransformerException) throwable;
            final Throwable nestedException = te.getException();
            if (nestedException == null || nestedException instanceof Exception) {
                final SourceLocator sourceLocator = te.getLocator();
                if (sourceLocator != null)
                    return Collections.singletonList(new LocationData(sourceLocator.getSystemId(), sourceLocator.getLineNumber(), sourceLocator.getColumnNumber()));
            }
        } else if (throwable instanceof SAXParseException) {
            // Special case of SAXParseException
            final SAXParseException saxParseException = (SAXParseException) throwable;
            return Collections.singletonList(new LocationData(saxParseException.getSystemId(), saxParseException.getLineNumber(), saxParseException.getColumnNumber()));
        }
        return null;
    }

    private List locationDataList = new ArrayList();
    private String simpleMessage;

    public ValidationException(String message, LocationData locationData) {
        this(message, null, locationData);
    }

    public ValidationException(Throwable throwable, LocationData locationData) {
        this(throwable.getMessage(), throwable, locationData);
    }

    public ValidationException(String message, Throwable throwable, LocationData locationData) {
        super((locationData == null ? "" : locationData.getSystemID() + ", line "
                + locationData.getLine() + ", column " + locationData.getCol() + ": ") + message, throwable);
        this.simpleMessage = message;
        if (locationData != null)
            this.locationDataList.add(locationData);
    }

    public static ValidationException wrapException(Throwable e, LocationData locationData) {
        if (e instanceof ValidationException) {
            final ValidationException validationException = (ValidationException) e;
            if (locationData != null)
                validationException.locationDataList.add(locationData);
            return validationException;
        } else {
            return new ValidationException(e, locationData);
        }
    }

    public void addLocationData(LocationData locationData) {
        if (locationData != null)
            locationDataList.add(locationData);
    }

    public LocationData getLocationData() {
        return (LocationData) ((locationDataList.size() == 0) ? null : locationDataList.get(0));
    }

    public List getAllLocationData() {
        return locationDataList;
    }

    public String getSimpleMessage() {
        return simpleMessage;
    }

    public String getMessage() {
        if (locationDataList.size() > 0)
            return locationDataList.get(0) + ": " + simpleMessage + "\n" + super.getMessage();
        else
            return simpleMessage + ": " + super.getMessage();
    }
}
