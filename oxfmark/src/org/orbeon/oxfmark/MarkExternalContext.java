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
package org.orbeon.oxfmark;

import org.orbeon.oxf.pipeline.SimpleExternalContext;

import java.util.Map;

public class MarkExternalContext extends SimpleExternalContext {

    private Map parameters;

    public MarkExternalContext(Map parameters) {
        this.parameters = parameters;
        this.request = new MarkRequest();
        this.response = new MarkResponse();
    }

    private class MarkRequest extends Request {
        public Map getParameterMap() {
            return parameters;
        }

        public String getRequestPath() {
            return "/examples/hello";
        }
    }

    private class MarkResponse extends Response {
        public String getResult() {
            return writer.toString();
        }
    }

    public String getResult() {
        return ((MarkResponse) response).getResult();
    }
}
