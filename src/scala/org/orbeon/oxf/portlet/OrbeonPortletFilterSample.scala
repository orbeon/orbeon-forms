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

package org.orbeon.oxf.portlet

import javax.portlet._

/**
 * Trivial sample of portlet outputting XHTML directly.
 */
class OrbeonPortletFilterSample extends GenericPortlet {
    override def render(request: RenderRequest, response: RenderResponse) {
        response.getWriter write
            <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
                <xh:head>
                    <xh:title>XForms Hello</xh:title>
                    <xf:model>
                        <xf:instance>
                            <first-name/>
                        </xf:instance>
                        <xf:bind ref="instance()" required="true()"/>
                    </xf:model>
                </xh:head>
                <xh:body>
                    <xh:p>
                        <xf:input ref="instance()" incremental="true">
                            <xf:label>Please enter your first name:</xf:label>
                            <xf:alert>First name is required</xf:alert>
                        </xf:input>
                    </xh:p>
                    <xh:p>
                        <xf:output value="if (normalize-space(instance()) = '') then '' else concat('Hello, ', instance(), '!')"/>
                    </xh:p>
                </xh:body>
            </xh:html>.toString
    }
}
