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
package org.orbeon.oxf.util;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.orbeon.datatypes.LocationData;
import org.orbeon.io.CharsetNames;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.externalcontext.WebAppListener;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.RequestGenerator;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.dom.IOSupport;
import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


// TODO: refactor, use functions in `FileItemSupport`
public class NetUtils {

    private static final Pattern PATTERN_NO_AMP;

    private static final int COPY_BUFFER_SIZE = 8192;
    private static final String STANDARD_PARAMETER_ENCODING = CharsetNames.Utf8();

    private static FileItemFactory fileItemFactory;

    public static final int REQUEST_SCOPE = 0;
    public static final int SESSION_SCOPE = 1;
    public static final int APPLICATION_SCOPE = 2;

    static {
        final String token = "[^=&]";
        PATTERN_NO_AMP = Pattern.compile( "(" + token + "+)=(" + token + "*)(?:&|(?<!&)\\z)" );
    }

    public static File getTemporaryDirectory() {
        return new File(System.getProperty("java.io.tmpdir")).getAbsoluteFile();
    }

    /**
     * Return true if the document was modified since the given date, based on the If-Modified-Since
     * header. If the request method was not "GET", or if no valid lastModified value was provided,
     * consider the document modified.
     */
    public static boolean checkIfModifiedSince(ExternalContext.Request request, long lastModified, Logger logger) {
        // Do the check only for the GET method
        if (!"GET".equals(request.getMethod().entryName().toUpperCase()) || lastModified <= 0)
            return true;
        // Check dates
        final String ifModifiedHeader = StringConversions.getFirstValueFromStringArray(request.getHeaderValuesMap().get("if-modified-since"));
        if (logger.isDebugEnabled())
            logger.debug("Found If-Modified-Since header");
        if (ifModifiedHeader != null) {
            try {
                long dateTime = DateUtils.parseRFC1123(ifModifiedHeader);
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
     *
     * @param request   servlet HTTP request
     * @return          path
     */
    public static String getRequestPathInfo(HttpServletRequest request) {

        // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the included
        // servlet via the getAttribute method on the request object and their values must be equal to the request URI,
        // context path, servlet path, path info, and query string of the included servlet, respectively."
        // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
        // first servlet in the chain!

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
     * Return the last modification date of the given absolute URL if it is "fast" to do so, i.e. if it is an "oxf:" or
     * a "file:" protocol.
     *
     * @param absoluteURL   absolute URL to check
     * @return              last modification date if "fast" or 0 if not fast or if an error occurred
     */
    public static long getLastModifiedIfFast(String absoluteURL) {
        final long lastModified;
        if (absoluteURL.startsWith("oxf:") || absoluteURL.startsWith("file:")) {
            try {
                lastModified = getLastModified(URLFactory.createURL(absoluteURL));
            } catch (IOException e) {
                throw new OXFException(e);
            }
        } else {
            // Value of `0` for `lastModified` will cause `XFormsAssetServer` to set `Last-Modified` and `Expires` properly to "now".
            lastModified = 0;
        }
        return lastModified;
    }

    /**
     * Get the last modification date of a URL.
     *
     * @return last modified timestamp, null if le 0
     */
    public static Long getLastModifiedAsLong(URL url) throws IOException {
        final long connectionLastModified = getLastModified(url);
        // Zero and negative values often have a special meaning, make sure to normalize here
        return connectionLastModified <= 0 ? null : connectionLastModified;
    }

    /**
     * Get the last modification date of a URL.
     *
     * @return last modified timestamp "as is"
     */
    public static long getLastModified(URL url) throws IOException {
        if ("file".equals(url.getProtocol())) {
            // Optimize file: access. Also, this prevents throwing an exception if the file doesn't exist as we try to close the stream below.
            return new File(URLDecoder.decode(url.getFile(), STANDARD_PARAMETER_ENCODING)).lastModified();
        } else {
            // Use URLConnection
            final URLConnection urlConnection = url.openConnection();
            if (urlConnection instanceof HttpURLConnection)
                ((HttpURLConnection) urlConnection).setRequestMethod("HEAD");
            try {
                return getLastModified(urlConnection);
            } finally {
                final InputStream is = urlConnection.getInputStream();
                if (is != null)
                    is.close();
            }
        }
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
                lastModified = new File(URLDecoder.decode(urlConnection.getURL().getFile(), STANDARD_PARAMETER_ENCODING)).lastModified();
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

    // 10 Java callers
    public static void copyStream(InputStream is, OutputStream os) throws IOException {
        int count;
        final byte[] buffer = new byte[COPY_BUFFER_SIZE];
        while ((count = is.read(buffer)) > 0)
            os.write(buffer, 0, count);
    }

    // 1 Java caller
    public static void copyStream(Reader reader, Writer writer) throws IOException {
        int count;
        final char[] buffer = new char[COPY_BUFFER_SIZE / 2];
        while ((count = reader.read(buffer)) > 0)
            writer.write(buffer, 0, count);
    }

    /**
     * @param queryString a query string of the form n1=v1&n2=v2&... to decode.  May be null.
     *
     * @return a Map of String[] indexed by name, an empty Map if the query string was null
     */
    // TODO: Move to PathUtils.
    public static Map<String, String[]> decodeQueryString(final CharSequence queryString) {

        final Map<String, String[]> result = new LinkedHashMap<>();
        if (queryString != null) {
            final Matcher matcher = PATTERN_NO_AMP.matcher(queryString);
            int matcherEnd = 0;
            while (matcher.find()) {
                matcherEnd = matcher.end();
                try {
                    final String name = URLDecoder.decode(matcher.group(1), NetUtils.STANDARD_PARAMETER_ENCODING);
                    final String value = URLDecoder.decode(matcher.group(2), NetUtils.STANDARD_PARAMETER_ENCODING);

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

    private static final Pattern PATTERN_AMP;

    static {
        final String token = "[^=&]+";
        PATTERN_AMP = Pattern.compile( "(" + token + ")=(" + token + ")?(?:&amp;|&|(?<!&amp;|&)\\z)" );
    }

    // This is a modified copy of decodeQueryString() above. Not sure why we need 2 versions! Try to avoid duplication!
    // TODO: Move to PathUtils.
    public static Map<String, String[]> decodeQueryStringPortlet(final CharSequence queryString) {

        final Map<String, String[]> result = new LinkedHashMap<>();
        if (queryString != null) {
            final Matcher matcher = PATTERN_AMP.matcher(queryString);
            int matcherEnd = 0;
            while (matcher.find()) {
                matcherEnd = matcher.end();
                try {
                    String name = URLDecoder.decode(matcher.group(1), STANDARD_PARAMETER_ENCODING);
                    String group2 = matcher.group(2);

                    final String value = group2 != null ? URLDecoder.decode(group2, STANDARD_PARAMETER_ENCODING) : "";

                    // Handle the case where the source contains &amp;amp; because of double escaping which does occur in
                    // full Ajax updates!
                    if (name.startsWith("amp;"))
                        name = name.substring("amp;".length());

                    // NOTE: Replace spaces with '+'. This is an artifact of the fact that URLEncoder/URLDecoder
                    // are not fully reversible.
                    StringConversions.addValueToStringArrayMap(result, name, value.replace(' ', '+'));
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
     * Encode a query string. The input Map contains names indexing Object[].
     */
    // TODO: Move to PathUtils.
    public static String encodeQueryString(Map<String, Object[]> parameters) {
        final StringBuilder sb = new StringBuilder(100);
        boolean first = true;
        try {
            for (final Map.Entry<String, Object[]> entry : parameters.entrySet()) {
                for (final Object currentValue : entry.getValue()) {
                    if (currentValue instanceof String) {
                        if (!first)
                            sb.append('&');

                        sb.append(URLEncoder.encode(entry.getKey(), NetUtils.STANDARD_PARAMETER_ENCODING));
                        sb.append('=');
                        sb.append(URLEncoder.encode((String) currentValue, NetUtils.STANDARD_PARAMETER_ENCODING));

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

    // TODO: Move to PathUtils.
    public static String encodeQueryString2(Map<String, String[]> parameters) {
        final StringBuilder sb = new StringBuilder(100);
        boolean first = true;
        try {
            for (final Map.Entry<String, String[]> entry : parameters.entrySet()) {
                for (final Object currentValue : entry.getValue()) {
                    if (!first)
                        sb.append('&');

                    sb.append(URLEncoder.encode(entry.getKey(), NetUtils.STANDARD_PARAMETER_ENCODING));
                    sb.append('=');
                    sb.append(URLEncoder.encode((String) currentValue, NetUtils.STANDARD_PARAMETER_ENCODING));

                    first = false;
                }
            }
        } catch (UnsupportedEncodingException e) {
            // Should not happen as we are using a required encoding
            throw new OXFException(e);
        }
        return sb.toString();
    }

    /**
     * Combine a path (possibly with parameters) and a parameters map to form a path info with a query string.
     */
    // TODO: Move to PathUtils.
    public static String pathInfoParametersToPathInfoQueryString(String path, Map<String, String[]> parameters) throws IOException {
        final StringBuilder redirectURL = new StringBuilder(path);
        if (parameters != null) {
            boolean first = ! path.contains("?");
            for (String name : parameters.keySet()) {
                final String[] values = parameters.get(name);
                for (final String currentValue : values) {
                    redirectURL.append(first ? "?" : "&");
                    redirectURL.append(URLEncoder.encode(name, NetUtils.STANDARD_PARAMETER_ENCODING));
                    redirectURL.append("=");
                    redirectURL.append(URLEncoder.encode(currentValue, NetUtils.STANDARD_PARAMETER_ENCODING));
                    first = false;
                }
            }
        }
        return redirectURL.toString();
    }

    /**
     * Resolve a URI against a base URI. (Be sure to pay attention to the order or parameters.)
     *
     * @param href  URI to resolve (accept human-readable URI)
     * @param base  URI base (accept human-readable URI)
     * @return      resolved URI
     */
    public static String resolveURI(String href, String base) {
        final String resolvedURIString;
        if (base != null) {
            final URI baseURI;
            try {
                baseURI = new URI(MarkupUtils.encodeHRRI(base, true));
            } catch (URISyntaxException e) {
                throw new OXFException(e);
            }
            resolvedURIString = baseURI.resolve(MarkupUtils.encodeHRRI(href, true)).normalize().toString();// normalize to remove "..", etc.
        } else {
            resolvedURIString = MarkupUtils.encodeHRRI(href, true);
        }
        return resolvedURIString;
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
    public static String base64BinaryToAnyURI(String value, int scope, Logger logger) {
        // Convert Base64 to binary first
        final byte[] bytes = base64StringToByteArray(value);

        return inputStreamToAnyURI(new ByteArrayInputStream(bytes), scope, logger);
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

    public static InputStream uriToInputStream(String uri) throws Exception {
        return new URI(uri).toURL().openStream();
    }

    // NOTE: Used by create-test-data.xpl
    //@XPathFunction
    public static String createTemporaryFile(int scope) {
        return inputStreamToAnyURI(new InputStream() {
            @Override
            public int read() {
                return -1;
            }
        }, scope, null);
    }

    /**
     * Convert an InputStream to an xs:anyURI.
     *
     * The implementation creates a temporary file.
     */
    public static String inputStreamToAnyURI(InputStream inputStream, int scope, Logger logger) {
        // Get FileItem
        final FileItem fileItem = prepareFileItemFromInputStream(inputStream, scope, logger);

        // Return a file URL
        final File storeLocation = ((DiskFileItem) fileItem).getStoreLocation();
        // Escape "+" because at least in one environment (JBoss 5.1.0 GA on OS X) not escaping the "+" in a file URL causes later incorrect conversion to space
        return storeLocation.toURI().toString().replace("+", "%2B");
    }

    private static FileItem prepareFileItemFromInputStream(InputStream inputStream, int scope, Logger logger) {
        // Get FileItem
        final FileItem fileItem = prepareFileItem(scope, logger);
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
    public static FileItem prepareFileItem(int scope, Logger logger) {
        // We use the commons file upload utilities to save a file
        if (fileItemFactory == null)
            fileItemFactory = new DiskFileItemFactory(0, getTemporaryDirectory());
        final FileItem fileItem = fileItemFactory.createItem("dummy", "dummy", false, null);
        // Make sure the file is deleted appropriately
        if (scope == REQUEST_SCOPE) {
            deleteFileOnRequestEnd(fileItem, logger);
        } else if (scope == SESSION_SCOPE) {
            deleteFileOnSessionTermination(fileItem, logger);
        } else if (scope == APPLICATION_SCOPE) {
            deleteFileOnApplicationDestroyed(fileItem, logger);
        } else {
            throw new OXFException("Invalid context requested: " + scope);
        }
        // Return FileItem object
        return fileItem;
    }

    /**
     * Add listener to fileItem which is going to be automatically destroyed at the end of request
     *
     * @param fileItem        FileItem
     */
    private static void deleteFileOnRequestEnd(final FileItem fileItem, final Logger logger) {
        // Make sure the file is deleted at the end of request
        PipelineContext.get().addContextListener(new PipelineContext.ContextListenerAdapter() {
            public void contextDestroyed(boolean success) {
                deleteFileItem(fileItem, REQUEST_SCOPE, logger);
            }
        });
    }

    /**
     * Add listener to fileItem which is going to be automatically destroyed on session destruction
     *
     * @param fileItem        FileItem
     */
    private static void deleteFileOnSessionTermination(final FileItem fileItem, final Logger logger) {
        // Try to delete the file on exit and on session termination
        final ExternalContext externalContext = getExternalContext();
        final ExternalContext.Session session = externalContext.getSession(false);
        if (session != null) {
            try {
                session.addListener(new ExternalContext.SessionListener() {
                    public void sessionDestroyed(ExternalContext.Session session) {
                        deleteFileItem(fileItem, SESSION_SCOPE, logger);
                    }
                });
            } catch (IllegalStateException e) {
                logger.info("Unable to add session listener: " + e.getMessage());
                deleteFileItem(fileItem, SESSION_SCOPE, logger); // remove immediately
                throw e;
            }
        } else if (logger != null) {
            logger.debug("No existing session found so cannot register temporary file deletion upon session destruction: " + fileItem.getName());
        }
    }

    /**
     * Add listener to fileItem which is going to be automatically destroyed when the servlet is destroyed
     *
     * @param fileItem        FileItem
     */
    private static void deleteFileOnApplicationDestroyed(final FileItem fileItem, final Logger logger) {
        // Try to delete the file on exit and on session termination
        final ExternalContext externalContext = getExternalContext();
        externalContext.getWebAppContext().addListener(new WebAppListener() {
            public void webAppDestroyed() {
                deleteFileItem(fileItem, APPLICATION_SCOPE, logger);
            }
        });
    }

    public static void anyURIToOutputStream(String value, OutputStream outputStream) {
        InputStream is = null;
        try {
            is = URLFactory.createURL(value).openStream();
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
     * Get the current external context.
     *
     * @return  external context if found, null otherwise
     */
    public static ExternalContext getExternalContext() {
        final PipelineContext pipelineContext = PipelineContext.get();
        return (pipelineContext != null) ? (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT) : null;
    }

    /**
     * Get the current session.
     *
     * Can return null if the external context is not found or if the session doesn't exist and is not forced.
     *
     * @return  session if found, null otherwise
     */
    public static ExternalContext.Session getSession(boolean create) {
        final ExternalContext externalContext = NetUtils.getExternalContext();
        return (externalContext != null) ? externalContext.getSession(create) : null;
    }

    public static void deleteFile(final File file, final Logger logger) {

        final boolean success = file.delete();

        if (logger != null && logger.isDebugEnabled()) {
            try {
                final String message =
                    success
                    ? "deleted temporary file upon session destruction: "
                    : "could not delete temporary file upon session destruction: ";

                logger.debug(message + file.getCanonicalPath());
            } catch (IOException e) {
                // NOP because as result of getCanonicalPath() and we don't care
            }
        }
    }

    private static void deleteFileItem(FileItem fileItem, int scope, Logger logger) {

        fileItem.delete();

        if (logger != null && logger.isDebugEnabled() && fileItem instanceof DiskFileItem) {
            final File storeLocation = ((DiskFileItem) fileItem).getStoreLocation();
            if (storeLocation != null) {
                final String temporaryFileName = storeLocation.getAbsolutePath();
                final String scopeString = (scope == REQUEST_SCOPE) ? "request" : (scope == SESSION_SCOPE) ? "session" : "application";
                logger.debug("deleting temporary " + scopeString + "-scoped file upon session destruction: " + temporaryFileName);
            }
        }
    }

    public static File renameAndExpireWithSession(String existingFileURI, final Logger logger) {

        try {
            // Assume the file will be deleted with the request so rename it first
            final String newPath;
            {
                final File newFile = File.createTempFile("xforms_upload_", null);
                newPath = newFile.getCanonicalPath();
                newFile.delete();
            }
            final File oldFile = new File(new URI(existingFileURI));
            final File newFile = new File(newPath);
            final boolean success = oldFile.renameTo(newFile);
            try {
                final String message = success ? "renamed temporary file" : "could not rename temporary file";
                logger.debug(message + " from " + oldFile.getCanonicalPath() + " to " + newFile.getCanonicalPath());
            } catch (IOException e) {
                // NOP
            }

            // Mark deletion of the file on exit and on session termination
            {
                newFile.deleteOnExit();
                final ExternalContext.Session session = getExternalContext().getSession(false);
                if (session != null) {
                    try {
                        session.addListener(new ExternalContext.SessionListener() {
                            public void sessionDestroyed(ExternalContext.Session session) {
                                deleteFile(newFile, logger);
                            }
                        });
                    } catch (IllegalStateException e) {
                        logger.info("Unable to add session listener: " + e.getMessage());
                        deleteFile(newFile, logger); // remove immediately
                        throw e;
                    }
                } else {
                    logger.debug("no existing session found so cannot register temporary file deletion upon session destruction: " + newFile.getCanonicalPath());
                }
            }
            return newFile;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Get a File object from either a URL or a path.
     */
    public static File getFile(String configDirectory, String configFile, String configUrl, LocationData locationData, boolean makeDirectories) {

        return configUrl == null ?
                getFile(configDirectory, configFile, makeDirectories)
                : getFile(configUrl, locationData, makeDirectories);
    }

    /**
     * Find the real path of an oxf: or file: URL.
     */
    public static String getRealPath(String configUrl, LocationData locationData) {
        // Use location data if present so that relative URLs can be supported
        final URL fullURL = (locationData != null && locationData.file() != null)
                ? URLFactory.createURL(locationData.file(), configUrl)
                : URLFactory.createURL(configUrl);

        final String realPath;
        if (fullURL.getProtocol().equals("oxf")) {
            // Get real path to resource path if possible
            realPath = ResourceManagerWrapper.instance().getRealPath(fullURL.getFile());
            if (realPath == null)
                throw new OXFException("Unable to obtain the real path of the file using the oxf: protocol for URL: " + configUrl);
        } else if (fullURL.getProtocol().equals("file")) {
            String host = fullURL.getHost();
            realPath = host + (host.length() > 0 ? ":" : "") + fullURL.getFile();
        } else {
            throw new OXFException("Only the file: and oxf: protocols are supported for URL: " + configUrl);
        }

        return realPath;
    }

    /**
     * Get a File object for an oxf: or file: URL.
     */
    private static File getFile(String configUrl, LocationData locationData, boolean makeDirectories) {
        return getFile(null, getRealPath(configUrl, locationData), makeDirectories);
    }

    /**
     * Get a File object from a path.
     */
    private static File getFile(String configDirectory, String configFile, boolean makeDirectories) {

        final File file;
        if (configDirectory == null) {
            // No base directory specified
            file = new File(configFile);
        } else {
            // Base directory specified
            final File baseDirectory = new File(configDirectory);

            // Make directories if needed
            if (makeDirectories) {
                if (!baseDirectory.exists()) {
                    if (!baseDirectory.mkdirs())
                        throw new OXFException("Directory '" + baseDirectory + "' could not be created.");
                }
            }

            if (!baseDirectory.isDirectory() || !baseDirectory.canWrite())
                throw new OXFException("Directory '" + baseDirectory + "' is not a directory or is not writeable.");

            file = new File(baseDirectory, configFile);
        }
        // Make directories if needed
        if (makeDirectories) {
            if (!file.getParentFile().exists()) {
                if (!file.getParentFile().mkdirs())
                    throw new OXFException("Directory '" + file.getParentFile() + "' could not be created.");
            }
        }

        return file;
    }
}
