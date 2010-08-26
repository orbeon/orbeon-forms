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
package org.orbeon.oxf.externalcontext;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.portlet.*;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.URLRewriterUtils;

import java.net.URL;
import java.util.Map;


public class WSRPURLRewriter implements URLRewriter {

    private final ExternalContext.Request request;

    public WSRPURLRewriter(ExternalContext.Request request) {
        this.request = request;
    }

    public String rewriteRenderURL(String urlString) {
        return rewritePortletURL(urlString, WSRP2Utils.URL_TYPE_RENDER, null, null);
    }

    public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
        return rewritePortletURL(urlString, WSRP2Utils.URL_TYPE_RENDER, portletMode, windowState);
    }

    public String rewriteActionURL(String urlString) {
        return rewritePortletURL(urlString, WSRP2Utils.URL_TYPE_BLOCKING_ACTION, null, null);
    }

    public String rewriteActionURL(String urlString, String portletMode, String windowState) {
        return rewritePortletURL(urlString, WSRP2Utils.URL_TYPE_BLOCKING_ACTION, portletMode, windowState);
    }

    private String rewritePortletURL(String urlString, int urlType, String portletMode, String windowState) {
        // Case where a protocol is specified OR it's just a fragment: the URL is left untouched
        if (NetUtils.urlHasProtocol(urlString) || urlString.startsWith("#")) return urlString;

        // TEMP HACK to avoid multiple rewrites
        // TODO: Find out where it happens. Check XFOutputControl with image mediatype for example.
        if (urlString.indexOf("wsrp_rewrite") != -1)
            return urlString;

        try {
            // Parse URL
            final URL baseURL = new URL("http", "example.org", request.getRequestPath());
            final URL u = new URL(baseURL, urlString);
            // Decode query string
            final Map<String, String[]> parameters = NetUtils.decodeQueryString(u.getQuery(), true);
            // Add special path parameter
            if (urlString.startsWith("?")) {
                // This is a special case that appears to be implemented
                // in Web browsers as a convenience. Users may use it.
                parameters.put(OrbeonPortletXFormsFilter.PATH_PARAMETER_NAME, new String[]{request.getRequestPath()});
            } else {
                // Regular case, use parsed path
                final String path = URLRewriterUtils.getRewritingContext("wsrp", "") + u.getPath();
                parameters.put(OrbeonPortletXFormsFilter.PATH_PARAMETER_NAME, new String[]{ path });
            }
            // Encode as "navigational state"
            final String navigationalState = NetUtils.encodeQueryString(parameters);

            // Encode the URL a la WSRP
            return WSRP2Utils.encodePortletURL(urlType, navigationalState, portletMode, windowState, u.getRef(), false);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public String rewriteResourceURL(String urlString, int rewriteMode) {
        // JSR-268 supports portlet resources
        return rewritePortletURL(urlString, WSRP2Utils.URL_TYPE_RESOURCE, null, null);
    }
}
