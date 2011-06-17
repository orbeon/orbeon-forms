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

import org.orbeon.oxf.common.OXFException;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ISODateUtils {

    public static final SimpleDateFormat XS_DATE_TIME_LONG = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    public static final SimpleDateFormat XS_DATE_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    public static final SimpleDateFormat XS_DATE = new SimpleDateFormat("yyyy-MM-dd");

    public static final SimpleDateFormat RFC1123_DATE = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
    public static final TimeZone GMT_TZ = TimeZone.getTimeZone("GMT");

    static {
        RFC1123_DATE.setTimeZone(GMT_TZ);
    }

    public static Date parseDate(String date) {
        try {
            if (date.length() == 10) {
                return XS_DATE.parse(date);
            } else if (date.indexOf('.') > 0) {
                return XS_DATE_TIME_LONG.parse(date);
            } else {
                return XS_DATE_TIME.parse(date);
            }
        } catch (ParseException e) {
            throw new OXFException(e);
        }
    }

    public static String formatDate(Date date, DateFormat format) {
        return format.format(date);
    }

    public static String getRFC1123Date(long time) {
        return RFC1123_DATE.format(new Long(time));
    }

    public static long parseRFC1123Date(String date) {
        try {
            // Add a trailing space so that parsing in the JDK does not internally throw a costly StringIndexOutOfBoundsException
            return RFC1123_DATE.parse(date + ' ').getTime();
        } catch (ParseException e) {
            throw new OXFException(e);
        }
    }

    public static long getCurrentTimeMillis() {
        return Calendar.getInstance().getTimeInMillis();
    }
}
