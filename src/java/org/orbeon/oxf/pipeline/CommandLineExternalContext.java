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
package org.orbeon.oxf.pipeline;

import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.common.OXFException;

import java.io.*;
import java.net.URL;

/**
 * Simple ExternalContext for command-line applications.
 */
public class CommandLineExternalContext extends SimpleExternalContext {

    public CommandLineExternalContext() {
        this.request = new CommandLineExternalContext.CommandLineRequest();
        this.response = new CommandLineExternalContext.CommandLineResponse();
    }

    private class CommandLineRequest extends Request {

        public String getContextPath() {
            return "";
        }

        public String getRequestPath() {
            return "/";
        }

        public String getContainerType() {
            return "command-line";
        }
    }

    private class CommandLineResponse extends Response {

        private PrintWriter printWriter;
        public PrintWriter getWriter() throws IOException {
            if (printWriter == null)
                printWriter = new PrintWriter(new OutputStreamWriter(getOutputStream()));
            return printWriter;
        }

        public OutputStream getOutputStream() throws IOException {
            return new FilterOutputStream(System.out) {
                public void close() {
                    // Don't close System.out
                    System.out.flush();
                }
            };
        }

        public String rewriteActionURL(String urlString) {
            return rewriteURL(urlString, false);
        }

        public String rewriteRenderURL(String urlString) {
            return rewriteURL(urlString, false);
        }

        public String rewriteActionURL(String urlString, String portletMode, String windowState) {
            return rewriteURL(urlString, false);
        }

        public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
            return rewriteURL(urlString, false);
        }

        public String rewriteResourceURL(String urlString) {
            return rewriteURL(urlString, false);
        }

        public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {
            return rewriteURL(urlString, generateAbsoluteURL);
        }
    }

    private String rewriteURL(String urlString, boolean generateAbsoluteURL) {
        // Case where a protocol is specified: the URL is left untouched
        // We consider that a protocol consists only of ASCII letters
        if (NetUtils.urlHasProtocol(urlString))
            return urlString;

        try {
            ExternalContext.Request request = getRequest();

            URL absoluteBaseURL = generateAbsoluteURL ? new URL(new URL(request.getRequestURL()), "/") : null;
            String baseURLString = generateAbsoluteURL ? absoluteBaseURL.toExternalForm() : "";
            if (baseURLString.endsWith("/"))
                baseURLString = baseURLString.substring(0, baseURLString.length() - 1);

            // Return absolute path URI with query string and fragment identifier if needed
            if (urlString.startsWith("?")) {
                // This is a special case that appears to be implemented
                // in Web browsers as a convenience. Users may use it.
                return baseURLString + request.getContextPath() + request.getRequestPath() + urlString;
            } else if (!urlString.startsWith("/") && !generateAbsoluteURL && !"".equals(urlString)) {
                // Don't change the URL if it is a relative path and we don't force absolute URLs
                return urlString;
            } else {
                // Regular case, parse the URL
                URL baseURLWithPath = new URL("http", "example.org", request.getRequestPath());
                URL u = new URL(baseURLWithPath, urlString);

                String tempResult = u.getFile();
                if (u.getRef() != null)
                    tempResult += "#" + u.getRef();
                return baseURLString + request.getContextPath() + tempResult;
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
