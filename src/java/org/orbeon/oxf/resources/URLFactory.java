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
package org.orbeon.oxf.resources;

import org.orbeon.oxf.resources.handler.HTTPHandler;
import org.orbeon.oxf.resources.handler.OXFHandler;

import java.net.*;

/**
 * This factory should be used (instead of new URL(...)) to create URL objects for URLs that use
 * the "oxf:" protocol (other protocols will work as well).
 *
 * Implementation note: This is necessary as the URL JDK implementation will always try to load
 * URLStreamHandler registered with the system property using the system class loader, and this
 * fails as our URLStreamHandler is not loaded by the system class loader.
 */
public class URLFactory {

    private static final URLStreamHandler oxfHandler = new OXFHandler();
    private static final URLStreamHandler httpHandler = new HTTPHandler();

    public static URL createURL(String spec) throws MalformedURLException {
        return createURL((URL) null, spec);
    }

    public static URL createURL(String context, String spec) throws MalformedURLException {
        return createURL(context == null || "".equals(context) ? null : createURL(context), spec);
    }

    public static URL createURL(URL context, String spec) throws MalformedURLException {
        return spec.startsWith(OXFHandler.PROTOCOL + ":") ? new URL(context, spec, oxfHandler)
             : spec.startsWith("http:") || spec.startsWith("https:") ? new URL(context, spec, httpHandler)
             : new URL(context, spec);
    }
}
