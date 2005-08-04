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
import org.orbeon.oxf.common.OXFException;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetUtils {

    private static Logger logger = LoggerFactory.createLogger(NetUtils.class);

    /**
     * <!-- PATTERN_NO_AMP -->
     * @see #decodeQueryString(CharSequence, boolean)
     * @author d
     */
    private static final Pattern PATTERN_NO_AMP;
    /**
     * <!-- PATTERN_AMP -->
     * @see #decodeQueryString(CharSequence, boolean)
     * @author d
     */
    private static final Pattern PATTERN_AMP;

    public static final String DEFAULT_URL_ENCODING = "utf-8";

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

        final String notEqNorAmpChar = "[^=&]";
        final String token = notEqNorAmpChar+ "+";
        PATTERN_NO_AMP = Pattern.compile( "(" + token + ")=(" + token + ")(?:&|(?<!&)\\z)" );
        PATTERN_AMP 
            = Pattern.compile( "(" + token + ")=(" + token + ")(?:&amp;|&|(?<!&amp;|&)\\z)" );
    }

    public static long getDateHeader(String stringValue) throws ParseException {
        for (int i = 0; i < dateHeaderFormats.length; i++) {
            try {
                Date date = dateHeaderFormats[i].parse(stringValue);
                return date.getTime();
            } catch (Exception e) {// used to be ParseException, but NumberFormatException may be thrown as well
                // Ignore and try next
            }
        }
        throw new ParseException(stringValue, 0);
    }

    /**
     * Return true if the document was modified since the given date, based on the If-Modified-Since
     * header. If the request method was not "GET", or if no valid lastModified value was provided,
     * consider the document modified.
     */
    public static boolean checkIfModifiedSince(HttpServletRequest request, long lastModified) {
        // Do the check only for the GET method
        if (!"GET".equals(request.getMethod()) || lastModified <= 0)
            return true;
        // Check dates
        String ifModifiedHeader = request.getHeader("If-Modified-Since");
        if (logger.isDebugEnabled())
            logger.debug("Found If-Modified-Since header");
        if (ifModifiedHeader != null) {
            try {
                long dateTime = getDateHeader(ifModifiedHeader);
                if (lastModified <= (dateTime + 1000)) {
                    if (logger.isDebugEnabled())
                        logger.debug("Sending SC_NOT_MODIFIED response");
                    return false;
                }
            } catch (Exception e) {// used to be ParseException, but NumberFormatException may be thrown as well
                // Ignore
            }
        }
        return true;
    }

    /**
     * Return a request path info that looks like what one would expect.
     * Request path = servlet path + path info
     */
    public static String getRequestPathInfo(HttpServletRequest request) {

        // Get servlet path and path info
        String servletPath = request.getServletPath();
        if (servletPath == null) servletPath = "";
        String pathInfo = request.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        // Concatenate servlet path and path info, avoiding a double slash
        String requestPath = servletPath.endsWith("/") && pathInfo.startsWith("/")
                ? servletPath + pathInfo.substring(1)
                : servletPath + pathInfo;

        // Add starting slash if missing
        if (!requestPath.startsWith("/"))
            requestPath = "/" + requestPath;

        return requestPath;
    }

    public static long getLastModified(java.net.URL u) throws java.io.IOException {
        final java.net.URLConnection uc = u.openConnection();
        final long ret = getLastModified(uc);
        return ret;
    }

    public static Long getLastModified(java.net.URL u, Long notUsed) throws java.io.IOException {
        final long modTim = getLastModified(u);
        final Long ret = new Long(modTim);
        return ret;
    }

    /**
     * Get the last modification date of an open URLConnection.
     * <p/>
     * This handles the (broken at some point) case of the file: protocol.
     */
    public static long getLastModified(URLConnection urlConnection) {
        try {
            long lastModified = urlConnection.getLastModified();
            if (lastModified == 0 && "file".equals(urlConnection.getURL().getProtocol()))
                lastModified = new File(URLDecoder.decode(urlConnection.getURL().getFile(), DEFAULT_URL_ENCODING)).lastModified();
            return lastModified;
        } catch (UnsupportedEncodingException e) {
            // Should not happen as we are using a required encoding
            throw new OXFException(e);
        }
    }

    /**
     * Check if an URL is relative to another URL.
     */
    public static boolean relativeURL(URL url1, URL url2) {
        return ((url1.getProtocol() == null && url2.getProtocol() == null) || url1.getProtocol().equals(url2.getProtocol()))
                && ((url1.getAuthority() == null && url2.getAuthority() == null) || url1.getAuthority().equals(url2.getAuthority()))
                && ((url1.getPath() == null && url2.getPath() == null) || url2.getPath().startsWith(url1.getPath()));
    }

    public static void copyStream(InputStream is, OutputStream os) throws IOException {
        int count;
        byte[] buffer = new byte[1024];
        while ((count = is.read(buffer)) > 0)
            os.write(buffer, 0, count);
    }

    public static void copyStream(Reader reader, Writer writer) throws IOException {
        int count;
        char[] buffer = new char[1024];
        while ((count = reader.read(buffer)) > 0)
            writer.write(buffer, 0, count);
    }

    public static String getContentTypeCharset(String contentType) {
        if (contentType == null)
            return null;
        int semicolumnIndex = contentType.indexOf(";");
        if (semicolumnIndex == -1)
            return null;
        int charsetIndex = contentType.indexOf("charset=", semicolumnIndex);
        if (charsetIndex == -1)
            return null;
        // FIXME: There may be other attributes after charset, right?
        String afterCharset = contentType.substring(charsetIndex + 8);
        afterCharset = afterCharset.replace('"', ' ');
        return afterCharset.trim();
    }

    public static String getContentTypeMediaType(String contentType) {
        if (contentType == null || contentType.equalsIgnoreCase("content/unknown"))
            return null;
        int semicolumnIndex = contentType.indexOf(";");
        if (semicolumnIndex == -1)
            return contentType;
        return contentType.substring(0, semicolumnIndex).trim();
    }

    /**
     * Convert an Enumeration of String into an array.
     */
    public static String[] stringEnumerationToArray(Enumeration enumeration) {
        List values = new ArrayList();
        while (enumeration.hasMoreElements())
            values.add(enumeration.nextElement());
        String[] stringValues = new String[values.size()];
        values.toArray(stringValues);
        return stringValues;
    }

    /**
     * @param a query string of the form n1=v1&n2=v2&... to decode.  May be null.
     * @param true if both &amp; and '&' should be accepted as delimiters and false if only & 
     *              should be considered as a delimiter.
     * @return a Map of String[] indexed by name, an empty Map if the query string was null
     */
    public static java.util.Map decodeQueryString
    ( final CharSequence qry, final boolean accptAmp ) {

        final java.util.Map ret = new java.util.TreeMap();

        if ( qry != null ) {

            final Matcher m = accptAmp ? PATTERN_AMP.matcher( qry ) : PATTERN_NO_AMP.matcher( qry );
            int mtchEnd = 0;
            while ( m.find() )
            {
                if ( m.start() != mtchEnd ) {
                    //  We have detected something like a=b=c=d.  That is we noticed that the last
                    //  match ended on 'b' and that this match starts on 'c'.  Since we skipped
                    //  something there must be a problem.
                    throw new OXFException( "Malformed URL: " + qry );
                }
                mtchEnd = m.end();

                try {
                    // Group 0 is the whole match, e.g. a=b, while group 1 is the first group
                    // denoted ( with parens ) in the expression.  Hence we start with group 1.
                    String nam = m.group( 1 );
                    nam = URLDecoder.decode( nam, NetUtils.DEFAULT_URL_ENCODING );

                    String val = m.group( 2 );
                    val= URLDecoder.decode( val, NetUtils.DEFAULT_URL_ENCODING );

                    NetUtils.addValueToStringArrayMap( ret, nam, val );
                } catch ( final java.io.UnsupportedEncodingException e ) {
                    // Should not happen as we are using a required encoding
                    throw new OXFException( e );
                }
            }
            if ( qry.length() != mtchEnd ) {
                // There was garbage at the end of the query.
                throw new OXFException( "Malformed URL: " + qry );
            }
        }
        return ret;
    }

    /**
     * Make sure a query string is made of valid pairs name/value.
     */
    public static String normalizeQuery(String query, boolean acceptAmp) {
        if (query == null)
            return null;
        return encodeQueryString(decodeQueryString(query, acceptAmp));
    }

    /**
     * Encode a query string. The input Map contains names indexing String[].
     */
    public static String encodeQueryString(Map parameters) {
        StringBuffer sb = new StringBuffer();
        boolean first = true;
        try {
            for (Iterator i = parameters.keySet().iterator(); i.hasNext();) {
                String name = (String) i.next();
                String[] values = (String[]) parameters.get(name);
                for (int j = 0; j < values.length; j++) {
                    if (!first)
                        sb.append('&');

                    sb.append(URLEncoder.encode(name, NetUtils.DEFAULT_URL_ENCODING));
                    sb.append('=');
                    sb.append(URLEncoder.encode(values[j], NetUtils.DEFAULT_URL_ENCODING));

                    first = false;
                }
            }
        } catch (UnsupportedEncodingException e) {
            // Should not happen as we are using a required encoding
            throw new OXFException(e);
        }
        return sb.toString();
    }

    public static void addValueToStringArrayMap(Map map, String name, String value) {
        String[] currentValue = (String[]) map.get(name);
        if (currentValue == null) {
            map.put(name, new String[]{value});
        } else {
            String[] newValue = new String[currentValue.length + 1];
            System.arraycopy(currentValue, 0, newValue, 0, currentValue.length);
            newValue[currentValue.length] = value;
            map.put(name, newValue);
        }
    }

    public static void addValuesToStringArrayMap(Map map, String name, String[] values) {
        String[] currentValues = (String[]) map.get(name);
        if (currentValues == null) {
            map.put(name, values);
        } else {
            String[] newValues = new String[currentValues.length + values.length];
            System.arraycopy(currentValues, 0, newValues, 0, currentValues.length);
            System.arraycopy(values, 0, newValues, currentValues.length, values.length);
            map.put(name, newValues);
        }
    }

    /**
     * Canonicalize a string of the form "a/b/../../c/./d/". The string may start and end with a
     * "/". Occurrences of "..." or other similar patterns are ignored. Also, contiguous "/" are
     * left as is.
     */
    public static String canonicalizePathString(String path) {

        StringTokenizer st = new StringTokenizer(path, "/", true);
        Stack elements = new Stack();
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            if (s.equals("..")) {
                elements.pop();
            } else if (s.equals(".")) {
                ;// Do nothing
            } else {
                elements.push(s);
            }
        }
        StringBuffer sb = new StringBuffer();
        int count = 0;
        for (Iterator i = elements.iterator(); i.hasNext(); count++) {
            String s = (String) i.next();
            if (count > 0 || path.startsWith("/"))
                sb.append("/");
            sb.append(s);
        }
        if (path.endsWith("/"))
            sb.append("/");
        return sb.toString();
    }

    /**
     * Combine a path info and a parameters map to form a relative URL.
     */
    public static String pathInfoParametersToRelativeURL(String pathInfo, Map parameters) throws IOException {
        StringBuffer redirectURL = new StringBuffer(pathInfo);
        if (parameters != null) {
            boolean first = true;
            for (Iterator i = parameters.keySet().iterator(); i.hasNext();) {
                String name = (String) i.next();
                String[] values = (String[]) parameters.get(name);
                for (int j = 0; j < values.length; j++) {
                    redirectURL.append(first ? "?" : "&");
                    redirectURL.append(URLEncoder.encode(name, NetUtils.DEFAULT_URL_ENCODING));
                    redirectURL.append("=");
                    redirectURL.append(URLEncoder.encode(values[j], NetUtils.DEFAULT_URL_ENCODING));
                    first = false;
                }
            }
        }
        return redirectURL.toString();
    }

    /**
     * Check whether a URL starts with a protocol.
     * <p/>
     * We consider that a protocol consists only of ASCII letters and must be at least two
     * characters long, to avoid confusion with Windows drive letters.
     */
    public static boolean urlHasProtocol(String urlString) {
        int colonIndex = urlString.indexOf(":");

        // No protocol is there is no colon or if there is only one character in the protocol
        if (colonIndex == -1 || colonIndex == 1)
            return false;

        // Check that there is a protocol
        boolean allChar = true;
        for (int i = 0; i < colonIndex; i++) {
            char c = urlString.charAt(i);
            if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
                allChar = false;
                break;
            }
        }
        return allChar;
    }
}
