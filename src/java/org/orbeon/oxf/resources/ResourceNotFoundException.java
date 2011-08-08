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
package org.orbeon.oxf.resources;

import org.orbeon.oxf.common.OXFException;

/**
 * This exception is thrown when the resource manager can not find a requested
 * document.
 */
public class ResourceNotFoundException extends OXFException {

    public final String resource;

    public ResourceNotFoundException(String resource, Exception exception) {
        super("Cannot find resource: " + resource, exception);
        this.resource = resource;
    }

    public ResourceNotFoundException(String resource) {
        this(resource, null);
    }

    public ResourceNotFoundException(Exception exception) {
        this(exception.getMessage(), exception);
    }
}

