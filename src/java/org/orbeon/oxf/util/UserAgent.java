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

import org.orbeon.oxf.pipeline.api.ExternalContext;

public class UserAgent {
    /**
     * Test whether the user agent is Trident.
     *
     * @param request   incoming request
     * @return          true if Trident is identified
     */
    public static boolean isRenderingEngineTrident(ExternalContext.Request request) {
        final Object[] userAgentHeader = (Object[]) request.getHeaderValuesMap().get("user-agent");
        if (userAgentHeader == null)
            return false;

        final String userAgent = ((String) userAgentHeader[0]).toLowerCase();
        return userAgent.indexOf("msie") != -1 && userAgent.indexOf("opera") == -1;
    }

    /**
     * Test whether the user agent is IE 6 or earlier.
     *
     * @param request   incoming request
     * @return          true if IE 6 or earlier is identified
     */
    public static boolean isRenderingEngineIE6OrEarlier(ExternalContext.Request request) {
        final Object[] userAgentHeader = (Object[]) request.getHeaderValuesMap().get("user-agent");
        if (userAgentHeader == null)
            return false;

        final String userAgent = ((String) userAgentHeader[0]).toLowerCase();

        final int msieIndex = userAgent.indexOf("msie");
        final boolean isIE = msieIndex != -1 && userAgent.indexOf("opera") == -1;
        if (!isIE)
            return false;

        final String versionString = userAgent.substring(msieIndex + 4, userAgent.indexOf(';', msieIndex + 5)).trim();

        final int dotIndex = versionString.indexOf('.');
        final int version;
        if (dotIndex == -1) {
            version = Integer.parseInt(versionString);
        } else {
            version = Integer.parseInt(versionString.substring(0, dotIndex));
        }
        return version <= 6;
    }

//    public static boolean isRenderingEngineSafari(ExternalContext.Request request) {
//        final Object[] userAgentHeader = (Object[]) request.getHeaderValuesMap().get("user-agent");
//        if (userAgentHeader == null)
//            return false;
//
//        final String userAgent = ((String) userAgentHeader[0]).toLowerCase();
//        return userAgent.indexOf("safari") != -1;
//    }
}
