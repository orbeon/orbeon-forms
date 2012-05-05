/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.portlet

import collection.JavaConverters._
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.webapp.{ServletPortlet, WebAppContext}
import javax.portlet.{PortletException, GenericPortlet}

/**
 * This is the Portlet (JSR-286) entry point of Orbeon.
 *
 * Several servlets and portlets can be used in the same web application. They all share the same context initialization
 * parameters, but each servlet and portlet can be configured with its own main processor and inputs.
 *
 * All servlets and portlets instances in a given web app share the same resource manager.
 */
abstract class OrbeonPortletBase extends GenericPortlet with ServletPortlet {

    def logPrefix = "Portlet"

    // Immutable map of portlet parameters
    lazy val initParameters =
        getInitParameterNames.asScala map
            (n ⇒ n → getInitParameter(n)) toMap

    // Portlet init
    override def init(): Unit =
        withRootException("initialization", new PortletException(_)) {
            Version.instance.checkPEFeature("Orbeon Forms portlet") // this is a PE feature
            init(WebAppContext.instance(getPortletContext), Some("oxf.portlet-initialized-processor." → "oxf.portlet-initialized-processor.input."))
        }

    // Portlet destroy
    override def destroy(): Unit =
        withRootException("destruction", new PortletException(_)) {
            destroy(Some("oxf.portlet-destroyed-processor." → "oxf.portlet-destroyed-processor.input."))
        }
}