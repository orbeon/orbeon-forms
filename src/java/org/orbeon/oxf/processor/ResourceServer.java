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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.ResourceNotFoundException;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.UserAgent;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.script.CoffeeScriptCompiler;
import org.orbeon.oxf.xml.ForwardingXMLReceiver;
import org.orbeon.oxf.xml.XPathUtils;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Serve resources to the response.
 */
public class ResourceServer extends ProcessorImpl {

    public static final String RESOURCE_SERVER_NAMESPACE_URI = "http://www.orbeon.com/oxf/resource-server";
    public static final String MIMETYPES_NAMESPACE_URI = "http://www.orbeon.com/oxf/mime-types";

    public static final String MIMETYPE_INPUT = "mime-types";

    public ResourceServer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, RESOURCE_SERVER_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(MIMETYPE_INPUT, MIMETYPES_NAMESPACE_URI));
    }

    public void start(PipelineContext context) {
        final ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        final ExternalContext.Response response = externalContext.getResponse();

        final MimeTypeConfig mimeTypeConfig = (MimeTypeConfig) readCacheInputAsObject(context, getInputByName(MIMETYPE_INPUT), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                final MimeTypesContentHandler ch = new MimeTypesContentHandler();
                readInputAsSAX(context, input, ch);
                return ch.getMimeTypes();
            }
        });

        try {
            // Read config input into a String, cache if possible
            final Node configNode = readCacheInputAsDOM(context, INPUT_CONFIG);

            // Get config URL first
            String urlString = XPathUtils.selectStringValueNormalize(configNode, "url");

            // For backward compatibility, try to get path element
            if (urlString == null) {
                urlString = XPathUtils.selectStringValueNormalize(configNode, "path");

                // There must be a configuration
                if (urlString == null)
                    throw new OXFException("Missing configuration.");

                // Use the default protocol to read the file as a resource
                urlString = "oxf:" + urlString;
            }

            InputStream urlConnectionInputStream = null;
            try {
                // Open resource and set headers
                try {
                    final URL newURL = URLFactory.createURL(urlString);

                    URLConnection urlConnection = null;
                    int length = -1;
                    long lastModified = -1;
                    {
                        final String urlPath = newURL.getPath();
                        if (newURL.getProtocol().equals("oxf")) {

                            // IE 6 hack for PNG images
                            final boolean isIE6 = UserAgent.isRenderingEngineIE6OrEarlier(externalContext.getRequest());
                            if (isIE6) {
                                if (urlPath.endsWith(".png")) {
                                    // Case of a PNG image served to IE 6 or earlier: check if there is a .gif instead
                                    urlString = "oxf:" + urlPath.substring(0, urlPath.length() - 3) + "gif";
                                    final URL gifURL = URLFactory.createURL(urlString);
                                    try {
                                        // Try to get InputStream
                                        final URLConnection gifURLConnection = gifURL.openConnection();
                                        urlConnectionInputStream = gifURLConnection.getInputStream();
                                        // If we get to here, we were successful
                                        urlConnection = gifURLConnection;
                                    } catch (ResourceNotFoundException e) {
                                        // GIF doesn't exist
                                        // NOTE: Exception throwing / catching is expensive so we hope this doesn't happen too often
                                    }
                                }
                            }

                            // Compile CoffeeScript to JavaScript if we have a .coffee and no .js
                            if (! (XFormsProperties.isCombinedResources() || XFormsProperties.isMinimalResources())
                                    && urlPath.endsWith(".js") && ! ResourceManagerWrapper.instance().exists(urlPath)) {
                                final String coffeePath = urlPath.substring(0, urlPath.length() - 2) + "coffee";
                                if (ResourceManagerWrapper.instance().exists(coffeePath)) {
                                    // Open URL for CoffeeScript file
                                    final URL coffeeURL = URLFactory.createURL("oxf:" + coffeePath);
                                    urlConnection = coffeeURL.openConnection();
                                    length = 0; // Unknown length, as it is not just the length of the compiled code
                                    lastModified = NetUtils.getLastModified(urlConnection);
                                    // Read CoffeeScript as a string; CoffeeScript is always UTF-8
                                    Reader coffeeReader = new InputStreamReader(urlConnection.getInputStream(), Charset.forName("UTF-8"));
                                    final String coffeeString = NetUtils.readStreamAsString(coffeeReader);
                                    // TODO: do we need to handle compilation errors?
                                    String javascriptString = CoffeeScriptCompiler.compile(coffeeString, coffeePath, 0);
                                    urlConnectionInputStream = new ByteArrayInputStream(javascriptString.getBytes("UTF-8"));
                                }
                            }
                        }
                    }

                    // Open the connection, if not node already
                    if (urlConnection == null) {
                        urlConnection = newURL.openConnection();
                        urlConnectionInputStream = urlConnection.getInputStream();
                    }
                    // Get length and last modified, if not done already
                    if (length == -1) length = urlConnection.getContentLength();
                    if (lastModified == -1) lastModified = NetUtils.getLastModified(urlConnection);

                    // Set Last-Modified, required for caching and conditional get
                    response.setCaching(lastModified, false, false);

                    // Check If-Modified-Since and don't return content if condition is met
                    if (!response.checkIfModifiedSince(lastModified, false)) {
                        response.setStatus(ExternalContext.SC_NOT_MODIFIED);
                        return;
                    }

                    // Lookup and set the content type
                    final String contentType = mimeTypeConfig.getMimeType(urlString);
                    if (contentType != null)
                        response.setContentType(contentType);

                    if (length > 0)
                        response.setContentLength(length);

                } catch (IOException e) {
                    response.setStatus(ExternalContext.SC_NOT_FOUND);
                    return;
                } catch (ResourceNotFoundException e) {
                    // Note: we should really not get this exception here, but an IOException
                    // However we do actually get it, and so do the same we do for IOException.
                    response.setStatus(ExternalContext.SC_NOT_FOUND);
                    return;
                }
                // Copy stream to output
                NetUtils.copyStream(urlConnectionInputStream, response.getOutputStream());
            } finally {
                // Make sure the stream is closed in all cases so as to not lock the file on disk
                if (urlConnectionInputStream != null) {
                    urlConnectionInputStream.close();
                }
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static class MimeTypesContentHandler extends ForwardingXMLReceiver {
        public static final String MIMETYPE_ELEMENT = "mime-type";
        public static final String NAME_ELEMENT = "name";
        public static final String PATTERN_ELEMENT = "pattern";

        public static final int NAME_STATUS = 1;
        public static final int EXT_STATUS = 2;

        private int status = 0;
        private StringBuilder buff = new StringBuilder();
        private String name;
        private MimeTypeConfig mimeTypeConfig = new MimeTypeConfig();

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            if (NAME_ELEMENT.equals(localname))
                status = NAME_STATUS;
            else if (PATTERN_ELEMENT.equals(localname))
                status = EXT_STATUS;
        }

        public void characters(char[] chars, int start, int length) throws SAXException {
            if (status == NAME_STATUS || status == EXT_STATUS)
                buff.append(chars, start, length);
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {
            if (NAME_ELEMENT.equals(localname)) {
                name = buff.toString().trim();
            } else if (PATTERN_ELEMENT.equals(localname)) {
                mimeTypeConfig.define(buff.toString().trim(), name);
            } else if (MIMETYPE_ELEMENT.equals(localname)) {
                name = null;
            }
            buff.delete(0, buff.length());
        }

        public MimeTypeConfig getMimeTypes() {
            return mimeTypeConfig;
        }
    }

    private static class PatternToMimeType {
        public String pattern;
        public String mimeType;

        public PatternToMimeType(String pattern, String mimeType) {
            this.pattern = pattern;
            this.mimeType = mimeType;
        }

        public boolean matches(String path) {
            if (pattern.equals("*")) {
                return true;
            } else if (pattern.startsWith("*") && pattern.endsWith("*")) {
                String middle = pattern.substring(1, pattern.length() - 1);
                return path.indexOf(middle) != -1;
            } else if (pattern.startsWith("*")) {
                return path.endsWith(pattern.substring(1));
            } else if (pattern.endsWith("*")) {
                return path.startsWith(pattern.substring(0, pattern.length() - 1));
            } else {
                return path.equals(pattern);
            }
        }

        public String getMimeType() {
            return mimeType;
        }
    }

    private static class MimeTypeConfig {
        private List<PatternToMimeType> patternToMimeTypes = new ArrayList<PatternToMimeType>();

        public void define(String pattern, String mimeType) {
            patternToMimeTypes.add(new PatternToMimeType(pattern.toLowerCase(), mimeType.toLowerCase()));
        }

        public String getMimeType(String path) {
            path = path.toLowerCase();
            for (final PatternToMimeType patternToMimeType: patternToMimeTypes) {
                if (patternToMimeType.matches(path))
                    return patternToMimeType.getMimeType();
            }
            return null;
        }
    }
}
