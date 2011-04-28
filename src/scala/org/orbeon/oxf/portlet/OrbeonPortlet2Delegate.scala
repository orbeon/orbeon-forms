/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.portlet

import javax.portlet._
import util.DynamicVariable

class OrbeonPortlet2Delegate extends OrbeonPortlet2DelegateBase {

    override def processAction(request: ActionRequest, response: ActionResponse) =
        OrbeonPortlet2Delegate.currentPortlet.withValue(this) {
            super.processAction(request, response)
        }

    override def render(request: RenderRequest, response: RenderResponse) =
        OrbeonPortlet2Delegate.currentPortlet.withValue(this) {
            super.render(request, response)
        }

    override def serveResource(request: ResourceRequest, response: ResourceResponse) =
        OrbeonPortlet2Delegate.currentPortlet.withValue(this) {
            super.serveResource(request, response)
        }
}

object OrbeonPortlet2Delegate {
    val currentPortlet = new DynamicVariable[OrbeonPortlet2Delegate](null)
}