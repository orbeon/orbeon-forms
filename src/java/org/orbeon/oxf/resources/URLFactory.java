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

import org.orbeon.oxf.resources.oxf.Handler;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;

/**
 * This factory should be used (instead of new URL(...)) to create URL objects for URLs that use
 * the "oxf:" protocol (other protocols will work as well).
 *
 * Implementation note: This is necessary as the URL JDK implementation will always try to load
 * URLStreamHandler registered with the system property using the system class loader, and this
 * fails as our URLStreamHandler is not loaded by the system class loader.
 */
public class URLFactory {

    private static final URLStreamHandler oxfHandler = new Handler();

    public static URL createURL(String spec) throws MalformedURLException {
        return createURL((URL) null, spec);
    }

    public static URL createURL(String context, String spec) throws MalformedURLException {
        return createURL(context == null ? null : createURL(context), spec);
    }

    public static URL createURL(URL context, String spec) throws MalformedURLException {
        return spec.startsWith(Handler.PROTOCOL + ":")
                ? new URL(context, spec, oxfHandler)
                : new URL(context, spec);
    }
}
