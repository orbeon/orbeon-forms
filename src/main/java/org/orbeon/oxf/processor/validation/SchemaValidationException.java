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
package org.orbeon.oxf.processor.validation;

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xml.dom.LocationData;

// So we can differentiate with other ValidationException, see:
// https://github.com/orbeon/orbeon-forms/issues/910
public class SchemaValidationException extends ValidationException {

    public SchemaValidationException(String message, LocationData locationData) {
        super(message, locationData);
    }

    public SchemaValidationException(Throwable throwable, LocationData locationData) {
        super(throwable, locationData);
    }

    public SchemaValidationException(String message, Throwable throwable, LocationData locationData) {
        super(message, throwable, locationData);
    }
}
