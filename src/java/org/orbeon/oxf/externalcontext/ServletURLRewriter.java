/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.URLRewriterUtils;

import java.util.List;


public class ServletURLRewriter implements URLRewriter {

    private final ExternalContext.Request request;

    private List<URLRewriterUtils.PathMatcher> pathMatchers;

    public ServletURLRewriter(ExternalContext.Request request) {
        this.request = request;
    }

    @SuppressWarnings("unchecked")
    private List<URLRewriterUtils.PathMatcher> getPathMatchers() {
        if (pathMatchers == null)
            pathMatchers = URLRewriterUtils.getPathMatchers();
        return pathMatchers;
    }

    public String rewriteActionURL(String urlString) {
        return URLRewriterUtils.rewriteURL(request, urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
    }

    public String rewriteRenderURL(String urlString) {
        return URLRewriterUtils.rewriteURL(request, urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
    }

    public String rewriteActionURL(String urlString, String portletMode, String windowState) {
        return URLRewriterUtils.rewriteURL(request, urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
    }

    public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
        return URLRewriterUtils.rewriteURL(request, urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
    }

    public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {
        // NOTE: Get path matchers lazily, as External Context might be created before PFC runs and sets matchers
        return URLRewriterUtils.rewriteResourceURL(request, urlString, getPathMatchers(),
                generateAbsoluteURL ? ExternalContext.Response.REWRITE_MODE_ABSOLUTE : ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
    }

    public String rewriteResourceURL(String urlString, int rewriteMode) {
        // NOTE: Get path matchers lazily, as External Context might be created before PFC runs and sets matchers
        return URLRewriterUtils.rewriteResourceURL(request, urlString, getPathMatchers(), rewriteMode);
    }

    public String getNamespacePrefix() {
        return "";
    }
}
