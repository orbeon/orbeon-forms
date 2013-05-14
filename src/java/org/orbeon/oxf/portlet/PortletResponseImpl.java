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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;

import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;

public class PortletResponseImpl implements PortletResponse {

    protected int portletId;
    protected PipelineContext pipelineContext;
    protected ExternalContext externalContext;
    protected PortletRequest portletRequest;

    public PortletResponseImpl(int portletId, PipelineContext pipelineContext, ExternalContext externalContext, PortletRequest portletRequest) {
        this.portletId = portletId;
        this.pipelineContext = pipelineContext;
        this.externalContext = externalContext;
        this.portletRequest = portletRequest;
    }

    public void addProperty(String key, String value) {
        // NIY / FIXME
    }

    public String encodeURL(String path) {
        // We don't have anything special to do, so just return the original path
        // NOTE: After thinking a little bit, this is NOT a place to add the context path, for example
        return path;
    }

    public void setProperty(String key, String value) {
        // NIY / FIXME
    }
}
