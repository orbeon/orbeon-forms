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
package org.orbeon.oxf.pipeline;

import org.dom4j.Document;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.sxpath.XPathEvaluator;

public class StaticExternalContext {

    public static String rewriteActionURL(String urlString) {
        return NetUtils.getExternalContext().getResponse().rewriteActionURL(urlString);
    }

    public static String rewriteRenderURL(String urlString) {
        return NetUtils.getExternalContext().getResponse().rewriteRenderURL(urlString);
    }

    public static String rewriteResourceURL(String urlString) {
        return rewriteResourceURL(urlString, false);
    }

    public static String rewriteResourceURL(String urlString, boolean forceAbsolute) {
        return NetUtils.getExternalContext().getResponse().rewriteResourceURL(urlString, forceAbsolute);
    }

    public static String rewriteServiceURL(String urlString, boolean forceAbsolute) {
        return NetUtils.getExternalContext().rewriteServiceURL(urlString,
                forceAbsolute ? ExternalContext.Response.REWRITE_MODE_ABSOLUTE : ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH);
    }

    public static String setTitle(String title) {
        NetUtils.getExternalContext().getResponse().setTitle(title);
        return "";
    }

    public static XPathEvaluator newEvaluator(NodeInfo context) {
        return new XPathEvaluator(context.getConfiguration());
    }

    public static String encodeXML(org.w3c.dom.Node node) {
        return XFormsUtils.encodeXMLAsDOM(node);
    }

    public static Document decodeXML(String encodedXML) {
        return XFormsUtils.decodeXML(encodedXML);
    }

    public static void putInSession(String key, String value) {
        NetUtils.getExternalContext().getSession(true)
                .getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE).put(key, value);
    }

    public static void removeFromSession(String key) {
        NetUtils.getExternalContext().getSession(true)
                .getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE).remove(key);
    }

    public static boolean isPE() {
        return Version.isPE();
    }

    public static boolean isPortlet() {
        return "portlet".equals(NetUtils.getExternalContext().getRequest().getContainerType());
    }
}
