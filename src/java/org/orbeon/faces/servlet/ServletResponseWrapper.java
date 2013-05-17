/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is XML RenderKit for JSF.
 *
 * The Initial Developer of the Original Code is
 * Orbeon, Inc (info@orbeon.com)
 * Portions created by the Initial Developer are Copyright (C) 2002
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 */
package org.orbeon.faces.servlet;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.Locale;

public class ServletResponseWrapper extends HttpServletResponseWrapper {

    public static final String DEFAULT_ENCODING = "iso-8859-1";

    private ServletContext servletContext;
    private SAXParserFactory saxParserFactory;

    final StringWriter[] writer = new StringWriter[1];
    final ByteArrayOutputStream[] byteStream = new ByteArrayOutputStream[1];
    final String[] charset = new String[1];

    private PrintWriter printWriter;
    private ServletOutputStream servletOutputStream;

    public ServletResponseWrapper(ServletContext servletContext, HttpServletResponse response) {
        super(response);
        this.servletContext = servletContext;
    }

    public PrintWriter getWriter() throws IOException {
        if (printWriter == null) {
            writer[0] = new StringWriter();
            printWriter = new PrintWriter(writer[0]);
        }
        return printWriter;
    }

    public ServletOutputStream getOutputStream() throws IOException {
        if (servletOutputStream == null) {
            byteStream[0] = new ByteArrayOutputStream();
            servletOutputStream = new ByteArrayServletOutputStream(byteStream[0]);
        }
        return servletOutputStream;
    }

    public void setContentType(String contentType) {
        charset[0] = getContentTypeCharset(contentType);
        if (Filter.DEBUG)
            servletContext.log("Filtered resource set encoding to: " + charset[0]);
    }

    public void addCookie(Cookie cookie) {
    }

    public void addDateHeader(String s, long l) {
    }

    public void addHeader(String s, String s1) {
    }

    public void addIntHeader(String s, int i) {
    }

    public boolean containsHeader(String s) {
        return false;
    }

    public String encodeRedirectURL(String s) {
        return super.encodeRedirectURL(s);
    }

    public String encodeRedirectUrl(String s) {
        return super.encodeRedirectUrl(s);
    }

    public String encodeURL(String s) {
        return super.encodeURL(s);
    }

    public String encodeUrl(String s) {
        return super.encodeUrl(s);
    }

    public void sendError(int i) throws IOException {
        // NOTE: Should do something?
    }

    public void sendError(int i, String s) throws IOException {
        // NOTE: Should do something?
    }

    public void sendRedirect(String s) throws IOException {
    }

    public void setDateHeader(String s, long l) {
    }

    public void setHeader(String s, String s1) {
    }

    public void setIntHeader(String s, int i) {
    }

    public void setStatus(int i) {
    }

    public void setStatus(int i, String s) {
    }

    public void flushBuffer() throws IOException {
    }

    public int getBufferSize() {
        // NOTE: What makes sense here?
        return super.getBufferSize();
    }

    public String getCharacterEncoding() {
        // NOTE: What makes sense here?
        return super.getCharacterEncoding();
    }

    public Locale getLocale() {
        // NOTE: What makes sense here?
        return super.getLocale();
    }

    public boolean isCommitted() {
        // NOTE: What makes sense here?
        return false;
    }

    public void reset() {
    }

    public void resetBuffer() {
    }

    public void setBufferSize(int i) {
    }

    public void setContentLength(int i) {
    }

    public void setLocale(Locale locale) {
    }

    public void setResponse(ServletResponse servletResponse) {
    }

    public void parse(ContentHandler contentHandler) {
        try {
            // Create InputSource
            InputSource inputSource = null;
            String encoding = (charset[0] != null) ? charset[0] : DEFAULT_ENCODING;
            if (writer[0] != null) {
                String content = writer[0].toString();
                if (content.length() > 0) {
                    if (Filter.DEBUG) {
                        servletContext.log("XML document to parse in filter: ");
                        servletContext.log(content);
                    }
                    inputSource = new InputSource(new StringReader(content));
                }
            } else if (byteStream[0] != null) {
                byte[] content = byteStream[0].toByteArray();
                if (content.length > 0) {
                    if (Filter.DEBUG) {
                        servletContext.log("XML document to parse in filter: ");
                        servletContext.log(new String(content, encoding));
                    }
                    inputSource = new InputSource(new ByteArrayInputStream(content));
                    if (charset[0] != null)
                        inputSource.setEncoding(encoding);
                }
            } else {
                throw new RuntimeException("Filtered resource did not call getWriter() or getOutputStream().");
            }

            // Parse the output only if text was generated
            if (inputSource != null) {
                // Parse the output
                SAXParser parser = getSAXParser();
                XMLReader reader = parser.getXMLReader();
                reader.setContentHandler(contentHandler);

                //inputSource.setSystemId();
                reader.parse(inputSource);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static class ByteArrayServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream byteOutput;

        public ByteArrayServletOutputStream(ByteArrayOutputStream output) {
            this.byteOutput = output;
        }

        public void write(int b) throws IOException {
            byteOutput.write(b);
        }
    }

    /**
     * Return a SAXParser.
     */
    private synchronized SAXParser getSAXParser() throws ServletException {
        try {
            // Create and cache the factory
            if (saxParserFactory == null) {
                saxParserFactory = SAXParserFactory.newInstance();
                saxParserFactory.setFeature("http://xml.org/sax/features/namespaces", true);
                saxParserFactory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
            }
            // Create a new SAXParser
            return saxParserFactory.newSAXParser();
        } catch (Exception e) {
            throw new ServletException("Exception caught while getting SAX parser", e);
        }
    }

    /**
     * Return a charset from a content-type.
     */
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
}
