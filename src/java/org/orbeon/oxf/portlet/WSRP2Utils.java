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
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringConversions;
import org.orbeon.oxf.xml.XMLUtils;

import javax.portlet.*;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;

/**
 * This class reproduces some methods from the super class so that it can still be used at runtime with a Portlet 1.0
 * container.
 */
public class WSRP2Utils {

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
//    public static final String URL_PARAM = "wsrp-url";
//    public static final String REQUIRES_REWRITE_PARAM = "wsrp-requiresRewrite";

    public static final String URL_TYPE_BLOCKING_ACTION_STRING = "blockingAction";
    public static final String URL_TYPE_RENDER_STRING = "render";
    public static final String URL_TYPE_RESOURCE_STRING = "resource";

    protected static final int BASE_TAG_LENGTH = BASE_TAG.length();
    protected static final int START_TAG_LENGTH = START_TAG.length();
    protected static final int END_TAG_LENGTH = END_TAG.length();
    protected static final int PREFIX_TAG_LENGTH = PREFIX_TAG.length();

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

        final StringBuffer sb = new StringBuffer(START_TAG);
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

    public static String encodeNamespacePrefix() {
        return PREFIX_TAG;
    }

    /**
     * This method parses a string containing WSRP Consumer URL and namespace encoding and encode the URLs and namespaces
     * as per the Portlet API.
     *
     * It is possible to escape by using the string wsrp_rewritewsrp_rewrite.
     */
    public static void write(MimeResponse response, String content, boolean encodeForXML) throws IOException {
        int stringLength = content.length();
        int currentIndex = 0;
        int index;
        Writer writer = response.getWriter();
        while ((index = content.indexOf(BASE_TAG, currentIndex)) != -1) {
            // Write up to the current mark
            writer.write(content, currentIndex, index - currentIndex);

            // Check if escaping is requested
            if (index + BASE_TAG_LENGTH * 2 <= stringLength
                    && content.substring(index + BASE_TAG_LENGTH, index + BASE_TAG_LENGTH * 2).equals(BASE_TAG)) {
                // Write escaped tag, update index and keep looking
                writer.write(BASE_TAG);
                currentIndex = index + BASE_TAG_LENGTH * 2;
                continue;
            }

            if (index < stringLength - BASE_TAG_LENGTH && content.charAt(index + BASE_TAG_LENGTH) == '?') {
                // URL encoding
                // Find the matching end mark
                int endIndex = content.indexOf(END_TAG, index);
                if (endIndex == -1)
                    throw new OXFException("Missing end tag for WSRP encoded URL.");
                String encodedURL = content.substring(index + START_TAG_LENGTH, endIndex);

                currentIndex = endIndex + END_TAG_LENGTH;

                final String decodedPortletURL = wsrpToPortletURL(encodedURL, response);
                writer.write(encodeForXML ? XMLUtils.escapeXMLMinimal(decodedPortletURL) : decodedPortletURL);

            } else if (index < stringLength - BASE_TAG_LENGTH && content.charAt(index + BASE_TAG_LENGTH) == '_') {
                // Namespace encoding
                writer.write(response.getNamespace());
                currentIndex = index + PREFIX_TAG_LENGTH;
            } else {
                throw new OXFException("Invalid wsrp rewrite tagging.");
            }
        }
        // Write remainder of string
        if (currentIndex < stringLength) {
            writer.write(content, currentIndex, content.length() - currentIndex);
        }
    }

    /**
     * Decode a WSRP-encoded URL into a Portlet-encoded URL.
     */
    private static String wsrpToPortletURL(String encodedURL, MimeResponse response) {
        // Parse URL
        final Map<String, String[]> wsrpParameters = NetUtils.decodeQueryString(encodedURL, true);

        // Check URL type and create URL
        try {
            final String urlTypeValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(URL_TYPE_PARAM));
            if (urlTypeValue == null)
                throw new OXFException("Missing URL type for WSRP encoded URL: " + encodedURL);

            // Case of a render or action request
            // Create a BaseURL
            final BaseURL baseURL;
            if (urlTypeValue.equals(URL_TYPE_RESOURCE_STRING))
                baseURL = response.createResourceURL();
            else if (urlTypeValue.equals(URL_TYPE_BLOCKING_ACTION_STRING))
                baseURL = response.createActionURL();
            else if (urlTypeValue.equals(URL_TYPE_RENDER_STRING))
                baseURL = response.createRenderURL();
            else
                throw new OXFException("Invalid URL type for WSRP encoded URL: " + encodedURL);

            // Get navigational state
            final Map<String, String[]> navigationParameters; {
                final String navigationalStateValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(NAVIGATIONAL_STATE_PARAM));
                if (navigationalStateValue != null) {
                    final String decodedNavigationalState;
                    try {
                        final String navigationalState = navigationalStateValue.startsWith("amp;") ? navigationalStateValue.substring(4) : navigationalStateValue;
                        decodedNavigationalState = URLDecoder.decode(navigationalState, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        // Should not happen
                        throw new OXFException(e);
                    }
                    navigationParameters = NetUtils.decodeQueryString(decodedNavigationalState, true);
                } else {
                    navigationParameters = null;
                }
            }

            if (urlTypeValue.equals(URL_TYPE_RESOURCE_STRING)) {// NOTE: With Liferay, baseURL instanceof ResourceURL is always true!
                final ResourceURL resourceURL = (ResourceURL) baseURL;

                // With resource URLs, mode and state can't be changed

                // Use resource id
                // The portal actually automatically adds existing parameters, including orbeon.path, in the resource URL.
                // If we set orbeon.path again to store the resource URL, the resulting URL ends up having two orbeon.path.
                // So instead we use the resource id, which seems to be designed for this anyway.
                final String resourceId = navigationParameters.get(OrbeonPortletXFormsFilter.PATH_PARAMETER_NAME)[0];
                resourceURL.setResourceID(resourceId);
                // PAGE is the default
                // Could set it to FULL or PORTLET for resources such as images, JavaScript, etc., but NOT for e.g. /xforms-server
                resourceURL.setCacheability(ResourceURL.PAGE);
            } else if (baseURL instanceof PortletURL) {

                final PortletURL portletURL = (PortletURL) baseURL;

                // Get and set portlet mode
                final String portletModeValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(MODE_PARAM));
                if (portletModeValue != null) {
                    final String portletMode = portletModeValue.startsWith("amp;") ? portletModeValue.substring(4) : portletModeValue;
                    portletURL.setPortletMode(new PortletMode(portletMode));
                }

                // Get and set window state
                final String windowStateValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(WINDOW_STATE_PARAM));
                if (windowStateValue != null) {
                    final String windowState = windowStateValue.startsWith("amp;") ? windowStateValue.substring(4) : windowStateValue;
                    portletURL.setWindowState(new WindowState(windowState));
                }

                //
                portletURL.setParameters(navigationParameters);
            }

            // TODO: wsrp-fragmentID
            // TODO: wsrp-secureURL

            // Write resulting encoded PortletURL
            return baseURL.toString();
        } catch (PortletException e) {
            throw new OXFException(e);
        }
    }
}
