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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * Simple ExternalContext for command-line applications.
 */
public class CommandLineExternalContext extends SimpleExternalContext {

    public CommandLineExternalContext() {
        this.request = new CommandLineExternalContext.CommandLineRequest();
        this.response = new CommandLineExternalContext.CommandLineResponse();
    }

    private class CommandLineRequest extends Request {
    }

    private class CommandLineResponse extends Response {

        private PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(System.out));
        public PrintWriter getWriter() throws IOException {
            return printWriter;
        }

        public OutputStream getOutputStream() throws IOException {
            return System.out;
        }
    }
}
