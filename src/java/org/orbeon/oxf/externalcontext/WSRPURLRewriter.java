/**
 * Copyright (C) 2011 Orbeon, Inc.
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
import org.orbeon.oxf.portlet.OrbeonPortletXFormsFilter;
import org.orbeon.oxf.processor.PageFlowControllerProcessor;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.URLRewriterUtils;
import org.orbeon.oxf.xforms.processor.XFormsResourceServer;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;


public class WSRPURLRewriter implements URLRewriter {

    public static final int URL_TYPE_BLOCKING_ACTION = 1;
    public static final int URL_TYPE_RENDER = 2;
    public static final int URL_TYPE_RESOURCE = 3;
    public static final String BASE_TAG = "wsrp_rewrite";
    public static final String START_TAG = BASE_TAG + "?";
    public static final String END_TAG = "/" + BASE_TAG;
    public static final String PREFIX_TAG = BASE_TAG + "_";
    public static final String URL_TYPE_PARAM = "wsrp-urlType";
    public static final String MODE_PARAM = "wsrp-mode";
    public static final String WINDOW_STATE_PARAM = "wsrp-windowState";
    public static final String NAVIGATIONAL_STATE_PARAM = "wsrp-navigationalState";
    public static final String URL_TYPE_BLOCKING_ACTION_STRING = "blockingAction";
    public static final String URL_TYPE_RENDER_STRING = "render";
    public static final String URL_TYPE_RESOURCE_STRING = "resource";
    public static final int BASE_TAG_LENGTH = BASE_TAG.length();
    public static final int START_TAG_LENGTH = START_TAG.length();
    public static final int END_TAG_LENGTH = END_TAG.length();
    public static final int PREFIX_TAG_LENGTH = PREFIX_TAG.length();
//    public static final String URL_PARAM = "wsrp-url";
//    public static final String REQUIRES_REWRITE_PARAM = "wsrp-requiresRewrite";

    private final PropertyContext propertyContext;
    private final ExternalContext.Request request;
    private final boolean wsrpEncodeResources;
    private List<URLRewriterUtils.PathMatcher> pathMatchers;

    public WSRPURLRewriter(PropertyContext propertyContext, ExternalContext.Request request, boolean wsrpEncodeResources) {
        this.propertyContext = propertyContext;
        this.request = request;
        this.wsrpEncodeResources = wsrpEncodeResources;
    }

    @SuppressWarnings("unchecked")
    private List<URLRewriterUtils.PathMatcher> getPathMatchers() {
        if (pathMatchers == null) {
            pathMatchers = (List<URLRewriterUtils.PathMatcher>) propertyContext.getAttribute(PageFlowControllerProcessor.PATH_MATCHERS);
            if (pathMatchers == null)
                pathMatchers = URLRewriterUtils.EMPTY_PATH_MATCHER_LIST;
        }
        return pathMatchers;
    }

    public String rewriteRenderURL(String urlString) {
        return rewritePortletURL(urlString, URL_TYPE_RENDER, null, null);
    }

    public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
        return rewritePortletURL(urlString, URL_TYPE_RENDER, portletMode, windowState);
    }

    public String rewriteActionURL(String urlString) {
        return rewritePortletURL(urlString, URL_TYPE_BLOCKING_ACTION, null, null);
    }

    public String rewriteActionURL(String urlString, String portletMode, String windowState) {
        return rewritePortletURL(urlString, URL_TYPE_BLOCKING_ACTION, portletMode, windowState);
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
            final String path;
            if (urlString.startsWith("?")) {
                // This is a special case that appears to be implemented
                // in Web browsers as a convenience. Users may use it.
                path = request.getRequestPath();
            } else {
                // Regular case, use parsed path
                path = URLRewriterUtils.getRewritingContext("wsrp", "") + u.getPath();
            }
            parameters.put(OrbeonPortletXFormsFilter.PATH_PARAMETER_NAME, new String[]{ path });
            // Encode as "navigational state"
            final String navigationalState = NetUtils.encodeQueryString2(parameters);

            // Encode the URL a la WSRP
            return encodePortletURL(urlType, navigationalState, portletMode, windowState, u.getRef(), false);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public String rewriteResourceURL(String urlString, int rewriteMode) { // NOTE: the mode is ignored
        // NOTE: Always encode dynamic resources
        if (wsrpEncodeResources || urlString.equals("/xforms-server") || urlString.startsWith(XFormsResourceServer.DYNAMIC_RESOURCES_PATH)) {

            // First rewrite path to support versioned resources
            final String rewrittenPath = URLRewriterUtils.rewriteResourceURL(request, urlString, getPathMatchers(),
                ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT);

            // Then do the WSRP encoding
            return rewritePortletURL(rewrittenPath, URL_TYPE_RESOURCE, null, null);
        } else {
            // Generate resource served by the servlet
            return URLRewriterUtils.rewriteResourceURL(request, urlString, getPathMatchers(),
                ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH);
        }
    }

    public String getNamespacePrefix() {
        return PREFIX_TAG;
    }

    /**
     * Encode an URL into a WSRP pattern including the string "wsrp_rewrite".
     *
     * This does not call the portlet API. Used by Portlet2URLRewriter.
     *
     * @param urlType
     * @param navigationalState
     * @param mode
     * @param windowState
     * @param fragmentId
     * @param secure
     * @return
     */
    public static String encodePortletURL(int urlType, String navigationalState, String mode, String windowState, String fragmentId, boolean secure) {

        final StringBuilder sb = new StringBuilder(START_TAG);
        sb.append(URL_TYPE_PARAM);
        sb.append('=');

        final String urlTypeString;
        if (urlType == URL_TYPE_BLOCKING_ACTION)
            urlTypeString = URL_TYPE_BLOCKING_ACTION_STRING;
        else if (urlType == URL_TYPE_RENDER)
            urlTypeString = URL_TYPE_RENDER_STRING;
        else if (urlType == URL_TYPE_RESOURCE)
            urlTypeString = URL_TYPE_RESOURCE_STRING;
        else
            throw new IllegalArgumentException();

        sb.append(urlTypeString);

        // Encode mode
        if (mode != null) {
            sb.append('&');
            sb.append(MODE_PARAM);
            sb.append('=');
            sb.append(mode);
        }

        // Encode window state
        if (windowState != null) {
            sb.append('&');
            sb.append(WINDOW_STATE_PARAM);
            sb.append('=');
            sb.append(windowState);
        }

        // Encode navigational state
        if (navigationalState != null) {
            try {
                sb.append('&');
                sb.append(NAVIGATIONAL_STATE_PARAM);
                sb.append('=');
                sb.append(URLEncoder.encode(navigationalState, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                throw new OXFException(e);
            }
        }

        sb.append(END_TAG);

        return sb.toString();
    }
}
