/**
 *  Copyright (C) 2006 Orbeon, Inc.
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

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.orbeon.oxf.common.OXFException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Implements a URLStreamHandler for HTTP to be passed to the URL constructor.
 * The Apache HTTP Client library will be used to handle the URL.
 */
public class HTTPHandler extends URLStreamHandler  {

    public static final String PROTOCOL = "http";

    protected URLConnection openConnection(URL url) throws IOException {
        return new URLConnection(url) {

            GetMethod get;

            public void connect() throws IOException {
                String userinfo = url.getUserInfo();
                HttpClient client = new HttpClient();

                if (userinfo != null) {
                    // Set username and password
                    int separatorPosition = userinfo.indexOf(":");
                    String username = userinfo.substring(0, separatorPosition);
                    String password = userinfo.substring(separatorPosition + 1);
                    client.getState().setCredentials(
                        new AuthScope(url.getHost(), 80),
                        new UsernamePasswordCredentials(username, password)
                    );
                }

                // Connect
                get = new GetMethod(url.toString());
                get.setDoAuthentication(true);
                client.executeMethod(get);
            }

            public InputStream getInputStream() throws IOException {
                if (get == null) connect();
                return get.getResponseBodyAsStream();
            }

            /**
             * This method will be called by URLConnection.getLastModified(),
             * URLConnection.getContentLength(), etc.
             */
            public String getHeaderField(String name) {
                if (get == null) try {
                    connect();
                    Header[] headers = get.getResponseHeaders();
                    for (int i = headers.length - 1; i >= 0; i--) {
                        if (headers[i].getName().equalsIgnoreCase(name)) {
                            return headers[i].getValue();
                        }
                    }
                } catch (IOException e) {
                    throw new OXFException(e);
                }
                return null;
            }
        };
    }
}
