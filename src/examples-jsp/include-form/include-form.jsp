<%@ page import="org.orbeon.oxf.util.WriterOutputStream" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.StringTokenizer" %>
<%@ page import="java.util.HashMap" %>
<%--
    Copyright (C) 2008 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
--%>
<html>
    <head>
        <title>Include Form</title>
        <style type="text/css">
        </style>
    </head>
    <body>
        <p>
            Below is an included form.
        </p>
        <%-- NOTE: We can't use jsp:include, because it doesn't handle OutputStream correctly --%>
        <%
            out.flush();
            final JspWriter myout = out;
            // Example 1: This gets the current servlet context
            //final ServletContext servletContext = getServletConfig().getServletContext();
            // Example 2: This gets another servlet's context (cross-context)
            final ServletContext servletContext = getServletConfig().getServletContext().getContext("/orbeon");
            final RequestDispatcher dispatcher = servletContext.getRequestDispatcher("/fr/orbeon/bookshelf/summary?orbeon-embeddable=true");

            dispatcher.include(request, new HttpServletResponseWrapper(response) {

                // Handle the case where the included Servlet calls getOutputStream(). Since the JSP only handles
                // writers.
                private WriterOutputStream os = new WriterOutputStream(myout);

                public ServletOutputStream getOutputStream() throws IOException {
                    return new ServletOutputStream() {
                        public void write(int b) throws IOException {
                            os.write(b);
                        }

                        public void flush() throws IOException {
                            os.flush();
                        }
                    };
                }

                public void setContentType(String contentType) {
                    // TODO: Handle non-text XML types at some point (which may not have a charset parameter)
                    final String charset = getContentTypeCharset(contentType);
                    os.setCharset(charset);
                }

                // This is copied from NetUtils
                public String getContentTypeCharset(String contentType) {
                    final Map parameters = getContentTypeParameters(contentType);
                    return (String) ((parameters == null) ? null : parameters.get("charset"));
                }

                public Map getContentTypeParameters(String contentType) {
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
            });
        %>
        <p>
            This is a footer after the included form.
        </p>
    </body>
</html>
