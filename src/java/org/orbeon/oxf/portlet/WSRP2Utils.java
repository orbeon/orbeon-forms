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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringUtils;
import org.orbeon.oxf.xml.XMLUtils;

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
public class WSRP2Utils extends WSRPUtils {

    /**
     * This method parses a string containing WSRP Consumer URL and namespace
     * encoding and encode the URLs and namespaces as per the Portlet API.
     *
     * It is possible to escape by using the string wsrp_rewritewsrp_rewrite.
     */
    public static void write(MimeResponse response, String s, boolean encodeForXML) throws IOException {
        int stringLength = s.length();
        int currentIndex = 0;
        int index;
        Writer writer = response.getWriter();
        while ((index = s.indexOf(BASE_TAG, currentIndex)) != -1) {
            // Write up to the current mark
            writer.write(s, currentIndex, index - currentIndex);

            // Check if escaping is requested
            if (index + BASE_TAG_LENGTH * 2 <= stringLength
                    && s.substring(index + BASE_TAG_LENGTH, index + BASE_TAG_LENGTH * 2).equals(BASE_TAG)) {
                // Write escaped tag, update index and keep looking
                writer.write(BASE_TAG);
                currentIndex = index + BASE_TAG_LENGTH * 2;
                continue;
            }

            if (index < stringLength - BASE_TAG_LENGTH && s.charAt(index + BASE_TAG_LENGTH) == '?') {
                // URL encoding
                // Find the matching end mark
                int endIndex = s.indexOf(END_TAG, index);
                if (endIndex == -1)
                    throw new OXFException("Missing end tag for WSRP encoded URL.");
                String encodedURL = s.substring(index + START_TAG_LENGTH, endIndex);
//                System.out.println("XXX Found WSRP-encoded URL: " + encodedURL);

                currentIndex = endIndex + END_TAG_LENGTH;

                final String decodedPortletURL = decodePortletURL(encodedURL, response);
                writer.write(encodeForXML ? XMLUtils.escapeXMLMinimal(decodedPortletURL) : decodedPortletURL);

            } else if (index < stringLength - BASE_TAG_LENGTH && s.charAt(index + BASE_TAG_LENGTH) == '_') {
                // Namespace encoding
                writer.write(response.getNamespace());
                currentIndex = index + PREFIX_TAG_LENGTH;
            } else {
                throw new OXFException("Invalid wsrp rewrite tagging.");
            }
        }
        // Write remainder of string
        if (currentIndex < stringLength) {
            writer.write(s, currentIndex, s.length() - currentIndex);
        }
    }
    
    private static String decodePortletURL(String encodedURL, MimeResponse response) {
        // Parse URL
        final Map wsrpParameters = NetUtils.decodeQueryString(encodedURL, true);

        // Check URL type and create URL
        try {
            final String urlTypeValue = StringUtils.getStringFromObjectArray((Object[]) wsrpParameters.get(URL_TYPE_PARAM));
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

            if (baseURL instanceof PortletURL) {
                // Get portlet mode
                final PortletURL portletURL = (PortletURL) baseURL;
                final String portletModeValue = StringUtils.getStringFromObjectArray((Object[]) wsrpParameters.get(MODE_PARAM));
                if (portletModeValue != null) {
                    final String portletMode = portletModeValue.startsWith("amp;") ? portletModeValue.substring(4) : portletModeValue;
                    portletURL.setPortletMode(new PortletMode(portletMode));
                }

                // Get window state
                final String windowStateValue = StringUtils.getStringFromObjectArray((Object[]) wsrpParameters.get(WINDOW_STATE_PARAM));
                if (windowStateValue != null) {
                    final String windowState = windowStateValue.startsWith("amp;") ? windowStateValue.substring(4) : windowStateValue;
                    portletURL.setWindowState(new WindowState(windowState));
                }
            } else {
                // NOP
                //final ResourceURL resourceURL = (ResourceURL) baseURL;
            }

            // Get navigational state
            final String navigationalStateValue = StringUtils.getStringFromObjectArray((Object[]) wsrpParameters.get(NAVIGATIONAL_STATE_PARAM));
            if (navigationalStateValue != null) {
                final String decodedNavigationalState;
                try {
                    final String navigationalState = navigationalStateValue.startsWith("amp;") ? navigationalStateValue.substring(4) : navigationalStateValue;
                    decodedNavigationalState = URLDecoder.decode(navigationalState, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    // Should not happen
                    throw new OXFException(e);
                }
                final Map navigationParameters = NetUtils.decodeQueryString(decodedNavigationalState, true);
                baseURL.setParameters(navigationParameters);
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
