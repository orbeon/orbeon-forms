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
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringConversions;

import javax.portlet.*;
import java.io.*;
import java.net.URLDecoder;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public static void write(MimeResponse response, String content, boolean encodeForXML) throws IOException {
        int stringLength = content.length();
        int currentIndex = 0;
        int index;
        Writer writer = response.getWriter();

        final String namespace = response.getNamespace().replace(' ', '_');// replacement probably because at some point Liferay was allowing spaces in namespace

        while ((index = content.indexOf(WSRPURLRewriter.BASE_TAG, currentIndex)) != -1) {
            // Write up to the current mark
            writer.write(content, currentIndex, index - currentIndex);

            // Check if escaping is requested
            if (index + WSRPURLRewriter.BASE_TAG_LENGTH * 2 <= stringLength
                    && content.substring(index + WSRPURLRewriter.BASE_TAG_LENGTH, index + WSRPURLRewriter.BASE_TAG_LENGTH * 2).equals(WSRPURLRewriter.BASE_TAG)) {
                // Write escaped tag, update index and keep looking
                writer.write(WSRPURLRewriter.BASE_TAG);
                currentIndex = index + WSRPURLRewriter.BASE_TAG_LENGTH * 2;
                continue;
            }

            if (index < stringLength - WSRPURLRewriter.BASE_TAG_LENGTH && content.charAt(index + WSRPURLRewriter.BASE_TAG_LENGTH) == '?') {
                // URL encoding
                // Find the matching end mark
                int endIndex = content.indexOf(WSRPURLRewriter.END_TAG, index);
                if (endIndex == -1)
                    throw new OXFException("Missing end tag for WSRP encoded URL.");
                String encodedURL = content.substring(index + WSRPURLRewriter.START_TAG_LENGTH, endIndex);

                currentIndex = endIndex + WSRPURLRewriter.END_TAG_LENGTH;

                final String decodedPortletURL = wsrpToPortletURL(encodedURL, response);
                writer.write(encodeForXML ? escapeXMLMinimal(decodedPortletURL) : decodedPortletURL);

            } else if (index < stringLength - WSRPURLRewriter.BASE_TAG_LENGTH && content.charAt(index + WSRPURLRewriter.BASE_TAG_LENGTH) == '_') {
                // Namespace encoding
                writer.write(namespace);
                currentIndex = index + WSRPURLRewriter.PREFIX_TAG_LENGTH;
            } else {
                throw new OXFException("Invalid wsrp rewrite tagging.");
            }
        }
        // Write remainder of string
        if (currentIndex < stringLength) {
            writer.write(content, currentIndex, content.length() - currentIndex);
        }
    }

    private static final Pattern PATTERN_NO_AMP;
    private static final Pattern PATTERN_AMP;

    static {
        final String notEqNorAmpChar = "[^=&]";
        final String token = notEqNorAmpChar+ "+";
        PATTERN_NO_AMP = Pattern.compile( "(" + token + ")=(" + token + ")(?:&|(?<!&)\\z)" );
        PATTERN_AMP = Pattern.compile( "(" + token + ")=(" + token + ")(?:&amp;|&|(?<!&amp;|&)\\z)" );
    }

    public static String escapeXMLMinimal(String str) {
        str = str.replace("&", "&amp;");
        str = str.replace("<", "&lt;");
        return str;
    }

    public static final String STANDARD_PARAMETER_ENCODING = "utf-8";

    public static Map<String, String[]> decodeQueryString(final CharSequence queryString, final boolean acceptAmp) {

        final Map<String, String[]> result = new TreeMap<String, String[]>();
        if (queryString != null) {
            final Matcher matcher = acceptAmp ? PATTERN_AMP.matcher(queryString) : PATTERN_NO_AMP.matcher(queryString);
            int matcherEnd = 0;
            while (matcher.find()) {
                matcherEnd = matcher.end();
                try {
                    // Group 0 is the whole match, e.g. a=b, while group 1 is the first group
                    // denoted (with parens) in the expression. Hence we start with group 1.
                    String name = URLDecoder.decode(matcher.group(1), STANDARD_PARAMETER_ENCODING);
                    final String value = URLDecoder.decode(matcher.group(2), STANDARD_PARAMETER_ENCODING);

                    // Handle the case where the source contains &amp;amp; because of double escaping which does occur in
                    // full Ajax updates!
                    if (acceptAmp && name.startsWith("amp;"))
                        name = name.substring("amp;".length());

                    StringConversions.addValueToStringArrayMap(result, name, value);
                } catch (UnsupportedEncodingException e) {
                    // Should not happen as we are using a required encoding
                    throw new OXFException(e);
                }
            }
            if (queryString.length() != matcherEnd) {
                // There was garbage at the end of the query.
                throw new OXFException("Malformed URL: " + queryString);
            }
        }
        return result;
    }

    /**
     * Decode a WSRP-encoded URL into a Portlet-encoded URL.
     */
    private static String wsrpToPortletURL(String encodedURL, MimeResponse response) {
        // Parse URL
        final Map<String, String[]> wsrpParameters = decodeQueryString(encodedURL, true);

        // Check URL type and create URL
        try {
            final String urlTypeValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(WSRPURLRewriter.URL_TYPE_PARAM));
            if (urlTypeValue == null)
                throw new OXFException("Missing URL type for WSRP encoded URL: " + encodedURL);

            // Case of a render or action request
            // Create a BaseURL
            final BaseURL baseURL;
            if (urlTypeValue.equals(WSRPURLRewriter.URL_TYPE_RESOURCE_STRING))
                baseURL = response.createResourceURL();
            else if (urlTypeValue.equals(WSRPURLRewriter.URL_TYPE_BLOCKING_ACTION_STRING))
                baseURL = response.createActionURL();
            else if (urlTypeValue.equals(WSRPURLRewriter.URL_TYPE_RENDER_STRING))
                baseURL = response.createRenderURL();
            else
                throw new OXFException("Invalid URL type for WSRP encoded URL: " + encodedURL);

            // Get navigational state
            final Map<String, String[]> navigationParameters; {
                final String navigationalStateValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(WSRPURLRewriter.NAVIGATIONAL_STATE_PARAM));
                if (navigationalStateValue != null) {
                    final String decodedNavigationalState;
                    try {
                        final String navigationalState = navigationalStateValue.startsWith("amp;") ? navigationalStateValue.substring(4) : navigationalStateValue;
                        decodedNavigationalState = URLDecoder.decode(navigationalState, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        // Should not happen
                        throw new OXFException(e);
                    }
                    navigationParameters = decodeQueryString(decodedNavigationalState, true);
                } else {
                    navigationParameters = null;
                }
            }

            if (urlTypeValue.equals(WSRPURLRewriter.URL_TYPE_RESOURCE_STRING)) {// NOTE: With Liferay, baseURL instanceof ResourceURL is always true!
                final String resourcePath = navigationParameters.get(OrbeonPortletXFormsFilter.PATH_PARAMETER_NAME)[0];

                // Encode the other parameters directly into the resource id, as they are really part of the identity
                // of the resource and have nothing to do with the current render parameters.
                navigationParameters.remove(OrbeonPortletXFormsFilter.PATH_PARAMETER_NAME); // WARNING: mutate navigationParameters
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
            } else if (baseURL instanceof PortletURL) {

                final PortletURL portletURL = (PortletURL) baseURL;

                // Get and set portlet mode
                final String portletModeValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(WSRPURLRewriter.MODE_PARAM));
                if (portletModeValue != null) {
                    final String portletMode = portletModeValue.startsWith("amp;") ? portletModeValue.substring(4) : portletModeValue;
                    portletURL.setPortletMode(new PortletMode(portletMode));
                }

                // Get and set window state
                final String windowStateValue = StringConversions.getStringFromObjectArray(wsrpParameters.get(WSRPURLRewriter.WINDOW_STATE_PARAM));
                if (windowStateValue != null) {
                    final String windowState = windowStateValue.startsWith("amp;") ? windowStateValue.substring(4) : windowStateValue;
                    portletURL.setWindowState(new WindowState(windowState));
                }

                // Simply set all navigation parameters, including orbeon.path
                if (navigationParameters != null)
                    portletURL.setParameters(navigationParameters);
            }

            // TODO: wsrp-fragmentID
            // TODO: wsrp-secureURL

            // Write resulting encoded PortletURL
            return baseURL.toString();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
