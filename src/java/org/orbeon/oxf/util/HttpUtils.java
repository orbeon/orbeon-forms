/**
 *  Copyright (C) 2004 Orbeon, Inc.
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

import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class HttpUtils {

    private static Logger logger = LoggerFactory.createLogger(HttpUtils.class);

    private static final SimpleDateFormat dateHeaderFormats[] = {
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    };

    private static final TimeZone gmtZone = TimeZone.getTimeZone("GMT");

    static {
        // Set timezone to GMT as required for HTTP headers
        for (int i = 0; i < dateHeaderFormats.length; i++)
            dateHeaderFormats[i].setTimeZone(gmtZone);
    }

    public static boolean checkIfModifiedSince(HttpServletRequest request, long lastModified) {
        String ifModifiedHeader = request.getHeader("If-Modified-Since");
        if (logger.isDebugEnabled())
            logger.debug("Found If-Modified-Since header");
        if (ifModifiedHeader != null) {
            for (int i = 0; i < dateHeaderFormats.length; i++) {
                try {
                    Date date = dateHeaderFormats[i].parse(ifModifiedHeader);
                    if (date != null) {
                        if (lastModified <= (date.getTime() + 1000)) {
                            if (logger.isDebugEnabled())
                                logger.debug("Sending SC_NOT_MODIFIED response");
                            return true;
                        } else {
                            break;
                        }
                    }
                } catch (ParseException e) {
                    // Ignore
                }
            }
        }
        return false;
    }
}
