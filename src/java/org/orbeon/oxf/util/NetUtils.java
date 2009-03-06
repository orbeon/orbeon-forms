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

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.RequestGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.resources.handler.HTTPURLConnection;
import org.orbeon.oxf.servlet.ServletExternalContext;
import org.orbeon.oxf.xml.ContentHandlerAdapter;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.FastStringBuffer;

import javax.servlet.http.Cookie;
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

    public static final String DYNAMIC_RESOURCES_SESSION_KEY = "orbeon.resources.dynamic.";
    // Resources are served by the XForms server. It is not ideal to refer to XForms-related functionality from here.
    public static final String DYNAMIC_RESOURCES_PATH = "/xforms-server/dynamic/";

    private static final Pattern PATTERN_NO_AMP;
    private static final Pattern PATTERN_AMP;
//    private static final Pattern PATTERN_AMP_AMP;

    public static final String DEFAULT_URL_ENCODING = "utf-8";

    private static final SimpleDateFormat dateHeaderFormats[] = {
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    };

    private static final TimeZone gmtZone = TimeZone.getTimeZone("GMT");
    private static FileItemFactory fileItemFactory;

    public static final int REQUEST_SCOPE = 0;
    public static final int SESSION_SCOPE = 1;
    public static final int APPLICATION_SCOPE = 2;

    // Default HTTP 1.1 charset for text/* mediatype
    public static final String DEFAULT_HTTP_TEXT_READING_ENCODING = "iso-8859-1";
    // Default RFC 3023 default charset for txt/xml mediatype
    public static final String DEFAULT_TEXT_XML_READING_ENCODING = "us-ascii";
    public static final String APPLICATION_SOAP_XML = "application/soap+xml";

    static {
        // Set timezone to GMT as required for HTTP headers
        for (int i = 0; i < dateHeaderFormats.length; i++)
            dateHeaderFormats[i].setTimeZone(gmtZone);

        final String notEqNorAmpChar = "[^=&]";
        final String token = notEqNorAmpChar+ "+";
        PATTERN_NO_AMP = Pattern.compile( "(" + token + ")=(" + token + ")(?:&|(?<!&)\\z)" );
        PATTERN_AMP = Pattern.compile( "(" + token + ")=(" + token + ")(?:&amp;|&|(?<!&amp;|&)\\z)" );
//        PATTERN_AMP_AMP = Pattern.compile( "(" + token + ")=(" + token + ")(?:&amp;amp;|&|(?<!&amp;amp;|&)\\z)" );
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
     * Return a request path info that looks like what one would expect. The path starts with a "/", relative to the
     * servlet context. If the servlet was included or forwarded to, return the path by which the *current* servlet was
     * invoked, NOT the path of the calling servlet.
     *
     * Request path = servlet path + path info.
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

    /**
     * Get the last modification date of a URL.
     *
     * * @return last modified timestamp, null if le 0
     */
    public static Long getLastModifiedAsLong(URL url) throws IOException {
        final long connectionLastModified = getLastModified(url);
        // Zero and negative values often have a special meaning, make sure to normalize here
        return connectionLastModified <= 0 ? null : new Long(connectionLastModified);
    }

    /**
     * Get the last modification date of a URL.
     *
     * * @return last modified timestamp "as is"
     */
    public static long getLastModified(URL url) throws IOException {
        final URLConnection urlConnection = url.openConnection();
        if (urlConnection instanceof HttpURLConnection)
            ((HttpURLConnection) urlConnection).setRequestMethod("HEAD");
        try {
            return getLastModified(urlConnection);
        } finally {
            urlConnection.getInputStream().close();
        }
    }

    /**
     * Get the last modification date of an open URLConnection.
     *
     * This handles the (broken at some point in the Java libraries) case of the file: protocol.
     *
     * * @return last modified timestamp, null if le 0
     */
    public static Long getLastModifiedAsLong(URLConnection urlConnection) {
        final long connectionLastModified = getLastModified(urlConnection);
        // Zero and negative values often have a special meaning, make sure to normalize here
        return connectionLastModified <= 0 ? null : new Long(connectionLastModified);
    }

    /**
     * Get the last modification date of an open URLConnection.
     *
     * This handles the (broken at some point in the Java libraries) case of the file: protocol.
     *
     * @return last modified timestamp "as is"
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
     * Convert an Object array into a String array, removing non-string values.
     */
    public static String[] objectArrayToStringArray(Object[] values) {

        if (values == null)
            return null;

        final String[] result = new String[values.length];
        int size = 0;
        for (int i = 0; i < values.length; i++) {
            final Object currentValue = values[i];
            if (currentValue instanceof String) {
                result[size++] = (String) currentValue;
            }
        }
        if (size == values.length) {
            // Optimistic approach worked
            return result;
        } else {
            // Optimistic approach failed
            final String[] newResult = new String[size];
            System.arraycopy(result, 0, newResult, 0, size);
            return newResult;
        }
    }

    /**
     * Return the value of the first object in the array as a String.
     */
    public static String getStringFromObjectArray(Object[] values) {
        if (values == null || values.length == 0 || !(values[0] instanceof String))
            return null;
        else
            return (String) values[0];
    }

    /**
     * @param queryString a query string of the form n1=v1&n2=v2&... to decode.  May be null.
     * @param acceptAmp -> "&amp;" if true, "&" if false
     *
     * @return a Map of String[] indexed by name, an empty Map if the query string was null
     */
    public static Map decodeQueryString(final CharSequence queryString, final boolean acceptAmp) {

        final Map result = new TreeMap();
        if (queryString != null) {
            final Matcher matcher = acceptAmp ? PATTERN_AMP.matcher(queryString) : PATTERN_NO_AMP.matcher(queryString);
            int matcherEnd = 0;
            while (matcher.find()) {
                matcherEnd = matcher.end();
                try {
                    // Group 0 is the whole match, e.g. a=b, while group 1 is the first group
                    // denoted ( with parens ) in the expression.  Hence we start with group 1.
                    final String name = URLDecoder.decode(matcher.group(1), NetUtils.DEFAULT_URL_ENCODING);
                    final String value = URLDecoder.decode(matcher.group(2), NetUtils.DEFAULT_URL_ENCODING);

                    NetUtils.addValueToStringArrayMap(result, name, value);
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
     * Make sure a query string is made of valid pairs name/value.
     */
    public static String normalizeQuery(String query, boolean acceptAmp) {
        if (query == null)
            return null;
        return encodeQueryString(decodeQueryString(query, acceptAmp));
    }

    /**
     * Encode a query string. The input Map contains names indexing Object[].
     */
    public static String encodeQueryString(Map parameters) {
        final FastStringBuffer sb = new FastStringBuffer(100);
        boolean first = true;
        try {
            for (Iterator i = parameters.keySet().iterator(); i.hasNext();) {
                final String name = (String) i.next();
                final Object[] values = (Object[]) parameters.get(name);
                for (int j = 0; j < values.length; j++) {
                    final Object currentValue = values[j];
                    if (currentValue instanceof String) {
                        if (!first)
                            sb.append('&');

                        sb.append(URLEncoder.encode(name, NetUtils.DEFAULT_URL_ENCODING));
                        sb.append('=');
                        sb.append(URLEncoder.encode((String) currentValue, NetUtils.DEFAULT_URL_ENCODING));

                        first = false;
                    }
                }
            }
        } catch (UnsupportedEncodingException e) {
            // Should not happen as we are using a required encoding
            throw new OXFException(e);
        }
        return sb.toString();
    }

    public static void addValueToObjectArrayMap(Map map, String name, Object value) {
        final Object[] currentValue = (Object[]) map.get(name);
        if (currentValue == null) {
            map.put(name, new Object[] { value });
        } else {
            final Object[] newValue = new Object[currentValue.length + 1];
            System.arraycopy(currentValue, 0, newValue, 0, currentValue.length);
            newValue[currentValue.length] = value;
            map.put(name, newValue);
        }
    }

    public static void addValueToStringArrayMap(Map map, String name, String value) {
        final String[] currentValue = (String[]) map.get(name);
        if (currentValue == null) {
            map.put(name, new String[] { value });
        } else {
            final String[] newValue = new String[currentValue.length + 1];
            System.arraycopy(currentValue, 0, newValue, 0, currentValue.length);
            newValue[currentValue.length] = value;
            map.put(name, newValue);
        }
    }

    public static void addValuesToStringArrayMap(Map map, String name, String[] values) {
        final String[] currentValue = (String[]) map.get(name);
        if (currentValue == null) {
            map.put(name, values);
        } else {
            final String[] newValues = new String[currentValue.length + values.length];
            System.arraycopy(currentValue, 0, newValues, 0, currentValue.length);
            System.arraycopy(values, 0, newValues, currentValue.length, values.length);
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
        final FastStringBuffer redirectURL = new FastStringBuffer(pathInfo);
        if (parameters != null) {
            boolean first = true;
            for (Iterator i = parameters.keySet().iterator(); i.hasNext();) {
                final String name = (String) i.next();
                final Object[] values = (Object[]) parameters.get(name);
                for (int j = 0; j < values.length; j++) {
                    final Object currentValue = values[j];
                    if (currentValue instanceof String) {
                        redirectURL.append(first ? "?" : "&");
                        redirectURL.append(URLEncoder.encode(name, NetUtils.DEFAULT_URL_ENCODING));
                        redirectURL.append("=");
                        redirectURL.append(URLEncoder.encode((String) currentValue, NetUtils.DEFAULT_URL_ENCODING));
                        first = false;
                    }
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
            resolvedURIString = baseURI.resolve(href).normalize().toString();// normalize to remove "..", etc.
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
     * Convert a URI to a FileItem.
     *
     * The implementation creates a temporary file. The PipelineContext is required so that the file can be deleted
     * when no longer used.
     */
    public static FileItem anyURIToFileItem(PipelineContext pipelineContext, String uri, int scope) {
        InputStream inputStream = null;
        try {
            inputStream = new URI(uri).toURL().openStream();

            // Get FileItem
            return prepareFileItemFromInputStream(pipelineContext, inputStream, scope);

        } catch (Exception e) {
            throw new OXFException(e);
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
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
        final FileItem fileItem = prepareFileItemFromInputStream(pipelineContext, inputStream, scope);

        // Return a file URL
        final File storeLocation = ((DiskFileItem) fileItem).getStoreLocation();
        return storeLocation.toURI().toString();
    }

    private static FileItem prepareFileItemFromInputStream(PipelineContext pipelineContext, InputStream inputStream, int scope) {
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
        final File storeLocation = ((DiskFileItem) fileItem).getStoreLocation();
        try {
            storeLocation.createNewFile();
        } catch (IOException e) {
            throw new OXFException(e);
        }

        return fileItem;
    }

    /**
     * Return a FileItem which is going to be automatically destroyed upon destruction of the request, session or
     * application.
     */
    public static FileItem prepareFileItem(PipelineContext pipelineContext, int scope) {
        // We use the commons fileupload utilities to save a file
        if (fileItemFactory == null)
            fileItemFactory = new DiskFileItemFactory(0, SystemUtils.getTemporaryDirectory());
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
                deleteFileItem(fileItem, REQUEST_SCOPE);
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
                    deleteFileItem(fileItem, SESSION_SCOPE);
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
                    deleteFileItem(fileItem, APPLICATION_SCOPE);
                }
            });
        } else {
            logger.debug("No application object found so cannot register temporary file deletion upon session destruction: " + fileItem.getName());
        }
    }

    private static void deleteFileItem(FileItem fileItem, int scope) {
        if (logger.isDebugEnabled() && fileItem instanceof DiskFileItem) {
            final File storeLocation = ((DiskFileItem) fileItem).getStoreLocation();
            if (storeLocation != null) {
                final String temporaryFileName = storeLocation.getAbsolutePath();
                final String scopeString = (scope == REQUEST_SCOPE) ? "request" : (scope == SESSION_SCOPE) ? "session" : "application";
                logger.debug("Deleting temporary " + scopeString + "-scoped file: " + temporaryFileName);
            }
        }
        fileItem.delete();
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

    /**
     * Return the charset associated with a text/* Content-Type header. If a charset is present, return it. Otherwise,
     * guess depending on whether the mediatype is text/xml or not.
     *
     * @param contentType   Content-Type header value
     * @return              charset
     */
    public static String getTextCharsetFromContentType(String contentType) {
        final String charset;
        final String connectionCharset = getContentTypeCharset(contentType);
        if (connectionCharset != null) {
            charset = connectionCharset;
        } else {

            // RFC 3023: "Conformant with [RFC2046], if a text/xml entity is
            // received with the charset parameter omitted, MIME processors and
            // XML processors MUST use the default charset value of
            // "us-ascii"[ASCII]. In cases where the XML MIME entity is
            // transmitted via HTTP, the default charset value is still
            // "us-ascii". (Note: There is an inconsistency between this
            // specification and HTTP/1.1, which uses ISO-8859-1[ISO8859] as the
            // default for a historical reason. Since XML is a new format, a new
            // default should be chosen for better I18N. US-ASCII was chosen,
            // since it is the intersection of UTF-8 and ISO-8859-1 and since it
            // is already used by MIME.)"

            if (XMLUtils.isXMLMediatype(contentType))
                charset = DEFAULT_TEXT_XML_READING_ENCODING;
            else
                charset = DEFAULT_HTTP_TEXT_READING_ENCODING;
        }
        return charset;
    }

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

        final String lowerCaseUserAgent = userAgent.toLowerCase();
        return lowerCaseUserAgent.indexOf("msie") != -1 && lowerCaseUserAgent.indexOf("opera") == -1;
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

    /**
     * Create an absolute URL from an action string and a search string.
     *
     * @param action            absolute URL or absolute path
     * @param queryString       optional query string to append to the action URL
     * @param externalContext   current ExternalContext
     * @return                  an absolute URL
     */
    public static URL createAbsoluteURL(String action, String queryString, ExternalContext externalContext) {
        URL resultURL;
        try {
            final String actionString;
            {
                final StringBuffer updatedActionStringBuffer = new StringBuffer(action);
                if (queryString != null && queryString.length() > 0) {
                    if (action.indexOf('?') == -1)
                        updatedActionStringBuffer.append('?');
                    else
                        updatedActionStringBuffer.append('&');
                    updatedActionStringBuffer.append(queryString);
                }
                actionString = updatedActionStringBuffer.toString();
            }

            if (actionString.startsWith("/")) {
                // Case of path absolute
                final String requestURL = externalContext.getRequest().getRequestURL();
                resultURL = URLFactory.createURL(requestURL, actionString);
            } else if (urlHasProtocol(actionString)) {
                // Case of absolute URL
                resultURL = URLFactory.createURL(actionString);
            } else {
                throw new OXFException("Invalid URL: " + actionString);
            }
        } catch (MalformedURLException e) {
            throw new OXFException("Invalid URL: " + action, e);
        }
        return resultURL;
    }

    /**
     * Remove the first path element of a path. Return null if there is only one path element
     *
     * E.g. /foo/bar => /bar?a=b
     *
     * @param path  path to modify
     * @return      modified path or null
     */
    public static String removeFirstPathElement(String path) {
        final int secondSlashIndex = path.indexOf('/', 1);
        if (secondSlashIndex == -1)
            return null;

        return path.substring(secondSlashIndex);
    }

    /**
     * Return the first path element of a path. If there is only one path element, return the entire path.
     *
     * E.g. /foo/bar => /foo
     *
     * @param path  path to analyze
     * @return      first path element
     */
    public static String getFirstPathElement(String path) {
        final int secondSlashIndex = path.indexOf('/', 1);
        if (secondSlashIndex == -1)
            return path;

        return path.substring(0, secondSlashIndex);
    }

    /**
     * Perform a connection to the given URL with the given parameters.
     *
     * This handles:
     *
     * o PUTting or POSTing a body
     * o handling username and password
     * o setting HTTP heades
     * o forwarding session cookies
     * o forwarding specified HTTP headers
     * o managing SOAP POST and GET a la XForms 1.1 (should this be here?)
     */
    public static ConnectionResult openConnection(ExternalContext externalContext, IndentedLogger indentedLogger,
                                                  String httpMethod, final URL connectionURL, String username, String password, String contentType,
                                                  byte[] messageBody, Map headerNameValues, String headersToForward) {

        // Get  the headers to forward if any
        final Map headersMap = (externalContext.getRequest() != null) ?
                getHeadersMap(externalContext, indentedLogger, username, headerNameValues, headersToForward) : headerNameValues;
        // Open the connection
        return openConnection(indentedLogger, httpMethod, connectionURL, username, password, contentType, messageBody, headersMap);
    }

    /**
     * Get header names and values to send given:
     *
     * o the incoming request
     * o a list of headers names and values to set
     * o authentication information including username
     * o a list of headers to forward
     *
     * @param headerNameValues  LinkedHashMap<String headerName, String[] headerValues>
     *
     * @return LinkedHashMap<String headerName, String[] headerValues>
     */
    public static Map getHeadersMap(ExternalContext externalContext, IndentedLogger indentedLogger, String username,
                                    Map headerNameValues, String headersToForward) {
        // Resulting header names and values to set
        final LinkedHashMap /* <String headerName, String[] headerValues> */ headersMap = new LinkedHashMap();

        // Get header forwarding information
        final Map headersToForwardMap = getHeadersToForward(headersToForward);

        // Set headers if provided
        if (headerNameValues != null && headerNameValues.size() > 0) {
            for (Iterator i = headerNameValues.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String currentHeaderName = (String) currentEntry.getKey();
                final String[] currentHeaderValues = (String[]) currentEntry.getValue();
                // Set header
                headersMap.put(currentHeaderName, currentHeaderValues);
                // Remove from list of headers to forward below
                if (headersToForwardMap != null)
                    headersToForwardMap.remove(currentHeaderName.toLowerCase());
            }
        }

        // Forward cookies for session handling
        boolean sessionCookieSet = false;
        if (username == null) {

            // START NEW ALGORITHM

            // 1. If there is an incoming JSESSIONID cookie, use it. The reason is that there is not necessarily an
            // obvious mapping between "session id" and JSESSIONID cookie value. With Tomcat, this works, but with e.g.
            // Websphere, you get session id="foobar" and JSESSIONID=0000foobar:-1. So we must first try to get the
            // incoming JSESSIONID. To do this, we get the cookie, then serialize it as a header.

            // TODO: ExternalContext must provide direct access to cookies
            final Object nativeRequest = externalContext.getNativeRequest();
            if (nativeRequest instanceof HttpServletRequest) {
                final HttpServletRequest httpServletRequest = (HttpServletRequest) nativeRequest;
                final Cookie[] cookies = httpServletRequest.getCookies();

                StringBuffer sb = new StringBuffer();

                if (cookies != null) {
                    for (int i = 0; i < cookies.length; i++) {
                        final Cookie cookie = cookies[i];

                        // This is the standard JSESSIONID cookie
                        final boolean isJsessionId = cookie.getName().equals("JSESSIONID");
                        // Remember if we've seen JSESSIONID
                        sessionCookieSet |= isJsessionId;

                        // Forward JSESSIONID and JSESSIONIDSSO for JBoss
                        if (isJsessionId || cookie.getName().equals("JSESSIONIDSSO")) {
                            // Multiple cookies in the header, separated with ";"
                            if (sb.length() > 0)
                                sb.append("; ");

                            sb.append(cookie.getName());
                            sb.append('=');
                            sb.append(cookie.getValue());
                        }
                    }

                    if (sb.length() > 0) {
                        // One or more cookies were set
                        final String cookieString = sb.toString();
                        indentedLogger.logDebug("connection", "forwarding cookies", new String[] { "cookie", cookieString });
                        NetUtils.addValueToStringArrayMap(headersMap, "Cookie", cookieString );

//                            CookieTools.getCookieHeaderValue(cookie, sb);
                    }
                }
            }

            // 2. If there is no incoming JSESSIONID cookie, try to make our own cookie. This may fail with e.g.
            // Websphere.
            if (!sessionCookieSet) {
                final ExternalContext.Session session = externalContext.getSession(false);

                if (session != null) {

                    // This will work with Tomcat, but may not work with other app servers
                    NetUtils.addValueToStringArrayMap(headersMap, "Cookie", "JSESSIONID=" + session.getId());

                    if (indentedLogger.isDebugEnabled()) {

                        String incomingSessionHeader = null;
                        final String[] cookieHeaders = (String[]) externalContext.getRequest(   ).getHeaderValuesMap().get("cookie");
                        if (cookieHeaders != null) {
                            for (int i = 0; i < cookieHeaders.length; i++) {
                                final String cookie = cookieHeaders[i];
                                if (cookie.indexOf("JSESSIONID") != -1) {
                                    incomingSessionHeader = cookie;
                                }
                            }
                        }

                        String incomingSessionCookie = null;
                        if (externalContext.getNativeRequest() instanceof HttpServletRequest) {
                            final Cookie[] cookies = ((HttpServletRequest) externalContext.getNativeRequest()).getCookies();
                            if (cookies != null) {
                                for (int i = 0; i < cookies.length; i++) {
                                    final Cookie cookie = cookies[i];
                                    if (cookie.getName().equals("JSESSIONID")) {
                                        incomingSessionCookie = cookie.getValue();
                                    }
                                }
                            }
                        }

                        indentedLogger.logDebug("connection", "setting cookie",
                            new String[] {
                                    "new session", Boolean.toString(session.isNew()),
                                    "session id", session.getId(),
                                    "requested session id", externalContext.getRequest().getRequestedSessionId(),
                                    "incoming JSESSIONID cookie", incomingSessionCookie,
                                    "incoming JSESSIONID header", incomingSessionHeader
                            });
                    }
                }
            }

            // END NEW ALGORITHM
        }

        // Forward headers if needed
        // NOTE: Forwarding the "Cookie" header may yield unpredictable results because of the above work done w/ JSESSIONID
        if (headersToForwardMap != null) {

            final Map requestHeaderValuesMap = externalContext.getRequest().getHeaderValuesMap();

            for (Iterator i = headersToForwardMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String currentHeaderName = (String) currentEntry.getValue();
                final String currentHeaderNameLowercase = (String) currentEntry.getKey();

                // Get incoming header value (Map contains values in lowercase!)
                final String[] currentIncomingHeaderValues = (String[]) requestHeaderValuesMap.get(currentHeaderNameLowercase);
                // Forward header if present
                if (currentIncomingHeaderValues != null) {
                    final boolean isAuthorizationHeader = currentHeaderNameLowercase.equals("authorization");
                    if (!isAuthorizationHeader || isAuthorizationHeader && username == null) {
                        // Only forward Authorization header if there is no username provided
                        indentedLogger.logDebug("connection", "forwarding header",
                            new String[] { "name", currentHeaderName, "value", currentIncomingHeaderValues.toString() });
                        NetUtils.addValuesToStringArrayMap(headersMap, currentHeaderName, currentIncomingHeaderValues);
                    } else {
                        // Just log this information
                        indentedLogger.logDebug("connection",
                                "not forwarding Authorization header because username is present");
                    }
                }
            }
        }

        return headersMap;
    }

    /**
     *
     * @param indentedLogger
     * @param httpMethod
     * @param connectionURL
     * @param username
     * @param password
     * @param contentType
     * @param messageBody
     * @param headersMap        LinkedHashMap<String headerName, String[] headerValues>
     * @return
     */
    public static ConnectionResult openConnection(IndentedLogger indentedLogger,
                                                   String httpMethod, final URL connectionURL, String username, String password,
                                                   String contentType, byte[] messageBody, Map headersMap) {

        // Perform connection
        final String scheme = connectionURL.getProtocol();
        if (scheme.equals("http") || scheme.equals("https") || (httpMethod.equals("GET") && (scheme.equals("file") || scheme.equals("oxf")))) {
            // http MUST be supported
            // https SHOULD be supported
            // file SHOULD be supported
            try {
                if (indentedLogger.isDebugEnabled()) {
                    final URI connectionURI;
                    try {
                        String userInfo = connectionURL.getUserInfo();
                        if (userInfo != null) {
                            final int colonIndex = userInfo.indexOf(':');
                            if (colonIndex != -1)
                                userInfo = userInfo.substring(0, colonIndex + 1) + "xxxxxxxx";// hide password in logs
                        }
                        connectionURI = new URI(connectionURL.getProtocol(), userInfo, connectionURL.getHost(),
                                connectionURL.getPort(), connectionURL.getPath(), connectionURL.getQuery(), connectionURL.getRef());
                    } catch (URISyntaxException e) {
                        throw new OXFException(e);
                    }
                    indentedLogger.logDebug("connection", "opening URL connection",
                        new String[] { "URL", connectionURI.toString() });
                }

                final URLConnection urlConnection = connectionURL.openConnection();
                final HTTPURLConnection httpURLConnection = (urlConnection instanceof HTTPURLConnection) ? (HTTPURLConnection) urlConnection : null;

                // Whether a message body must be sent
                final boolean hasRequestBody = httpMethod.equals("POST") || httpMethod.equals("PUT");
                // Case of empty body
                if (messageBody == null)
                    messageBody = new byte[0];

                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(hasRequestBody);

                if (httpURLConnection != null) {
                    httpURLConnection.setRequestMethod(httpMethod);
                    if (username != null) {
                        httpURLConnection.setUsername(username);
                        if (password != null)
                           httpURLConnection.setPassword(password);
                    }
                }
                final String contentTypeMediaType = getContentTypeMediaType(contentType);
                if (hasRequestBody) {
                    if (httpMethod.equals("POST") && APPLICATION_SOAP_XML.equals(contentTypeMediaType)) {
                        // SOAP POST

                        indentedLogger.logDebug("connection", "found SOAP POST");

                        final Map parameters = getContentTypeParameters(contentType);
                        final FastStringBuffer sb = new FastStringBuffer("text/xml");

                        // Extract charset parameter if present
                        // TODO: We have the body as bytes already, using the xforms:submission/@encoding attribute, so this is not right.
                        if (parameters != null) {
                            final String charsetParameter = (String) parameters.get("charset");
                            if (charsetParameter != null) {
                                // Append charset parameter
                                sb.append("; ");
                                sb.append(charsetParameter);
                            }
                        }

                        // Set new content type
                        urlConnection.setRequestProperty("Content-Type", sb.toString());

                        // Extract action parameter if present
                        if (parameters != null) {
                            final String actionParameter = (String) parameters.get("action");
                            if (actionParameter != null) {
                                // Set SOAPAction header
                                urlConnection.setRequestProperty("SOAPAction", actionParameter);
                                indentedLogger.logDebug("connection", "setting header",
                                    new String[] { "SOAPAction", actionParameter });
                            }
                        }
                    } else {
                        urlConnection.setRequestProperty("Content-Type", (contentType != null) ? contentType : "application/xml");
                    }
                } else {
                    if (httpMethod.equals("GET") && APPLICATION_SOAP_XML.equals(contentTypeMediaType)) {
                        // SOAP GET
                        indentedLogger.logDebug("connection", "found SOAP GET");

                        final Map parameters = getContentTypeParameters(contentType);
                        final FastStringBuffer sb = new FastStringBuffer(APPLICATION_SOAP_XML);

                        // Extract charset parameter if present
                        if (parameters != null) {
                            final String charsetParameter = (String) parameters.get("charset");
                            if (charsetParameter != null) {
                                // Append charset parameter
                                sb.append("; ");
                                sb.append(charsetParameter);
                            }
                        }

                        // Set Accept header with optional charset
                        urlConnection.setRequestProperty("Accept", sb.toString());
                    }
                }

                // Set headers if provided
                if (headersMap != null && headersMap.size() > 0) {
                    for (Iterator i = headersMap.entrySet().iterator(); i.hasNext();) {
                        final Map.Entry currentEntry = (Map.Entry) i.next();
                        final String currentHeaderName = (String) currentEntry.getKey();
                        final String[] currentHeaderValues = (String[]) currentEntry.getValue();
                        if (currentHeaderValues != null) {
                            // Add all header values as "request properties"
                            for (int j = 0; j < currentHeaderValues.length; j++) {
                                final String currentHeaderValue = currentHeaderValues[j];
                                urlConnection.addRequestProperty(currentHeaderName, currentHeaderValue);
                            }
                        }
                    }
                }

                // Write request body if needed
                if (hasRequestBody) {
                    // Log message mody for debugging purposes
                    if (indentedLogger.isDebugEnabled())
                        logRequestBody(indentedLogger, contentType, messageBody);
                    // Set request body on connection
                    
                    httpURLConnection.setRequestBody(messageBody);
                }

                urlConnection.connect();

                // Create result
                final ConnectionResult connectionResult = new ConnectionResult(connectionURL.toExternalForm()) {
                    public void close() {
                        if (getResponseInputStream() != null) {
                            try {
                                getResponseInputStream().close();
                            } catch (IOException e) {
                                throw new OXFException("Exception while closing input stream for action: " + connectionURL);
                            }
                        }

                        if (httpURLConnection != null)
                            httpURLConnection.disconnect();
                    }
                };

                // Get response information that needs to be forwarded
                connectionResult.statusCode = (httpURLConnection != null) ? httpURLConnection.getResponseCode() : 200;
                final String responseContentType = urlConnection.getContentType();
                connectionResult.setResponseContentType(responseContentType != null ? responseContentType : "application/xml");
                connectionResult.responseHeaders = urlConnection.getHeaderFields();
                connectionResult.setLastModified(NetUtils.getLastModifiedAsLong(urlConnection));
                connectionResult.setResponseInputStream(urlConnection.getInputStream());

                return connectionResult;

            } catch (IOException e) {
                throw new ValidationException(e, new LocationData(connectionURL.toExternalForm(), -1, -1));
            }
        } else if (!httpMethod.equals("GET") && (scheme.equals("file") || scheme.equals("oxf"))) {
            // TODO: implement writing to file: and oxf:
            // SHOULD be supported (should probably support oxf: as well)
            throw new OXFException("submission URL scheme not yet implemented: " + scheme);
        } else if (scheme.equals("mailto")) {
            // TODO: implement sending mail
            // MAY be supported
            throw new OXFException("submission URL scheme not yet implemented: " + scheme);
        } else {
            throw new OXFException("submission URL scheme not supported: " + scheme);
        }
    }

    /**
     * Get user-specified list of headers to forward.
     *
     * @param headersToForward  space-separated list of headers to forward
     * @return  Map<String, String> lowercase header name to user-specified header name or null if null String passed
     */
    public static Map getHeadersToForward(String headersToForward) {
        if (headersToForward == null)
            return null;

        final Map result = new HashMap();
        for (final StringTokenizer st = new StringTokenizer(headersToForward, ", "); st.hasMoreTokens();) {
            final String currentHeaderName = st.nextToken().trim();
            final String currentHeaderNameLowercase = currentHeaderName.toLowerCase();
            result.put(currentHeaderNameLowercase, currentHeaderName);
        }
        return result;
    }

    public static void logRequestBody(IndentedLogger indentedLogger, String mediatype, byte[] messageBody) throws UnsupportedEncodingException {
        if (XMLUtils.isXMLMediatype(mediatype) || XMLUtils.isTextContentType(mediatype) || (mediatype != null && mediatype.equals("application/x-www-form-urlencoded"))) {
            indentedLogger.logDebug("submission", "setting request body",
                new String[] { "mediatype", mediatype, "body", new String(messageBody, "UTF-8")});
        } else {
            indentedLogger.logDebug("submission", "setting binary request body", new String[] { "mediatype", mediatype });
        }
    }

    /**
     * Transform an URI accessible from the server into a URI accessible from the client. The mapping expires with the
     * session.
     *
     * @param pipelineContext   PipelineContext to obtain session
     * @param uri               server URI to transform
     * @param contentType       type of the content referred to by the URI, or null if unknown
     * @param lastModified      last modification timestamp
     * @return                  client URI
     */
    public static String proxyURI(PipelineContext pipelineContext, String uri, String filename, String contentType, long lastModified) {

        // Create a digest, so that for a given URI we always get the same key
        final String digest = SecureUtils.digestString(uri, "MD5", "hex");

        // Get session
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        final ExternalContext.Session session = externalContext.getSession(true);// NOTE: We force session creation here. Should we? What's the alternative?

        if (session != null) {
            // Store mapping into session
            session.getAttributesMap(ExternalContext.Session.APPLICATION_SCOPE).put(DYNAMIC_RESOURCES_SESSION_KEY + digest,
                    new DynamicResource(uri, filename, contentType, -1, lastModified));
        }

        // Rewrite new URI to absolute path without the context
        return externalContext.getResponse().rewriteResourceURL(DYNAMIC_RESOURCES_PATH + digest,
                ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT);
    }

    /**
     * Utility method to decode a multipart/fomr-data stream and return a Map of parameters of type Object[], each of
     * which can be a String or FileData.
     */
    public static Map getParameterMapMultipart(PipelineContext pipelineContext, final ExternalContext.Request request, String headerEncoding) {

        final Map uploadParameterMap = new HashMap();
        try {
            // Setup commons upload

            // Read properties
            // NOTE: We use properties scoped in the Request generator for historical reasons. Not too good.
            int maxSize = RequestGenerator.getMaxSizeProperty();
            int maxMemorySize = RequestGenerator.getMaxMemorySizeProperty();

            final DiskFileItemFactory diskFileItemFactory = new DiskFileItemFactory(maxMemorySize, SystemUtils.getTemporaryDirectory());

            final ServletFileUpload upload = new ServletFileUpload(diskFileItemFactory) {
                protected FileItem createItem(Map headers, boolean isFormField) throws FileUploadException {
                    if (isFormField) {
                        // Handle externalized values
                        final String externalizeFormValuesPrefix = org.orbeon.oxf.properties.Properties.instance().getPropertySet().getString(ServletExternalContext.EXTERNALIZE_FORM_VALUES_PREFIX_PROPERTY);
                        final String fieldName = getFieldName(headers);
                        if (externalizeFormValuesPrefix != null && fieldName.startsWith(externalizeFormValuesPrefix)) {
                            // In this case, we do as if the value content is an uploaded file so that it can be externalized
                            return super.createItem(headers, false);
                        } else {
                            // Just create the FileItem using the default way
                            return super.createItem(headers, isFormField);
                        }
                    } else {
                        // Just create the FileItem using the default way
                        return super.createItem(headers, isFormField);
                    }
                }
            };
            upload.setHeaderEncoding(headerEncoding);
            upload.setSizeMax(maxSize);

            // Add a listener to destroy file items when the pipeline context is destroyed
            pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter() {
                public void contextDestroyed(boolean success) {
                    if (uploadParameterMap != null) {
                        for (Iterator i = uploadParameterMap.keySet().iterator(); i.hasNext();) {
                            final String name = (String) i.next();
                            final Object values[] = (Object[]) uploadParameterMap.get(name);
                            for (int j = 0; j < values.length; j++) {
                                final Object currentValue = values[j];
                                if (currentValue instanceof FileItem) {
                                    final FileItem fileItem = (FileItem) currentValue;
                                    fileItem.delete();
                                }
                            }
                        }
                    }
                }
            });

            // Wrap and implement just the required methods for the upload code
            final InputStream inputStream;
            try {
                inputStream = request.getInputStream();
            } catch (IOException e) {
                throw new OXFException(e);
            }

            final RequestContext requestContext = new RequestContext() {

                public int getContentLength() {
                    return request.getContentLength();
                }

                public InputStream getInputStream() {
                    // NOTE: The upload code does not actually check that it doesn't read more than the content-length
                    // sent by the client! Maybe here would be a good place to put an interceptor and make sure we
                    // don't read too much.
                    return new InputStream() {
                        public int read() throws IOException {
                            return inputStream.read();
                        }
                    };
                }

                public String getContentType() {
                    return request.getContentType();
                }

                public String getCharacterEncoding() {
                    return request.getCharacterEncoding();
                }
            };

            // Parse the request and add file information
            try {
                for (Iterator i = upload.parseRequest(requestContext).iterator(); i.hasNext();) {
                    final FileItem fileItem = (FileItem) i.next();
                    // Add value to existing values if any
                    if (fileItem.isFormField()) {
                        // Simple form field
                        addValueToObjectArrayMap(uploadParameterMap, fileItem.getFieldName(), fileItem.getString());// FIXME: FORM_ENCODING getString() should use an encoding
                    } else {
                        // File
                        addValueToObjectArrayMap(uploadParameterMap, fileItem.getFieldName(), fileItem);
                    }
                }
            } catch (FileUploadBase.SizeLimitExceededException e) {
                // Should we do something smart so we can use the Presentation
                // Server error page anyway? Right now, this is going to fail
                // miserably with an error.
                throw e;
            } finally {
                // Close the input stream; if we don't nobody does, and if this stream is
                // associated with a temporary file, that file may resist deletion
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        throw new OXFException(e);
                    }
                }
            }

            return uploadParameterMap;
        } catch (FileUploadException e) {
            throw new OXFException(e);
        }
    }

    public static class DynamicResource {
        private String uri;
        private String filename;
        private String contentType;
        private long size;
        private long lastModified;

        public DynamicResource(String uri, String filename, String contentType, long size, long lastModified) {
            this.uri = uri;
            this.filename = filename;
            this.contentType = contentType;
            this.size = size;
            this.lastModified = lastModified;
        }

        public String getURI() {
            return uri;
        }

        public String getFilename() {
            return filename;
        }

        public String getContentType() {
            return contentType;
        }

        public long getSize() {
            return size;
        }

        public long getLastModified() {
            return lastModified;
        }
    }
}
