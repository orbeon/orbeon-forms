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
import org.orbeon.oxf.portlet.OrbeonPortletXFormsFilter;
import org.orbeon.oxf.util.NetUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

public class TemplateURLRewriter implements URLRewriter {

    private final String namespace;
    private final String renderURLTemplate;
    private final String actionURLTemplate;
    private final String resourceURLTemplate;

    public TemplateURLRewriter(ExternalContext.Request request) {
        final Map<String, Object> attributes = request.getAttributesMap();
        namespace = (String) attributes.get(OrbeonPortletXFormsFilter.PORTLET_NAMESPACE_TEMPLATE_ATTRIBUTE);
        renderURLTemplate = (String) attributes.get(OrbeonPortletXFormsFilter.PORTLET_RENDER_URL_TEMPLATE_ATTRIBUTE);
        actionURLTemplate = (String) attributes.get(OrbeonPortletXFormsFilter.PORTLET_ACTION_URL_TEMPLATE_ATTRIBUTE);
        resourceURLTemplate = (String) attributes.get(OrbeonPortletXFormsFilter.PORTLET_RESOURCE_URL_TEMPLATE_ATTRIBUTE);
    }

    public String rewriteRenderURL(String urlString) {
        return rewriteRenderURL(urlString, null, null);
    }

    public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
        return rewrite(urlString, renderURLTemplate);
    }

    public String rewriteActionURL(String urlString) {
        return rewriteActionURL(urlString, null, null);
    }

    public String rewriteActionURL(String urlString, String portletMode, String windowState) {
        return rewrite(urlString, actionURLTemplate);
    }

    public String rewriteResourceURL(String urlString, int rewriteMode) {
        return rewrite(urlString, resourceURLTemplate);
    }

    private String rewrite(String urlString, String template) {
        if (NetUtils.urlHasProtocol(urlString) || urlString.startsWith("#")) {
            return urlString;
        } else {
            try {
                return template.replace(OrbeonPortletXFormsFilter.PATH_TEMPLATE, URLEncoder.encode(urlString, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                throw new OXFException(e);
            }
        }
    }

    public String getNamespacePrefix() {
        return namespace != null ? namespace : "";
    }
}
