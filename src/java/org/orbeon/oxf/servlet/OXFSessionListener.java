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
package org.orbeon.oxf.servlet;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.util.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * This listener listens for HTTP session lifecycle changes.
 */
public class OXFSessionListener implements HttpSessionListener {

    private static Logger logger = LoggerFactory.createLogger(OXFSessionListener.class);

    public static final String INIT_PROCESSOR_PROPERTY_PREFIX = "oxf.session-created-processor.";
    public static final String INIT_PROCESSOR_INPUT_PROPERTY = "oxf.session-created-processor.input.";
    public static final String DESTROY_PROCESSOR_PROPERTY_PREFIX = "oxf.session-destroyed-processor.";
    public static final String DESTROY_PROCESSOR_INPUT_PROPERTY = "oxf.session-destroyed-processor.input.";

    public void sessionCreated(HttpSessionEvent event) {
        HttpSession httpSession = event.getSession();
        ServletContext servletContext = httpSession.getServletContext();
        try {
            InitUtils.run(servletContext, httpSession, INIT_PROCESSOR_PROPERTY_PREFIX, INIT_PROCESSOR_INPUT_PROPERTY);
        } catch (Exception e) {
            logger.error("Exception when running session creation processor", OXFException.getRootThrowable(e));
            throw new OXFException(e);
        }
    }

    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession httpSession = event.getSession();
        ServletContext servletContext = httpSession.getServletContext();
        try {
            InitUtils.run(servletContext, httpSession, DESTROY_PROCESSOR_PROPERTY_PREFIX, DESTROY_PROCESSOR_INPUT_PROPERTY);
        } catch (Exception e) {
            logger.error("Exception when running session destruction processor", OXFException.getRootThrowable(e));
            throw new OXFException(e);
        }
    }
}
