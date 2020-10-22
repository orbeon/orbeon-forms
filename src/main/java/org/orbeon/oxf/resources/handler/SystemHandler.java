/**
 *  Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.resources.handler;
/**
 * This class exposes java System input and outputs as system: URLs.
 */

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.resources.ResourceManagerWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class SystemHandler extends URLStreamHandler {

    public static final String PROTOCOL = "system";
    public static final String INPUT_URL = "system:in";
    public static final String OUTPUT_URL = "system:out";
    public static final String ERROR_URL = "system:err";

    protected URLConnection openConnection(URL url) {
        return new URLConnection(url) {

            public void connect() { /* nop */
            }

            public InputStream getInputStream() {
                String urlString = getURL().toExternalForm();
                if (urlString.equals(INPUT_URL)) {
                    return System.in;
                }
                throw new OXFException("Can't read from " + urlString);
            }

            public OutputStream getOutputStream() {
                String urlString = getURL().toExternalForm();
                if (urlString.equals(OUTPUT_URL)) {
                    return System.out;
                }
                if (urlString.equals(ERROR_URL)) {
                    return System.err;
                }
                throw new OXFException("Can't write to " + urlString);
            }


            public long getLastModified() {
                return 0;
            }

            public int getContentLength() {
                return -1;
            }
        };
    }

   protected void parseURL(URL u, String spec, int start, int limit) {
        super.parseURL(u, spec, start, limit);
    }

    protected void setURL(URL u, String protocol, String host, int port,
                          String authority, String userInfo, String path,
                          String query, String ref) {
        super.setURL(u, protocol, null, port, authority, userInfo, host + path, query, ref);
    }
}
