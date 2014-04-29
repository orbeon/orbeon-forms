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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.WSRPURLRewriter;
import org.orbeon.oxf.portlet.liferay.LiferayURL;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringConversions;

import javax.portlet.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.Map;

/**
 * This class reproduces some methods from the super class so that it can still be used at runtime with a Portlet 1.0
 * container.
 */
public class WSRP2Utils {

    /**
     * This method parses a string containing WSRP Consumer URL and namespace encoding and encode the URLs and namespaces
     * as per the Portlet API.
     *
     * It is possible to escape by using the string wsrp_rewritewsrp_rewrite.
     */
    public static void write(MimeResponse response, String content, String namespace, boolean encodeForXML) throws IOException {
        int stringLength = content.length();
        int currentIndex = 0;
        int index;
        final Writer writer = response.getWriter();

        while ((index = content.indexOf(WSRPURLRewriter.BaseTag(), currentIndex)) != -1) {
            // Write up to the current mark
            writer.write(content, currentIndex, index - currentIndex);

            // Check if escaping is requested
            if (index + WSRPURLRewriter.BaseTagLength() * 2 <= stringLength
                    && content.substring(index + WSRPURLRewriter.BaseTagLength(), index + WSRPURLRewriter.BaseTagLength() * 2).equals(WSRPURLRewriter.BaseTag())) {
                // Write escaped tag, update index and keep looking
                writer.write(WSRPURLRewriter.BaseTag());
                currentIndex = index + WSRPURLRewriter.BaseTagLength() * 2;
                continue;
            }

            if (index < stringLength - WSRPURLRewriter.BaseTagLength() && content.charAt(index + WSRPURLRewriter.BaseTagLength()) == '?') {
                // URL encoding
                // Find the matching end mark
                int endIndex = content.indexOf(WSRPURLRewriter.EndTag(), index);
                if (endIndex == -1)
                    throw new OXFException("Missing end tag for WSRP encoded URL.");
                final String encodedURL = content.substring(index + WSRPURLRewriter.StartTagLength(), endIndex);

                currentIndex = endIndex + WSRPURLRewriter.EndTagLength();

                final String decodedPortletURL = wsrpToPortletURL(encodedURL, response);
                writer.write(encodeForXML ? escapeXMLMinimal(decodedPortletURL) : decodedPortletURL);

            } else if (index < stringLength - WSRPURLRewriter.BaseTagLength() && content.charAt(index + WSRPURLRewriter.BaseTagLength()) == '_') {
                // Namespace encoding
                writer.write(namespace);
                currentIndex = index + WSRPURLRewriter.PrefixTagLength();
            } else {
                throw new OXFException("Invalid wsrp rewrite tagging.");
            }
        }
        // Write remainder of string
        if (currentIndex < stringLength) {
            writer.write(content, currentIndex, content.length() - currentIndex);
        }
    }

    // FIXME: Duplicated from XFormsUtils, see https://github.com/orbeon/orbeon-forms/issues/960
    public static String escapeXMLMinimal(String str) {
        str = str.replace("&", "&amp;");
        str = str.replace("<", "&lt;");
        return str;
    }

    /**
     * Decode a WSRP-encoded URL into a Portlet-encoded URL.
     */
    private static String wsrpToPortletURL(String encodedURL, MimeResponse response) {
        // Parse URL
        final Map<String, String[]> wsrpParameters = NetUtils.decodeQueryStringPortlet(encodedURL);

        // Check URL type and create URL
        try {
            final String urlTypeValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(WSRPURLRewriter.URLTypeParam()));
            if (urlTypeValue == null)
                throw new OXFException("Missing URL type for WSRP encoded URL: " + encodedURL);

            // Case of a render or action request
            // Create a BaseURL
            final BaseURL baseURL;
            if (urlTypeValue.equals(WSRPURLRewriter.URLTypeResourceString()))
                baseURL = response.createResourceURL();
            else if (urlTypeValue.equals(WSRPURLRewriter.URLTypeBlockingActionString()))
                baseURL = response.createActionURL();
            else if (urlTypeValue.equals(WSRPURLRewriter.URLTypeRenderString()))
                baseURL = response.createRenderURL();
            else
                throw new OXFException("Invalid URL type for WSRP encoded URL: " + encodedURL);

            // Get navigational state
            final Map<String, String[]> navigationParameters; {
                final String navigationalStateValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(WSRPURLRewriter.NavigationalStateParam()));
                if (navigationalStateValue != null) {
                    final String decodedNavigationalState;
                    try {
                        final String navigationalState = navigationalStateValue.startsWith("amp;") ? navigationalStateValue.substring(4) : navigationalStateValue;
                        decodedNavigationalState = URLDecoder.decode(navigationalState, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        // Should not happen
                        throw new OXFException(e);
                    }
                    navigationParameters = NetUtils.decodeQueryStringPortlet(decodedNavigationalState);
                } else {
                    navigationParameters = null;
                }
            }

            if (urlTypeValue.equals(WSRPURLRewriter.URLTypeResourceString())) {// NOTE: With Liferay, baseURL instanceof ResourceURL is always true!
                final String resourcePath = navigationParameters.get(WSRPURLRewriter.PathParameterName())[0];

                // Encode the other parameters directly into the resource id, as they are really part of the identity
                // of the resource and have nothing to do with the current render parameters.
                navigationParameters.remove(WSRPURLRewriter.PathParameterName()); // WARNING: mutate navigationParameters
                final String resourceQuery = NetUtils.encodeQueryString2(navigationParameters);
                final String resourceId = NetUtils.appendQueryString(resourcePath, resourceQuery);

                // Serve resources via the portlet
                // NOTE: If encoding resource URLs is disabled, we won't even reach this point as a plain URL/path is
                // generated. See WSRPURLRewriter.rewriteResourceURL().
                final ResourceURL resourceURL = (ResourceURL) baseURL;

                // With resource URLs, mode and state can't be changed

                // Use resource id
                // The portal actually automatically adds existing parameters, including orbeon.path, in the resource URL.
                // If we set orbeon.path again to store the resource URL, the resulting URL ends up having two orbeon.path.
                // So instead we use the resource id, which seems to be designed for this anyway.

                resourceURL.setResourceID(resourceId);

                // PAGE is the default
                // Could set it to FULL or PORTLET for resources such as images, JavaScript, etc., but NOT for e.g. /xforms-server
                // Note that Liferay doesn't seem to do much with ResourceURL.FULL.
                resourceURL.setCacheability(ResourceURL.PAGE);

                return LiferayURL.moveMagicResourceId(baseURL.toString());
            } else if (baseURL instanceof PortletURL) {

                final PortletURL portletURL = (PortletURL) baseURL;

                // Get and set portlet mode
                final String portletModeValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(WSRPURLRewriter.ModeParam()));
                if (portletModeValue != null) {
                    final String portletMode = portletModeValue.startsWith("amp;") ? portletModeValue.substring(4) : portletModeValue;
                    portletURL.setPortletMode(new PortletMode(portletMode));
                }

                // Get and set window state
                final String windowStateValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(WSRPURLRewriter.WindowStateParam()));
                if (windowStateValue != null) {
                    final String windowState = windowStateValue.startsWith("amp;") ? windowStateValue.substring(4) : windowStateValue;
                    portletURL.setWindowState(new WindowState(windowState));
                }

                // Simply set all navigation parameters, including orbeon.path
                if (navigationParameters != null)
                    portletURL.setParameters(navigationParameters);

                return baseURL.toString();
            } else {
                return baseURL.toString();
            }

            // TODO: wsrp-fragmentID
            // TODO: wsrp-secureURL
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
