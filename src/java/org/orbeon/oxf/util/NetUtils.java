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
import org.apache.commons.fileupload.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xml.*;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetUtils {

    private static Logger logger = LoggerFactory.createLogger(NetUtils.class);

    /**
     * @see #decodeQueryString(CharSequence, boolean)
     */
    private static final Pattern PATTERN_NO_AMP;
    /**
     * @see #decodeQueryString(CharSequence, boolean)
     */
    private static final Pattern PATTERN_AMP;

    public static final String DEFAULT_URL_ENCODING = "utf-8";

    private static final SimpleDateFormat dateHeaderFormats[] = {
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    };

    private static final TimeZone gmtZone = TimeZone.getTimeZone("GMT");
    public static FileItemFactory fileItemFactory;
    public static final int REQUEST_SCOPE = 0;
    public static final int SESSION_SCOPE = 1;
    public static final int APPLICATION_SCOPE = 2;

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
     * Return a request path info that looks like what one would expect. The path starts with a "/".
     *
     * Request path = servlet path + path info
     */
    public static String getRequestPathInfo(HttpServletRequest request) {

        // Get servlet path
        String servletPath = (String) request.getAttribute("javax.servlet.include.servlet_path");
        if (servletPath == null) {
            servletPath = request.getServletPath();
            if (servletPath == null)
                servletPath = "";
        }
        
        // Get path info
        String pathInfo = (String) request.getAttribute("javax.servlet.include.path_info");
        if (pathInfo == null) {
            pathInfo = request.getPathInfo();
            if (pathInfo == null)
                pathInfo = "";
        }

        // Concatenate servlet path and path info, avoiding a double slash
        String requestPath = servletPath.endsWith("/") && pathInfo.startsWith("/")
                ? servletPath + pathInfo.substring(1)
                : servletPath + pathInfo;

        // Add starting slash if missing
        if (!requestPath.startsWith("/"))
            requestPath = "/" + requestPath;

        return requestPath;
    }

    public static long getLastModified(URL url) throws java.io.IOException {
        return getLastModified(url.openConnection());
    }

    public static Long getLastModifiedAsLong(URL url) throws java.io.IOException {
        return new Long(getLastModified(url));
    }

    /**
     * Get the last modification date of an open URLConnection.
     *
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

    public static String readStreamAsString(Reader reader) throws IOException {
        final StringWriter writer = new StringWriter();
        copyStream(reader, writer);
        return writer.toString();
    }

    public static String getContentTypeCharset(String contentType) {
        final Map parameters = getContentTypeParameters(contentType);
        return (String) ((parameters == null) ? null : parameters.get("charset"));
    }

    public static Map getContentTypeParameters(String contentType) {
        if (contentType == null)
            return null;

        // Check whether there may be parameters
        final int semicolumnIndex = contentType.indexOf(";");
        if (semicolumnIndex == -1)
            return null;

        // Tokenize
        final StringTokenizer st = new StringTokenizer(contentType, ";");

        if (!st.hasMoreTokens())
            return null; // should not happen as there should be at least the content type    

        st.nextToken();

        // No parameters
        if (!st.hasMoreTokens())
            return null;

        // Parse parameters
        final Map parameters = new HashMap();
        while (st.hasMoreTokens()) {
            final String parameter = st.nextToken().trim();
            final int equalIndex = parameter.indexOf('=');
            if (equalIndex == -1)
                continue;
            final String name = parameter.substring(0, equalIndex).trim();
            final String value = parameter.substring(equalIndex + 1).trim();
            parameters.put(name, value);
        }
        return parameters;
    }

    public static Map getCharsetHeaderCharsets(String header) {
        if (header == null)
            return null;
        int semicolumnIndex = header.indexOf(";");
        final String charsets;
        if (semicolumnIndex == -1)
            charsets = header.trim();
        else
            charsets = header.substring(0, semicolumnIndex).trim();

        final StringTokenizer st = new StringTokenizer(charsets, ",");
        final Map charsetsMap = new HashMap();
        while (st.hasMoreTokens()) {
            charsetsMap.put(st.nextToken(), "");
        }

        return charsetsMap;
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
     * @param qry a query string of the form n1=v1&n2=v2&... to decode.  May be null.
     * @param accptAmp true if both &amp; and '&' should be accepted as delimiters and false if only & 
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
     * Combine a path info and a parameters map to form a path info with a query string.
     */
    public static String pathInfoParametersToPathInfoQueryString(String pathInfo, Map parameters) throws IOException {
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
     *
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

    /**
     * Resolve a URI against a base URI. (Be sure to pay attention to the order or parameters.)
     *
     * @param href  URI to resolve
     * @param base  URI base
     * @return      resolved URI
     */
    public static String resolveURI(String href, String base) {
        final String resolvedURIString;
        if (base != null) {
            final URI baseURI;
            try {
                baseURI = new URI(base);
            } catch (URISyntaxException e) {
                throw new OXFException(e);
            }
            resolvedURIString = baseURI.resolve(href).toString();
        } else {
            resolvedURIString = href;
        }
        return resolvedURIString;
    }

    public static String headersToString(HttpServletRequest request) {
        final StringBuffer sb = new StringBuffer();
        for (Enumeration e = request.getHeaderNames(); e.hasMoreElements();) {
            final String name = (String) e.nextElement();
            sb.append(name);
            sb.append("=");
            for (Enumeration f = request.getHeaders(name); f.hasMoreElements();) {
                final String value = (String) f.nextElement();
                sb.append(value);
                if (f.hasMoreElements())
                    sb.append(",");
            }
            if (e.hasMoreElements())
                    sb.append("|");
        }
        return sb.toString();
    }

   public static String readURIToLocalURI(String uri) throws URISyntaxException, IOException {
       final PipelineContext pipelineContext = StaticExternalContext.getStaticContext().getPipelineContext();
       final URLConnection urlConnection = new URI(uri).toURL().openConnection();
       InputStream inputStream = null;
       try {
           inputStream = urlConnection.getInputStream();
           return inputStreamToAnyURI(pipelineContext, inputStream, REQUEST_SCOPE);
       } finally {
           if (inputStream != null) inputStream.close();
       }
   }

    public static byte[] base64StringToByteArray(String base64String) {
        return Base64.decode(base64String);
    }

    /**
     * Convert a String in xs:base64Binary to an xs:anyURI.
     *
     * NOTE: The implementation creates a temporary file. The Pipeline Context is required so
     * that the file can be deleted when no longer used.
     */
    public static String base64BinaryToAnyURI(PipelineContext pipelineContext, String value, int scope) {
        // Convert Base64 to binary first
        final byte[] bytes = base64StringToByteArray(value);

        return inputStreamToAnyURI(pipelineContext, new ByteArrayInputStream(bytes), scope);
    }

    /**
     * Read an InputStream into a byte array.
     *
     * @param is    InputStream
     * @return      byte array
     */
    public static byte[] inputStreamToByteArray(InputStream is) {
        try {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            copyStream(new BufferedInputStream(is), os);
            os.close();
            return os.toByteArray();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Read a URI into a byte array.
     *
     * @param uri   URI to read
     * @return      byte array
     */
    public static byte[] uriToByteArray(String uri) {
        InputStream is = null;
        try {
            is = new URI(uri).toURL().openStream();
            return inputStreamToByteArray(is);
        } catch (Exception e) {
            throw new OXFException(e);
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (IOException e) {
                throw new OXFException(e);
            }
        }
    }

    /**
     * Convert an InputStream to an xs:anyURI.
     *
     * The implementation creates a temporary file. The PipelineContext is required so that the file can be deleted
     * when no longer used.
     */
    public static String inputStreamToAnyURI(PipelineContext pipelineContext, InputStream inputStream, int scope) {
        // Get FileItem
        final FileItem fileItem = prepareFileItem(pipelineContext, scope);
        // Write to file
        OutputStream os = null;
        try {
            os = fileItem.getOutputStream();
            copyStream(inputStream, os);
        } catch (IOException e) {
            throw new OXFException(e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        }
        // Create file if it doesn't exist (necessary when the file size is 0)
        final File storeLocation = ((DefaultFileItem) fileItem).getStoreLocation();
        try {
            storeLocation.createNewFile();
        } catch (IOException e) {
            throw new OXFException(e);
        }
        // Return a file URL
        return storeLocation.toURI().toString();
    }

    /**
     * Return a FileItem which is going to be automatically destroyed upon destruction of the request, session or
     * application.
     */
    public static FileItem prepareFileItem(PipelineContext pipelineContext, int scope) {
        // We use the commons fileupload utilities to save a file
        if (fileItemFactory == null)
            fileItemFactory = new DefaultFileItemFactory(0, SystemUtils.getTemporaryDirectory());
        final FileItem fileItem = fileItemFactory.createItem("dummy", "dummy", false, null);
        // Make sure the file is deleted appropriately
        if (scope == REQUEST_SCOPE) {
            deleteFileOnRequestEnd(pipelineContext, fileItem);
        } else if (scope == SESSION_SCOPE) {
            deleteFileOnSessionTermination(pipelineContext, fileItem);
        } else if (scope == APPLICATION_SCOPE) {
            deleteFileOnContextDestroyed(pipelineContext, fileItem);
        } else {
            throw new OXFException("Invalid context requested: " + scope);
        }
        // Return FileItem object
        return fileItem;
    }

    /**
     * Add listener to fileItem which is going to be automatically destroyed at the end of request
     *
     * @param pipelineContext PipelineContext
     * @param fileItem        FileItem
     */
    public static void deleteFileOnRequestEnd(PipelineContext pipelineContext, final FileItem fileItem) {
        // Make sure the file is deleted at the end of request
        pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter() {
            public void contextDestroyed(boolean success) {
                try {
                    // Log when we delete files
                    if (logger.isDebugEnabled()) {
                        final String temporaryFileName = ((DeferredFileOutputStream) fileItem.getOutputStream()).getFile().getAbsolutePath();
                        logger.debug("Deleting temporary file: " + temporaryFileName);
                    }
                    fileItem.delete();
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        });
    }

    /**
     * Add listener to fileItem which is going to be automatically destroyed on session destruction
     *
     * @param pipelineContext PipelineContext
     * @param fileItem        FileItem
     */
    public static void deleteFileOnSessionTermination(PipelineContext pipelineContext, final FileItem fileItem) {
        // Try to delete the file on exit and on session termination
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        final ExternalContext.Session session = externalContext.getSession(false);
        if (session != null) {
            session.addListener(new ExternalContext.Session.SessionListener() {
                public void sessionDestroyed() {
                    try {
                        if (logger.isDebugEnabled()) {
                            final String temporaryFileName = ((DeferredFileOutputStream) fileItem.getOutputStream()).getFile().getAbsolutePath();
                            logger.debug("Deleting temporary Session file: " + temporaryFileName);
                        }
                        fileItem.delete();
                    }
                    catch (IOException e) {
                        throw new OXFException(e);
                    }
                }
            });
        } else {
            logger.debug("No existing session found so cannot register temporary file deletion upon session destruction: " + fileItem.getName());
        }
    }

    /**
     * Add listener to fileItem which is going to be automatically destroyed when the servlet is destroyed
     *
     * @param pipelineContext PipelineContext
     * @param fileItem        FileItem
     */
    public static void deleteFileOnContextDestroyed(PipelineContext pipelineContext, final FileItem fileItem) {
        // Try to delete the file on exit and on session termination
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        ExternalContext.Application application = externalContext.getApplication();
        if (application != null) {
            application.addListener(new ExternalContext.Application.ApplicationListener() {
                public void servletDestroyed() {
                    try {
                        if (logger.isDebugEnabled()) {
                            final String temporaryFileName = ((DeferredFileOutputStream) fileItem.getOutputStream()).getFile().getAbsolutePath();
                            logger.debug("Deleting temporary Application file: " + temporaryFileName);
                        }
                        fileItem.delete();
                    }
                    catch (IOException e) {
                        throw new OXFException(e);
                    }
                }
            });
        } else {
            logger.debug("No application object found so cannot register temporary file deletion upon session destruction: " + fileItem.getName());
        }
    }

    /**
     * Convert a String in xs:anyURI to an xs:base64Binary.
     *
     * The URI has to be a URL. It is read entirely
     */
    public static String anyURIToBase64Binary(String value) {
        InputStream is = null;
        try {
            // Read from URL and convert to Base64
            is = new URL(value).openStream();
            final StringBuffer sb = new StringBuffer();
            XMLUtils.inputStreamToBase64Characters(is, new ContentHandlerAdapter() {
                public void characters(char ch[], int start, int length) {
                    sb.append(ch, start, length);
                }
            });
            // Return Base64 String
            return sb.toString();
        } catch (IOException e) {
            throw new OXFException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        }
    }

    public static void anyURIToOutputStream(String value, OutputStream outputStream) {
        InputStream is = null;
        try {
            is = new URL(value).openStream();
            copyStream(is, outputStream);
        } catch (IOException e) {
            throw new OXFException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        }
    }
}
