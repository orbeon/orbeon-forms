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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.resources.ResourceNotFoundException;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XPathUtils;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ResourceServer extends ProcessorImpl {

    public static final String RESOURCE_SERVER_NAMESPACE_URI = "http://www.orbeon.com/oxf/resource-server";
    public static final String MIMETYPES_NAMESPACE_URI = "http://www.orbeon.com/oxf/mime-types";

    public static final String MIMETYPE_INPUT = "mime-types";

    public ResourceServer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, RESOURCE_SERVER_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(MIMETYPE_INPUT, MIMETYPES_NAMESPACE_URI));
    }

    public void start(org.orbeon.oxf.pipeline.api.PipelineContext context) {
        ExternalContext externalContext = (ExternalContext) context.getAttribute(org.orbeon.oxf.pipeline.api.PipelineContext.EXTERNAL_CONTEXT);
        ExternalContext.Response response = externalContext.getResponse();

        MimeTypeConfig mimeTypeConfig = (MimeTypeConfig) readCacheInputAsObject(context, getInputByName(MIMETYPE_INPUT), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                MimeTypesContentHandler ch = new MimeTypesContentHandler();
                readInputAsSAX(context, input, ch);
                return ch.getMimeTypes();
            }
        });

        try {
            // Read config input into a String, cache if possible
            Node configNode = readCacheInputAsDOM(context, INPUT_CONFIG);

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

            URLConnection urlConnection = null;
            InputStream urlConnectionInputStream = null;
            try {
                // Open resource and set headers
                try {
                    URL newURL = URLFactory.createURL(urlString);

                    // Try to open the connection
                    urlConnection = newURL.openConnection();
                    // Get InputStream and make sure it supprorts marks
                    urlConnectionInputStream = urlConnection.getInputStream();

                    // Get date of last modification of resource
                    long lastModified = NetUtils.getLastModified(urlConnection);

                    // Set Last-Modified, required for caching and conditional get
                    response.setCaching(lastModified, false, false);

                    // Check If-Modified-Since and don't return content if condition is met
                    if (!response.checkIfModifiedSince(lastModified, false)) {
                        response.setStatus(ExternalContext.SC_NOT_MODIFIED);
                        return;
                    }

                    // Lookup and set the content type
                    String contentType = mimeTypeConfig.getMimeType(urlString);
                    if (contentType != null)
                        response.setContentType(contentType);

                    int length = urlConnection.getContentLength();
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

    private static class MimeTypesContentHandler extends ForwardingContentHandler {
        public static final String MIMETYPE_ELEMENT = "mime-type";
        public static final String NAME_ELEMENT = "name";
        public static final String PATTERN_ELEMENT = "pattern";

        public static final int NAME_STATUS = 1;
        public static final int EXT_STATUS = 2;

        private int status = 0;
        private StringBuffer buff = new StringBuffer();
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
        private List patternToMimeTypes = new ArrayList();

        public void define(String pattern, String mimeType) {
            patternToMimeTypes.add(new PatternToMimeType(pattern.toLowerCase(), mimeType.toLowerCase()));
        }

        public String getMimeType(String path) {
            path = path.toLowerCase();
            for (Iterator i = patternToMimeTypes.iterator(); i.hasNext();) {
                PatternToMimeType patternToMimeType = (PatternToMimeType) i.next();
                if (patternToMimeType.matches(path))
                    return patternToMimeType.getMimeType();
            }
            return null;
        }
    }
}
