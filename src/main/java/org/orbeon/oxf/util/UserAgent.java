/**
 *  Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.util;

import org.orbeon.oxf.externalcontext.ExternalContext;

public class UserAgent {

    private static String getUserAgentLowerCase(ExternalContext.Request request) {
        final String[] userAgentHeader = request.getHeaderValuesMap().get("user-agent");
        if (userAgentHeader == null)
            return null;
        else
            return userAgentHeader[0].toLowerCase();
    }

    /**
     * Test whether the user agent is IE.
     */
    public static boolean isUserAgentIE(ExternalContext.Request request) {
        final String userAgent = getUserAgentLowerCase(request);
        return userAgent != null && userAgent.contains("msie") && ! userAgent.contains("opera");
    }

    /**
     * Return the IE version, -1 if not IE.
     */
    public static int getMSIEVersion(ExternalContext.Request request) {

        if (! isUserAgentIE(request))
            return -1;

        final String userAgent = getUserAgentLowerCase(request);
        final int msieIndex = userAgent.indexOf("msie");

        try {
            final String versionString = StringUtils.trimAllToEmpty(userAgent.substring(msieIndex + 4, userAgent.indexOf(';', msieIndex + 5)));

            final int dotIndex = versionString.indexOf('.');
            if (dotIndex == -1)
                return Integer.parseInt(versionString);
            else
                return Integer.parseInt(versionString.substring(0, dotIndex));
        } catch (Exception e) {
            return -1;
        }
    }
}
