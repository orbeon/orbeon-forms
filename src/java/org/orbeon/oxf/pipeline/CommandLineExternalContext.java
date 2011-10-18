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

import org.orbeon.oxf.externalcontext.URLRewriter;
import org.orbeon.oxf.util.URLRewriterUtils;

import java.io.*;

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
            return rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteRenderURL(String urlString) {
            return rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteActionURL(String urlString, String portletMode, String windowState) {
            return rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
            return rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteResourceURL(String urlString) {
            return rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {
            return rewriteResourceURL(urlString, generateAbsoluteURL ? Response.REWRITE_MODE_ABSOLUTE : Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteResourceURL(String urlString, int rewriteMode) {
            return URLRewriterUtils.rewriteURL(getRequest(), urlString, rewriteMode);
        }
    }
}
