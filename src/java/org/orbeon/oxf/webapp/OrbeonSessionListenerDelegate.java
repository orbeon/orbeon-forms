/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.webapp;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.servlet.ServletExternalContext;
import org.orbeon.oxf.util.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.Iterator;

/**
 * This listener listens for HTTP session lifecycle changes.
 */
public class OrbeonSessionListenerDelegate implements HttpSessionListener {

    private static Logger logger = LoggerFactory.createLogger(OrbeonSessionListener.class);

    private static final String INIT_PROCESSOR_PROPERTY_PREFIX = "oxf.session-created-processor.";
    private static final String INIT_PROCESSOR_INPUT_PROPERTY = "oxf.session-created-processor.input.";
    private static final String DESTROY_PROCESSOR_PROPERTY_PREFIX = "oxf.session-destroyed-processor.";
    private static final String DESTROY_PROCESSOR_INPUT_PROPERTY = "oxf.session-destroyed-processor.input.";

    private static final String LOG_MESSAGE_PREFIX = "Session Listener";

    public void sessionCreated(HttpSessionEvent event) {
        final HttpSession httpSession = event.getSession();
        final ServletContext servletContext = httpSession.getServletContext();
        try {
            InitUtils.run(servletContext, httpSession, null, logger, LOG_MESSAGE_PREFIX, "Session created.", INIT_PROCESSOR_PROPERTY_PREFIX, INIT_PROCESSOR_INPUT_PROPERTY);
        } catch (Exception e) {
            logger.error(LOG_MESSAGE_PREFIX + " - Exception when running session creation processor.", OXFException.getRootThrowable(e));
            throw new OXFException(e);
        }
    }

    public void sessionDestroyed(HttpSessionEvent event) {
        final HttpSession httpSession = event.getSession();
        if (httpSession != null) {
            try {
                // Run listeners if any
                if (httpSession.getAttribute(ServletExternalContext.SESSION_LISTENERS) != null) {
                    // Iterate through listeners
                    final ServletExternalContext.SessionListeners listeners = (ServletExternalContext.SessionListeners) httpSession.getAttribute(ServletExternalContext.SESSION_LISTENERS);
                    if (listeners != null) {
                        for (Iterator i = listeners.iterator(); i.hasNext();) {
                            final ExternalContext.Session.SessionListener currentListener = (ExternalContext.Session.SessionListener) i.next();
                            currentListener.sessionDestroyed();
                        }
                    }
                }

                // Run processor
                final ServletContext servletContext = httpSession.getServletContext();
                InitUtils.run(servletContext, httpSession, null, logger, LOG_MESSAGE_PREFIX, "Session destroyed.", DESTROY_PROCESSOR_PROPERTY_PREFIX, DESTROY_PROCESSOR_INPUT_PROPERTY);

            } catch (Exception e) {
                logger.error(LOG_MESSAGE_PREFIX + " - Exception when running session destruction processor.", OXFException.getRootThrowable(e));
                throw new OXFException(e);
            }
        }
    }
}
