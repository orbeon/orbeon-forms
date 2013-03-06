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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.servlet.OrbeonXFormsFilter;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;

/**
 * The trampoline servlet is used by the Orbeon portlet filter. Unlike servlets, portlets cannot get a request
 * dispatcher for a different webapp. So the portlet forwards the request to the trampoline servlet, which resides in
 * the same webapp as the portlet, and the trampoline servlet decides whether to forward to the XForms renderer within
 * the same webapp or to one in a separate webapp if configured to do so.
 */
public class OrbeonTrampolineServlet extends HttpServlet {
    
    private String orbeonContextPath;

    @Override
    public void init() throws ServletException {
        this.orbeonContextPath = getInitParameter(OrbeonXFormsFilter.RENDERER_CONTEXT_PARAMETER_NAME);
    }

    @Override
    public void service(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws ServletException, IOException {
        // Simply forward to the renderer context in the other servlet
        final MyRequestWrapper requestWrapper = new MyRequestWrapper(httpRequest);
        getOrbeonDispatcher(requestWrapper.pathQuery).forward(requestWrapper, httpResponse);
    }

    private RequestDispatcher getOrbeonDispatcher(String path) throws ServletException {
        final RequestDispatcher dispatcher = getOrbeonContext().getRequestDispatcher(path);
        if (dispatcher == null)
            throw new ServletException("Can't find Orbeon Forms request dispatcher.");

        return dispatcher;
    }

    private ServletContext getOrbeonContext() throws ServletException {
        final ServletContext orbeonContext = (orbeonContextPath != null) ? getServletContext().getContext(orbeonContextPath) : getServletContext();
        if (orbeonContext  == null)
            throw new ServletException("Can't find Orbeon Forms context called '" + orbeonContextPath + "'. Check the '"
                    + OrbeonXFormsFilter.RENDERER_CONTEXT_PARAMETER_NAME + "' filter initialization parameter and the <Context crossContext=\"true\"/> attribute.");

        return orbeonContext ;
    }

    private class MyRequestWrapper extends HttpServletRequestWrapper {

        private String method;
        private String pathQuery;

        public MyRequestWrapper(HttpServletRequest request) {
            super(request);
            this.method = (String) request.getAttribute(OrbeonPortletXFormsFilter.PORTLET_METHOD_ATTRIBUTE);
            this.pathQuery = (String) request.getAttribute(OrbeonPortletXFormsFilter.PORTLET_PATH_QUERY_ATTRIBUTE);
        }

        @Override
        public String getPathInfo() {
            final int questionIndex = pathQuery.indexOf('?');
            return (questionIndex != -1) ? pathQuery.substring(0, questionIndex) : pathQuery;
        }

        @Override
        public String getQueryString() {
            final int questionIndex = pathQuery.indexOf('?');
            return (questionIndex != -1) ? pathQuery.substring(questionIndex + 1) : pathQuery;
        }

        @Override
        public String getServletPath() {
            return "";
        }

        @Override
        public String getMethod() {
            return method;
        }
    }
}

